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
package io.netty.handler.codec.http3;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.internal.ObjectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;


/**
 * Handler that handles <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32">HTTP3</a> for the server-side.
 */
public final class Http3ServerConnectionHandler extends Http3ConnectionHandler {
    private final ChannelHandler requestStreamHandler;

    /**
     * Create a new instance.
     *
     * @param requestStreamHandler  the {@link ChannelHandler} that is used for each new request stream.
     *                              This handler will receive {@link Http3HeadersFrame} and {@link Http3DataFrame}s.
     */
    public Http3ServerConnectionHandler(ChannelHandler requestStreamHandler) {
        this(requestStreamHandler, null, null, null, true);
    }

    /**
     * Create a new instance.
     * @param requestStreamHandler                  the {@link ChannelHandler} that is used for each new request stream.
     *                                              This handler will receive {@link Http3HeadersFrame} and
     *                                              {@link Http3DataFrame}s.
     * @param inboundControlStreamHandler           the {@link ChannelHandler} which will be notified about
     *                                              {@link Http3RequestStreamFrame}s or {@code null} if the user is not
     *                                              interested in these.
     * @param unknownInboundStreamHandlerFactory    the {@link LongFunction} that will provide a custom
     *                                              {@link ChannelHandler} for unknown inbound stream types or
     *                                              {@code null} if no special handling should be done.
     * @param localSettings                         the local {@link Http3SettingsFrame} that should be sent to the
     *                                              remote peer or {@code null} if the default settings should be used.
     * @param disableQpackDynamicTable              If QPACK dynamic table should be disabled.
     */
    public Http3ServerConnectionHandler(ChannelHandler requestStreamHandler,
                                        @Nullable ChannelHandler inboundControlStreamHandler,
                                        @Nullable LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory,
                                        @Nullable Http3SettingsFrame localSettings, boolean disableQpackDynamicTable) {
        super(true, inboundControlStreamHandler, unknownInboundStreamHandlerFactory, localSettings,
                disableQpackDynamicTable);
        this.requestStreamHandler = ObjectUtil.checkNotNull(requestStreamHandler, "requestStreamHandler");
    }

    @Override
    void initBidirectionalStream(ChannelHandlerContext ctx, QuicStreamChannel streamChannel) {
        ChannelPipeline pipeline = streamChannel.pipeline();
        Http3RequestStreamEncodeStateValidator encodeStateValidator = new Http3RequestStreamEncodeStateValidator();
        Http3RequestStreamDecodeStateValidator decodeStateValidator = new Http3RequestStreamDecodeStateValidator();
        // Add the encoder and decoder in the pipeline so we can handle Http3Frames
        pipeline.addLast(newCodec(encodeStateValidator, decodeStateValidator));
        pipeline.addLast(encodeStateValidator);
        pipeline.addLast(decodeStateValidator);
        pipeline.addLast(newRequestStreamValidationHandler(streamChannel, encodeStateValidator, decodeStateValidator));
        pipeline.addLast(requestStreamHandler);
    }

    @Override
    void initUnidirectionalStream(ChannelHandlerContext ctx, QuicStreamChannel streamChannel) {
        final long maxTableCapacity = maxTableCapacity();
        streamChannel.pipeline().addLast(
                new Http3UnidirectionalStreamInboundServerHandler(codecFactory,
                        localControlStreamHandler, remoteControlStreamHandler,
                        unknownInboundStreamHandlerFactory,
                        () -> new QpackEncoderHandler(maxTableCapacity, qpackDecoder),
                        () -> new QpackDecoderHandler(qpackEncoder)));
    }
}
