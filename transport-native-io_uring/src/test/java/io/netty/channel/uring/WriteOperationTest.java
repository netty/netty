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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteOperationTest {

    @Test
    void recordDoesNotRetain() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();

        operation.record(Native.IORING_OP_SEND, buffer);

        assertEquals(Native.IORING_OP_SEND, operation.opCode());
        assertEquals(1, buffer.refCnt());

        operation.complete(0);
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void retainForShutdownIsIdempotent() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);

        operation.retainForShutdown();
        assertEquals(2, buffer.refCnt());

        operation.retainForShutdown();
        assertEquals(2, buffer.refCnt());

        operation.complete(0);
        buffer.release();
    }

    @Test
    void terminalCqeAfterShutdownRetainReleasesExactlyOnce() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);
        operation.retainForShutdown();
        assertEquals(2, buffer.refCnt());

        operation.complete(0);
        operation.complete(0);

        assertTrue(operation.isDone());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void terminalCqeWithoutShutdownRetainLeavesRefCntUnchanged() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);

        operation.complete(0);
        operation.complete(0);

        assertTrue(operation.isDone());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void zeroCopyMoreCompletionDefersReleaseUntilNotification() {
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SENDMSG_ZC, new ReferenceCounted[] {first, second}, 2);
        operation.retainForShutdown();
        assertEquals(2, first.refCnt());
        assertEquals(2, second.refCnt());

        operation.complete(Native.IORING_CQE_F_MORE);

        assertFalse(operation.isDone());
        assertEquals(2, first.refCnt());
        assertEquals(2, second.refCnt());

        operation.complete(Native.IORING_CQE_F_NOTIF);
        operation.complete(Native.IORING_CQE_F_NOTIF);

        assertTrue(operation.isDone());
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());
        first.release();
        second.release();
    }

    @Test
    void rollbackAfterShutdownRetainReleasesExactlyOnce() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);
        operation.retainForShutdown();

        // A failed submission never produces a CQE, so the rollback is the only release.
        operation.rollback();
        operation.rollback();

        assertTrue(operation.isDone());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void zeroReferenceRecordStillOccupiesTheSlot() {
        WriteOperation operation = new WriteOperation();

        operation.record(Native.IORING_OP_WRITEV, new ReferenceCounted[0], 0);

        assertTrue(operation.isActive());

        operation.complete(0);

        assertTrue(operation.isDone());
    }

    @Test
    void arrayOverloadCopiesPassedInArraySoCallerCanReuseIt() {
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        ByteBuf replacement = Unpooled.buffer();
        ReferenceCounted[] references = new ReferenceCounted[] {first, second};
        WriteOperation operation = new WriteOperation();

        operation.record(Native.IORING_OP_WRITEV, references, 2);

        // Mutating the array the operation was handed must not be visible through the operation: the array is
        // reused by callers such as a channel-lifetime IovArrayReferenceCollector, so the operation must have
        // copied the contents into its own backing array instead of storing the array instance directly.
        references[0] = replacement;

        operation.retainForShutdown();
        assertEquals(2, first.refCnt());
        assertEquals(1, replacement.refCnt());
        assertEquals(2, second.refCnt());

        operation.rollback();
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());

        first.release();
    }
}
