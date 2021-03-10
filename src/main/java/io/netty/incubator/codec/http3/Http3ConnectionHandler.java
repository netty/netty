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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;

import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * Handler that handles <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32">HTTP3</a> connections.
 */
public abstract class Http3ConnectionHandler extends ChannelInboundHandlerAdapter {
    private final Function<Http3FrameTypeValidator, Http3FrameCodec> codecFactory;
    private final LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory;
    private final Http3ControlStreamInboundHandler localControlStreamHandler;
    private final Http3ControlStreamOutboundHandler remoteControlStreamHandler;
    private boolean controlStreamCreationInProgress;

    /**
     * Create a new instance.
     * @param server                                {@code true} if server-side, {@code false} otherwise.
     * @param inboundControlStreamHandler           the {@link ChannelHandler} which will be notified about
     *                                              {@link Http3RequestStreamFrame}s or {@code null} if the user is not
     *                                              interested in these.
     * @param unknownInboundStreamHandlerFactory    the {@link LongFunction} that will provide a custom
     *                                              {@link ChannelHandler} for unknown inbound stream types or
     *                                              {@code null} if no special handling should be done.
     * @param localSettings                         the local {@link Http3SettingsFrame} that should be sent to the
     *                                              remote peer or {@code null} if the default settings should be used.
     */
    Http3ConnectionHandler(boolean server, ChannelHandler inboundControlStreamHandler,
                           LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory,
                           Http3SettingsFrame localSettings) {
        this.unknownInboundStreamHandlerFactory = unknownInboundStreamHandlerFactory;
        if (localSettings == null) {
            localSettings = new DefaultHttp3SettingsFrame();
        } else {
            localSettings = DefaultHttp3SettingsFrame.copyOf(localSettings);
        }
        Long maxFieldSectionSize = localSettings.get(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
        if (maxFieldSectionSize == null) {
            maxFieldSectionSize = Http3CodecUtils.DEFAULT_MAX_HEADER_LIST_SIZE;
            localSettings.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, maxFieldSectionSize);
        }
        // As we not support the dynamic table at the moment lets override whatever the user specified and set
        // the capacity to 0.
        localSettings.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L);
        codecFactory = Http3FrameCodec.newFactory(new QpackDecoder(), maxFieldSectionSize, new QpackEncoder());
        localControlStreamHandler = new Http3ControlStreamInboundHandler(server, inboundControlStreamHandler);
        remoteControlStreamHandler =  new Http3ControlStreamOutboundHandler(server, localSettings,
                codecFactory.apply(Http3FrameTypeValidator.NO_VALIDATION));
    }

    private void createControlStreamIfNeeded(ChannelHandlerContext ctx) {
        if (!controlStreamCreationInProgress && Http3.getLocalControlStream(ctx.channel()) == null) {
            controlStreamCreationInProgress = true;
            QuicChannel channel = (QuicChannel) ctx.channel();
            // Once the channel became active we need to create an unidirectional stream and write the
            // Http3SettingsFrame to it. This needs to be the first frame on this stream.
            // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1.
            channel.createStream(QuicStreamType.UNIDIRECTIONAL, remoteControlStreamHandler)
                    .addListener(f -> {
                        if (!f.isSuccess()) {
                            ctx.fireExceptionCaught(new Http3Exception(Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                                    "Unable to open control stream"));
                            ctx.close();
                        } else {
                            Http3.setLocalControlStream(channel, (QuicStreamChannel) f.getNow());
                        }
                    });
        }
    }

    /**
     * Returns {@code true} if we received a GOAWAY frame from the remote peer.
     * @return {@code true} if we received the frame, {@code false} otherwise.
     */
    public final boolean isGoAwayReceived() {
        return localControlStreamHandler.isGoAwayReceived();
    }

    /**
     * Returns a new codec that will encode and decode {@link Http3Frame}s for this HTTP/3 connection.
     *
     * @return a new codec.
     */
    public final ChannelHandler newCodec() {
        return codecFactory.apply(Http3RequestStreamFrameTypeValidator.INSTANCE);
    }

    final Http3RequestStreamValidationHandler newRequestStreamValidationHandler() {
        if (localControlStreamHandler.isServer()) {
            return Http3RequestStreamValidationHandler.newServerValidator();
        }
        return Http3RequestStreamValidationHandler.newClientValidator(localControlStreamHandler::isGoAwayReceived);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            createControlStreamIfNeeded(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        createControlStreamIfNeeded(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof QuicStreamChannel) {
            QuicStreamChannel channel = (QuicStreamChannel) msg;
            switch (channel.type()) {
                case BIDIRECTIONAL:
                    initBidirectionalStream(ctx, channel);
                    break;
                case UNIDIRECTIONAL:
                    channel.pipeline().addLast(
                            new Http3UnidirectionalStreamInboundHandler(codecFactory,
                                    localControlStreamHandler, remoteControlStreamHandler,
                                    unknownInboundStreamHandlerFactory));
                    break;
                default:
                    throw new Error();
            }
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * Called when an bidirectional stream is opened from the remote-peer.
     *
     * @param ctx           the {@link ChannelHandlerContext} of the parent {@link QuicChannel}.
     * @param streamChannel the {@link QuicStreamChannel}.
     */
    abstract void initBidirectionalStream(ChannelHandlerContext ctx, QuicStreamChannel streamChannel);

    /**
     * Always returns {@code false} as it keeps state.
     */
    @Override
    public boolean isSharable() {
        return false;
    }
}
