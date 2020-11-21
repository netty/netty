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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class QuicConnectionStatsTest {

    @Test
    public void testStatsAreCollected() throws Throwable {
        Channel server = null;
        QuicChannel client = null;
        AtomicInteger counter = new AtomicInteger();

        try {
            Promise<QuicConnectionStats> serverActiveStats = ImmediateEventExecutor.INSTANCE.newPromise();
            Promise<QuicConnectionStats> serverInactiveStats = ImmediateEventExecutor.INSTANCE.newPromise();
            server = QuicTestUtils.newServer(new ChannelInboundHandlerAdapter() {
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
            }, new ChannelInboundHandlerAdapter() {

                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
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

            client = (QuicChannel) QuicTestUtils.newChannelBuilder(new ChannelInboundHandlerAdapter(), null)
                    .connect(QuicConnectionAddress.random((InetSocketAddress) server.localAddress())).sync().channel();
            assertNotNull(client.collectStats().sync().getNow());
            client.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
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
                        ctx.close().addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) {
                                // Close the underlying QuicChannel as well.
                                future.channel().parent().close();
                                //
                            }
                        });
                    }
                }
            }).sync();

            // Wait until closure
            client.closeFuture().sync();
            assertStats(client.collectStats().sync().getNow());
            assertNotNull(serverActiveStats.sync().getNow());
            assertStats(serverInactiveStats.sync().getNow());
            assertEquals(1, counter.get());
        } finally {
            QuicTestUtils.closeParent(client);
            if (server != null) {
                server.close().sync();
            }
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
