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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuicStreamTypeTest {

    @Test
    public void testUnidirectionalCreatedByClient() throws Exception {
        Channel server = null;
        Channel channel = null;
        try {
            Promise<Throwable> serverWritePromise = ImmediateEventExecutor.INSTANCE.newPromise();
            server = QuicTestUtils.newServer(null, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    QuicStreamChannel channel = (QuicStreamChannel) ctx.channel();
                    assertEquals(QuicStreamType.UNIDIRECTIONAL, channel.type());
                    assertFalse(channel.isLocalCreated());
                    ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                            .addListener(future -> serverWritePromise.setSuccess(future.cause()));
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                }
            });

            channel = QuicTestUtils.newClient();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .sync()
                    .get();
            QuicStreamChannel streamChannel = quicChannel.createStream(
                    QuicStreamType.UNIDIRECTIONAL, new ChannelInboundHandlerAdapter()).get();
            // Do the write which should succeed
            streamChannel.writeAndFlush(Unpooled.buffer().writeZero(8)).sync();

            // Close stream and quic channel
            streamChannel.close().sync();
            quicChannel.close().sync();
            assertThat(serverWritePromise.get(), instanceOf(UnsupportedOperationException.class));
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);
        }
    }

    @Test
    public void testUnidirectionalCreatedByServer() throws Exception {
        Channel server = null;
        Channel channel = null;
        try {
            Promise<Void> serverWritePromise = ImmediateEventExecutor.INSTANCE.newPromise();
            Promise<Throwable> clientWritePromise = ImmediateEventExecutor.INSTANCE.newPromise();

            server = QuicTestUtils.newServer(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    QuicChannel channel = (QuicChannel) ctx.channel();
                    channel.createStream(QuicStreamType.UNIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // Do the write which should succeed
                            ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                                    .addListener(new PromiseNotifier<>(serverWritePromise));
                        }
                    });
                }
            }, new ChannelInboundHandlerAdapter());

            channel = QuicTestUtils.newClient();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // Do the write should fail
                            ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                                    .addListener(future -> clientWritePromise.setSuccess(future.cause()));
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            // Close the QUIC channel as well.
                            ctx.channel().parent().close();
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ReferenceCountUtil.release(msg);
                            // Let's close the stream
                            ctx.close();
                        }
                    })
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            quicChannel.closeFuture().sync();
            assertTrue(serverWritePromise.await().isSuccess());
            assertThat(clientWritePromise.get(), instanceOf(UnsupportedOperationException.class));
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);
        }
    }
}
