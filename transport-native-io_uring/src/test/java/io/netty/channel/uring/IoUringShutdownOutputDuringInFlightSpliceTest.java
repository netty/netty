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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.concurrent.Future;
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

import static io.netty.channel.uring.IoUringRefCntZeroAwaiter.awaitRefCntZero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AtomicReference<Future<Void>> writeFutureHolder = new AtomicReference<>();
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
                    .childHandler(new ChannelInboundHandler() { });
            serverChannel = sb.bind(0).sync().getNow();

            Bootstrap cb = new Bootstrap();
            cb.group(clientGroup)
                    .channel(IoUringSocketChannel.class);
            cb.handler(new ChannelInboundHandler() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    DefaultFileRegion region = new DefaultFileRegion(
                            new RandomAccessFile(file, "r").getChannel(), 0, PAYLOAD.length);
                    regionRef.set(region);
                    // Submit the SQE, then shut down output in the same event-loop task: submitAndRunNow() is a
                    // NOOP for stream channels, so io_uring_enter(...) has not run yet and the SQE is still only
                    // queued locally.
                    writeFutureHolder.set(ctx.writeAndFlush(region));
                    ctx.channel().shutdown(ChannelShutdownType.newOutbound());
                    refCntAfterShutdown.set(region.refCnt());
                    openAfterShutdown.set(region.isOpen());
                    outputShutdown.countDown();
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).sync().getNow();

            assertTrue(outputShutdown.await(5, TimeUnit.SECONDS), "shutdown() was not invoked");
            DefaultFileRegion region = regionRef.get();
            assertNotNull(region, "file region was never allocated");
            // channel.shutdown(...) is the public path (AbstractChannel#shutdown): it only flips the
            // outputShutdown flag and fires the shutdown event, it never touches the outbound buffer. Releasing
            // (and closing) the outbound resource is the job of the private shutdownOutput(...), which only runs
            // from the write-error path, not from here. So the region is still held by its original reference,
            // and on top of that AbstractIoUringChannel#doShutdown retains it once more via
            // writeTracker.retainAll() before this SQE's CQE lands, because the kernel still owns the submitted
            // file and pipe descriptors until it posts the splice CQE. refCnt 2 here is that original reference
            // plus the tracker's retain, not a release having failed to happen.
            assertEquals(2, refCntAfterShutdown.get(), "shutdown() did not retain the in-flight splice's FileRegion");
            assertTrue(openAfterShutdown.get(), "shutdown() closed an in-flight splice's file descriptor");

            assertTrue(awaitRefCntZero(clientChannel, region, 5, TimeUnit.SECONDS),
                    "file region was not released after its terminal completion");

            assertTrue(clientChannel.close().await(5, TimeUnit.SECONDS),
                    "channel did not close within the timeout after shutdown() raced an "
                            + "in-flight splice");
            assertFalse(clientChannel.isActive());
            assertFalse(clientChannel.isOpen());

            Future<Void> writeFuture = writeFutureHolder.get();
            assertNotNull(writeFuture, "write was never submitted");
            assertTrue(writeFuture.await(5, TimeUnit.SECONDS), "write future did not complete in time");
            // In 4.2, shutdownOutput() was a single method that both flipped the outputShutdown flag and
            // cleared the outbound buffer, so a pending write promise was failed right there with
            // ChannelOutputShutdownException. In 5.0 that's split: the public shutdown(...) this test calls
            // never touches the outbound buffer, and only the private shutdownOutput(...) -- reached from the
            // write-error path, not from here -- clears the buffer and fails pending writes with
            // ChannelOutputShutdownException. Here, by the time we check, the channel is already closed, so
            // which of two racing paths failed the in-flight splice depends on timing: the kernel may have
            // completed it against a socket that already had its output shut down (surfacing as a
            // NativeIoException), or close() may have reaped it first (surfacing as a
            // StacklessClosedChannelException). Either way is a legitimate failure; this test only asserts
            // that the splice did not silently succeed or hang.
            assertNotNull(writeFuture.cause(), "the in-flight splice should have failed");
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
}
