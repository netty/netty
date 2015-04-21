/*
 * Copyright 2014 The Netty Project
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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Exception.ClosedStreamCreationException;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;

/**
 * Default implementation of {@link Http2ConnectionEncoder}.
 */
public class DefaultHttp2ConnectionEncoder implements Http2ConnectionEncoder {
    private final Http2FrameWriter frameWriter;
    private final Http2Connection connection;
    private Http2LifecycleManager lifecycleManager;
    // We prefer ArrayDeque to LinkedList because later will produce more GC.
    // This initial capacity is plenty for SETTINGS traffic.
    private final ArrayDeque<Http2Settings> outstandingLocalSettingsQueue = new ArrayDeque<Http2Settings>(4);

    public DefaultHttp2ConnectionEncoder(Http2Connection connection, Http2FrameWriter frameWriter) {
        this.connection = checkNotNull(connection, "connection");
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        if (connection.remote().flowController() == null) {
            connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection));
        }
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        this.lifecycleManager = checkNotNull(lifecycleManager, "lifecycleManager");
    }

    @Override
    public Http2FrameWriter frameWriter() {
        return frameWriter;
    }

    @Override
    public Http2Connection connection() {
        return connection;
    }

    @Override
    public final Http2RemoteFlowController flowController() {
        return connection().remote().flowController();
    }

    @Override
    public void remoteSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        Http2FrameWriter.Configuration config = configuration();
        Http2HeaderTable outboundHeaderTable = config.headerTable();
        Http2FrameSizePolicy outboundFrameSizePolicy = config.frameSizePolicy();
        if (pushEnabled != null) {
            if (!connection.isServer()) {
                throw connectionError(PROTOCOL_ERROR, "Client received SETTINGS frame with ENABLE_PUSH specified");
            }
            connection.remote().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            connection.local().maxActiveStreams((int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE));
        }

        Long headerTableSize = settings.headerTableSize();
        if (headerTableSize != null) {
            outboundHeaderTable.maxHeaderTableSize((int) Math.min(headerTableSize, Integer.MAX_VALUE));
        }

        Integer maxHeaderListSize = settings.maxHeaderListSize();
        if (maxHeaderListSize != null) {
            outboundHeaderTable.maxHeaderListSize(maxHeaderListSize);
        }

        Integer maxFrameSize = settings.maxFrameSize();
        if (maxFrameSize != null) {
            outboundFrameSizePolicy.maxFrameSize(maxFrameSize);
        }

        Integer initialWindowSize = settings.initialWindowSize();
        if (initialWindowSize != null) {
            flowController().initialWindowSize(initialWindowSize);
        }
    }

    @Override
    public ChannelFuture writeData(final ChannelHandlerContext ctx, final int streamId, ByteBuf data, int padding,
            final boolean endOfStream, ChannelPromise promise) {
        final Http2Stream stream;
        try {
            stream = requireStream(streamId);

            // Verify that the stream is in the appropriate state for sending DATA frames.
            switch (stream.state()) {
                case OPEN:
                case HALF_CLOSED_REMOTE:
                    // Allowed sending DATA frames in these states.
                    break;
                default:
                    throw new IllegalStateException(String.format(
                            "Stream %d in unexpected state: %s", stream.id(), stream.state()));
            }
        } catch (Throwable e) {
            data.release();
            return promise.setFailure(e);
        }

        // Hand control of the frame to the flow controller.
        flowController().sendFlowControlled(ctx, stream,
                new FlowControlledData(ctx, stream, data, padding, endOfStream, promise));
        return promise;
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        return writeHeaders(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId,
            final Http2Headers headers, final int streamDependency, final short weight,
            final boolean exclusive, final int padding, final boolean endOfStream,
            final ChannelPromise promise) {
        try {
            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                stream = connection.local().createStream(streamId);
            }

            switch (stream.state()) {
                case RESERVED_LOCAL:
                case IDLE:
                    stream.open(endOfStream);
                    break;
                case OPEN:
                case HALF_CLOSED_REMOTE:
                    // Allowed sending headers in these states.
                    break;
                default:
                    throw new IllegalStateException(String.format(
                            "Stream %d in unexpected state: %s", stream.id(), stream.state()));
            }

            // Pass headers to the flow-controller so it can maintain their sequence relative to DATA frames.
            flowController().sendFlowControlled(ctx, stream,
                    new FlowControlledHeaders(ctx, stream, headers, streamDependency, weight,
                            exclusive, padding, endOfStream, promise));
            return promise;
        } catch (Http2NoMoreStreamIdsException e) {
            lifecycleManager.onException(ctx, e);
            return promise.setFailure(e);
        } catch (Throwable e) {
            return promise.setFailure(e);
        }
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive, ChannelPromise promise) {
        try {
            // Update the priority on this stream.
            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                stream = connection.local().createStream(streamId);
            }

            // The set priority operation must be done before sending the frame. The parent may not yet exist
            // and the priority tree may also be modified before sending.
            stream.setPriority(streamDependency, weight, exclusive);
        } catch (ClosedStreamCreationException ignored) {
            // It is possible that either the stream for this frame or the parent stream is closed.
            // In this case we should ignore the exception and allow the frame to be sent.
        } catch (Throwable t) {
            return promise.setFailure(t);
        }

        ChannelFuture future = frameWriter.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        // Delegate to the lifecycle manager for proper updating of connection state.
        return lifecycleManager.resetStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings,
            ChannelPromise promise) {
        outstandingLocalSettingsQueue.add(settings);
        try {
            Boolean pushEnabled = settings.pushEnabled();
            if (pushEnabled != null && connection.isServer()) {
                throw connectionError(PROTOCOL_ERROR, "Server sending SETTINGS frame with ENABLE_PUSH specified");
            }
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        ChannelFuture future = frameWriter.writeSettings(ctx, settings, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        ChannelFuture future = frameWriter.writeSettingsAck(ctx, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, ByteBuf data, ChannelPromise promise) {
        ChannelFuture future = frameWriter.writePing(ctx, ack, data, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding, ChannelPromise promise) {
        try {
            if (connection.goAwayReceived()) {
                throw connectionError(PROTOCOL_ERROR, "Sending PUSH_PROMISE after GO_AWAY received.");
            }

            Http2Stream stream = requireStream(streamId);
            // Reserve the promised stream.
            connection.local().reservePushStream(promisedStreamId, stream);
        } catch (Throwable e) {
            return promise.setFailure(e);
        }

        ChannelFuture future = frameWriter.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
        ctx.flush();
        return future;
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
            ChannelPromise promise) {
        return lifecycleManager.goAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement,
            ChannelPromise promise) {
        return promise.setFailure(new UnsupportedOperationException("Use the Http2[Inbound|Outbound]FlowController" +
                " objects to control window sizes"));
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload, ChannelPromise promise) {
        return frameWriter.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    @Override
    public void close() {
        frameWriter.close();
    }

    @Override
    public Http2Settings pollSentSettings() {
        return outstandingLocalSettingsQueue.poll();
    }

    @Override
    public Configuration configuration() {
        return frameWriter.configuration();
    }

    private Http2Stream requireStream(int streamId) {
        Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            final String message;
            if (connection.streamMayHaveExisted(streamId)) {
                message = "Stream no longer exists: " + streamId;
            } else {
                message = "Stream does not exist: " + streamId;
            }
            throw new IllegalArgumentException(message);
        }
        return stream;
    }

    /**
     * Wrap a DATA frame so it can be written subject to flow-control. Note that this implementation assumes it
     * only writes padding once for the entire payload as opposed to writing it once per-frame. This makes the
     * {@link #size} calculation deterministic thereby greatly simplifying the implementation.
     * <p>
     * If frame-splitting is required to fit within max-frame-size and flow-control constraints we ensure that
     * the passed promise is not completed until last frame write.
     * </p>
     */
    private final class FlowControlledData extends FlowControlledBase {
        private ByteBuf data;
        private int size;

        private FlowControlledData(ChannelHandlerContext ctx, Http2Stream stream, ByteBuf data, int padding,
                                    boolean endOfStream, ChannelPromise promise) {
            super(ctx, stream, padding, endOfStream, promise);
            this.data = data;
            size = data.readableBytes() + padding;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void error(Throwable cause) {
            ReferenceCountUtil.safeRelease(data);
            lifecycleManager.onException(ctx, cause);
            data = null;
            size = 0;
            promise.tryFailure(cause);
        }

        @Override
        public boolean write(int allowedBytes) {
            int bytesWritten = 0;
            try {
                if (data == null) {
                    return false;
                }
                if (allowedBytes == 0 && size != 0) {
                    // No point writing an empty DATA frame, wait for a bigger allowance.
                    return false;
                }
                int maxFrameSize = frameWriter().configuration().frameSizePolicy().maxFrameSize();
                do {
                    int allowedFrameSize = Math.min(maxFrameSize, allowedBytes - bytesWritten);
                    ByteBuf toWrite;
                    // Let data consume the frame before padding.
                    int writeableData = data.readableBytes();
                    if (writeableData > allowedFrameSize) {
                        writeableData = allowedFrameSize;
                        toWrite = data.readSlice(writeableData).retain();
                    } else {
                        // We're going to write the full buffer which will cause it to be released, for subsequent
                        // writes just use empty buffer to avoid over-releasing. Have to use an empty buffer
                        // as we may continue to write padding in subsequent frames.
                        toWrite = data;
                        data = Unpooled.EMPTY_BUFFER;
                    }
                    int writeablePadding = Math.min(allowedFrameSize - writeableData, padding);
                    padding -= writeablePadding;
                    bytesWritten += writeableData + writeablePadding;
                    ChannelPromise writePromise;
                    if (size == bytesWritten) {
                        // Can use the original promise if it's the last write
                        writePromise = promise;
                    } else {
                        // Create a new promise and listen to it for failure
                        writePromise = ctx.newPromise();
                        writePromise.addListener(this);
                    }
                    frameWriter().writeData(ctx, stream.id(), toWrite, writeablePadding,
                        size == bytesWritten && endOfStream, writePromise);
                } while (size != bytesWritten && allowedBytes > bytesWritten);
                return true;
            } finally {
                size -= bytesWritten;
            }
        }
    }

    /**
     * Wrap headers so they can be written subject to flow-control. While headers do not have cost against the
     * flow-control window their order with respect to other frames must be maintained, hence if a DATA frame is
     * blocked on flow-control a HEADER frame must wait until this frame has been written.
     */
    private final class FlowControlledHeaders extends FlowControlledBase {

        private final Http2Headers headers;
        private final int streamDependency;
        private final short weight;
        private final boolean exclusive;

        private FlowControlledHeaders(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers,
                int streamDependency, short weight, boolean exclusive, int padding,
                boolean endOfStream, ChannelPromise promise) {
            super(ctx, stream, padding, endOfStream, promise);
            this.headers = headers;
            this.streamDependency = streamDependency;
            this.weight = weight;
            this.exclusive = exclusive;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void error(Throwable cause) {
            lifecycleManager.onException(ctx, cause);
            promise.tryFailure(cause);
        }

        @Override
        public boolean write(int allowedBytes) {
            frameWriter().writeHeaders(ctx, stream.id(), headers, streamDependency, weight, exclusive,
                    padding, endOfStream, promise);
            return true;
        }
    }

    /**
     * Common base type for payloads to deliver via flow-control.
     */
    public abstract class FlowControlledBase implements Http2RemoteFlowController.FlowControlled,
            ChannelFutureListener {
        protected final ChannelHandlerContext ctx;
        protected final Http2Stream stream;
        protected final ChannelPromise promise;
        protected final boolean endOfStream;
        protected int padding;

        public FlowControlledBase(final ChannelHandlerContext ctx, final Http2Stream stream, int padding,
                                  boolean endOfStream, final ChannelPromise promise) {
            this.ctx = ctx;
            if (padding < 0) {
                throw new IllegalArgumentException("padding must be >= 0");
            }
            this.padding = padding;
            this.endOfStream = endOfStream;
            this.stream = stream;
            this.promise = promise;
            // Ensure error() gets called in case something goes wrong after the frame is passed to Netty.
            promise.addListener(this);
        }

        @Override
        public void writeComplete() {
            if (endOfStream) {
                lifecycleManager.closeStreamLocal(stream, promise);
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                error(future.cause());
            }
        }
    }
}
