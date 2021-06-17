/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.channels.ClosedChannelException;

import static io.netty.incubator.codec.http3.Http3CodecUtils.closeOnFailure;
import static io.netty.incubator.codec.http3.Http3CodecUtils.connectionError;
import static io.netty.incubator.codec.http3.Http3CodecUtils.criticalStreamClosed;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_FRAME_UNEXPECTED;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_ID_ERROR;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_MISSING_SETTINGS;
import static io.netty.incubator.codec.http3.Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static io.netty.incubator.codec.http3.QpackUtil.toIntOrThrow;
import static io.netty.util.internal.ThrowableUtil.unknownStackTrace;

final class Http3ControlStreamInboundHandler extends Http3FrameTypeInboundValidationHandler<Http3ControlStreamFrame> {
    final boolean server;
    private final ChannelHandler controlFrameHandler;
    private final QpackEncoder qpackEncoder;
    private final Http3ControlStreamOutboundHandler remoteControlStreamHandler;
    private boolean firstFrameRead;
    private Long receivedGoawayId;
    private Long receivedMaxPushId;

    Http3ControlStreamInboundHandler(boolean server, ChannelHandler controlFrameHandler, QpackEncoder qpackEncoder,
                                     Http3ControlStreamOutboundHandler remoteControlStreamHandler) {
        super(Http3ControlStreamFrame.class);
        this.server = server;
        this.controlFrameHandler = controlFrameHandler;
        this.qpackEncoder = qpackEncoder;
        this.remoteControlStreamHandler = remoteControlStreamHandler;
    }

    boolean isServer() {
        return server;
    }

    boolean isGoAwayReceived() {
        return receivedGoawayId != null;
    }

    long maxPushIdReceived() {
        return receivedMaxPushId == null ? -1 : receivedMaxPushId;
    }

    private boolean forwardControlFrames() {
        return controlFrameHandler != null;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        // The user want's to be notified about control frames, add the handler to the pipeline.
        if (controlFrameHandler != null) {
            ctx.pipeline().addLast(controlFrameHandler);
        }
    }

    @Override
    void readFrameDiscarded(ChannelHandlerContext ctx, Object discardedFrame) {
        if (!firstFrameRead && !(discardedFrame instanceof Http3SettingsFrame)) {
            connectionError(ctx, Http3ErrorCode.H3_MISSING_SETTINGS, "Missing settings frame.", forwardControlFrames());
        }
    }

    @Override
    void channelRead(ChannelHandlerContext ctx, Http3ControlStreamFrame frame) throws QpackException {
        boolean isSettingsFrame = frame instanceof Http3SettingsFrame;
        if (!firstFrameRead && !isSettingsFrame) {
            connectionError(ctx, H3_MISSING_SETTINGS, "Missing settings frame.", forwardControlFrames());
            ReferenceCountUtil.release(frame);
            return;
        }
        if (firstFrameRead && isSettingsFrame) {
            connectionError(ctx, H3_FRAME_UNEXPECTED, "Second settings frame received.", forwardControlFrames());
            ReferenceCountUtil.release(frame);
            return;
        }
        firstFrameRead = true;

        final boolean valid;
        if (isSettingsFrame) {
            valid = handleHttp3SettingsFrame(ctx, (Http3SettingsFrame) frame);
        } else if (frame instanceof Http3GoAwayFrame) {
            valid = handleHttp3GoAwayFrame(ctx, (Http3GoAwayFrame) frame);
        } else if (frame instanceof Http3MaxPushIdFrame) {
            valid = handleHttp3MaxPushIdFrame(ctx, (Http3MaxPushIdFrame) frame);
        } else if (frame instanceof Http3CancelPushFrame) {
            valid = handleHttp3CancelPushFrame(ctx, (Http3CancelPushFrame) frame);
        } else {
            // We don't need to do any special handling for Http3UnknownFrames as we either pass these to the next#
            // handler or release these directly.
            assert frame instanceof Http3UnknownFrame;
            valid = true;
        }

        if (!valid || controlFrameHandler == null) {
            ReferenceCountUtil.release(frame);
            return;
        }

        // The user did specify ChannelHandler that should be notified about control stream frames.
        // Let's forward the frame so the user can do something with it.
        ctx.fireChannelRead(frame);
    }

