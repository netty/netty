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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class QuicConnectionStatsTest extends AbstractQuicTest {

    @Test
    public void testStatsAreCollected() throws Throwable {
        Channel server = null;
        Channel channel = null;
        AtomicInteger counter = new AtomicInteger();

        Promise<QuicConnectionStats> serverActiveStats = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<QuicConnectionStats> serverInactiveStats = ImmediateEventExecutor.INSTANCE.newPromise();
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                collectStats(ctx, serverActiveStats);
                ctx.fireChannelActive();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                collectStats(ctx, serverInactiveStats);
                ctx.fireChannelInactive();
            }

            private void collectStats(ChannelHandlerContext ctx, Promise<QuicConnectionStats> promise) {
                QuicheQuicChannel channel = (QuicheQuicChannel) ctx.channel();
                channel.collectStats(promise);
            }
        };
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();
        try {
            server = QuicTestUtils.newServer(serverHandler, new ChannelInboundHandlerAdapter() {

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
            channel = QuicTestUtils.newClient();

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
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
            assertStats(quicChannel.collectStats().sync().getNow());
            assertNotNull(serverActiveStats.sync().getNow());
            assertStats(serverInactiveStats.sync().getNow());
            assertEquals(1, counter.get());

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);
        }
    }

    private static void assertStats(QuicConnectionStats stats) {
        assertThat(stats.congestionWindow(), greaterThan(0L));
        assertThat(stats.deliveryRate(), greaterThan(0L));
        assertThat(stats.lost(), greaterThanOrEqualTo(0L));
        assertThat(stats.recv(), greaterThan(0L));
        assertThat(stats.rttNanos(), greaterThan(0L));
        assertThat(stats.sent(), greaterThan(0L));
    }
}
