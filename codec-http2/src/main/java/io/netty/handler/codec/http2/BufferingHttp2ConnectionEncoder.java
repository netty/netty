/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

/**
 * Implementation of a {@link Http2ConnectionEncoder} that dispatches all method call to another
 * {@link Http2ConnectionEncoder}, until {@code SETTINGS_MAX_CONCURRENT_STREAMS} is reached.
 * <p/>
 * <p>When this limit is hit, instead of rejecting any new streams this implementation buffers newly
 * created streams and their corresponding frames. Once an active stream gets closed or the maximum
 * number of concurrent streams is increased, this encoder will automatically try to empty its
 * buffer and create as many new streams as possible.
 * <p/>
 * <p>This implementation makes the buffering mostly transparent and can be used as a drop in
 * replacement for {@link io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder}.
 */
public class BufferingHttp2ConnectionEncoder extends DecoratingHttp2ConnectionEncoder {

    /**
     * Thrown by {@link BufferingHttp2ConnectionEncoder} if buffered streams are terminated due to
     * receipt of a {@code GOAWAY}.
     */
    public static final class GoAwayClosedStreamException extends Http2Exception {
        private static final long serialVersionUID = 1326785622777291198L;
        private final int lastStreamId;
        private final long errorCode;
        private final ByteBuf debugData;

        public GoAwayClosedStreamException(int lastStreamId, long errorCode, ByteBuf debugData) {
            super(Http2Error.STREAM_CLOSED);
            this.lastStreamId = lastStreamId;
            this.errorCode = errorCode;
            this.debugData = debugData;
        }

        public int lastStreamId() {
            return lastStreamId;
        }

        public long errorCode() {
            return errorCode;
        }

        public ByteBuf debugData() {
            return debugData;
        }
    }

    /**
     * Buffer for any streams and corresponding frames that could not be created due to the maximum
     * concurrent stream limit being hit.
     */
    private final TreeMap<Integer, PendingStream> pendingStreams =
            new TreeMap<Integer, PendingStream>();
    private final int initialMaxConcurrentStreams;
    // Smallest stream id whose corresponding frames do not get buffered.
    private int largestCreatedStreamId;
    private boolean receivedSettings;

    public BufferingHttp2ConnectionEncoder(Http2ConnectionEncoder delegate) {
        this(delegate, SMALLEST_MAX_CONCURRENT_STREAMS);
    }

