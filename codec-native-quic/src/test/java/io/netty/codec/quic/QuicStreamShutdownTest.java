/*
 * Copyright 2023 The Netty Project
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
package io.netty.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelOutputShutdownException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuicStreamShutdownTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownInputClosureCausesStreamStopped(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        CountDownLatch latch = new CountDownLatch(2);
        try {
            server = QuicTestUtils.newServer(executor, new ChannelInboundHandlerAdapter(),
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            ChannelFutureListener futureListener = new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture channelFuture) {
                                    Throwable cause = channelFuture.cause();
                                    if (cause instanceof ChannelOutputShutdownException) {
                                        latch.countDown();
                                    }
                                }
                            };
                            ByteBuf buffer = (ByteBuf) msg;
                            ctx.write(buffer.retainedDuplicate()).addListener(futureListener);
                            ctx.writeAndFlush(buffer).addListener(futureListener);
                        }
                    });
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).sync().getNow();
            streamChannel.shutdownInput().sync();
            assertTrue(streamChannel.isInputShutdown());
            streamChannel.writeAndFlush(Unpooled.buffer().writeLong(8)).sync();

            latch.await();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }
}
