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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QuicConnectionPathStatsTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testPathStatsAreCollected(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        AtomicInteger counter = new AtomicInteger();

        Promise<QuicConnectionPathStats> serverActiveStats = ImmediateEventExecutor.INSTANCE.newPromise();
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                super.channelActive(ctx);
                collectPathStats(ctx, serverActiveStats);
            }

            private void collectPathStats(ChannelHandlerContext ctx, Promise<QuicConnectionPathStats> promise) {
                QuicheQuicChannel channel = (QuicheQuicChannel) ctx.channel();
                channel.collectPathStats(0, promise);
            }
        };
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();
        try {
            server = QuicTestUtils.newServer(executor, serverHandler, new ChannelInboundHandlerAdapter() {

                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    counter.incrementAndGet();
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    // Let's just echo back the message.
                    ctx.writeAndFlush(msg);
                }

                @Override
                public boolean isSharable() {
                    return true;
                }
            });
            channel = QuicTestUtils.newClient(executor);

            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect().get();
            assertNotNull(quicChannel.collectStats().sync().getNow());
            quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
                private final int bufferSize = 8;
                private int received;

                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    ctx.writeAndFlush(Unpooled.buffer().writeZero(bufferSize));
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ByteBuf buffer = (ByteBuf) msg;
                    received += buffer.readableBytes();
                    buffer.release();
                    if (received == bufferSize) {
                        ctx.close().addListener((ChannelFuture future) -> {
                            // Close the underlying QuicChannel as well.
                            future.channel().parent().close();
                        });
                    }
                }
            }).sync();

            // Wait until closure
            quicChannel.closeFuture().sync();
            assertStats(serverActiveStats.sync().getNow());
            assertEquals(1, counter.get());

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    private static void assertStats(QuicConnectionPathStats stats) {
        assertNotNull(stats);
        assertThat(stats.lost(), greaterThanOrEqualTo(0L));
        assertThat(stats.recv(), greaterThan(0L));
        assertThat(stats.sent(), greaterThan(0L));
        assertThat(stats.sentBytes(), greaterThan(0L));
        assertThat(stats.recvBytes(), greaterThan(0L));
        assertThat(stats.rtt(), greaterThan(0L));
    }
}
