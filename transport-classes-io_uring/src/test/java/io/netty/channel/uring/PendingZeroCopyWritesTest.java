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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@EnabledForJreRange(min = JRE.JAVA_9)
class PendingZeroCopyWritesTest {
    @Test
    void adoptsReferencesAndReleasesNotificationsOutOfOrder() {
        PendingZeroCopyWrites pending = new PendingZeroCopyWrites();
        ByteBuf first = Unpooled.buffer(1).writeByte(1);
        ByteBuf second = Unpooled.buffer(1).writeByte(2);
        long firstId = pending.nextUserData();
        long secondId = pending.nextUserData();
        try {
            PendingZeroCopyWrites.PendingWrite firstWrite = pending.register(firstId);
            firstWrite.add(first.retain());
            PendingZeroCopyWrites.PendingWrite secondWrite = pending.register(secondId);
            secondWrite.add(second.retain());
            assertEquals(2, first.refCnt());
            assertEquals(2, second.refCnt());
            pending.release(secondId);
            assertEquals(2, first.refCnt());
            assertEquals(1, second.refCnt());
            long reusedId = pending.nextUserData();
            assertEquals(secondId, reusedId);
            PendingZeroCopyWrites.PendingWrite reusedWrite = pending.register(reusedId);
            assertSame(secondWrite, reusedWrite);
            reusedWrite.add(second.retain());
            pending.release(reusedId);
            assertEquals(1, second.refCnt());
            pending.release(firstId);
            assertEquals(1, first.refCnt());
        } finally {
            if (first.refCnt() == 2) {
                pending.release(firstId);
            }
            if (second.refCnt() == 2) {
                pending.release(secondId);
            }
            first.release();
            second.release();
        }
    }

    @Test
    void emptySlotsRemainReservedUntilCompletion() {
        PendingZeroCopyWrites pending = new PendingZeroCopyWrites();
        long firstId = pending.nextUserData();
        long secondId = pending.nextUserData();
        assertEquals(1L, firstId);
        assertEquals(2L, secondId);
        pending.register(firstId);
        pending.recycle(secondId);
        assertEquals(secondId, pending.nextUserData());
        assertEquals(3L, pending.nextUserData());
        pending.release(firstId);
        assertEquals(firstId, pending.nextUserData());
    }

    @Test
    void overflowDoesNotPreventPooledIdReuse() {
        PendingZeroCopyWrites pending = new PendingZeroCopyWrites();
        for (int id = 1; id <= Short.MAX_VALUE; id++) {
            assertEquals(id, pending.nextUserData());
        }
        long overflowId = pending.nextUserData();
        assertEquals(Short.MAX_VALUE + 1L, overflowId);
        ByteBuf buffer = Unpooled.buffer(1).writeByte(1);
        pending.register(overflowId).add(buffer.retain());
        try {
            pending.recycle(1);
            assertEquals(1L, pending.nextUserData());
            assertEquals(2, buffer.refCnt());
            for (int id = 1; id <= Short.MAX_VALUE; id++) {
                pending.recycle(id);
            }
            assertEquals(2, buffer.refCnt());
            pending.release(overflowId);
            assertEquals(1, buffer.refCnt());
        } finally {
            if (buffer.refCnt() == 2) {
                pending.release(overflowId);
            }
            buffer.release();
        }
    }
}
