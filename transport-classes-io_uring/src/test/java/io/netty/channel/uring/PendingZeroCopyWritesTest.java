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
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_9)
class PendingZeroCopyWritesTest {
    @Test
    void adoptsReferencesAndReleasesNotificationsOutOfOrder() {
        PendingZeroCopyWrites pending = new PendingZeroCopyWrites();
        ByteBuf first = Unpooled.buffer(1).writeByte(1);
        ByteBuf second = Unpooled.buffer(1).writeByte(2);
        try {
            pending.register(1, Collections.<ReferenceCounted>singletonList(first.retain()));
            pending.register(2, Collections.<ReferenceCounted>singletonList(second.retain()));
            assertEquals(2, first.refCnt());
            assertEquals(2, second.refCnt());
            pending.release(2);
            assertEquals(2, first.refCnt());
            assertEquals(1, second.refCnt());
            assertFalse(pending.isEmpty());
            pending.release(1);
            assertEquals(1, first.refCnt());
            assertTrue(pending.isEmpty());
        } finally {
            if (first.refCnt() == 2) {
                pending.release(1);
            }
            if (second.refCnt() == 2) {
                pending.release(2);
            }
            first.release();
            second.release();
        }
    }

    @Test
    void emptyNotificationsStillReserveTheirUserData() {
        PendingZeroCopyWrites pending = new PendingZeroCopyWrites();
        short candidate = Short.MIN_VALUE;
        assertEquals(candidate, pending.nextUserData(candidate));
        pending.register(candidate, Collections.<ReferenceCounted>emptyList());
        long next = pending.nextUserData(candidate);
        assertEquals(32768L, next);
        pending.register(next, Collections.<ReferenceCounted>emptyList());
        assertEquals(next + 65536L, pending.nextUserData(candidate));
        pending.release(next);
        assertEquals(next, pending.nextUserData(candidate));
        pending.release(candidate);
        assertTrue(pending.isEmpty());
    }
}
