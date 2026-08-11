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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class WriteOperationIdOverflowTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void everyShortIdIsHandedOutOnceAndThenTheShortPoolReportsExhaustion() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                // Ids are dense and start at 1, so the pool hands out exactly Short.MAX_VALUE of them.
                for (int i = 1; i <= Short.MAX_VALUE; i++) {
                    assertEquals((short) i, channel.nextWriteOperationId());
                }
                assertEquals(0, channel.nextWriteOperationId());
            }
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void zeroCopyIdFallsBackOutsideTheShortRangeOnceEveryShortIdIsInFlight() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                exhaustShortIds(channel);

                // A value outside the short range is what makes IoUringIoHandler.canUseFastPath(...) fail, which
                // is how the original user_data survives the round-trip through the slow path.
                long overflowId = channel.nextZeroCopyWriteOperationId();
                assertTrue(overflowId > Short.MAX_VALUE, "expected an overflow id, got " + overflowId);
            }
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void overflowIdsAreMonotonicSoTheyCanNeverCollide() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                exhaustShortIds(channel);

                long first = channel.nextZeroCopyWriteOperationId();
                long second = channel.nextZeroCopyWriteOperationId();
                long third = channel.nextZeroCopyWriteOperationId();
                assertTrue(first < second, first + " < " + second);
                assertTrue(second < third, second + " < " + third);
            }
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void recordingAnOverflowWriteDoesNotRetainAndItsTerminalCqeDropsTheEntry() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    long overflowId = overflowId(channel);
                    channel.recordWriteOperation(overflowId, Native.IORING_OP_SEND_ZC, buffer);
                    assertEquals(1, buffer.refCnt());

                    channel.completeWriteOperation(overflowId, Native.IORING_OP_SEND_ZC, 0);
                    assertEquals(1, buffer.refCnt());
                    // Overflow ids are never recycled, so the map has to shrink back or it grows without bound.
                    assertEquals(0, channel.overflowWriteOperationCount());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void shutdownRetainsAnOverflowWriteUntilItsCompletionReleasesItOnce() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    long overflowId = overflowId(channel);
                    channel.recordWriteOperation(overflowId, Native.IORING_OP_SEND_ZC, buffer);

                    shutdownOutput(channel);
                    assertEquals(2, buffer.refCnt());

                    channel.completeWriteOperation(overflowId, Native.IORING_OP_SEND_ZC,
                            Native.IORING_CQE_F_NOTIF);
                    assertEquals(1, buffer.refCnt());
                    assertEquals(0, channel.overflowWriteOperationCount());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void deregistrationReleasesAnOverflowWriteThatWillNeverSeeItsCompletion() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    long overflowId = overflowId(channel);
                    channel.recordWriteOperation(overflowId, Native.IORING_OP_SEND_ZC, buffer);

                    shutdownOutput(channel);
                    assertEquals(2, buffer.refCnt());

                    ((AbstractIoUringChannel.AbstractUringUnsafe) channel.unsafe()).unregistered();
                    assertEquals(1, buffer.refCnt());
                    assertEquals(0, channel.overflowWriteOperationCount());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    private static void exhaustShortIds(IoUringSocketChannel channel) {
        for (int i = 1; i <= Short.MAX_VALUE; i++) {
            channel.nextWriteOperationId();
        }
    }

    private static long overflowId(IoUringSocketChannel channel) {
        exhaustShortIds(channel);
        long id = channel.nextZeroCopyWriteOperationId();
        assertTrue(id > Short.MAX_VALUE, "expected an overflow id, got " + id);
        return id;
    }

    // The channel is not connected here, so the shutdown(2) that follows the retain fails; the retain itself
    // is what this exercises.
    private static void shutdownOutput(IoUringSocketChannel channel) {
        try {
            channel.doShutdownOutput();
        } catch (Exception expected) {
            // Not connected.
        }
    }

    private interface BookkeepingTask {
        void run(IoUringSocketChannel channel);
    }

    // The bookkeeping is event-loop state and the channel can only be closed once registered, so the task
    // runs on a real event loop.
    private static void runOnEventLoop(final BookkeepingTask task) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        final IoUringSocketChannel channel = new IoUringSocketChannel();
        try {
            group.register(channel).sync();
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    task.run(channel);
                }
            }).sync();
        } finally {
            channel.close().syncUninterruptibly();
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
