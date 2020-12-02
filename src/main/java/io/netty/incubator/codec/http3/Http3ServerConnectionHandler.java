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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.function.Supplier;

/**
 * Handler that handles <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32">HTTP3</a> for the server-side.
 */
public final class Http3ServerConnectionHandler extends ChannelInboundHandlerAdapter {
    private static final long DEFAULT_MAX_HEADER_LIST_SIZE = 0xffffffffL;

    private final Supplier<Http3FrameCodec> codecSupplier =
            Http3FrameCodec.newSupplier(new QpackDecoder(), DEFAULT_MAX_HEADER_LIST_SIZE, new QpackEncoder());
    private final Http3SettingsFrame localSettings;
    private final ChannelHandler requestStreamHandler;
    private final ChannelHandler controlStreamHandler;

    // TODO: Use this stream to sent other control frames as well.
    private QuicStreamChannel localControlStream;

    /**
     * Create a new instance.
     *
     * @param requestStreamHandler  the {@link ChannelHandler} that is used for each new request stream.
     *                              This handler will receive {@link Http3HeadersFrame} and {@link Http3DataFrame}s.
     */
    public Http3ServerConnectionHandler(ChannelHandler requestStreamHandler) {
        this(requestStreamHandler, null, null);
    }

    /**
     * Create a new instance.
     * @param requestStreamHandler  the {@link ChannelHandler} that is used for each new request stream.
     *                              This handler will receive {@link Http3HeadersFrame} and {@link Http3DataFrame}s.
     * @param controlStreamHandler  the {@link ChannelHandler} which will be notified about
     *                              {@link Http3RequestStreamFrame}s or {@code null} if the user is not interested
     *                              in these.
     * @param localSettings         the local {@link Http3SettingsFrame} that should be sent to the remote peer or
     *                              {@code null} if the default settings should be used.
     */
    public Http3ServerConnectionHandler(ChannelHandler requestStreamHandler, ChannelHandler controlStreamHandler,
                                        Http3SettingsFrame localSettings) {
        this.requestStreamHandler = ObjectUtil.checkNotNull(requestStreamHandler, "requestStreamHandler");
        this.controlStreamHandler = controlStreamHandler;
        this.localSettings = localSettings;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        QuicChannel channel = (QuicChannel) ctx.channel();
        // Once the channel became active we need to create an unidirectional stream and write the Http3SettingsFrame
        // to it. This needs to be the first frame on this stream.
        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1.
        channel.createStream(QuicStreamType.UNIDIRECTIONAL, new Http3ServerOutboundControlStreamHandler())
                .addListener(f -> {
            if (!f.isSuccess()) {
                // TODO: Handle me the right way.
                ctx.close();
            }
        });

        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        QuicStreamChannel channel = (QuicStreamChannel) msg;
        ChannelPipeline pipeline = channel.pipeline();
        switch (channel.type()) {
            case BIDIRECTIONAL:
                // Add the encoder and decoder in the pipeline so we can handle Http3Frames
                pipeline.addLast(codecSupplier.get());
                pipeline.addLast(new Http3RequestStreamValidationHandler());
                pipeline.addLast(requestStreamHandler);
                break;
            case UNIDIRECTIONAL:
                pipeline.addLast(new Http3ServerUnidirectionalStreamHandler(codecSupplier, controlStreamHandler));
                break;
            default:
                throw new Error();
        }
        ctx.fireChannelRead(msg);
    }

    private static void readIfNoAutoRead(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }

