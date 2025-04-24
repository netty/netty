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
package io.netty.handler.codec.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NetUtil;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

final class QuicTestUtils {
    static final String[] PROTOS = new String[]{"hq-29"};
    static final SelfSignedCertificate SELF_SIGNED_CERTIFICATE;

    private static final int DATAGRAM_SIZE = 2048;

    static {
        SelfSignedCertificate cert;
        try {
            cert = new SelfSignedCertificate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        SELF_SIGNED_CERTIFICATE = cert;
    }

    private QuicTestUtils() {
    }

    private static final EventLoopGroup GROUP = Epoll.isAvailable() ? new EpollEventLoopGroup() :
            new NioEventLoopGroup();

    static final ChannelHandlerAdapter NOOP_HANDLER = new ChannelHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    GROUP.shutdownGracefully().sync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    static Channel newClient(Executor sslTaskExecutor) throws Exception {
        return newClient(newQuicClientBuilder(sslTaskExecutor));
    }

    private static Bootstrap newBootstrap() {
        Bootstrap bs = new Bootstrap();
        if (GROUP instanceof EpollEventLoopGroup) {
            bs.channel(EpollDatagramChannel.class)
                    // Use recvmmsg when possible.
                    .option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, DATAGRAM_SIZE)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(DATAGRAM_SIZE * 8));
        } else {
            bs.channel(NioDatagramChannel.class)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(DATAGRAM_SIZE));
        }
        return bs.group(GROUP);
    }

    static Channel newClient(QuicClientCodecBuilder builder) throws Exception {
        return newBootstrap()
                // We don't want any special handling of the channel so just use a dummy handler.
                .handler(builder.build())
                .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();
    }

    static QuicChannelBootstrap newQuicChannelBootstrap(Channel channel) {
        QuicChannelBootstrap bs = QuicChannel.newBootstrap(channel);
        if (GROUP instanceof EpollEventLoopGroup) {
            bs.option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                    EpollQuicUtils.newSegmentedAllocator(10));
        }
        return bs;
    }

    static QuicClientCodecBuilder newQuicClientBuilder(Executor sslTaskExecutor) {
        return newQuicClientBuilder(sslTaskExecutor, QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocols(PROTOS).build());
    }

    static QuicClientCodecBuilder newQuicClientBuilder(Executor sslTaskExecutor, QuicSslContext sslContext) {
        return new QuicClientCodecBuilder()
                .sslEngineProvider(q -> sslContext.newEngine(q.alloc()))
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .initialMaxStreamDataUnidirectional(1000000)
                .activeMigration(false).sslTaskExecutor(sslTaskExecutor);
    }

    static QuicServerCodecBuilder newQuicServerBuilder(Executor sslTaskExecutor) {
        return newQuicServerBuilder(sslTaskExecutor, QuicSslContextBuilder.forServer(
                SELF_SIGNED_CERTIFICATE.privateKey(), null, SELF_SIGNED_CERTIFICATE.certificate())
                .applicationProtocols(PROTOS).build());
    }

    static QuicServerCodecBuilder newQuicServerBuilder(Executor sslTaskExecutor, QuicSslContext context) {
        QuicServerCodecBuilder builder = new QuicServerCodecBuilder()
                .sslEngineProvider(q -> context.newEngine(q.alloc()))
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamDataUnidirectional(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .activeMigration(false)
                .sslTaskExecutor(sslTaskExecutor);
        if (GROUP instanceof EpollEventLoopGroup) {
            builder.option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                    EpollQuicUtils.newSegmentedAllocator(10));
        }
        return builder;
    }

    private static Bootstrap newServerBootstrap(QuicServerCodecBuilder serverBuilder,
                                                QuicTokenHandler tokenHandler, @Nullable ChannelHandler handler,
                                                ChannelHandler streamHandler) {
        serverBuilder.tokenHandler(tokenHandler)
                .streamHandler(streamHandler);
        if (handler != null) {
            serverBuilder.handler(handler);
        }
        ChannelHandler codec = serverBuilder.build();
        return newServerBootstrap()
                .handler(codec);
    }

    static Bootstrap newServerBootstrap() {
        return newBootstrap()
                .localAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
    }

    static Channel newServer(QuicServerCodecBuilder serverBuilder, QuicTokenHandler tokenHandler,
                             ChannelHandler handler, ChannelHandler streamHandler)
            throws Exception {
        return newServerBootstrap(serverBuilder, tokenHandler, handler, streamHandler)
                .bind().sync().channel();
    }

    static Channel newServer(Executor sslTaskExecutor, QuicTokenHandler tokenHandler,
                             ChannelHandler handler, ChannelHandler streamHandler)
            throws Exception {
        return newServer(newQuicServerBuilder(sslTaskExecutor), tokenHandler, handler, streamHandler);
    }

    static Channel newServer(Executor sslTaskExecutor, ChannelHandler handler,
                             ChannelHandler streamHandler) throws Exception {
        return newServer(sslTaskExecutor, InsecureQuicTokenHandler.INSTANCE, handler, streamHandler);
    }

    static void closeIfNotNull(@Nullable Channel channel) throws Exception {
        if (channel != null) {
            channel.close().sync();
        }
    }

    @Nullable
    static ChannelOption<Boolean> soReusePortOption() {
        if (GROUP instanceof EpollEventLoopGroup) {
            return EpollChannelOption.SO_REUSEPORT;
        }
        return null;
    }
}
