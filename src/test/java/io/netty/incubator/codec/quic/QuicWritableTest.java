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
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuicWritableTest {

    @Test
    public void testCorrectlyHandleWritabilityReadRequestedInReadComplete() throws Throwable {
        testCorrectlyHandleWritability(true);
    }

    @Test
    public void testCorrectlyHandleWritabilityReadRequestedInRead() throws Throwable {
        testCorrectlyHandleWritability(false);
    }

    private static void testCorrectlyHandleWritability(boolean readInComplete) throws Throwable  {
        int bufferSize = 64 * 1024;
        Promise<Void> writePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final AtomicReference<Throwable> serverErrorRef = new AtomicReference<>();
        final AtomicReference<Throwable> clientErrorRef = new AtomicReference<>();
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder().initialMaxStreamsBidirectional(5000),
                InsecureQuicTokenHandler.INSTANCE,
                new QuicChannelInitializer(new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf buffer = (ByteBuf) msg;
                        buffer.release();
                        ctx.writeAndFlush(ctx.alloc().buffer(bufferSize).writeZero(bufferSize))
                                .addListener(new PromiseNotifier<>(writePromise));
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        serverErrorRef.set(cause);
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        ChannelFuture future = null;
        try {
            Bootstrap bootstrap = QuicTestUtils.newClientBootstrap(QuicTestUtils.newQuicClientBuilder()
                    .initialMaxStreamDataBidirectionalLocal(bufferSize / 4));
            future = bootstrap
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());
            QuicChannel channel = (QuicChannel) future.channel();
            QuicStreamChannel stream = channel.createStream(
                    QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
                        int bytes;

                        @Override
                        public void channelRegistered(ChannelHandlerContext ctx) {
                            ctx.channel().config().setAutoRead(false);
                        }

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ctx.writeAndFlush(ctx.alloc().buffer(8).writeLong(8));
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (bytes == 0) {
                                // First read
                                assertFalse(writePromise.isDone());
                            }
                            ByteBuf buffer = (ByteBuf) msg;
                            bytes += buffer.readableBytes();
                            if (bytes == bufferSize) {
                                ctx.close();
                                assertTrue(writePromise.isDone());
                            }

                            if (!readInComplete) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            if (readInComplete) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            clientErrorRef.set(cause);
                        }
                    }).get();
            assertFalse(writePromise.isDone());

            // Let's trigger the reads. This will ensure we will consume the data and the remote peer
            // should be notified that it can write more data.
            stream.read();

            writePromise.sync();
            stream.closeFuture().sync();
            channel.close().sync();

            throwIfNotNull(serverErrorRef);
            throwIfNotNull(clientErrorRef);
        } finally {
            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }

    private static void throwIfNotNull(AtomicReference<Throwable> errorRef) throws Throwable {
        Throwable cause = errorRef.get();
        if (cause != null) {
            throw cause;
        }
    }
}
