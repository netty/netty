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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A splice picks up its file and pipe descriptors lazily, inside the kernel's async worker, so submitting the
 * SQE does not itself pin them. If {@code shutdownOutput()} releases the {@link IoUringFileRegion} before the
 * kernel has taken that reference, the fd it later dereferences may already be closed (and, on 7.x kernels,
 * reused by an unrelated file), which is a use-after-free rather than a Netty-visible exception. This mirrors
 * {@link IoUringShutdownOutputDuringInFlightWriteTest}, but for the splice write path.
 */
public class IoUringShutdownOutputDuringInFlightSpliceTest {

    private static final byte[] PAYLOAD = "hello splice race".getBytes();

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testCloseCompletesAfterShutdownOutputDuringInFlightSplice() throws Exception {
        assumeTrue(IoUring.isSpliceSupported());

        MultiThreadIoEventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        MultiThreadIoEventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());

        Channel serverChannel = null;
        Channel clientChannel = null;
        File file = PlatformDependent.createTempFile("netty-iouring-splice-race-", ".tmp", null);
        file.deleteOnExit();
        final ChannelFuture[] writeFutureHolder = new ChannelFuture[1];
        AtomicReference<DefaultFileRegion> regionRef = new AtomicReference<>();
        AtomicInteger refCntAfterShutdown = new AtomicInteger(-1);
        AtomicReference<Boolean> openAfterShutdown = new AtomicReference<>();
        CountDownLatch outputShutdown = new CountDownLatch(1);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(PAYLOAD);
        }
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup)
                    .channel(IoUringServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childHandler(new ChannelInboundHandlerAdapter());
            serverChannel = sb.bind(0).sync().channel();

            Bootstrap cb = new Bootstrap();
            cb.group(clientGroup)
                    .channel(IoUringSocketChannel.class);
            cb.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    DefaultFileRegion region = new DefaultFileRegion(
                            new RandomAccessFile(file, "r").getChannel(), 0, PAYLOAD.length);
                    regionRef.set(region);
                    // Submit the SQE, then shut down output in the same event-loop task: submitAndRunNow() is a
                    // NOOP for stream channels, so io_uring_enter(...) has not run yet and the SQE is still only
                    // queued locally.
                    writeFutureHolder[0] = ctx.writeAndFlush(region);
                    ((DuplexChannel) ctx.channel()).shutdownOutput();
                    refCntAfterShutdown.set(region.refCnt());
                    openAfterShutdown.set(region.isOpen());
                    outputShutdown.countDown();
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();

            assertTrue(outputShutdown.await(5, TimeUnit.SECONDS), "shutdownOutput() was not invoked");
            DefaultFileRegion region = regionRef.get();
            assertNotNull(region, "file region was never allocated");
            // shutdownOutput() fails the write promise immediately, but the kernel still owns the submitted file
            // and pipe descriptors until it posts the splice CQE.
            assertEquals(1, refCntAfterShutdown.get(), "shutdownOutput() released an in-flight splice's FileRegion");
            assertTrue(openAfterShutdown.get(), "shutdownOutput() closed an in-flight splice's file descriptor");

            assertTrue(awaitRefCntZero(clientChannel, region, 5, TimeUnit.SECONDS),
                    "file region was not released after its terminal completion");

            assertTrue(clientChannel.close().await(5, TimeUnit.SECONDS),
                    "channel did not close within the timeout after shutdownOutput() raced an "
                            + "in-flight splice");
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
            file.delete();
        }
    }

    private static boolean awaitRefCntZero(Channel channel, DefaultFileRegion region, long timeout, TimeUnit unit)
            throws InterruptedException {
        CountDownLatch released = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (region.refCnt() == 0) {
                    released.countDown();
                } else {
                    channel.eventLoop().schedule(this, 10, TimeUnit.MILLISECONDS);
                }
            }
        });
        return released.await(timeout, unit);
    }
}
