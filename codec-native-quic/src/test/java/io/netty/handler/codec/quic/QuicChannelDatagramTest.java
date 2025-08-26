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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuicChannelDatagramTest extends AbstractQuicTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[512];

    static {
        random.nextBytes(data);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramFlushInChannelRead(Executor executor) throws Throwable {
        testDatagram(executor, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramFlushInChannelReadComplete(Executor executor) throws Throwable {
        testDatagram(executor, true);
    }

    private void testDatagram(Executor executor, boolean flushInReadComplete) throws Throwable {
        AtomicReference<QuicDatagramExtensionEvent> serverEventRef = new AtomicReference<>();

        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler() {

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof ByteBuf) {
                    final ChannelFuture future;
                    if (!flushInReadComplete) {
                        future = ctx.writeAndFlush(msg);
                    } else {
                        future = ctx.write(msg);
                    }
                    future.addListener(ChannelFutureListener.CLOSE);
                } else {
                    ctx.fireChannelRead(msg);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (flushInReadComplete) {
                    ctx.flush();
                }
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof QuicDatagramExtensionEvent) {
                    serverEventRef.set((QuicDatagramExtensionEvent) evt);
                }
                super.userEventTriggered(ctx, evt);
            }
        };
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor)
                        .datagram(10, 10),
                InsecureQuicTokenHandler.INSTANCE, serverHandler , new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Promise<ByteBuf> receivedBuffer = ImmediateEventExecutor.INSTANCE.newPromise();
        AtomicReference<QuicDatagramExtensionEvent> clientEventRef = new AtomicReference<>();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .datagram(10, 10));

        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (!receivedBuffer.trySuccess((ByteBuf) msg)) {
                    ReferenceCountUtil.release(msg);
                }
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof QuicDatagramExtensionEvent) {
                    clientEventRef.set((QuicDatagramExtensionEvent) evt);
                }
                super.userEventTriggered(ctx, evt);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                receivedBuffer.tryFailure(cause);
                super.exceptionCaught(ctx, cause);
            }
        };

        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .remoteAddress(address)
                    .connect()
                    .get();
            quicChannel.writeAndFlush(Unpooled.copiedBuffer(data)).sync();

            ByteBuf buffer = receivedBuffer.get();
            ByteBuf expected = Unpooled.wrappedBuffer(data);
            assertEquals(expected, buffer);
            buffer.release();
            expected.release();

            assertNotEquals(0, serverEventRef.get().maxLength());
            assertNotEquals(0, clientEventRef.get().maxLength());

            quicChannel.close().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramNoAutoReadMaxMessagesPerRead1(Executor executor) throws Throwable {
        testDatagramNoAutoRead(executor, 1, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramNoAutoReadMaxMessagesPerRead3(Executor executor) throws Throwable {
        testDatagramNoAutoRead(executor, 3, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramNoAutoReadMaxMessagesPerRead1OutSideEventLoop(Executor executor) throws Throwable {
        testDatagramNoAutoRead(executor, 1, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testDatagramNoAutoReadMaxMessagesPerRead3OutSideEventLoop(Executor executor) throws Throwable {
        testDatagramNoAutoRead(executor, 3, true);
    }

    private void testDatagramNoAutoRead(Executor executor, int maxMessagesPerRead, boolean readLater) throws Throwable {
        Promise<Void> serverPromise = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<ByteBuf> clientPromise = ImmediateEventExecutor.INSTANCE.newPromise();

        int numDatagrams = 5;
        AtomicInteger serverReadCount = new AtomicInteger();
        CountDownLatch latch  = new CountDownLatch(numDatagrams);
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler() {
            private int readPerLoop;

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                super.channelActive(ctx);
                ctx.read();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof ByteBuf) {
                    readPerLoop++;

                    ctx.writeAndFlush(msg).addListener(future -> {
                        if (future.isSuccess()) {
                            latch.countDown();
                        }
                    });
                    if (serverReadCount.incrementAndGet() == numDatagrams) {
                        serverPromise.trySuccess(null);
                    }
                } else {
                    ctx.fireChannelRead(msg);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (readPerLoop > maxMessagesPerRead) {
                    ctx.close();
                    serverPromise.tryFailure(new AssertionError(
                            "Read more then " + maxMessagesPerRead +  " time per read loop"));
                    return;
                }
                readPerLoop = 0;
                if (serverReadCount.get() < numDatagrams) {
                    if (readLater) {
                        ctx.executor().execute(ctx::read);
                    } else {
                        ctx.read();
                    }
                }
            }
        };
        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor)
                        .option(ChannelOption.AUTO_READ, false)
                        .option(ChannelOption.MAX_MESSAGES_PER_READ, maxMessagesPerRead)
                        .datagram(10, 10),
                InsecureQuicTokenHandler.INSTANCE, serverHandler, new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                .datagram(10, 10));
        AtomicInteger clientReadCount = new AtomicInteger();
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler() {

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof ByteBuf) {

                    if (clientReadCount.incrementAndGet() == numDatagrams) {
                        if (!clientPromise.trySuccess((ByteBuf) msg)) {
                            ReferenceCountUtil.release(msg);
                        }
                    } else {
                        ReferenceCountUtil.release(msg);
                    }
                } else {
                    ctx.fireChannelRead(msg);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                clientPromise.tryFailure(cause);
            }
        };
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .remoteAddress(address)
                    .connect()
                    .get();
            for (int i = 0; i < numDatagrams; i++) {
                quicChannel.writeAndFlush(Unpooled.copiedBuffer(data)).sync();
                // Let's add some sleep in between as this is UDP so we may loose some data otherwise.
                Thread.sleep(50);
            }
            assertTrue(serverPromise.await(3000), "Server received: " + serverReadCount.get() +
                    ", Client received: " + clientReadCount.get());
            serverPromise.sync();

            assertTrue(clientPromise.await(3000), "Server received: " + serverReadCount.get() +
                    ", Client received: " + clientReadCount.get());
            ByteBuf buffer = clientPromise.get();
            ByteBuf expected = Unpooled.wrappedBuffer(data);
            assertEquals(expected, buffer);
            buffer.release();
            expected.release();

            quicChannel.close().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }
}
