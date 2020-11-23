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
package io.netty.incubator.codec.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;

final class QuicTestUtils {

    private QuicTestUtils() { }

    private static final EventLoopGroup GROUP = new NioEventLoopGroup();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                GROUP.shutdownGracefully();
            }
        });
    }
    private static final byte[] PROTOS = new byte[] {
            0x05, 'h', 'q', '-', '2', '9',
            0x05, 'h', 'q', '-', '2', '8',
            0x05, 'h', 'q', '-', '2', '7',
            0x08, 'h', 't', 't', 'p', '/', '0', '.', '9'
    };

    static QuicChannelBootstrap newChannelBuilder(ChannelHandler handler, ChannelHandler streamHandler)
            throws Exception {
        QuicClientCodecBuilder builder = newQuicClientBuilder();
        if (streamHandler == null) {
            streamHandler = new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ctx.close();
                }

                @Override
                public boolean isSharable() {
                    return true;
                }
            };
        }
        return newChannelBuilder(builder).handler(handler).streamHandler(streamHandler);
    }

    static QuicChannelBootstrap newChannelBuilder(QuicClientCodecBuilder builder) throws Exception {
        Bootstrap bs = new Bootstrap();
        Channel channel = bs.group(GROUP)
                .channel(NioDatagramChannel.class)
                // We don't want any special handling of the channel so just use a dummy handler.
                .handler(builder.build())
                .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();
        return QuicChannel.newBootstrap(channel);
    }

    static QuicClientCodecBuilder newQuicClientBuilder() {
        return new QuicClientCodecBuilder()
                .certificateChain("./src/test/resources/cert.crt")
                .privateKey("./src/test/resources/cert.key")
                .applicationProtocols(PROTOS)
                .maxIdleTimeout(5000)
                .maxUdpPayloadSize(Quic.MAX_DATAGRAM_SIZE)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .initialMaxStreamDataUnidirectional(1000000)
                .disableActiveMigration(true)
                .enableEarlyData();
    }

    static QuicServerCodecBuilder newQuicServerBuilder() {
        return new QuicServerCodecBuilder()
                .certificateChain("./src/test/resources/cert.crt")
                .privateKey("./src/test/resources/cert.key")
                .applicationProtocols(PROTOS)
                .maxIdleTimeout(5000)
                .maxUdpPayloadSize(Quic.MAX_DATAGRAM_SIZE)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamDataUnidirectional(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .disableActiveMigration(true);
    }

    private static Bootstrap newServerBootstrap(QuicServerCodecBuilder serverBuilder,
                                                QuicTokenHandler tokenHandler, ChannelHandler handler,
                                                ChannelHandler streamHandler) {
        serverBuilder.tokenHandler(tokenHandler)
                .streamHandler(streamHandler);
        if (handler != null) {
            serverBuilder.handler(handler);
        }
        ChannelHandler codec = serverBuilder.build();
        Bootstrap bs = new Bootstrap();
        return bs.group(GROUP)
                .channel(NioDatagramChannel.class)
                // We don't want any special handling of the channel so just use a dummy handler.
                .handler(codec)
                .localAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
    }

    static Channel newServer(QuicServerCodecBuilder serverBuilder, QuicTokenHandler tokenHandler,
                             ChannelHandler handler, ChannelHandler streamHandler)
            throws Exception {
        return newServerBootstrap(serverBuilder, tokenHandler, handler, streamHandler)
                .bind().sync().channel();
    }

    static Channel newServer(QuicTokenHandler tokenHandler, ChannelHandler handler, ChannelHandler streamHandler)
            throws Exception {
        return newServer(newQuicServerBuilder(), tokenHandler, handler, streamHandler);
    }

    static Channel newServer(ChannelHandler handler, ChannelHandler streamHandler) throws Exception {
        return newServer(InsecureQuicTokenHandler.INSTANCE, handler, streamHandler);
    }

    static void closeParent(ChannelFuture future) throws Exception {
        if (future != null) {
            closeParent(future.channel());
        }
    }

    static void closeParent(Channel channel) throws Exception {
        if (channel != null) {
            channel.parent().close().sync();
        }
    }
}
