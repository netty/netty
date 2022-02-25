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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.ChannelInputShutdownEvent;
import io.netty5.channel.socket.ChannelInputShutdownReadComplete;
import io.netty5.channel.socket.ChannelOutputShutdownEvent;
import io.netty5.channel.socket.DuplexChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class SocketHalfClosedTest extends AbstractSocketTest {

    @Test
    @Timeout(value = 5000, unit = MILLISECONDS)
    public void testHalfClosureReceiveDataOnFinalWait2StateWhenSoLingerSetByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testHalfClosureReceiveDataOnFinalWait2StateWhenSoLingerSet(sb, cb, false));
    }

    @Test
    @Timeout(value = 5000, unit = MILLISECONDS)
    public void testHalfClosureReceiveDataOnFinalWait2StateWhenSoLingerSet(TestInfo testInfo) throws Throwable {
        run(testInfo, (sb, cb) -> testHalfClosureReceiveDataOnFinalWait2StateWhenSoLingerSet(sb, cb, true));
    }

    private void testHalfClosureReceiveDataOnFinalWait2StateWhenSoLingerSet(
            ServerBootstrap sb, Bootstrap cb, boolean newBufferAPI)
            throws Throwable {
        if (newBufferAPI) {
            enableNewBufferAPI(sb, cb);
        }
        Channel serverChannel = null;
        Channel clientChannel = null;

        final CountDownLatch waitHalfClosureDone = new CountDownLatch(1);
        try {
            sb.childOption(ChannelOption.SO_LINGER, 1)
              .childHandler(new ChannelInitializer<Channel>() {

                  @Override
                  protected void initChannel(Channel ch) throws Exception {
                      ch.pipeline().addLast(new ChannelHandler() {

                            @Override
                            public void channelActive(final ChannelHandlerContext ctx) {
                                SocketChannel channel = (SocketChannel) ctx.channel();
                                channel.shutdownOutput();
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                if (msg instanceof Resource<?>) {
                                    ((Resource<?>) msg).close();
                                } else {
                                    ReferenceCountUtil.release(msg);
                                }
                                waitHalfClosureDone.countDown();
                            }
                        });
                  }
              });

            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
              .handler(new ChannelInitializer<Channel>() {
                  @Override
                  protected void initChannel(Channel ch) throws Exception {
                      ch.pipeline().addLast(new ChannelHandler() {

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (ChannelInputShutdownEvent.INSTANCE == evt) {
                                    ctx.writeAndFlush(newBufferAPI ?
                                                              ctx.bufferAllocator().copyOf(new byte[16]) :
                                                              ctx.alloc().buffer().writeZero(16));
                                }

                                if (ChannelInputShutdownReadComplete.INSTANCE == evt) {
                                    ctx.close();
                                }
                            }
                        });
                  }
              });

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
            waitHalfClosureDone.await();
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
    @Timeout(value = 10000, unit = MILLISECONDS)
    public void testHalfClosureOnlyOneEventWhenAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testHalfClosureOnlyOneEventWhenAutoRead);
    }

    public void testHalfClosureOnlyOneEventWhenAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .option(ChannelOption.AUTO_READ, true);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelHandler() {
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
                    ch.pipeline().addLast(new ChannelHandler() {

                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                shutdownEventReceivedCounter.incrementAndGet();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                shutdownReadCompleteEventReceivedCounter.incrementAndGet();
                                ctx.executor().schedule((Runnable) ctx::close, 100, MILLISECONDS);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            serverChannel = sb.bind().get();
            Channel clientChannel = cb.connect(serverChannel.localAddress()).get();
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
    public void testAllDataReadAfterHalfClosureByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAllDataReadAfterHalfClosureByteBuf);
    }

    public void testAllDataReadAfterHalfClosureByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadAfterHalfClosure(true, sb, cb, false);
        testAllDataReadAfterHalfClosure(false, sb, cb, false);
    }

    @Test
    public void testAllDataReadAfterHalfClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAllDataReadAfterHalfClosure);
    }

    public void testAllDataReadAfterHalfClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testAllDataReadAfterHalfClosure(true, sb, cb, true);
        testAllDataReadAfterHalfClosure(false, sb, cb, true);
    }

    private static void testAllDataReadAfterHalfClosure(final boolean autoRead,
                                                        ServerBootstrap sb, Bootstrap cb,
                                                        boolean newBufferAPI) throws Throwable {
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
              .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvBufferAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            final Object msg;
                            if (newBufferAPI) {
                                Buffer buf = ctx.bufferAllocator().allocate(totalServerBytesWritten);
                                buf.writerOffset(buf.capacity());
                                msg = buf;
                            } else {
                                ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                                buf.writerIndex(buf.capacity());
                                msg = buf;
                            }
                            ctx.writeAndFlush(msg).addListener((DuplexChannel) ctx.channel(), (c, f) ->
                                    c.shutdownOutput());
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
                    ch.pipeline().addLast(new ChannelHandler() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Buffer) {
                                try (Buffer buf = (Buffer) msg) {
                                    bytesRead += buf.readableBytes();
                                }
                            } else {
                                ByteBuf buf = (ByteBuf) msg;
                                bytesRead += buf.readableBytes();
                                buf.release();
                            }
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

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
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
    public void testAutoCloseFalseDoesShutdownOutputByteBuf(TestInfo testInfo) throws Throwable {
        // This test only works on Linux / BSD / MacOS as we assume some semantics that are not true for Windows.
        assumeFalse(PlatformDependent.isWindows());
        run(testInfo, this::testAutoCloseFalseDoesShutdownOutputByteBuf);
    }

    public void testAutoCloseFalseDoesShutdownOutputByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAutoCloseFalseDoesShutdownOutput(false, false, sb, cb, false);
        testAutoCloseFalseDoesShutdownOutput(false, true, sb, cb, false);
        testAutoCloseFalseDoesShutdownOutput(true, false, sb, cb, false);
        testAutoCloseFalseDoesShutdownOutput(true, true, sb, cb, false);
    }

    @Test
    public void testAutoCloseFalseDoesShutdownOutput(TestInfo testInfo) throws Throwable {
        // This test only works on Linux / BSD / MacOS as we assume some semantics that are not true for Windows.
        assumeFalse(PlatformDependent.isWindows());
        run(testInfo, this::testAutoCloseFalseDoesShutdownOutput);
    }

    public void testAutoCloseFalseDoesShutdownOutput(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testAutoCloseFalseDoesShutdownOutput(false, false, sb, cb, true);
        testAutoCloseFalseDoesShutdownOutput(false, true, sb, cb, true);
        testAutoCloseFalseDoesShutdownOutput(true, false, sb, cb, true);
        testAutoCloseFalseDoesShutdownOutput(true, true, sb, cb, true);
    }

    private static void testAutoCloseFalseDoesShutdownOutput(boolean allowHalfClosed,
                                                             final boolean clientIsLeader,
                                                             ServerBootstrap sb,
                                                             Bootstrap cb,
                                                             boolean newBufferAPI) throws Exception {
        final int expectedBytes = 100;
        final CountDownLatch serverReadExpectedLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .option(ChannelOption.AUTO_CLOSE, false)
                    .option(ChannelOption.SO_LINGER, 0);
            sb.childOption(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosed)
                    .childOption(ChannelOption.AUTO_CLOSE, false)
                    .childOption(ChannelOption.SO_LINGER, 0);

            final SimpleChannelInboundHandler<?> leaderHandler = new AutoCloseFalseLeader(
                    expectedBytes, serverReadExpectedLatch, doneLatch, causeRef, newBufferAPI);
            final SimpleChannelInboundHandler<?> followerHandler = new AutoCloseFalseFollower(expectedBytes,
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

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();

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

    private static final class AutoCloseFalseFollower extends SimpleChannelInboundHandler<Object> {
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
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Buffer) {
                bytesRead += ((Buffer) msg).readableBytes();
                if (bytesRead >= expectedBytes) {
                    // We write a reply and immediately close our end of the socket.
                    Buffer buf = ctx.bufferAllocator().allocate(expectedBytes);
                    buf.skipWritable(expectedBytes);
                    ctx.writeAndFlush(buf).addListener(ctx.channel(), (c, f) ->
                            c.close().addListener(c, (channel, future) -> {
                                // This is a bit racy but there is no better way how to handle this in Java11.
                                // The problem is that on close() the underlying FD will not actually be closed directly
                                // but the close will be done after the Selector did process all events. Because of
                                // this we will need to give it a bit time to ensure the FD is actual closed before we
                                // count down the latch and try to write.
                                channel.executor().schedule(followerCloseLatch::countDown, 200, MILLISECONDS);
                            }));
                }
            } else {
                bytesRead += ((ByteBuf) msg).readableBytes();
                if (bytesRead >= expectedBytes) {
                    // We write a reply and immediately close our end of the socket.
                    ByteBuf buf = ctx.alloc().buffer(expectedBytes);
                    buf.writerIndex(buf.writerIndex() + expectedBytes);
                    ctx.writeAndFlush(buf).addListener(ctx.channel(), (c, f) ->
                            c.close().addListener(c, (channel, future) -> {
                                // This is a bit racy but there is no better way how to handle this in Java11.
                                // The problem is that on close() the underlying FD will not actually be closed directly
                                // but the close will be done after the Selector did process all events. Because of
                                // this we will need to give it a bit time to ensure the FD is actual closed before we
                                // count down the latch and try to write.
                                channel.executor().schedule(followerCloseLatch::countDown, 200, MILLISECONDS);
                            }));
                }
            }
        }

        private void checkPrematureClose() {
            if (bytesRead < expectedBytes) {
                causeRef.set(new IllegalStateException("follower premature close"));
                doneLatch.countDown();
            }
        }
    }

    private static final class AutoCloseFalseLeader extends SimpleChannelInboundHandler<Object> {
        private final int expectedBytes;
        private final CountDownLatch followerCloseLatch;
        private final CountDownLatch doneLatch;
        private final AtomicReference<Throwable> causeRef;
        private final boolean newBufferAPI;
        private int bytesRead;
        private boolean seenOutputShutdown;

        AutoCloseFalseLeader(int expectedBytes, CountDownLatch followerCloseLatch, CountDownLatch doneLatch,
                             AtomicReference<Throwable> causeRef, boolean newBufferAPI) {
            this.expectedBytes = expectedBytes;
            this.followerCloseLatch = followerCloseLatch;
            this.doneLatch = doneLatch;
            this.causeRef = causeRef;
            this.newBufferAPI = newBufferAPI;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final Object msg;
            if (newBufferAPI) {
                Buffer buf = ctx.bufferAllocator().allocate(expectedBytes);
                buf.skipWritable(expectedBytes);
                msg = buf.copy();
                ctx.writeAndFlush(buf);
            } else {
                ByteBuf buf = ctx.alloc().buffer(expectedBytes);
                buf.writerIndex(buf.writerIndex() + expectedBytes);
                ctx.writeAndFlush(buf.retainedDuplicate());
                msg = buf;
            }

            // We wait here to ensure that we write before we have a chance to process the outbound
            // shutdown event.
            followerCloseLatch.await();

            // This write should fail, but we should still be allowed to read the peer's data
            ctx.writeAndFlush(msg).addListener(future -> {
                if (future.cause() == null) {
                    causeRef.set(new IllegalStateException("second write should have failed!"));
                    doneLatch.countDown();
                }
            });
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Buffer) {
                bytesRead += ((Buffer) msg).readableBytes();
            } else {
                bytesRead += ((ByteBuf) msg).readableBytes();
            }
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
    public void testAllDataReadClosureByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAllDataReadClosureByteBuf);
    }

    public void testAllDataReadClosureByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testAllDataReadClosure(true, false, sb, cb, false);
        testAllDataReadClosure(true, true, sb, cb, false);
        testAllDataReadClosure(false, false, sb, cb, false);
        testAllDataReadClosure(false, true, sb, cb, false);
    }

    @Test
    public void testAllDataReadClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testAllDataReadClosure);
    }

    public void testAllDataReadClosure(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testAllDataReadClosure(true, false, sb, cb, true);
        testAllDataReadClosure(true, true, sb, cb, true);
        testAllDataReadClosure(false, false, sb, cb, true);
        testAllDataReadClosure(false, true, sb, cb, true);
    }

    private static void testAllDataReadClosure(final boolean autoRead, final boolean allowHalfClosed,
                                               ServerBootstrap sb, Bootstrap cb, boolean newBufferAPI)
            throws Throwable {
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
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new TestNumReadsRecvBufferAllocator(numReadsPerReadLoop));

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            if (newBufferAPI) {
                                Buffer buf = ctx.bufferAllocator().allocate(totalServerBytesWritten);
                                buf.writerOffset(buf.capacity());
                                ctx.writeAndFlush(buf).addListener(ctx, ChannelFutureListeners.CLOSE);
                            } else {
                                ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                                buf.writerIndex(buf.capacity());
                                ctx.writeAndFlush(buf).addListener(ctx, ChannelFutureListeners.CLOSE);
                            }
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
                    ch.pipeline().addLast(new ChannelHandler() {
                        private int bytesRead;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Buffer) {
                                try (Buffer buf = (Buffer) msg) {
                                    bytesRead += buf.readableBytes();
                                }
                            } else {
                                ByteBuf buf = (ByteBuf) msg;
                                bytesRead += buf.readableBytes();
                                buf.release();
                            }
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

            serverChannel = sb.bind().get();
            clientChannel = cb.connect(serverChannel.localAddress()).get();
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
    private static final class TestNumReadsRecvBufferAllocator implements RecvBufferAllocator {
        private final int numReads;
        TestNumReadsRecvBufferAllocator(int numReads) {
            this.numReads = numReads;
        }

        @Override
        public Handle newHandle() {
            return new Handle() {
                private int attemptedBytesRead;
                private int lastBytesRead;
                private int numMessagesRead;

                @Override
                public ByteBuf allocate(ByteBufAllocator alloc) {
                    return alloc.ioBuffer(guess(), guess());
                }

                @Override
                public Buffer allocate(BufferAllocator alloc) {
                    return alloc.allocate(guess());
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
