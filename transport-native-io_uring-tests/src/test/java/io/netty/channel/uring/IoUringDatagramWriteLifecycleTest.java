/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.uring.IoUringRefCntZeroAwaiter.awaitRefCntZero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringDatagramWriteLifecycleTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testCloseKeepsSendmsgDatagramAliveUntilTerminalCqe() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        Channel channel = null;
        try {
            channel = new Bootstrap()
                    .group(group)
                    .channelFactory(new ChannelFactory<Channel>() {
                        @Override
                        public Channel newChannel(EventLoop eventLoop) {
                            return new IoUringDatagramChannel(eventLoop, SocketProtocolFamily.INET);
                        }
                    })
                    .handler(new ChannelInboundHandler() { })
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().getNow();

            AtomicReference<ByteBuf> bufferRef = new AtomicReference<>();
            AtomicInteger refCntAfterClose = new AtomicInteger(-1);
            CountDownLatch closeIssued = new CountDownLatch(1);
            Channel finalChannel = channel;
            finalChannel.executor().execute(new Runnable() {
                @Override
                public void run() {
                    ByteBuf buffer = finalChannel.alloc().directBuffer(1024);
                    buffer.writeZero(buffer.capacity());
                    bufferRef.set(buffer);
                    finalChannel.writeAndFlush(new DatagramPacket(buffer,
                            new InetSocketAddress(NetUtil.LOCALHOST4, 9)));
                    // CQEs can only be reaped once this task returns, so the sendmsg is still in flight here.
                    finalChannel.close();
                    refCntAfterClose.set(buffer.refCnt());
                    closeIssued.countDown();
                }
            });

            assertTrue(closeIssued.await(5, TimeUnit.SECONDS), "local close was not issued");
            ByteBuf buffer = bufferRef.get();
            assertNotNull(buffer, "datagram buffer was not allocated");
            // WRITE_SCHEDULED is set, so close() parks in delayedClose instead of releasing the outbound
            // buffer, keeping the flushed DatagramPacket alive on its own single reference until the CQE.
            assertEquals(1, refCntAfterClose.get(), "close must keep sendmsg memory live before its terminal CQE");

            assertTrue(channel.closeFuture().await(5, TimeUnit.SECONDS), "channel did not close in time");
            assertTrue(awaitRefCntZero(channel, buffer, 5, TimeUnit.SECONDS),
                    "sendmsg memory was not released by its terminal CQE");
        } finally {
            // Use a bounded await instead of syncUninterruptibly(): if the bug under test
            // reproduces, close() never completes, and this finally block must not hang forever.
            if (channel != null) {
                channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testFailedShutdownOutputDoesNotResendInFlightDatagram() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        Channel receiver = null;
        Channel sender = null;
        try {
            AtomicInteger received = new AtomicInteger();
            receiver = newDatagramBootstrap(group)
                    .handler(new ChannelInboundHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            received.incrementAndGet();
                            ReferenceCountUtil.release(msg);
                        }
                    })
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().getNow();
            sender = newDatagramBootstrap(group)
                    .handler(new ChannelInboundHandler() { })
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().getNow();

            InetSocketAddress recipient = (InetSocketAddress) receiver.localAddress();
            AtomicReference<Future<Void>> writeFuture = new AtomicReference<>();
            AtomicReference<Future<Void>> shutdownFuture = new AtomicReference<>();
            CountDownLatch shutdownIssued = new CountDownLatch(1);
            Channel finalSender = sender;
            finalSender.executor().execute(new Runnable() {
                @Override
                public void run() {
                    ByteBuf buffer = finalSender.alloc().directBuffer(8).writeLong(1);
                    writeFuture.set(finalSender.writeAndFlush(new DatagramPacket(buffer, recipient)));
                    // CQEs can only be reaped once this task returns, so the sendmsg is still in flight here.
                    shutdownFuture.set(finalSender.shutdown(ChannelShutdownType.newOutbound()));
                    shutdownIssued.countDown();
                }
            });

            assertTrue(shutdownIssued.await(5, TimeUnit.SECONDS), "shutdown was not issued");
            // Datagram channels do not support shutting down the output, so the datagram stays queued.
            assertFalse(shutdownFuture.get().isSuccess(), "shutdown(Outbound) must fail for datagrams");
            assertTrue(writeFuture.get().await(5, TimeUnit.SECONDS), "write did not complete in time");
            assertTrue(writeFuture.get().isSuccess(), "write must succeed");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (received.get() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            // Give a duplicate send time to arrive.
            Thread.sleep(500);
            assertEquals(1, received.get(), "the in-flight datagram must be sent exactly once");
        } finally {
            if (sender != null) {
                sender.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            if (receiver != null) {
                receiver.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testRetryOfFailedInFlightDatagramIsSent() throws Exception {
        assertRetryOfFailedInFlightDatagramIsSent(false);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testRetryOfFailedInFlightDatagramIsSentAfterFailedShutdownOutput() throws Exception {
        assertRetryOfFailedInFlightDatagramIsSent(true);
    }

    private static void assertRetryOfFailedInFlightDatagramIsSent(boolean shutdownOutputFirst) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        Channel receiver = null;
        Channel sender = null;
        try {
            AtomicInteger received = new AtomicInteger();
            receiver = newDatagramBootstrap(group)
                    .handler(new ChannelInboundHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            received.incrementAndGet();
                            ReferenceCountUtil.release(msg);
                        }
                    })
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().getNow();
            InetSocketAddress recipient = (InetSocketAddress) receiver.localAddress();
            sender = newDatagramBootstrap(group)
                    .handler(new ChannelInboundHandler() { })
                    .connect(recipient).sync().getNow();

            AtomicReference<Future<Void>> firstFuture = new AtomicReference<>();
            AtomicReference<Future<Void>> retryFuture = new AtomicReference<>();
            CountDownLatch retryIssued = new CountDownLatch(1);
            Channel finalSender = sender;
            finalSender.executor().execute(new Runnable() {
                @Override
                public void run() {
                    DatagramPacket packet = new DatagramPacket(
                            finalSender.alloc().directBuffer(8).writeLong(1), recipient);
                    try {
                        firstFuture.set(finalSender.writeAndFlush(packet.retain()));
                        // CQEs can only be reaped once this task returns, so the sendmsg is still in flight here.
                        if (shutdownOutputFirst) {
                            // Fails for datagrams, but retains the in-flight datagram without failing it.
                            finalSender.shutdown(ChannelShutdownType.newOutbound());
                        }
                        // The flush of the inactive channel fails the in-flight datagram.
                        finalSender.disconnect();
                        finalSender.flush();
                        finalSender.bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
                        // Retry while the failed sendmsg is still in flight, with the same packet unless the output
                        // was shut down first, which also fails for a new packet.
                        retryFuture.set(finalSender.writeAndFlush(shutdownOutputFirst ? new DatagramPacket(
                                finalSender.alloc().directBuffer(8).writeLong(1), recipient) : packet.retain()));
                    } finally {
                        packet.release();
                        retryIssued.countDown();
                    }
                }
            });

            assertTrue(retryIssued.await(5, TimeUnit.SECONDS), "retry was not issued");
            assertTrue(firstFuture.get().isDone(), "first write must be failed by the inactive flush");
            assertFalse(firstFuture.get().isSuccess(), "first write must be failed by the inactive flush");
            assertTrue(retryFuture.get().await(5, TimeUnit.SECONDS), "retry did not complete in time");
            assertTrue(retryFuture.get().isSuccess(), "retry must succeed");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (received.get() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            // The failed sendmsg was already in flight, so the receiver gets it as well as the retry.
            assertEquals(2, received.get(), "the retry must be sent");
        } finally {
            if (sender != null) {
                sender.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            if (receiver != null) {
                receiver.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
    }

    private static Bootstrap newDatagramBootstrap(MultiThreadIoEventLoopGroup group) {
        return new Bootstrap()
                .group(group)
                .channelFactory(new ChannelFactory<Channel>() {
                    @Override
                    public Channel newChannel(EventLoop eventLoop) {
                        return new IoUringDatagramChannel(eventLoop, SocketProtocolFamily.INET);
                    }
                });
    }
}
