/*
 * Copyright 2017 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class SocketHalfClosedTest extends AbstractSocketTest {
    @Test
    @Timeout(value = 10000, unit = MILLISECONDS)
    public void testHalfClosureOnlyOneEventWhenAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testHalfClosureOnlyOneEventWhenAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    public void testHalfClosureOnlyOneEventWhenAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .option(ChannelOption.AUTO_READ, true);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ((DuplexChannel) ctx).shutdownOutput();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            final AtomicInteger shutdownEventReceivedCounter = new AtomicInteger();
            final AtomicInteger shutdownReadCompleteEventReceivedCounter = new AtomicInteger();

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                shutdownEventReceivedCounter.incrementAndGet();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                shutdownReadCompleteEventReceivedCounter.incrementAndGet();
                                ctx.executor().schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        ctx.close();
                                    }
                                }, 100, MILLISECONDS);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().sync().channel();
            Channel clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.closeFuture().await();
            assertEquals(1, shutdownEventReceivedCounter.get());
            assertEquals(1, shutdownReadCompleteEventReceivedCounter.get());
        } finally {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    @Test
    public void testAllDataReadAfterHalfClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAllDataReadAfterHalfClosure(serverBootstrap, bootstrap);
            }
        });
    }

    public void testAllDataReadAfterHalfClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadAfterHalfClosure(true, sb, cb);
        testAllDataReadAfterHalfClosure(false, sb, cb);
    }

    private static void testAllDataReadAfterHalfClosure(final boolean autoRead,
                                                        ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final int totalServerBytesWritten = 1024 * 16;
        final int numReadsPerReadLoop = 2;
        final CountDownLatch serverInitializedLatch = new CountDownLatch(1);
        final CountDownLatch clientReadAllDataLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedLatch = new CountDownLatch(1);
        final AtomicInteger clientReadCompletes = new AtomicInteger();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
              .option(ChannelOption.AUTO_READ, autoRead)
              .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvByteBufAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                            buf.writerIndex(buf.capacity());
                            ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    ((DuplexChannel) future.channel()).shutdownOutput();
                                }
                            });
                            serverInitializedLatch.countDown();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buf = (ByteBuf) msg;
                            bytesRead += buf.readableBytes();
                            buf.release();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                clientHalfClosedLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ctx.close();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            clientReadCompletes.incrementAndGet();
                            if (bytesRead == totalServerBytesWritten) {
                                clientReadAllDataLatch.countDown();
                            }
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.read();

            serverInitializedLatch.await();
            clientReadAllDataLatch.await();
            clientHalfClosedLatch.await();
            assertTrue(totalServerBytesWritten / numReadsPerReadLoop + 10 > clientReadCompletes.get(),
                "too many read complete events: " + clientReadCompletes.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    @Test
    public void testAutoCloseFalseDoesShutdownOutput(TestInfo testInfo) throws Throwable {
        // This test only works on Linux / BSD / MacOS as we assume some semantics that are not true for Windows.
        assumeFalse(PlatformDependent.isWindows());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAutoCloseFalseDoesShutdownOutput(serverBootstrap, bootstrap);
            }
        });
    }

    public void testAutoCloseFalseDoesShutdownOutput(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAutoCloseFalseDoesShutdownOutput(false, false, sb, cb);
        testAutoCloseFalseDoesShutdownOutput(false, true, sb, cb);
        testAutoCloseFalseDoesShutdownOutput(true, false, sb, cb);
        testAutoCloseFalseDoesShutdownOutput(true, true, sb, cb);
    }

    private static void testAutoCloseFalseDoesShutdownOutput(boolean allowHalfClosed,
                                                             final boolean clientIsLeader,
                                                             ServerBootstrap sb,
                                                             Bootstrap cb) throws InterruptedException {
        final int expectedBytes = 100;
        final CountDownLatch serverReadExpectedLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> causeRef = new AtomicReference<Throwable>();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .option(ChannelOption.AUTO_CLOSE, false)
                    .option(ChannelOption.SO_LINGER, 0);
            sb.childOption(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .childOption(ChannelOption.AUTO_CLOSE, false)
                    .childOption(ChannelOption.SO_LINGER, 0);

            final SimpleChannelInboundHandler<ByteBuf> leaderHandler = new AutoCloseFalseLeader(expectedBytes,
                    serverReadExpectedLatch, doneLatch, causeRef);
            final SimpleChannelInboundHandler<ByteBuf> followerHandler = new AutoCloseFalseFollower(expectedBytes,
                    serverReadExpectedLatch, doneLatch, causeRef);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(clientIsLeader ? followerHandler :leaderHandler);
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(clientIsLeader ? leaderHandler : followerHandler);
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();

            doneLatch.await();
            assertNull(causeRef.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    private static final class AutoCloseFalseFollower extends SimpleChannelInboundHandler<ByteBuf> {
        private final int expectedBytes;
        private final CountDownLatch followerCloseLatch;
        private final CountDownLatch doneLatch;
        private final AtomicReference<Throwable> causeRef;
        private int bytesRead;

        AutoCloseFalseFollower(int expectedBytes, CountDownLatch followerCloseLatch, CountDownLatch doneLatch,
                               AtomicReference<Throwable> causeRef) {
            this.expectedBytes = expectedBytes;
            this.followerCloseLatch = followerCloseLatch;
            this.doneLatch = doneLatch;
            this.causeRef = causeRef;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            checkPrematureClose();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            checkPrematureClose();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            bytesRead += msg.readableBytes();
            if (bytesRead >= expectedBytes) {
                // We write a reply and immediately close our end of the socket.
                ByteBuf buf = ctx.alloc().buffer(expectedBytes);
                buf.writerIndex(buf.writerIndex() + expectedBytes);
                ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().close().addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(final ChannelFuture future) throws Exception {
                                // This is a bit racy but there is no better way how to handle this in Java11.
                                // The problem is that on close() the underlying FD will not actually be closed directly
                                // but the close will be done after the Selector did process all events. Because of
                                // this we will need to give it a bit time to ensure the FD is actual closed before we
                                // count down the latch and try to write.
                                future.channel().eventLoop().schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        followerCloseLatch.countDown();
                                    }
                                }, 200, TimeUnit.MILLISECONDS);
                            }
                        });
                    }
                });
            }
        }

        private void checkPrematureClose() {
            if (bytesRead < expectedBytes) {
                causeRef.set(new IllegalStateException("follower premature close"));
                doneLatch.countDown();
            }
        }
    }

    private static final class AutoCloseFalseLeader extends SimpleChannelInboundHandler<ByteBuf> {
        private final int expectedBytes;
        private final CountDownLatch followerCloseLatch;
        private final CountDownLatch doneLatch;
        private final AtomicReference<Throwable> causeRef;
        private int bytesRead;
        private boolean seenOutputShutdown;

        AutoCloseFalseLeader(int expectedBytes, CountDownLatch followerCloseLatch, CountDownLatch doneLatch,
                             AtomicReference<Throwable> causeRef) {
            this.expectedBytes = expectedBytes;
            this.followerCloseLatch = followerCloseLatch;
            this.doneLatch = doneLatch;
            this.causeRef = causeRef;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ByteBuf buf = ctx.alloc().buffer(expectedBytes);
            buf.writerIndex(buf.writerIndex() + expectedBytes);
            ctx.writeAndFlush(buf.retainedDuplicate());

            // We wait here to ensure that we write before we have a chance to process the outbound
            // shutdown event.
            followerCloseLatch.await();

            // This write should fail, but we should still be allowed to read the peer's data
            ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.cause() == null) {
                        causeRef.set(new IllegalStateException("second write should have failed!"));
                        doneLatch.countDown();
                    }
                }
            });
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            bytesRead += msg.readableBytes();
            if (bytesRead >= expectedBytes) {
                if (!seenOutputShutdown) {
                    causeRef.set(new IllegalStateException(
                            ChannelOutputShutdownEvent.class.getSimpleName() + " event was not seen"));
                }
                doneLatch.countDown();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof ChannelOutputShutdownEvent) {
                seenOutputShutdown = true;
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            checkPrematureClose();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            checkPrematureClose();
        }

        private void checkPrematureClose() {
            if (bytesRead < expectedBytes || !seenOutputShutdown) {
                causeRef.set(new IllegalStateException("leader premature close"));
                doneLatch.countDown();
            }
        }
    }

    @Test
    public void testAllDataReadClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAllDataReadClosure(serverBootstrap, bootstrap);
            }
        });
    }

    public void testAllDataReadClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadClosure(true, false, sb, cb);
        testAllDataReadClosure(true, true, sb, cb);
        testAllDataReadClosure(false, false, sb, cb);
        testAllDataReadClosure(false, true, sb, cb);
    }

    private static void testAllDataReadClosure(final boolean autoRead, final boolean allowHalfClosed,
                                               ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final int totalServerBytesWritten = 1024 * 16;
        final int numReadsPerReadLoop = 2;
        final CountDownLatch serverInitializedLatch = new CountDownLatch(1);
        final CountDownLatch clientReadAllDataLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedLatch = new CountDownLatch(1);
        final AtomicInteger clientReadCompletes = new AtomicInteger();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .option(ChannelOption.AUTO_READ, autoRead)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvByteBufAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                            buf.writerIndex(buf.capacity());
                            ctx.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
                            serverInitializedLatch.countDown();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buf = (ByteBuf) msg;
                            bytesRead += buf.readableBytes();
                            buf.release();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE && allowHalfClosed) {
                                clientHalfClosedLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ctx.close();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            if (!allowHalfClosed) {
                                clientHalfClosedLatch.countDown();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            clientReadCompletes.incrementAndGet();
                            if (bytesRead == totalServerBytesWritten) {
                                clientReadAllDataLatch.countDown();
                            }
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.read();

            serverInitializedLatch.await();
            clientReadAllDataLatch.await();
            clientHalfClosedLatch.await();
            assertTrue(totalServerBytesWritten / numReadsPerReadLoop + 10 > clientReadCompletes.get(),
                "too many read complete events: " + clientReadCompletes.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }

    /**
     * Designed to read a single byte at a time to control the number of reads done at a fine granularity.
     */
    private static final class TestNumReadsRecvByteBufAllocator implements RecvByteBufAllocator {
        private final int numReads;
        TestNumReadsRecvByteBufAllocator(int numReads) {
            this.numReads = numReads;
        }

        @Override
        public ExtendedHandle newHandle() {
            return new ExtendedHandle() {
                private int attemptedBytesRead;
                private int lastBytesRead;
                private int numMessagesRead;
                @Override
                public ByteBuf allocate(ByteBufAllocator alloc) {
                    return alloc.ioBuffer(guess(), guess());
                }

                @Override
                public int guess() {
                    return 1; // only ever allocate buffers of size 1 to ensure the number of reads is controlled.
                }

                @Override
                public void reset(ChannelConfig config) {
                    numMessagesRead = 0;
                }

                @Override
                public void incMessagesRead(int numMessages) {
                    numMessagesRead += numMessages;
                }

                @Override
                public void lastBytesRead(int bytes) {
                    lastBytesRead = bytes;
                }

                @Override
                public int lastBytesRead() {
                    return lastBytesRead;
                }

                @Override
                public void attemptedBytesRead(int bytes) {
                    attemptedBytesRead = bytes;
                }

                @Override
                public int attemptedBytesRead() {
                    return attemptedBytesRead;
                }

                @Override
                public boolean continueReading() {
                    return numMessagesRead < numReads;
                }

                @Override
                public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
                    return continueReading() && maybeMoreDataSupplier.get();
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
