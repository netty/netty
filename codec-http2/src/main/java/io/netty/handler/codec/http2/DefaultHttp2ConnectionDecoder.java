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
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Provides the default implementation for processing inbound frame events
 * and delegates to a {@link Http2FrameListener}
 * <p>
 * This class will read HTTP/2 frames and delegate the events to a {@link Http2FrameListener}
 * <p>
 * This interface enforces inbound flow control functionality through {@link Http2InboundFlowController}
 */
public class DefaultHttp2ConnectionDecoder implements Http2ConnectionDecoder {
    private final Http2FrameListener internalFrameListener = new FrameReadListener();
    private final Http2Connection connection;
    private final Http2LifecycleManager lifecycleManager;
    private final Http2ConnectionEncoder encoder;
    private final Http2FrameReader frameReader;
    private final Http2InboundFlowController inboundFlow;
    private final Http2FrameListener listener;
    private boolean prefaceReceived;

    /**
     * Builder for instances of {@link DefaultHttp2ConnectionDecoder}.
     */
    public static class Builder implements Http2ConnectionDecoder.Builder {
        private Http2Connection connection;
        private Http2LifecycleManager lifecycleManager;
        private Http2ConnectionEncoder encoder;
        private Http2FrameReader frameReader;
        private Http2InboundFlowController inboundFlow;
        private Http2FrameListener listener;

        @Override
        public Builder connection(Http2Connection connection) {
            this.connection = connection;
            return this;
        }

        @Override
        public Builder lifecycleManager(Http2LifecycleManager lifecycleManager) {
            this.lifecycleManager = lifecycleManager;
            return this;
        }

        @Override
        public Builder inboundFlow(Http2InboundFlowController inboundFlow) {
            this.inboundFlow = inboundFlow;
            return this;
        }

        @Override
        public Builder frameReader(Http2FrameReader frameReader) {
            this.frameReader = frameReader;
            return this;
        }

        @Override
        public Builder listener(Http2FrameListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public Builder encoder(Http2ConnectionEncoder encoder) {
            this.encoder = encoder;
            return this;
        }

        @Override
        public Http2ConnectionDecoder build() {
            return new DefaultHttp2ConnectionDecoder(this);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    protected DefaultHttp2ConnectionDecoder(Builder builder) {
        this.connection = checkNotNull(builder.connection, "connection");
        this.frameReader = checkNotNull(builder.frameReader, "frameReader");
        this.lifecycleManager = checkNotNull(builder.lifecycleManager, "lifecycleManager");
        this.encoder = checkNotNull(builder.encoder, "encoder");
        this.inboundFlow = checkNotNull(builder.inboundFlow, "inboundFlow");
        this.listener = checkNotNull(builder.listener, "listener");
    }

    @Override
    public Http2Connection connection() {
        return connection;
    }

    @Override
    public Http2FrameListener listener() {
        return listener;
    }

    @Override
    public boolean prefaceReceived() {
        return prefaceReceived;
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
        settings.initialWindowSize(inboundFlow.initialInboundWindowSize());
        settings.maxConcurrentStreams(connection.remote().maxStreams());
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
                throw protocolError("Server sending SETTINGS frame with ENABLE_PUSH specified");
            }
            connection.local().allowPushTo(pushEnabled);
        }

        Long maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams != null) {
            int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
            connection.remote().maxStreams(value);
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
            inboundFlow.initialInboundWindowSize(initialWindowSize);
        }
    }

    @Override
    public void close() {
        frameReader.close();
    }

    /**
     * Handles all inbound frames from the network.
     */
    private final class FrameReadListener implements Http2FrameListener {

        @Override
        public void onDataRead(final ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream) throws Http2Exception {
            verifyPrefaceReceived();

            // Check if we received a data frame for a stream which is half-closed
            Http2Stream stream = connection.requireStream(streamId);
            stream.verifyState(STREAM_CLOSED, OPEN, HALF_CLOSED_LOCAL);

            // Apply flow control.
            inboundFlow.onDataRead(ctx, streamId, data, padding, endOfStream);

            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            listener.onDataRead(ctx, streamId, data, padding, endOfStream);

            if (endOfStream) {
                lifecycleManager.closeRemoteSide(stream, ctx.newSucceededFuture());
            }
        }

