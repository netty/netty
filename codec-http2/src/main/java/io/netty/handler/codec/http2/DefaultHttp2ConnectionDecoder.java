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
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2PromisedRequestVerifier.ALWAYS_VERIFY;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Exception.ClosedStreamCreationException;

import java.util.List;

/**
 * Provides the default implementation for processing inbound frame events and delegates to a
 * {@link Http2FrameListener}
 * <p>
 * This class will read HTTP/2 frames and delegate the events to a {@link Http2FrameListener}
 * <p>
 * This interface enforces inbound flow control functionality through
 * {@link Http2LocalFlowController}
 */
public class DefaultHttp2ConnectionDecoder implements Http2ConnectionDecoder {
    private Http2FrameListener internalFrameListener = new PrefaceFrameListener();
    private final Http2Connection connection;
    private Http2LifecycleManager lifecycleManager;
    private final Http2ConnectionEncoder encoder;
    private final Http2FrameReader frameReader;
    private final Http2FrameListener listener;
    private final Http2PromisedRequestVerifier requestVerifier;

    public DefaultHttp2ConnectionDecoder(Http2Connection connection,
                                         Http2ConnectionEncoder encoder,
                                         Http2FrameReader frameReader,
                                         Http2FrameListener listener) {
        this(connection, encoder, frameReader, listener, ALWAYS_VERIFY);
    }

    public DefaultHttp2ConnectionDecoder(Http2Connection connection,
                                         Http2ConnectionEncoder encoder,
                                         Http2FrameReader frameReader,
                                         Http2FrameListener listener,
                                         Http2PromisedRequestVerifier requestVerifier) {
        this.connection = checkNotNull(connection, "connection");
        this.frameReader = checkNotNull(frameReader, "frameReader");
        this.encoder = checkNotNull(encoder, "encoder");
        this.listener = checkNotNull(listener, "listener");
        this.requestVerifier = checkNotNull(requestVerifier, "requestVerifier");
        if (connection.local().flowController() == null) {
            connection.local().flowController(
                    new DefaultHttp2LocalFlowController(connection, encoder.frameWriter()));
        }
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        this.lifecycleManager = checkNotNull(lifecycleManager, "lifecycleManager");
    }

    @Override
    public Http2Connection connection() {
        return connection;
    }

    @Override
    public final Http2LocalFlowController flowController() {
        return connection.local().flowController();
    }

    @Override
    public Http2FrameListener listener() {
        return listener;
    }

    @Override
    public boolean prefaceReceived() {
        return FrameReadListener.class == internalFrameListener.getClass();
    }