    public BufferingHttp2ConnectionEncoder(Http2ConnectionEncoder delegate,
                                           int initialMaxConcurrentStreams) {
        super(delegate);
        this.initialMaxConcurrentStreams = initialMaxConcurrentStreams;
        connection().addListener(new Http2ConnectionAdapter() {

            @Override
            public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
                cancelGoAwayStreams(lastStreamId, errorCode, debugData);
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                tryCreatePendingStreams();
            }
        });
    }

    /**
     * Indicates the number of streams that are currently buffered, awaiting creation.
     */
    public int numBufferedStreams() {
        return pendingStreams.size();
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                      int padding, boolean endStream, ChannelPromise promise) {
        return writeHeaders(ctx, streamId, headers, 0, Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                false,
                padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                      int streamDependency, short weight, boolean exclusive,
                                      int padding, boolean endOfStream, ChannelPromise promise) {
        if (existingStream(streamId) || connection().goAwayReceived()) {
            return super.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endOfStream, promise);
        }
        if (canCreateStream()) {
            assert streamId > largestCreatedStreamId;
            largestCreatedStreamId = streamId;
            return super.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endOfStream, promise);
        }
        PendingStream pendingStream = pendingStreams.get(streamId);
        if (pendingStream == null) {
            pendingStream = new PendingStream(ctx, streamId);
            pendingStreams.put(streamId, pendingStream);
        }
        pendingStream.frames.add(new HeadersFrame(headers, streamDependency, weight, exclusive,
                padding, endOfStream, promise));
        return promise;
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
                                        ChannelPromise promise) {
        if (existingStream(streamId)) {
            return super.writeRstStream(ctx, streamId, errorCode, promise);
        }
        // Since the delegate doesn't know about any buffered streams we have to handle cancellation
        // of the promises and releasing of the ByteBufs here.
        PendingStream stream = pendingStreams.remove(streamId);
        if (stream != null) {
            // Sending a RST_STREAM to a buffered stream will succeed the promise of all frames
            // associated with the stream, as sending a RST_STREAM means that someone "doesn't care"
            // about the stream anymore and thus there is not point in failing the promises and invoking
            // error handling routines.
            stream.close(null);
            promise.setSuccess();
        } else {
            promise.setFailure(
                    connectionError(PROTOCOL_ERROR, "Stream does not exist %d", streamId));
        }
        return promise;
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                   int padding,
                                   boolean endOfStream, ChannelPromise promise) {
        if (existingStream(streamId)) {
            return super.writeData(ctx, streamId, data, padding, endOfStream, promise);
        }
        PendingStream pendingStream = pendingStreams.get(streamId);
        if (pendingStream != null) {
            pendingStream.frames.add(new DataFrame(data, padding, endOfStream, promise));
        } else {
            ReferenceCountUtil.safeRelease(data);
            promise.setFailure(
                    connectionError(PROTOCOL_ERROR, "Stream does not exist %d", streamId));
        }
        return promise;
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        receivedSettings = true;
        ChannelFuture future = super.writeSettingsAck(ctx, promise);
        // After having received a SETTINGS frame, the maximum number of concurrent streams
        // might have changed. So try to create some buffered streams.
        tryCreatePendingStreams();
        return future;
    }

    @Override
    public void close() {
        super.close();
        cancelPendingStreams();
    }

    private void tryCreatePendingStreams() {
        while (!pendingStreams.isEmpty() && canCreateStream()) {
            Map.Entry<Integer, PendingStream> entry = pendingStreams.pollFirstEntry();
            PendingStream pendingStream = entry.getValue();
            pendingStream.sendFrames();
            largestCreatedStreamId = pendingStream.streamId;
        }
    }

    private void cancelPendingStreams() {
        Exception e = new Exception("Connection closed.");
        while (!pendingStreams.isEmpty()) {
            PendingStream stream = pendingStreams.pollFirstEntry().getValue();
            stream.close(e);
        }
    }

    private void cancelGoAwayStreams(int lastStreamId, long errorCode, ByteBuf debugData) {
        Iterator<PendingStream> iter = pendingStreams.values().iterator();
        Exception e = new GoAwayClosedStreamException(lastStreamId, errorCode, debugData);
        while (iter.hasNext()) {
            PendingStream stream = iter.next();
            if (stream.streamId > lastStreamId) {
                iter.remove();
                stream.close(e);
            }
        }
    }

    /**
     * Determines whether or not we're allowed to create a new stream right now.
     */
    private boolean canCreateStream() {
        Http2Connection.Endpoint<?> local = connection().local();
        return (receivedSettings || local.numActiveStreams() < initialMaxConcurrentStreams)
                && local.canCreateStream();
    }

    private boolean existingStream(int streamId) {
        return streamId <= largestCreatedStreamId;
    }

    private static class PendingStream {
        final ChannelHandlerContext ctx;
        final int streamId;
        final Queue<Frame> frames = new ArrayDeque<Frame>(2);

        PendingStream(ChannelHandlerContext ctx, int streamId) {
            this.ctx = ctx;
            this.streamId = streamId;
        }

        void sendFrames() {
            for (Frame frame : frames) {
                frame.send(ctx, streamId);
            }
        }

        void close(Throwable t) {
            for (Frame frame : frames) {
                frame.release(t);
            }
        }
    }

    private abstract static class Frame {
        final ChannelPromise promise;

        Frame(ChannelPromise promise) {
            this.promise = promise;
        }

        /**
         * Release any resources (features, buffers, ...) associated with the frame.
         */
        void release(Throwable t) {
            if (t == null) {
                promise.setSuccess();
            } else {
                promise.setFailure(t);
            }
        }

        abstract void send(ChannelHandlerContext ctx, int streamId);
    }

    private class HeadersFrame extends Frame {
        final Http2Headers headers;
        final int streamDependency;
        final short weight;
        final boolean exclusive;
        final int padding;
        final boolean endOfStream;

        HeadersFrame(Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                     int padding, boolean endOfStream, ChannelPromise promise) {
            super(promise);
            this.headers = headers;
            this.streamDependency = streamDependency;
            this.weight = weight;
            this.exclusive = exclusive;
            this.padding = padding;
            this.endOfStream = endOfStream;
        }

        @Override
        void send(ChannelHandlerContext ctx, int streamId) {
            writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding,
                    endOfStream, promise);
        }
    }

    private class DataFrame extends Frame {
        final ByteBuf data;
        final int padding;
        final boolean endOfStream;

        DataFrame(ByteBuf data, int padding, boolean endOfStream, ChannelPromise promise) {
            super(promise);
            this.data = data;
            this.padding = padding;
            this.endOfStream = endOfStream;
        }

        @Override
        public void release(Throwable t) {
            super.release(t);
            ReferenceCountUtil.safeRelease(data);
        }

        @Override
        void send(ChannelHandlerContext ctx, int streamId) {
            writeData(ctx, streamId, data, padding, endOfStream, promise);
        }
    }
}
