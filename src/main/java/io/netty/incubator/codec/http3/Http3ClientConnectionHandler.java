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
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.util.function.LongFunction;

public final class Http3ClientConnectionHandler extends Http3ConnectionHandler {

    /**
     * Create a new instance.
     */
    public Http3ClientConnectionHandler() {
        this(null, null, null, false);
    }

    /**
     * Create a new instance.
     *
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
    public Http3ClientConnectionHandler(ChannelHandler inboundControlStreamHandler,
                                        LongFunction<ChannelHandler> unknownInboundStreamHandlerFactory,
                                        Http3SettingsFrame localSettings, boolean disableQpackDynamicTable) {
        super(false, inboundControlStreamHandler, unknownInboundStreamHandlerFactory, localSettings,
                disableQpackDynamicTable);
    }

    @Override
    void initBidirectionalStream(ChannelHandlerContext ctx, QuicStreamChannel channel) {
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-6.1
        Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_STREAM_CREATION_ERROR,
                "Server initiated bidirectional streams are not allowed", true);
    }
}