    private boolean handleHttp3SettingsFrame(ChannelHandlerContext ctx, Http3SettingsFrame settingsFrame)
            throws QpackException {
        final QuicChannel quicChannel = (QuicChannel) ctx.channel().parent();
        final QpackAttributes qpackAttributes = Http3.getQpackAttributes(quicChannel);
        assert qpackAttributes != null;
        final GenericFutureListener<Future<? super QuicStreamChannel>> closeOnFailure = future -> {
            if (!future.isSuccess()) {
                criticalStreamClosed(ctx);
            }
        };
        if (qpackAttributes.dynamicTableDisabled()) {
            qpackEncoder.configureDynamicTable(qpackAttributes, 0, 0);
            return true;
        }
        quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL,
                new QPackEncoderStreamInitializer(qpackEncoder, qpackAttributes,
                        settingsFrame.getOrDefault(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0),
                        settingsFrame.getOrDefault(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 0)))
                .addListener(closeOnFailure);
        quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL, new QPackDecoderStreamInitializer(qpackAttributes))
                .addListener(closeOnFailure);
        return true;
    }

    private boolean handleHttp3GoAwayFrame(ChannelHandlerContext ctx, Http3GoAwayFrame goAwayFrame) {
        long id = goAwayFrame.id();
        if (!server && id % 4 != 0) {
            connectionError(ctx, H3_FRAME_UNEXPECTED, "GOAWAY received with ID of non-request stream.",
                    forwardControlFrames());
            return false;
        }
        if (receivedGoawayId != null && id > receivedGoawayId) {
            connectionError(ctx, H3_ID_ERROR,
                    "GOAWAY received with ID larger than previously received.", forwardControlFrames());
            return false;
        }
        receivedGoawayId = id;
        return true;
    }

    private boolean handleHttp3MaxPushIdFrame(ChannelHandlerContext ctx, Http3MaxPushIdFrame frame) {
        long id = frame.id();
        if (!server) {
            connectionError(ctx, H3_FRAME_UNEXPECTED, "MAX_PUSH_ID received by client.",
                    forwardControlFrames());
            return false;
        }
        if (receivedMaxPushId != null && id < receivedMaxPushId) {
            connectionError(ctx, H3_ID_ERROR, "MAX_PUSH_ID reduced limit.", forwardControlFrames());
            return false;
        }
        receivedMaxPushId = id;
        return true;
    }

    private boolean handleHttp3CancelPushFrame(ChannelHandlerContext ctx, Http3CancelPushFrame cancelPushFrame) {
        final Long maxPushId = server ? receivedMaxPushId : remoteControlStreamHandler.sentMaxPushId();
        if (maxPushId == null || maxPushId < cancelPushFrame.id()) {
            connectionError(ctx, H3_ID_ERROR, "CANCEL_PUSH received with an ID greater than MAX_PUSH_ID.",
                    forwardControlFrames());
            return false;
        }
        return true;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();

        // control streams should always be processed, no matter what the user is doing in terms of
        // configuration and AUTO_READ.
        Http3CodecUtils.readIfNoAutoRead(ctx);
    }

    @Override
    public boolean isSharable() {
        // Not sharable as it keeps state.
        return false;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof ChannelInputShutdownEvent) {
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            criticalStreamClosed(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    private abstract static class AbstractQPackStreamInitializer extends ChannelInboundHandlerAdapter {
        private final int streamType;
        protected final QpackAttributes attributes;

        AbstractQPackStreamInitializer(int streamType, QpackAttributes attributes) {
            this.streamType = streamType;
            this.attributes = attributes;
        }

        @Override
        public final void channelActive(ChannelHandlerContext ctx) {
            // We need to write the streamType into the stream before doing anything else.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
            // Just allocate 8 bytes which would be the max needed.
            ByteBuf buffer = ctx.alloc().buffer(8);
            Http3CodecUtils.writeVariableLengthInteger(buffer, streamType);
            closeOnFailure(ctx.writeAndFlush(buffer));
            streamAvailable(ctx);
            ctx.fireChannelActive();
        }

        @Override
        public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            streamClosed(ctx);
            if (evt instanceof ChannelInputShutdownEvent) {
                // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#section-4.2
                criticalStreamClosed(ctx);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            streamClosed(ctx);
            // See https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#section-4.2
            criticalStreamClosed(ctx);
            ctx.fireChannelInactive();
        }

        protected abstract void streamAvailable(ChannelHandlerContext ctx);

        protected abstract void streamClosed(ChannelHandlerContext ctx);
    }

    private static final class QPackEncoderStreamInitializer extends AbstractQPackStreamInitializer {
        private static final ClosedChannelException ENCODER_STREAM_INACTIVE =
                unknownStackTrace(new ClosedChannelException(), ClosedChannelException.class, "streamClosed()");
        private final QpackEncoder encoder;
        private final long maxTableCapacity;
        private final long blockedStreams;

        QPackEncoderStreamInitializer(QpackEncoder encoder, QpackAttributes attributes, long maxTableCapacity,
                                      long blockedStreams) {
            super(Http3CodecUtils.HTTP3_QPACK_ENCODER_STREAM_TYPE, attributes);
            this.encoder = encoder;
            this.maxTableCapacity = maxTableCapacity;
            this.blockedStreams = blockedStreams;
        }

        @Override
        protected void streamAvailable(ChannelHandlerContext ctx) {
            final QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
            attributes.encoderStream(stream);

            try {
                encoder.configureDynamicTable(attributes, maxTableCapacity, toIntOrThrow(blockedStreams));
            } catch (QpackException e) {
                connectionError(ctx, new Http3Exception(QPACK_ENCODER_STREAM_ERROR,
                        "Dynamic table configuration failed.", e), true);
            }
        }

        @Override
        protected void streamClosed(ChannelHandlerContext ctx) {
            attributes.encoderStreamInactive(ENCODER_STREAM_INACTIVE);
        }
    }

    private static final class QPackDecoderStreamInitializer extends AbstractQPackStreamInitializer {
        private static final ClosedChannelException DECODER_STREAM_INACTIVE =
                unknownStackTrace(new ClosedChannelException(), ClosedChannelException.class, "streamClosed()");
        private QPackDecoderStreamInitializer(QpackAttributes attributes) {
            super(Http3CodecUtils.HTTP3_QPACK_DECODER_STREAM_TYPE, attributes);
        }

        @Override
        protected void streamAvailable(ChannelHandlerContext ctx) {
            attributes.decoderStream((QuicStreamChannel) ctx.channel());
        }

        @Override
        protected void streamClosed(ChannelHandlerContext ctx) {
            attributes.decoderStreamInactive(DECODER_STREAM_INACTIVE);
        }
    }
}
