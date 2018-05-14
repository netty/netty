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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CoalescingBufferQueue;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayDeque;

import static io.netty.handler.codec.http.HttpStatusClass.INFORMATIONAL;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.min;

/**
 * Default implementation of {@link Http2ConnectionEncoder}.
 */
@UnstableApi
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
        Http2HeadersEncoder.Configuration outboundHeaderConfig = config.headersConfiguration();
        Http2FrameSizePolicy outboundFrameSizePolicy = config.frameSizePolicy();
        if (pushEnabled != null) {
            if (!connection.isServer() && pushEnabled) {
                throw connectionError(PROTOCOL_ERROR,
                    "Client received a value of ENABLE_PUSH specified to other than 0");
            }
            connection.remote().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            connection.local().maxActiveStreams((int) min(maxConcurrentStreams, MAX_VALUE));
        }

        Long headerTableSize = settings.headerTableSize();
        if (headerTableSize != null) {
            outboundHeaderConfig.maxHeaderTableSize((int) min(headerTableSize, MAX_VALUE));
        }

        Long maxHeaderListSize = settings.maxHeaderListSize();
        if (maxHeaderListSize != null) {
            outboundHeaderConfig.maxHeaderListSize(maxHeaderListSize);
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
                    throw new IllegalStateException("Stream " + stream.id() + " in unexpected state " + stream.state());
            }
        } catch (Throwable e) {
            data.release();
            return promise.setFailure(e);
        }

        // Hand control of the frame to the flow controller.
        flowController().addFlowControlled(stream,
                new FlowControlledData(stream, data, padding, endOfStream, promise));
        return promise;
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, ChannelPromise promise) {
        return writeHeaders(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream, promise);
    }

    private static boolean validateHeadersSentState(Http2Stream stream, Http2Headers headers, boolean isServer,
                                                    boolean endOfStream) {
        boolean isInformational = isServer && HttpStatusClass.valueOf(headers.status()) == INFORMATIONAL;
        if ((isInformational || !endOfStream) && stream.isHeadersSent() || stream.isTrailersSent()) {
            throw new IllegalStateException("Stream " + stream.id() + " sent too many headers EOS: " + endOfStream);
        }
        return isInformational;
    }

    @Override
    public ChannelFuture writeHeaders(final ChannelHandlerContext ctx, final int streamId,
            final Http2Headers headers, final int streamDependency, final short weight,
            final boolean exclusive, final int padding, final boolean endOfStream, ChannelPromise promise) {
        try {
            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                try {
                    stream = connection.local().createStream(streamId, endOfStream);
                } catch (Http2Exception cause) {
                    if (connection.remote().mayHaveCreatedStream(streamId)) {
                        promise.tryFailure(new IllegalStateException("Stream no longer exists: " + streamId, cause));
                        return promise;
                    }
                    throw cause;
                }
            } else {
                switch (stream.state()) {
                    case RESERVED_LOCAL:
                        stream.open(endOfStream);
                        break;
                    case OPEN:
                    case HALF_CLOSED_REMOTE:
                        // Allowed sending headers in these states.
                        break;
                    default:
                        throw new IllegalStateException("Stream " + stream.id() + " in unexpected state " +
                                                        stream.state());
                }
            }

            // Trailing headers must go through flow control if there are other frames queued in flow control
            // for this stream.
            Http2RemoteFlowController flowController = flowController();
            if (!endOfStream || !flowController.hasFlowControlled(stream)) {
                boolean isInformational = validateHeadersSentState(stream, headers, connection.isServer(), endOfStream);
                if (endOfStream) {
                    final Http2Stream finalStream = stream;
                    final ChannelFutureListener closeStreamLocalListener = new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            lifecycleManager.closeStreamLocal(finalStream, future);
                        }
                    };
                    promise = promise.unvoid().addListener(closeStreamLocalListener);
                }

                ChannelFuture future = frameWriter.writeHeaders(ctx, streamId, headers, streamDependency,
                                                                weight, exclusive, padding, endOfStream, promise);
                // Writing headers may fail during the encode state if they violate HPACK limits.
                Throwable failureCause = future.cause();
                if (failureCause == null) {
                    // Synchronously set the headersSent flag to ensure that we do not subsequently write
                    // other headers containing pseudo-header fields.
                    //
                    // This just sets internal stream state which is used elsewhere in the codec and doesn't
                    // necessarily mean the write will complete successfully.
                    stream.headersSent(isInformational);

                    if (!future.isSuccess()) {
                        // Either the future is not done or failed in the meantime.
                        notifyLifecycleManagerOnError(future, ctx);
                    }
                } else {
                    lifecycleManager.onError(ctx, true, failureCause);
                }

                return future;
            } else {
                // Pass headers to the flow-controller so it can maintain their sequence relative to DATA frames.
                flowController.addFlowControlled(stream,
                        new FlowControlledHeaders(stream, headers, streamDependency, weight, exclusive, padding,
                                                  true, promise));
                return promise;
            }
        } catch (Throwable t) {
            lifecycleManager.onError(ctx, true, t);
            promise.tryFailure(t);
            return promise;
        }
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive, ChannelPromise promise) {
        return frameWriter.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
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

        return frameWriter.writeSettings(ctx, settings, promise);
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        return frameWriter.writeSettingsAck(ctx, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
        return frameWriter.writePing(ctx, ack, data, promise);
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

            ChannelFuture future = frameWriter.writePushPromise(ctx, streamId, promisedStreamId, headers, padding,
                                                                promise);
            // Writing headers may fail during the encode state if they violate HPACK limits.
            Throwable failureCause = future.cause();
            if (failureCause == null) {
                // This just sets internal stream state which is used elsewhere in the codec and doesn't
                // necessarily mean the write will complete successfully.
                stream.pushPromiseSent();

                if (!future.isSuccess()) {
                    // Either the future is not done or failed in the meantime.
                    notifyLifecycleManagerOnError(future, ctx);
                }
            } else {
                lifecycleManager.onError(ctx, true, failureCause);
            }
            return future;
        } catch (Throwable t) {
            lifecycleManager.onError(ctx, true, t);
            promise.tryFailure(t);
            return promise;
        }
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
        private final CoalescingBufferQueue queue;
        private int dataSize;

        FlowControlledData(Http2Stream stream, ByteBuf buf, int padding, boolean endOfStream,
                                   ChannelPromise promise) {
            super(stream, padding, endOfStream, promise);
            queue = new CoalescingBufferQueue(promise.channel());
            queue.add(buf, promise);
            dataSize = queue.readableBytes();
        }

        @Override
        public int size() {
            return dataSize + padding;
        }

        @Override
        public void error(ChannelHandlerContext ctx, Throwable cause) {
            queue.releaseAndFailAll(cause);
            // Don't update dataSize because we need to ensure the size() method returns a consistent size even after
            // error so we don't invalidate flow control when returning bytes to flow control.
            lifecycleManager.onError(ctx, true, cause);
        }

        @Override
        public void write(ChannelHandlerContext ctx, int allowedBytes) {
            int queuedData = queue.readableBytes();
            if (!endOfStream) {
                if (queuedData == 0) {
                    // There's no need to write any data frames because there are only empty data frames in the queue
                    // and it is not end of stream yet. Just complete their promises by getting the buffer corresponding
                    // to 0 bytes and writing it to the channel (to preserve notification order).
                    ChannelPromise writePromise = ctx.newPromise().addListener(this);
                    ctx.write(queue.remove(0, writePromise), writePromise);
                    return;
                }

                if (allowedBytes == 0) {
                    return;
                }
            }

            // Determine how much data to write.
            int writableData = min(queuedData, allowedBytes);
            ChannelPromise writePromise = ctx.newPromise().addListener(this);
            ByteBuf toWrite = queue.remove(writableData, writePromise);
            dataSize = queue.readableBytes();

            // Determine how much padding to write.
            int writablePadding = min(allowedBytes - writableData, padding);
            padding -= writablePadding;

            // Write the frame(s).
            frameWriter().writeData(ctx, stream.id(), toWrite, writablePadding,
                    endOfStream && size() == 0, writePromise);
        }

        @Override
        public boolean merge(ChannelHandlerContext ctx, Http2RemoteFlowController.FlowControlled next) {
            FlowControlledData nextData;
            if (FlowControlledData.class != next.getClass() ||
                MAX_VALUE - (nextData = (FlowControlledData) next).size() < size()) {
                return false;
            }
            nextData.queue.copyTo(queue);
            dataSize = queue.readableBytes();
            // Given that we're merging data into a frame it doesn't really make sense to accumulate padding.
            padding = Math.max(padding, nextData.padding);
            endOfStream = nextData.endOfStream;
            return true;
        }
    }

    private void notifyLifecycleManagerOnError(ChannelFuture future, final ChannelHandlerContext ctx) {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Throwable cause = future.cause();
                if (cause != null) {
                    lifecycleManager.onError(ctx, true, cause);
                }
            }
        });
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

        FlowControlledHeaders(Http2Stream stream, Http2Headers headers, int streamDependency, short weight,
                boolean exclusive, int padding, boolean endOfStream, ChannelPromise promise) {
            super(stream, padding, endOfStream, promise);
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
        public void error(ChannelHandlerContext ctx, Throwable cause) {
            if (ctx != null) {
                lifecycleManager.onError(ctx, true, cause);
            }
            promise.tryFailure(cause);
        }

        @Override
        public void write(ChannelHandlerContext ctx, int allowedBytes) {
            boolean isInformational = validateHeadersSentState(stream, headers, connection.isServer(), endOfStream);
            if (promise.isVoid()) {
                promise = ctx.newPromise();
            }
            promise.addListener(this);

            ChannelFuture f = frameWriter.writeHeaders(ctx, stream.id(), headers, streamDependency, weight, exclusive,
                                                       padding, endOfStream, promise);
            // Writing headers may fail during the encode state if they violate HPACK limits.
            Throwable failureCause = f.cause();
            if (failureCause == null) {
                // This just sets internal stream state which is used elsewhere in the codec and doesn't
                // necessarily mean the write will complete successfully.
                stream.headersSent(isInformational);
            }
        }

        @Override
        public boolean merge(ChannelHandlerContext ctx, Http2RemoteFlowController.FlowControlled next) {
            return false;
        }
    }

    /**
     * Common base type for payloads to deliver via flow-control.
     */
    public abstract class FlowControlledBase implements Http2RemoteFlowController.FlowControlled,
            ChannelFutureListener {
        protected final Http2Stream stream;
        protected ChannelPromise promise;
        protected boolean endOfStream;
        protected int padding;

        FlowControlledBase(final Http2Stream stream, int padding, boolean endOfStream,
                final ChannelPromise promise) {
            if (padding < 0) {
                throw new IllegalArgumentException("padding must be >= 0");
            }
            this.padding = padding;
            this.endOfStream = endOfStream;
            this.stream = stream;
            this.promise = promise;
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
                error(flowController().channelHandlerContext(), future.cause());
            }
        }
    }
}
