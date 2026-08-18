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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// WriteOperation is compiled with release 9 (see the module's pom.xml), so it cannot be loaded on a Java 8
// runtime; gate the whole class since every test here touches WriteOperation.
@EnabledForJreRange(min = JRE.JAVA_9)
class WriteOperationTest {

    /**
     * A reference that always fails to retain, so tests can force {@link WriteOperation#retainReferences()} to
     * throw partway through its loop. {@link #release()} asserts it is never called, since a reference this class
     * never actually retained must never be released either.
     */
    private static final class ThrowsOnRetain implements ReferenceCounted {
        @Override
        public int refCnt() {
            return 1;
        }

        @Override
        public ReferenceCounted retain() {
            throw new RuntimeException("retain failed");
        }

        @Override
        public ReferenceCounted retain(int increment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReferenceCounted touch() {
            return this;
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }

        @Override
        public boolean release() {
            throw new AssertionError("must not release a reference that was never retained");
        }

        @Override
        public boolean release(int decrement) {
            throw new AssertionError("must not release a reference that was never retained");
        }
    }

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
    void retainReferencesIsIdempotent() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);

        operation.retainReferences();
        assertEquals(2, buffer.refCnt());

        operation.retainReferences();
        assertEquals(2, buffer.refCnt());

        operation.complete(0);
        buffer.release();
    }

    @Test
    void terminalCqeAfterShutdownRetainReleasesExactlyOnce() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);
        operation.retainReferences();
        assertEquals(2, buffer.refCnt());

        operation.complete(0);
        operation.complete(0);

        assertFalse(operation.isActive());
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

        assertFalse(operation.isActive());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void zeroCopyMoreCompletionDefersReleaseUntilNotification() {
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SENDMSG_ZC, new ReferenceCounted[] {first, second}, 2);
        operation.retainReferences();
        assertEquals(2, first.refCnt());
        assertEquals(2, second.refCnt());

        operation.complete(Native.IORING_CQE_F_MORE);

        assertTrue(operation.isActive());
        assertEquals(2, first.refCnt());
        assertEquals(2, second.refCnt());

        operation.complete(Native.IORING_CQE_F_NOTIF);
        operation.complete(Native.IORING_CQE_F_NOTIF);

        assertFalse(operation.isActive());
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());
        first.release();
        second.release();
    }

    @Test
    void zeroCopyMoreCompletionWithoutRetainDoesNotReleaseUntilNotification() {
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SENDMSG_ZC, new ReferenceCounted[] {first, second}, 2);

        operation.complete(Native.IORING_CQE_F_MORE);

        // Not retained, so the primary completion must leave refCnt alone and must not finish the operation.
        assertTrue(operation.isActive());
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());

        operation.complete(Native.IORING_CQE_F_NOTIF);
        operation.complete(Native.IORING_CQE_F_NOTIF);

        assertFalse(operation.isActive());
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());
        first.release();
        second.release();
    }

    @Test
    void retainFailurePartwayReleasesOnlyWhatWasActuallyRetained() {
        ByteBuf first = Unpooled.buffer();
        ReferenceCounted second = new ThrowsOnRetain();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_WRITEV, new ReferenceCounted[] {first, second}, 2);

        // references[1].retain() throws, after references[0].retain() already succeeded.
        assertThrows(RuntimeException.class, operation::retainReferences);
        assertEquals(2, first.refCnt());

        // finish() must release only the one reference that was actually retained. Releasing "second" too --
        // which never was retained -- would be an over-release; ThrowsOnRetain.release() asserts on that.
        operation.complete(0);

        assertFalse(operation.isActive());
        assertEquals(1, first.refCnt());
        first.release();
    }

    @Test
    void abandonAfterShutdownRetainReleasesExactlyOnce() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);
        operation.retainReferences();

        // A failed submission never produces a CQE, so the abandon is the only release.
        operation.abandon();
        operation.abandon();

        assertFalse(operation.isActive());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void zeroReferenceRecordStillOccupiesTheSlot() {
        WriteOperation operation = new WriteOperation();

        operation.record(Native.IORING_OP_WRITEV, new ReferenceCounted[0], 0);

        assertTrue(operation.isActive());

        operation.complete(0);

        assertFalse(operation.isActive());
    }

    @Test
    void finishedOperationIsNoLongerActive() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);

        assertTrue(operation.isActive());

        operation.complete(0);

        assertFalse(operation.isActive());
        buffer.release();
    }

    @Test
    void completeAndAbandonAfterFinishDoNotReleaseAgain() {
        ByteBuf buffer = Unpooled.buffer();
        WriteOperation operation = new WriteOperation();
        operation.record(Native.IORING_OP_SEND, buffer);
        operation.retainReferences();
        assertEquals(2, buffer.refCnt());

        operation.complete(0);
        assertEquals(1, buffer.refCnt());

        operation.complete(0);
        operation.abandon();
        operation.complete(Native.IORING_CQE_F_NOTIF);

        assertFalse(operation.isActive());
        assertEquals(1, buffer.refCnt());
        buffer.release();
    }

    @Test
    void arrayOverloadCopiesPassedInArraySoCallerCanReuseIt() {
        ByteBuf first = Unpooled.buffer();
        ByteBuf second = Unpooled.buffer();
        ByteBuf replacement = Unpooled.buffer();
        ReferenceCounted[] references = new ReferenceCounted[] {first, second};
        WriteOperation operation = new WriteOperation();

        operation.record(Native.IORING_OP_WRITEV, references, 2);

        // Callers such as the per-event-loop IovArrayReferenceCollector reuse this array between writes.
        references[0] = replacement;

        operation.retainReferences();
        assertEquals(2, first.refCnt());
        assertEquals(1, replacement.refCnt());
        assertEquals(2, second.refCnt());

        operation.abandon();
        assertEquals(1, first.refCnt());
        assertEquals(1, second.refCnt());

        first.release();
        second.release();
        replacement.release();
    }
}
