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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                            ChannelFutureListener futureListener = channelFuture -> {
                                Throwable cause = channelFuture.cause();
                                if (cause instanceof ChannelOutputShutdownException) {
                                    latch.countDown();
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

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownInputClosureByServerCausesStreamStopped(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        try {
            server = QuicTestUtils.newServer(executor, new ChannelInboundHandlerAdapter(),
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
                            streamChannel.shutdownInput()
                                    .addListener(f -> {
                                        ByteBuf buffer = (ByteBuf) msg;
                                        if (!f.isSuccess()) {
                                            errorRef.compareAndSet(null, f.cause());
                                            latch.countDown();
                                            buffer.release();
                                        } else {
                                            ctx.writeAndFlush(buffer).addListener(channelFuture -> {
                                                if (!channelFuture.isSuccess()) {
                                                    errorRef.compareAndSet(null, channelFuture.cause());
                                                }
                                                latch.countDown();
                                            });
                                        }
                                    });
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
                    new ByteToMessageDecoder() {
                        @Override
                        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> list) {
                            if (in.readableBytes() == 4) {
                                latch.countDown();
                                ctx.close();
                            }
                        }
                    }).sync().getNow();

            streamChannel.writeAndFlush(Unpooled.buffer().writeInt(4)).sync();

            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("Timeout while waiting for completion", errorRef.get());
            }
            Throwable error = errorRef.get();
            if (error != null) {
                fail("Failure during execution", error);
            }
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownOutputClosureByServerCausesStreamReset(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicLong applicationErrorCode = new AtomicLong(-1);
        try {
            server = QuicTestUtils.newServer(executor, new ChannelInboundHandlerAdapter(),
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            QuicStreamChannel streamChannel = (QuicStreamChannel) ctx.channel();
                            ByteBuf buffer = (ByteBuf) msg;
                            buffer.release();
                            streamChannel.shutdownOutput(100)
                                    .addListener(f -> {
                                        if (!f.isSuccess()) {
                                            errorRef.compareAndSet(null, f.cause());
                                        }
                                        latch.countDown();
                                    });
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
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            if (cause instanceof QuicStreamResetException) {
                                QuicStreamResetException reset = (QuicStreamResetException) cause;
                                applicationErrorCode.set(reset.applicationProtocolCode());
                            } else {
                                errorRef.set(cause);
                            }
                            latch.countDown();
                        }
                    }).sync().getNow();

            streamChannel.writeAndFlush(Unpooled.buffer().writeInt(4)).sync();

            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("Timeout while waiting for completion", errorRef.get());
            }
            Throwable error = errorRef.get();
            if (error != null) {
                fail("Failure during execution", error);
            }
            assertEquals(100, applicationErrorCode.get());
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testNotWritableAfterShutdownOutput(Executor executor) throws Throwable {
        testNotWritableAfterOutputShutdown(executor, QuicStreamChannel::shutdownOutput);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testNotWritableAfterShutdownOutputWithError(Executor executor) throws Throwable {
        testNotWritableAfterOutputShutdown(executor, stream -> stream.shutdownOutput(100));
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testNotWritableAfterShutdownWithError(Executor executor) throws Throwable {
        testNotWritableAfterOutputShutdown(executor, stream -> stream.shutdown(100));
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testNotWritableAfterClose(Executor executor) throws Throwable {
        testNotWritableAfterOutputShutdown(executor, QuicStreamChannel::close);
    }

    private static void testNotWritableAfterOutputShutdown(
            Executor executor, Function<QuicStreamChannel, ChannelFuture> shutdownOutput) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, new ChannelInboundHandlerAdapter());
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get(5, TimeUnit.SECONDS);

            AtomicInteger writabilityChanges = new AtomicInteger();
            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                            writabilityChanges.incrementAndGet();
                            ctx.fireChannelWritabilityChanged();
                        }
                    }).get(5, TimeUnit.SECONDS);
            assertThat(stream.isWritable()).isTrue();

            shutdownOutput.apply(stream).sync();

            // Like a channel whose output was shut down: not writable, without a writability change.
            assertThat(stream.isWritable()).isFalse();
            assertThat(stream.bytesBeforeUnwritable()).isZero();
            assertThat(writabilityChanges.get()).isZero();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownInputThenShutdownOutputClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, stream -> stream.shutdownInput(100), QuicStreamChannel::shutdownOutput);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownOutputThenShutdownInputClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, QuicStreamChannel::shutdownOutput, stream -> stream.shutdownInput(100));
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownInputThenShutdownOutputWithErrorClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, stream -> stream.shutdownInput(100),
                stream -> stream.shutdownOutput(100));
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownOutputWithErrorThenShutdownInputClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, stream -> stream.shutdownOutput(100),
                stream -> stream.shutdownInput(100));
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, QuicStreamChannel::shutdown);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testShutdownWithErrorClosesStream(Executor executor) throws Throwable {
        testClosedAfterLocalShutdowns(executor, stream -> stream.shutdown(100));
    }

    @SafeVarargs
    private static void testClosedAfterLocalShutdowns(
            Executor executor, Function<QuicStreamChannel, ChannelFuture>... shutdowns) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, new ChannelInboundHandlerAdapter());
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get(5, TimeUnit.SECONDS);
            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);

            for (Function<QuicStreamChannel, ChannelFuture> shutdown : shutdowns) {
                shutdown.apply(stream).sync();
            }

            // Both directions are terminated locally, so the stream is closed, while the connection is still open.
            assertThat(stream.closeFuture().await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(quicChannel.isActive()).isTrue();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }
}