        /**
         * Verifies that the HTTP/2 connection preface has been received from the remote endpoint.
         */
        private void verifyPrefaceReceived() throws Http2Exception {
            if (!prefaceReceived) {
                throw protocolError("Received non-SETTINGS as first frame.");
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                boolean endStream) throws Http2Exception {
            onHeadersRead(ctx, streamId, headers, 0, DEFAULT_PRIORITY_WEIGHT, false, padding, endStream);
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.stream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (connection.goAwaySent() || stream != null && shouldIgnoreFrame(stream)) {
                // Ignore this frame.
                return;
            }

            if (stream == null) {
                stream = connection.createRemoteStream(streamId, endStream);
            } else {
                if (stream.state() == RESERVED_REMOTE) {
                    // Received headers for a reserved push stream ... open it for push to the local endpoint.
                    stream.verifyState(PROTOCOL_ERROR, RESERVED_REMOTE);
                    stream.openForPush();
                } else {
                    // Receiving headers on an existing stream. Make sure the stream is in an allowed state.
                    stream.verifyState(PROTOCOL_ERROR, OPEN, HALF_CLOSED_LOCAL);
                }
            }

            listener.onHeadersRead(ctx, streamId, headers,
                    streamDependency, weight, exclusive, padding, endStream);

            stream.setPriority(streamDependency, weight, exclusive);

            // If the headers completes this stream, close it.
            if (endStream) {
                lifecycleManager.closeRemoteSide(stream, ctx.newSucceededFuture());
            }
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                boolean exclusive) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);

            stream.setPriority(streamDependency, weight, exclusive);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED) {
                // RstStream frames must be ignored for closed streams.
                return;
            }

            stream.terminateReceived();

            listener.onRstStreamRead(ctx, streamId, errorCode);

            lifecycleManager.closeStream(stream, ctx.newSucceededFuture());
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            verifyPrefaceReceived();
            // Apply oldest outstanding local settings here. This is a synchronization point
            // between endpoints.
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
                    throw protocolError("Server sending SETTINGS frame with ENABLE_PUSH specified");
                }
                connection.local().allowPushTo(pushEnabled);
            }

            Long maxConcurrentStreams = settings.maxConcurrentStreams();
            if (maxConcurrentStreams != null) {
                int value = (int) Math.min(maxConcurrentStreams, Integer.MAX_VALUE);
                connection.remote().maxStreams(value);
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
                inboundFlow.initialInboundWindowSize(initialWindowSize);
            }
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            encoder.remoteSettings(settings);

            // Acknowledge receipt of the settings.
            encoder.writeSettingsAck(ctx, ctx.newPromise());
            ctx.flush();

            // We've received at least one non-ack settings frame from the remote endpoint.
            prefaceReceived = true;

            listener.onSettingsRead(ctx, settings);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            // Send an ack back to the remote client.
            // Need to retain the buffer here since it will be released after the write completes.
            encoder.writePing(ctx, true, data.retain(), ctx.newPromise());
            ctx.flush();

            listener.onPingRead(ctx, data);
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            verifyPrefaceReceived();

            listener.onPingAckRead(ctx, data);
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                Http2Headers headers, int padding) throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream parentStream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(parentStream);
            if (shouldIgnoreFrame(parentStream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Reserve the push stream based with a priority based on the current stream's priority.
            connection.remote().reservePushStream(promisedStreamId, parentStream);

            listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            // Don't allow any more connections to be created.
            connection.goAwayReceived(lastStreamId);

            listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                throws Http2Exception {
            verifyPrefaceReceived();

            Http2Stream stream = connection.requireStream(streamId);
            verifyGoAwayNotReceived();
            verifyRstStreamNotReceived(stream);
            if (stream.state() == CLOSED || shouldIgnoreFrame(stream)) {
                // Ignore frames for any stream created after we sent a go-away.
                return;
            }

            // Update the outbound flow controller.
            encoder.updateOutboundWindowSize(streamId, windowSizeIncrement);

            listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                ByteBuf payload) {
            listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
        }

        /**
         * Indicates whether or not frames for the given stream should be ignored based on the state of the
         * stream/connection.
         */
        private boolean shouldIgnoreFrame(Http2Stream stream) {
            if (connection.goAwaySent() && connection.remote().lastStreamCreated() <= stream.id()) {
                // Frames from streams created after we sent a go-away should be ignored.
                // Frames for the connection stream ID (i.e. 0) will always be allowed.
                return true;
            }

            // Also ignore inbound frames after we sent a RST_STREAM frame.
            return stream.isTerminateSent();
        }

        /**
         * Verifies that a GO_AWAY frame was not previously received from the remote endpoint. If it was, throws an
         * exception.
         */
        private void verifyGoAwayNotReceived() throws Http2Exception {
            if (connection.goAwayReceived()) {
                throw protocolError("Received frames after receiving GO_AWAY");
            }
        }

        /**
         * Verifies that a RST_STREAM frame was not previously received for the given stream. If it was, throws an
         * exception.
         */
        private void verifyRstStreamNotReceived(Http2Stream stream) throws Http2Exception {
            if (stream != null && stream.isTerminateReceived()) {
                throw new Http2StreamException(stream.id(), STREAM_CLOSED,
                        "Frame received after receiving RST_STREAM for stream: " + stream.id());
            }
        }
    }
}
