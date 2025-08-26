/*
 * Copyright 2024 The Netty Project
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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class QuicPortReuseTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(100)
    public void testReusePort(Executor executor) throws Throwable {
        int numBytes = 1000;
        final AtomicInteger byteCounter = new AtomicInteger();

        ChannelOption<Boolean> reusePort = QuicTestUtils.soReusePortOption();
        int numBinds = reusePort == null ? 1 : 4;
        int numConnects = 16;

        List<Channel> serverChannels = new ArrayList<>();
        Bootstrap serverBootstrap = QuicTestUtils.newServerBootstrap()
                .handler(new QuicCodecDispatcher() {
                    @Override
                    protected void initChannel(Channel channel, int localConnectionIdLength,
                                               QuicConnectionIdGenerator idGenerator) {
                        ChannelHandler codec = QuicTestUtils.newQuicServerBuilder(executor)
                                .localConnectionIdLength(localConnectionIdLength)
                                .connectionIdAddressGenerator(idGenerator)
                                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                                .handler(QuicTestUtils.NOOP_HANDLER)
                                .streamHandler(new ChannelInboundHandlerAdapter() {

                                    @Override
                                    public boolean isSharable() {
                                        return true;
                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        byteCounter.addAndGet(((ByteBuf) msg).readableBytes());
                                        ReferenceCountUtil.release(msg);
                                    }
                                }).build();
                        channel.pipeline().addLast(codec);
                    }
                });

        if (reusePort != null) {
            serverBootstrap.option(reusePort, true);
        }

        SocketAddress bindAddress = null;
        for (int i = 0; i < numBinds; i++) {
            Channel bindChannel;
            if (bindAddress == null) {
                bindChannel = serverBootstrap.bind().sync().channel();
            } else {
                bindChannel = serverBootstrap.bind(bindAddress).sync().channel();
            }
            serverChannels.add(bindChannel);
            if (bindAddress == null) {
                bindAddress = bindChannel.localAddress();
            }
        }

        Channel channel = QuicTestUtils.newClient(executor);
        QuicChannelBootstrap cb = QuicTestUtils.newQuicChannelBootstrap(channel)
                .handler(QuicTestUtils.NOOP_HANDLER)
                .streamHandler(QuicTestUtils.NOOP_HANDLER)
                .remoteAddress(bindAddress);
        List<QuicChannel> channels = new ArrayList<>();

        try {
            for (int i = 0; i < numConnects; i++) {
                channels.add(cb
                        .connect()
                        .get());
            }

            for (QuicChannel quicChannel: channels) {
                quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.writeAndFlush(Unpooled.directBuffer().writeZero(numBytes))
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        });
            }

            while (byteCounter.get() != numConnects * numBytes) {
                Thread.sleep(100);
            }
            for (QuicChannel quicChannel: channels) {
                quicChannel.close().sync();
            }
            for (Channel serverChannel: serverChannels) {
                serverChannel.close().sync();
            }
        } finally {
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }
}