    @Override
    public void decodeFrame(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Http2Exception {
        frameReader.readFrame(ctx, in, internalFrameListener);
    }

    @Override
    public Http2Settings localSettings() {
        Http2Settings settings = new Http2Settings();
        Http2FrameReader.Configuration config = frameReader.configuration();
        Http2HeaderTable headerTable = config.headerTable();
        Http2FrameSizePolicy frameSizePolicy = config.frameSizePolicy();
        settings.initialWindowSize(flowController().initialWindowSize());
        settings.maxConcurrentStreams(connection.remote().maxActiveStreams());
        settings.headerTableSize(headerTable.maxHeaderTableSize());
        settings.maxFrameSize(frameSizePolicy.maxFrameSize());
        settings.maxHeaderListSize(headerTable.maxHeaderListSize());
        if (!connection.isServer()) {
            // Only set the pushEnabled flag if this is a client endpoint.
            settings.pushEnabled(connection.local().allowPushTo());
        }
        return settings;
    }

    @Override
    public void localSettings(Http2Settings settings) throws Http2Exception {
        Boolean pushEnabled = settings.pushEnabled();
        Http2FrameReader.Configuration config = frameReader.configuration();
        Http2HeaderTable inboundHeaderTable = config.headerTable();
        Http2FrameSizePolicy inboundFrameSizePolicy = config.frameSizePolicy();
        if (pushEnabled != null) {
            if (connection.isServer()) {
                throw connectionError(PROTOCOL_ERROR, "Server sending SETTINGS frame with ENABLE_PUSH specified");
            }
            connection.local().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
            connection.remote().maxActiveStreams(value);
        }

        Long headerTableSize = settings.headerTableSize();
        if (headerTableSize != null) {
            inboundHeaderTable.maxHeaderTableSize((int) Math.min(headerTableSize, Integer.MAX_VALUE));
        }

        Integer maxHeaderListSize = settings.maxHeaderListSize();
        if (maxHeaderListSize != null) {
            inboundHeaderTable.maxHeaderListSize(maxHeaderListSize);
        }

        Integer maxFrameSize = settings.maxFrameSize();
        if (maxFrameSize != null) {
            inboundFrameSizePolicy.maxFrameSize(maxFrameSize);
        }

        Integer initialWindowSize = settings.initialWindowSize();
        if (initialWindowSize != null) {
            flowController().initialWindowSize(initialWindowSize);
        }
    }

    @Override
    public void close() {
        frameReader.close();
    }

    private int unconsumedBytes(Http2Stream stream) {
        return flowController().unconsumedBytes(stream);
    }

    void onGoAwayRead0(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
        listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        connection.goAwayReceived(lastStreamId, errorCode, debugData);
    }

    void onUnknownFrame0(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload) throws Http2Exception {
        listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
    }

    /**
     * Handles all inbound frames from the network.
     */
    private final class FrameReadListener implements Http2FrameListener {
        @Override
        public int onDataRead(final ChannelHandlerContext ctx, int streamId, ByteBuf data,
                int padding, boolean endOfStream) throws Http2Exception {
            Http2Stream stream = connection.stream(streamId);
            Http2LocalFlowController flowController = flowController();
            int bytesToReturn = data.readableBytes() + padding;

            if (stream == null || stream.isResetSent() || streamCreatedAfterGoAwaySent(streamId)) {
                // Ignoring this frame. We still need to count the frame towards the connection flow control
                // window, but we immediately mark all bytes as consumed.
                flowController.receiveFlowControlledFrame(ctx, stream, data, padding, endOfStream);
                flowController.consumeBytes(ctx, stream, bytesToReturn);

                // Verify that the stream may have existed after we apply flow control.
                verifyStreamMayHaveExisted(streamId);

                // All bytes have been consumed.
                return bytesToReturn;
            }

            Http2Exception error = null;
            switch (stream.state()) {
                case OPEN:
                case HALF_CLOSED_LOCAL:
                    break;
                case HALF_CLOSED_REMOTE:
                case CLOSED:
                    error = streamError(stream.id(), STREAM_CLOSED, "Stream %d in unexpected state: %s",
                        stream.id(), stream.state());
                    break;
                default:
                    error = streamError(stream.id(), PROTOCOL_ERROR,
                        "Stream %d in unexpected state: %s", stream.id(), stream.state());
                    break;
            }

            int unconsumedBytes = unconsumedBytes(stream);
            try {
                flowController.receiveFlowControlledFrame(ctx, stream, data, padding, endOfStream);
                // Update the unconsumed bytes after flow control is applied.
                unconsumedBytes = unconsumedBytes(stream);

                // If the stream is in an invalid state to receive the frame, throw the error.
                if (error != null) {
                    throw error;
                }

                // Call back the application and retrieve the number of bytes that have been
                // immediately processed.
                bytesToReturn = listener.onDataRead(ctx, streamId, data, padding, endOfStream);
                return bytesToReturn;
            } catch (Http2Exception e) {
                // If an exception happened during delivery, the listener may have returned part
                // of the bytes before the error occurred. If that's the case, subtract that from
                // the total processed bytes so that we don't return too many bytes.
                int delta = unconsumedBytes - unconsumedBytes(stream);
                bytesToReturn -= delta;
                throw e;
            } catch (RuntimeException e) {
                // If an exception happened during delivery, the listener may have returned part
                // of the bytes before the error occurred. If that's the case, subtract that from
                // the total processed bytes so that we don't return too many bytes.
                int delta = unconsumedBytes - unconsumedBytes(stream);
                bytesToReturn -= delta;
                throw e;
            } finally {
                // If appropriate, return the processed bytes to the flow controller.
                flowController.consumeBytes(ctx, stream, bytesToReturn);

                if (endOfStream) {
                    lifecycleManager.closeStreamRemote(stream, ctx.newSucceededFuture());
                }
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endOfStream) throws Http2Exception {
            onHeadersRead(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
            Http2Stream stream = connection.stream(streamId);
            boolean allowHalfClosedRemote = false;
            if (stream == null && !connection.streamMayHaveExisted(streamId)) {
                stream = connection.remote().createStream(streamId, endOfStream);
                // Allow the state to be HALF_CLOSE_REMOTE if we're creating it in that state.
                allowHalfClosedRemote = stream.state() == HALF_CLOSED_REMOTE;
            }

            if (stream == null || stream.isResetSent() || streamCreatedAfterGoAwaySent(streamId)) {
                // Ignore this frame.
                return;
            }

            switch (stream.state()) {
                case RESERVED_REMOTE:
                    stream.open(endOfStream);
                    break;
                case OPEN:
                case HALF_CLOSED_LOCAL:
                    // Allowed to receive headers in these states.
                    break;
                case HALF_CLOSED_REMOTE:
                    if (!allowHalfClosedRemote) {
                        throw streamError(stream.id(), STREAM_CLOSED, "Stream %d in unexpected state: %s",
                                stream.id(), stream.state());
                    }
                    break;
                case CLOSED:
                    throw streamError(stream.id(), STREAM_CLOSED, "Stream %d in unexpected state: %s",
                            stream.id(), stream.state());
                default:
                    // Connection error.
                    throw connectionError(PROTOCOL_ERROR, "Stream %d in unexpected state: %s", stream.id(),
                            stream.state());
            }

            try {
                // This call will create a stream for streamDependency if necessary.
                // For this reason it must be done before notifying the listener.
                stream.setPriority(streamDependency, weight, exclusive);
            } catch (ClosedStreamCreationException ignored) {
                // It is possible that either the stream for this frame or the parent stream is closed.
                // In this case we should ignore the exception and allow the frame to be sent.
            }

            listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);

            // If the headers completes this stream, close it.
            if (endOfStream) {
                lifecycleManager.closeStreamRemote(stream, ctx.newSucceededFuture());
            }
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                boolean exclusive) throws Http2Exception {
            Http2Stream stream = connection.stream(streamId);

            try {
                if (stream == null) {
                    if (connection.streamMayHaveExisted(streamId)) {
                        // Ignore this frame.
                        return;
                    }

                    // PRIORITY frames always identify a stream. This means that if a PRIORITY frame is the
                    // first frame to be received for a stream that we must create the stream.
                    stream = connection.remote().createIdleStream(streamId);
                } else if (streamCreatedAfterGoAwaySent(streamId)) {
                    // Ignore this frame.
                    return;
                }

                // This call will create a stream for streamDependency if necessary.
                // For this reason it must be done before notifying the listener.
                stream.setPriority(streamDependency, weight, exclusive);
            } catch (ClosedStreamCreationException ignored) {
                // It is possible that either the stream for this frame or the parent stream is closed.
                // In this case we should ignore the exception and allow the frame to be sent.
            }

            listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            Http2Stream stream = connection.stream(streamId);
            if (stream == null) {
                verifyStreamMayHaveExisted(streamId);
                return;
            }

            switch(stream.state()) {
            case IDLE:
                throw connectionError(PROTOCOL_ERROR, "RST_STREAM received for IDLE stream %d", streamId);
            case CLOSED:
                return; // RST_STREAM frames must be ignored for closed streams.
            default:
                break;
            }

            listener.onRstStreamRead(ctx, streamId, errorCode);

            lifecycleManager.closeStream(stream, ctx.newSucceededFuture());
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            // Apply oldest outstanding local settings here. This is a synchronization point between endpoints.
            Http2Settings settings = encoder.pollSentSettings();

            if (settings != null) {
                applyLocalSettings(settings);
            }

            listener.onSettingsAckRead(ctx);
        }

        /**
         * Applies settings sent from the local endpoint.
         */
        private void applyLocalSettings(Http2Settings settings) throws Http2Exception {
            Boolean pushEnabled = settings.pushEnabled();
            final Http2FrameReader.Configuration config = frameReader.configuration();
            final Http2HeaderTable headerTable = config.headerTable();
            final Http2FrameSizePolicy frameSizePolicy = config.frameSizePolicy();
            if (pushEnabled != null) {
                if (connection.isServer()) {
                    throw connectionError(PROTOCOL_ERROR, "Server sending SETTINGS frame with ENABLE_PUSH specified");
                }
                connection.local().allowPushTo(pushEnabled);
            }

            Long maxConcurrentStreams = settings.maxConcurrentStreams();
            if (maxConcurrentStreams != null) {
                int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
                connection.remote().maxActiveStreams(value);
            }

            Long headerTableSize = settings.headerTableSize();
            if (headerTableSize != null) {
                headerTable.maxHeaderTableSize((int) Math.min(headerTableSize, Integer.MAX_VALUE));
            }

            Integer maxHeaderListSize = settings.maxHeaderListSize();
            if (maxHeaderListSize != null) {
                headerTable.maxHeaderListSize(maxHeaderListSize);
            }

            Integer maxFrameSize = settings.maxFrameSize();
            if (maxFrameSize != null) {
                frameSizePolicy.maxFrameSize(maxFrameSize);
            }

            Integer initialWindowSize = settings.initialWindowSize();
            if (initialWindowSize != null) {
                flowController().initialWindowSize(initialWindowSize);
            }
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            encoder.remoteSettings(settings);

            // Acknowledge receipt of the settings.
            encoder.writeSettingsAck(ctx, ctx.newPromise());

            listener.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            // Send an ack back to the remote client.
            // Need to retain the buffer here since it will be released after the write completes.
            encoder.writePing(ctx, true, data.retain(), ctx.newPromise());

            listener.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            listener.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                Http2Headers headers, int padding) throws Http2Exception {
            Http2Stream parentStream = connection.stream(streamId);

            if (streamCreatedAfterGoAwaySent(streamId)) {
                return;
            }

            if (parentStream == null) {
                throw connectionError(PROTOCOL_ERROR, "Stream does not exist %d", streamId);
            }

            switch (parentStream.state()) {
              case OPEN:
              case HALF_CLOSED_LOCAL:
                  // Allowed to receive push promise in these states.
                  break;
              default:
                  // Connection error.
                  throw connectionError(PROTOCOL_ERROR,
                      "Stream %d in unexpected state for receiving push promise: %s",
                      parentStream.id(), parentStream.state());
            }

            if (!requestVerifier.isAuthoritative(ctx, headers)) {
                throw streamError(promisedStreamId, PROTOCOL_ERROR,
                        "Promised request on stream %d for promised stream %d is not authoritative",
                        streamId, promisedStreamId);
            }
            if (!requestVerifier.isCacheable(headers)) {
                throw streamError(promisedStreamId, PROTOCOL_ERROR,
                        "Promised request on stream %d for promised stream %d is not known to be cacheable",
                        streamId, promisedStreamId);
            }
            if (!requestVerifier.isSafe(headers)) {
                throw streamError(promisedStreamId, PROTOCOL_ERROR,
                        "Promised request on stream %d for promised stream %d is not known to be safe",
                        streamId, promisedStreamId);
            }

            // Reserve the push stream based with a priority based on the current stream's priority.
            connection.remote().reservePushStream(promisedStreamId, parentStream);

            listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            onGoAwayRead0(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            Http2Stream stream = connection.stream(streamId);
            if (stream == null || stream.state() == CLOSED || streamCreatedAfterGoAwaySent(streamId)) {
                // Ignore this frame.
                verifyStreamMayHaveExisted(streamId);
                return;
            }

            // Update the outbound flow control window.
            encoder.flowController().incrementWindowSize(ctx, stream, windowSizeIncrement);

            listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) throws Http2Exception {
            onUnknownFrame0(ctx, frameType, streamId, flags, payload);
        }

        /**
         * Helper method for determining whether or not to ignore inbound frames. A stream is considered to be created
         * after a {@code GOAWAY} is sent if the following conditions hold:
         * <p/>
         * <ul>
         *     <li>A {@code GOAWAY} must have been sent by the local endpoint</li>
         *     <li>The {@code streamId} must identify a legitimate stream id for the remote endpoint to be creating</li>
         *     <li>{@code streamId} is greater than the Last Known Stream ID which was sent by the local endpoint
         *     in the last {@code GOAWAY} frame</li>
         * </ul>
         * <p/>
         */
        private boolean streamCreatedAfterGoAwaySent(int streamId) {
            Http2Connection.Endpoint<?> remote = connection.remote();
            return connection.goAwaySent() && remote.isValidStreamId(streamId) &&
                    streamId > remote.lastStreamKnownByPeer();
        }

        private void verifyStreamMayHaveExisted(int streamId) throws Http2Exception {
            if (!connection.streamMayHaveExisted(streamId)) {
                throw connectionError(PROTOCOL_ERROR, "Stream does not exist %d", streamId);
            }
        }
    }

    private final class PrefaceFrameListener implements Http2FrameListener {
        /**
         * Verifies that the HTTP/2 connection preface has been received from the remote endpoint.
         * It is possible that the current call to
         * {@link Http2FrameReader#readFrame(ChannelHandlerContext, ByteBuf, Http2FrameListener)} will have multiple
         * frames to dispatch. So it may be OK for this class to get legitimate frames for the first readFrame.
         */
        private void verifyPrefaceReceived() throws Http2Exception {
            if (!prefaceReceived()) {
                throw connectionError(PROTOCOL_ERROR, "Received non-SETTINGS as first frame.");
            }
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
                throws Http2Exception {
            verifyPrefaceReceived();
            return internalFrameListener.onDataRead(ctx, streamId, data, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endOfStream) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onHeadersRead(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endOfStream);
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                boolean exclusive) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onRstStreamRead(ctx, streamId, errorCode);
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onSettingsAckRead(ctx);
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            // The first settings should change the internalFrameListener to the "real" listener
            // that expects the preface to be verified.
            if (!prefaceReceived()) {
                internalFrameListener = new FrameReadListener();
            }
            internalFrameListener.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                Http2Headers headers, int padding) throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            onGoAwayRead0(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            verifyPrefaceReceived();
            internalFrameListener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) throws Http2Exception {
            onUnknownFrame0(ctx, frameType, streamId, flags, payload);
        }
    }
}
