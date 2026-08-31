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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class WriteOperationTrackerTest extends AbstractWriteOperationTrackerTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void completionFromAForeignIdNamespaceDoesNotTerminateALiveOperation() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf zeroCopyData = Unpooled.buffer(1).writeByte(1);
                short id = channel.writeTracker.nextId();
                try {
                    channel.writeTracker.record(id, Native.IORING_OP_SEND_ZC, zeroCopyData);
                    assertEquals(1, zeroCopyData.refCnt());

                    // A splice picks its own data to tell its two stages apart and never allocates from
                    // writeTracker.nextId(), so its completion can carry an id that a zero-copy send owns.
                    channel.writeTracker.complete(id, Native.IORING_OP_SPLICE, 0);
                    assertEquals(1, zeroCopyData.refCnt());
                    channel.writeTracker.abandon(id, Native.IORING_OP_SPLICE);
                    assertEquals(1, zeroCopyData.refCnt());

                    // It was never retained (no shutdown raced it), so the terminal CQE leaves refCnt untouched.
                    channel.writeTracker.complete(id, Native.IORING_OP_SEND_ZC, Native.IORING_CQE_F_NOTIF);
                    assertEquals(1, zeroCopyData.refCnt());
                    assertEquals(id, channel.writeTracker.nextId());
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
                    short id = channel.writeTracker.nextId();
                    channel.writeTracker.record(id, Native.IORING_OP_SEND_ZC, buffer);
                    channel.writeTracker.complete(id, Native.IORING_OP_SEND_ZC, Native.IORING_CQE_F_NOTIF);

                    assertEquals(1, buffer.refCnt());
                    assertEquals(id, channel.writeTracker.nextId());
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
                    channel.writeTracker.recordForeign((short) 0, Native.IORING_OP_SENDMSG, buffer);
                    channel.writeTracker.complete((short) 0, Native.IORING_OP_SENDMSG, 0);

                    assertEquals(1, buffer.refCnt());
                    assertNotEquals(0, channel.writeTracker.nextId());
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
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    channel.writeTracker.recordStream(Native.IORING_OP_SEND, buffer);
                    assertEquals(1, buffer.refCnt());

                    // doShutdown(...) reaches the single slot through this hook before it drops the
                    // outbound buffer's own reference.
                    channel.writeTracker.retainAll();
                    assertEquals(2, buffer.refCnt());

                    // Retaining twice must not stack references; a shutdown may race a zero-copy retain.
                    channel.writeTracker.retainAll();
                    assertEquals(2, buffer.refCnt());

                    channel.writeTracker.completeStream(0);
                    assertEquals(1, buffer.refCnt());
                    assertFalse(channel.writeTracker.isStreamActive());

                    // The slot is already inactive, so a second completion for it must not release again.
                    channel.writeTracker.completeStream(0);
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
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    channel.writeTracker.recordStream(Native.IORING_OP_WRITEV, buffer);
                    channel.writeTracker.retainAll();
                    assertEquals(2, buffer.refCnt());

                    channel.writeTracker.releaseAll();
                    assertEquals(1, buffer.refCnt());
                    assertFalse(channel.writeTracker.isStreamActive());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void shutdownRetainsAPooledWriteUntilItsCompletionReleasesItOnce() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    short id = channel.writeTracker.nextId();
                    channel.writeTracker.record(id, Native.IORING_OP_SEND_ZC, buffer);

                    shutdownOutput(channel);
                    assertEquals(2, buffer.refCnt());

                    channel.writeTracker.complete(id, Native.IORING_OP_SEND_ZC, Native.IORING_CQE_F_NOTIF);
                    assertEquals(1, buffer.refCnt());
                    // Unlike an overflow id, a pooled id is recycled back to the free list once its slot
                    // terminates.
                    assertEquals(id, channel.writeTracker.nextId());
                } finally {
                    buffer.release();
                }
            }
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void deregistrationReleasesAPooledWriteThatWillNeverSeeItsCompletion() throws Exception {
        runOnEventLoop(new BookkeepingTask() {
            @Override
            public void run(IoUringSocketChannel channel) {
                ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
                try {
                    short id = channel.writeTracker.nextId();
                    channel.writeTracker.record(id, Native.IORING_OP_SEND_ZC, buffer);

                    shutdownOutput(channel);
                    assertEquals(2, buffer.refCnt());

                    channel.unregistered();
                    assertEquals(1, buffer.refCnt());
                } finally {
                    buffer.release();
                }
            }
        });
    }
}
