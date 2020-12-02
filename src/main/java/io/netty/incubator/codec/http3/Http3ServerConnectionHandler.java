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
import io.netty.channel.ChannelPipeline;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
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
    private final ChannelHandler inboundControlStreamHandler;

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
     * @param requestStreamHandler          the {@link ChannelHandler} that is used for each new request stream.
     *                                      This handler will receive {@link Http3HeadersFrame} and
     *                                      {@link Http3DataFrame}s.
     * @param inboundControlStreamHandler   the {@link ChannelHandler} which will be notified about
     *                                      {@link Http3RequestStreamFrame}s or {@code null} if the user is not
     *                                      interested in these.
     * @param localSettings                 the local {@link Http3SettingsFrame} that should be sent to the remote peer
     *                                      or {@code null} if the default settings should be used.
     */
    public Http3ServerConnectionHandler(ChannelHandler requestStreamHandler, ChannelHandler inboundControlStreamHandler,
                                        Http3SettingsFrame localSettings) {
        this.requestStreamHandler = ObjectUtil.checkNotNull(requestStreamHandler, "requestStreamHandler");
        this.inboundControlStreamHandler = inboundControlStreamHandler;
        this.localSettings = localSettings;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        QuicChannel channel = (QuicChannel) ctx.channel();
        // Once the channel became active we need to create an unidirectional stream and write the Http3SettingsFrame
        // to it. This needs to be the first frame on this stream.
        // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.2.1.
        channel.createStream(QuicStreamType.UNIDIRECTIONAL,
                new Http3ControlStreamOutboundHandler(localSettings, codecSupplier))
                .addListener(f -> {
            if (!f.isSuccess()) {
                // TODO: Handle me the right way.
                ctx.close();
            } else {
                localControlStream = (QuicStreamChannel) f.get();
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
                pipeline.addLast(new Http3RequestStreamValidationHandler(true));
                pipeline.addLast(requestStreamHandler);
                break;
            case UNIDIRECTIONAL:
                pipeline.addLast(
                        new Http3UnidirectionalStreamInboundHandler(true, codecSupplier, inboundControlStreamHandler));
                break;
            default:
                throw new Error();
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * Always returns {@code false} as it keeps state.
     */
    @Override
    public boolean isSharable() {
        return false;
    }
}
