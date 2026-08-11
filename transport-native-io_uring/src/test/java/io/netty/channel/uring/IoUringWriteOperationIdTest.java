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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringWriteOperationIdTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void completionFromAForeignIdNamespaceDoesNotTerminateALiveOperation() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf zeroCopyData = Unpooled.buffer(1).writeByte(1);
                short id = channel.nextWriteOperationId();
                try {
                    channel.recordWriteOperation(id, Native.IORING_OP_SEND_ZC, zeroCopyData);
                    assertEquals(1, zeroCopyData.refCnt());

                    // A splice picks its own data to tell its two stages apart and never allocates from
                    // nextWriteOperationId(), so its completion can carry an id that a zero-copy send owns.
                    channel.completeWriteOperation(id, Native.IORING_OP_SPLICE, 0);
                    assertEquals(1, zeroCopyData.refCnt());
                    channel.rollbackWriteOperation(id, Native.IORING_OP_SPLICE);
                    assertEquals(1, zeroCopyData.refCnt());

                    // It was never retained (no shutdown raced it), so the terminal CQE leaves refCnt untouched.
                    channel.completeWriteOperation(id, Native.IORING_OP_SEND_ZC, Native.IORING_CQE_F_NOTIF);
                    assertEquals(1, zeroCopyData.refCnt());
                    assertEquals(id, channel.nextWriteOperationId());
                } finally {
                    zeroCopyData.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void terminalCompletionRecyclesTheWriteOperationId() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    short id = channel.nextWriteOperationId();
                    channel.recordWriteOperation(id, Native.IORING_OP_SEND_ZC, buffer);
                    channel.completeWriteOperation(id, Native.IORING_OP_SEND_ZC, Native.IORING_CQE_F_NOTIF);

                    assertEquals(1, buffer.refCnt());
                    assertEquals(id, channel.nextWriteOperationId());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void anIdOwnedByAnotherAllocatorNeverEntersTheFreeList() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    // The datagram sendmsg path registers MsgHdrMemoryArray indices, which start at 0. Recycling
                    // one would hand out 0, the value reserved for "no id".
                    channel.recordUnpooledWriteOperation((short) 0, Native.IORING_OP_SENDMSG, buffer);
                    channel.completeWriteOperation((short) 0, Native.IORING_OP_SENDMSG, 0);

                    assertEquals(1, buffer.refCnt());
                    assertNotEquals(0, channel.nextWriteOperationId());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void shutdownRetainsTheStreamWriteUntilItsCompletionReleasesItOnce() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                AbstractIoUringStreamChannel.IoUringStreamUnsafe unsafe = streamUnsafe(channel);
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    unsafe.writeOperation.record(Native.IORING_OP_SEND, buffer);
                    assertEquals(1, buffer.refCnt());

                    // doShutdownOutput() reaches the single slot through this hook before it drops the
                    // outbound buffer's own reference.
                    unsafe.retainInflightWriteOperations();
                    assertEquals(2, buffer.refCnt());

                    // Retaining twice must not stack references; a shutdown may race a zero-copy retain.
                    unsafe.retainInflightWriteOperations();
                    assertEquals(2, buffer.refCnt());

                    unsafe.writeOperation.complete(0);
                    assertEquals(1, buffer.refCnt());
                    assertFalse(unsafe.writeOperation.isActive());

                    // A completion carrying an id the slot does not own must not release anything again.
                    unsafe.writeOperation.complete(0);
                    assertEquals(1, buffer.refCnt());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void deregistrationReleasesAStreamWriteThatWillNeverSeeItsCompletion() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                AbstractIoUringStreamChannel.IoUringStreamUnsafe unsafe = streamUnsafe(channel);
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    unsafe.writeOperation.record(Native.IORING_OP_WRITEV, buffer);
                    unsafe.retainInflightWriteOperations();
                    assertEquals(2, buffer.refCnt());

                    unsafe.releaseInflightWriteOperations();
                    assertEquals(1, buffer.refCnt());
                    assertFalse(unsafe.writeOperation.isActive());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    private static AbstractIoUringStreamChannel.IoUringStreamUnsafe streamUnsafe(IoUringSocketChannel channel) {
        return (AbstractIoUringStreamChannel.IoUringStreamUnsafe) channel.unsafe();
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
