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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.channel.socket.DuplexChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.uring.IoUringRefCntZeroAwaiter.awaitRefCntZero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A write CQE (and, for zero-copy, its follow-up notification) that lands after the outbound buffer is gone
 * must still be consumed, otherwise the channel's write-tracking state stays stuck and close() never completes.
 */
public class IoUringShutdownOutputDuringInFlightWriteTest {

    private enum WriteMode {
        PLAIN,
        ZERO_COPY
    }

    // A payload this large, together with AUTO_READ=false on the peer, keeps the write from completing
    // synchronously on submission, widening the window in which its CQE is still outstanding.
    private static final int WRITE_SIZE = 1024 * 1024;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testCloseCompletesAfterShutdownOutputDuringInFlightWrite() throws Exception {
        runTest(WriteMode.PLAIN);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testCloseCompletesAfterShutdownOutputDuringInFlightZeroCopyWrite() throws Exception {
        assumeTrue(IoUring.isSendZcSupported());
        runTest(WriteMode.ZERO_COPY);
    }

    private static void runTest(WriteMode writeMode) throws Exception {
        MultiThreadIoEventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        MultiThreadIoEventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());

        Channel serverChannel = null;
        Channel clientChannel = null;
        final ChannelFuture[] writeFutureHolder = new ChannelFuture[1];
        AtomicReference<ByteBuf> bufferRef = new AtomicReference<>();
        AtomicInteger refCntAfterShutdown = new AtomicInteger(-1);
        CountDownLatch outputShutdown = new CountDownLatch(1);
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup)
                    .channel(IoUringServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new ChannelInboundHandlerAdapter());
            serverChannel = sb.bind(0).sync().channel();

            Bootstrap cb = new Bootstrap();
            cb.group(clientGroup)
                    .channel(IoUringSocketChannel.class);
            if (writeMode == WriteMode.ZERO_COPY) {
                cb.option(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 1024);
            }
            cb.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    ByteBuf buf = ctx.alloc().directBuffer(WRITE_SIZE);
                    buf.writeZero(WRITE_SIZE);
                    bufferRef.set(buf);
                    // Submit the SQE, then shut down output in the same event-loop task:
                    // no CQE for the write can have been processed by the loop yet.
                    writeFutureHolder[0] = ctx.writeAndFlush(buf);
                    ((DuplexChannel) ctx.channel()).shutdownOutput();
                    refCntAfterShutdown.set(buf.refCnt());
                    outputShutdown.countDown();
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();

            assertTrue(outputShutdown.await(5, TimeUnit.SECONDS), "shutdownOutput() was not invoked");
            ByteBuf buffer = bufferRef.get();
            assertNotNull(buffer, "write buffer was never allocated");
            // shutdownOutput() fails the write promise immediately, but the kernel still owns the
            // submitted buffer until it posts the write CQE (or the SEND_ZC notification).
            assertEquals(1, refCntAfterShutdown.get(), "shutdownOutput() released an in-flight write buffer");

            assertTrue(awaitRefCntZero(clientChannel, buffer, 5, TimeUnit.SECONDS),
                    "write buffer was not released after its terminal completion");

            assertTrue(clientChannel.close().await(5, TimeUnit.SECONDS),
                    "channel did not close within the timeout after shutdownOutput() raced an "
                            + "in-flight write");
            assertFalse(clientChannel.isActive());
            assertFalse(clientChannel.isOpen());

            ChannelFuture writeFuture = writeFutureHolder[0];
            assertNotNull(writeFuture, "write was never submitted");
            assertTrue(writeFuture.await(5, TimeUnit.SECONDS), "write future did not complete in time");
            assertInstanceOf(ChannelOutputShutdownException.class, writeFuture.cause());
        } finally {
            // Use a bounded await instead of syncUninterruptibly(): if the bug under test
            // reproduces, close() never completes, and this finally block must not hang forever.
            if (clientChannel != null) {
                clientChannel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            if (serverChannel != null) {
                serverChannel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
            serverGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
            clientGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
    }
}