    private final class Http3ServerOutboundControlStreamHandler
            extends Http3FrameTypeValidationHandler<Http3ControlStreamFrame> {
        Http3ServerOutboundControlStreamHandler() {
            super(Http3ControlStreamFrame.class);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            localControlStream = (QuicStreamChannel) ctx.channel();

            // We need to write 0x00 into the stream before doing anything else.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
            // Just allocate 8 bytes which would be the max needed.
            ByteBuf buffer = ctx.alloc().directBuffer(8);
            Http3CodecUtils.writeVariableLengthInteger(buffer, 0x00);
            ctx.write(buffer);
            // Add the encoder and decoder in the pipeline so we can handle Http3Frames
            ctx.pipeline().addFirst(codecSupplier.get());
            final Http3SettingsFrame settingsFrame;
            if (localSettings == null) {
                settingsFrame = new DefaultHttp3SettingsFrame();
            } else {
                settingsFrame = DefaultHttp3SettingsFrame.copyOf(localSettings);
            }
            // As we not support the dynamic table at the moment lets override whatever the user specified and set
            // the capacity to 0.
            settingsFrame.put(Http3Constants.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L);
            // If writing of the local settings fails let's just teardown the connection.
            localControlStream.writeAndFlush(settingsFrame)
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            ctx.fireChannelActive();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt)  {
            if (evt instanceof ChannelInputShutdownEvent) {
                criticalStreamClosed(ctx);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            criticalStreamClosed(ctx);
            ctx.fireChannelInactive();
        }

        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1
        private void criticalStreamClosed(ChannelHandlerContext ctx) {
            Http3CodecUtils.closeParent(
                    ctx.channel(), Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, "Critical stream closed.");
        }
    }

    private static final class Http3ServerUnidirectionalStreamHandler extends Http3UnidirectionalStreamHandler {
        private final ChannelHandler controlStreamHandler;
        private QuicStreamChannel remoteControlStream;
        private QuicStreamChannel qpackEncoderStream;
        private QuicStreamChannel qpackDecoderStream;

        Http3ServerUnidirectionalStreamHandler(Supplier<Http3FrameCodec> codecSupplier,
                                               ChannelHandler controlStreamHandler) {
            super(codecSupplier);
            this.controlStreamHandler = controlStreamHandler;
        }

        @Override
        protected void initControlStream(QuicStreamChannel channel) {
            if (remoteControlStream == null) {
                remoteControlStream = channel;
                channel.pipeline().addLast(new Http3FrameTypeValidationHandler<Http3ControlStreamFrame>(
                        Http3ControlStreamFrame.class) {
                    private boolean firstFrameRead;

                    @Override
                    public void channelRead(ChannelHandlerContext ctx,  Http3ControlStreamFrame frame) {
                        final boolean firstFrame;
                        if (!firstFrameRead) {
                            firstFrameRead = true;
                            firstFrame = true;
                        } else {
                            firstFrame = false;
                        }
                        if (frame instanceof Http3SettingsFrame) {
                            // TODO: handle this.
                            Http3SettingsFrame settingsFrame = (Http3SettingsFrame) frame;
                            // As we not support dynamic table yet we dont create QPACK encoder / decoder streams.
                            // Once we support dynamic table this should be done here as well.
                            // TODO: Add QPACK stream creation.

                        } else if (firstFrame) {
                            // Only one control stream is allowed.
                            // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-6.2.1
                            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_MISSING_SETTINGS,
                                    "Missing settings frame.");
                        } else {
                            // TODO: Handle all other frames
                        }
                        if (controlStreamHandler != null) {
                            // The user did specify ChannelHandler that should be notified about control stream frames.
                            // Let's forward the frame so the user can do something with it.
                            ctx.fireChannelRead(frame);
                        } else {
                            // We handled the frame, release it.
                            ReferenceCountUtil.release(frame);
                        }
                    }

                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                        ctx.fireChannelReadComplete();

                        // control streams should always be processed, no matter what the user is doing in terms of
                        // configuration and AUTO_READ.
                        readIfNoAutoRead(ctx);
                    }
                });
                if (controlStreamHandler != null) {
                    // The user want's to be notified about control frames, add the handler to the pipeline.
                    channel.pipeline().addLast(controlStreamHandler);
                }
            } else {
                // Only one control stream is allowed.
                // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-6.2.1
                Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                        "Received multiple control streams.");
            }
        }

        @Override
        protected void initPushStream(QuicStreamChannel channel, long id) {
            Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                    "Server received push stream.");
        }

        @Override
        protected void initQpackEncoderStream(QuicStreamChannel channel) {
            if (qpackEncoderStream == null) {
                qpackEncoderStream = channel;
                // Just drop stuff on the floor as we dont support dynamic table atm.
                channel.pipeline().addLast(QpackStreamHandler.INSTANCE);
            } else {
                // Only one stream is allowed.
                // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
                Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                        "Received multiple QPACK encoder streams.");
            }
        }

        @Override
        protected void initQpackDecoderStream(QuicStreamChannel channel) {
            if (qpackDecoderStream == null) {
                qpackDecoderStream = channel;
                // Just drop stuff on the floor as we dont support dynamic table atm.
                channel.pipeline().addLast(QpackStreamHandler.INSTANCE);
            } else {
                // Only one stream is allowed.
                // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
                Http3CodecUtils.closeParent(channel, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                        "Received multiple QPACK decoder streams.");
            }
        }
    }

    private static final class QpackStreamHandler extends ChannelInboundHandlerAdapter {
        static final QpackStreamHandler INSTANCE = new QpackStreamHandler();

        private QpackStreamHandler() { }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.fireChannelReadComplete();

            // QPACK streams should always be processed, no matter what the user is doing in terms of configuration
            // and AUTO_READ.
            readIfNoAutoRead(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof ChannelInputShutdownEvent) {
                criticalStreamClosed(ctx);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)  {
            criticalStreamClosed(ctx);
            ctx.fireChannelInactive();
        }

        // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
        private void criticalStreamClosed(ChannelHandlerContext ctx) {
            Http3CodecUtils.closeParent((QuicStreamChannel) ctx.channel(),
                    Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, "Critical QPACK stream closed.");
        }
    }
}
