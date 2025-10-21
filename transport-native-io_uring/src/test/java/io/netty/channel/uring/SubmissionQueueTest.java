/*
 * Copyright 2024 The Netty Project
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SubmissionQueueTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void sqeFullTest() {
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        ringBuffer.enable();
        try {
            SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
            final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

            assertNotNull(ringBuffer);
            assertNotNull(submissionQueue);
            assertNotNull(completionQueue);

            int counter = 0;
            while (submissionQueue.remaining() > 0) {
                assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
                counter++;
            }
            assertEquals(8, counter);
            assertEquals(8, submissionQueue.count());
            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
            assertEquals(1, submissionQueue.count());
            submissionQueue.submitAndGet();
            assertEquals(9, completionQueue.count());
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void useAfterClose() {
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        ringBuffer.enable();
        ringBuffer.close();

        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

        assertNotNull(ringBuffer);
        assertNotNull(submissionQueue);
        assertNotNull(completionQueue);

        assertThrows(IllegalStateException.class, () -> submissionQueue.addNop((byte) 0, 1));
        assertThrows(IllegalStateException.class, submissionQueue::tryRegisterRingFd);
        assertThrows(IllegalStateException.class, submissionQueue::submit);
        assertThrows(IllegalStateException.class, submissionQueue::submitAndGet);

        assertEquals(0, completionQueue.count());
        assertFalse(completionQueue.hasCompletions());
        assertEquals(0, completionQueue.process((res, flags, data, cqeExtraData) -> {
            fail("Should not be called");
        }));

        // Ensure both return not null and also not segfault.
        assertNotNull(submissionQueue.toString());
        assertNotNull(completionQueue.toString());
    }

    @Test
    @DisabledIf("setUpCQSizeUnavailable")
    public void testSetUpCqSize() {
        int cqSize = 8;
        RingBuffer ringBuffer = Native.createRingBuffer(2, cqSize, Native.IORING_SETUP_CQSIZE);
        try {
            assertNotNull(ringBuffer);
            ringBuffer.enable();
            assertEquals(cqSize, ringBuffer.ioUringCompletionQueue().ringEntries);

            int count = cqSize;

            while (count > 0) {
                assertThat(ringBuffer.ioUringSubmissionQueue().addNop((byte) 0, 1)).isNotZero();
                count--;
                if (ringBuffer.ioUringSubmissionQueue().remaining() == 0) {
                    ringBuffer.ioUringSubmissionQueue().submitAndGet();
                }
            }

            ringBuffer.ioUringSubmissionQueue().submit();
            assertEquals(cqSize, ringBuffer.ioUringCompletionQueue().count());
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    @DisabledIf("setUpCQSizeUnavailable")
    public void testCqOverflow() {
        int cqSize = 2;
        RingBuffer ringBuffer = Native.createRingBuffer(2, cqSize, Native.IORING_SETUP_CQSIZE);
        try {
            assertNotNull(ringBuffer);
            ringBuffer.enable();
            assertNotEquals(0, ringBuffer.features() & Native.IORING_FEAT_NODROP);

            CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
            assertEquals(cqSize, completionQueue.ringEntries);

            SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
            submissionQueue.submitAndGet();
            assertEquals(0, submissionQueue.flags() & Native.IORING_SQ_CQ_OVERFLOW);

            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
            submissionQueue.submitAndGet();
            assertNotEquals(0, ringBuffer.ioUringSubmissionQueue().flags() & Native.IORING_SQ_CQ_OVERFLOW);

            // The completion queue should have only had space for 2 events
            int processed = completionQueue.process((res, flags, udata, extraCqeData) -> { });
            assertEquals(2, processed);

            // submit again to ensure we flush the event that did overflow
            submissionQueue.submitAndGetNow();
            processed = completionQueue.process((res, flags, udata, extraCqeData) -> { });
            assertEquals(1, processed);

            // Everything was processed and so the overflow flag should have been cleared
            assertEquals(0, submissionQueue.flags() & Native.IORING_SQ_CQ_OVERFLOW);
        } finally {
            ringBuffer.close();
        }
    }

    private static boolean setUpCQSizeUnavailable() {
        return !IoUring.isSetupCqeSizeSupported();
    }

    @Test
    public void testIoUringProbeSupported() {
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        Native.IoUringProbe ioUringProbe = Native.ioUringProbe(ringBuffer.fd());
        assertNotNull(ioUringProbe);
        assertNotEquals(0, ioUringProbe.lastOp);
        assertNotEquals(0, ioUringProbe.opsLen);
        assertNotNull(ioUringProbe.ops);
        assertFalse(Native.ioUringProbe(ioUringProbe, new int[] {Integer.MAX_VALUE}));
        assertDoesNotThrow(() -> Native.checkAllIOSupported(ioUringProbe));

        // Let's mark it as not supported.
        ioUringProbe.ops[Native.IORING_OP_READ] = new Native.IoUringProbeOp(Native.IORING_OP_READ, 0);
        assertFalse(Native.ioUringProbe(ioUringProbe, new int[] {Native.IORING_OP_READ}));
        assertThrows(UnsupportedOperationException.class, () -> Native.checkAllIOSupported(ioUringProbe));
    }

    public enum CqeSize {
        NORMAL,
        LARGE,
        MIXED;

        int setupFlag() {
            switch (this) {
                case NORMAL: return 0;
                case LARGE: return Native.IORING_SETUP_CQE32;
                case MIXED: return Native.IORING_SETUP_CQE_MIXED;
                default: throw new AssertionError();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(CqeSize.class)
    public void testCqe(CqeSize cqeSize) {
        assumeTrue(Native.ioUringSetupSupportsFlags(cqeSize.setupFlag()));

        RingBuffer ringBuffer = Native.createRingBuffer(8, cqeSize.setupFlag());
        ringBuffer.enable();
        try {
            SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
            final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

            assertNotNull(ringBuffer);
            assertNotNull(submissionQueue);
            assertNotNull(completionQueue);

            long result;
            if (cqeSize == CqeSize.MIXED) {
                result = submissionQueue.addNop((byte) 0, Native.IORING_NOP_CQE32, 1);
            } else {
                result = submissionQueue.addNop((byte) 0, 1);
            }
            assertThat(result).isNotZero();
            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();

            assertEquals(2, submissionQueue.submitAndGet());
            assertEquals(cqeSize == CqeSize.MIXED ? 3 : 2, completionQueue.count());
            int processed = completionQueue.process(new CompletionCallback() {
                private boolean first = true;
                @Override
                public void handle(int res, int flags, long udata, ByteBuffer extraCqeData) {
                    assertEquals(0, res);
                    assertEquals(cqeSize == CqeSize.MIXED && first ? Native.IORING_CQE_F_32 : 0, flags);
                    assertEquals(1, udata);
                    if (cqeSize == CqeSize.LARGE || (cqeSize == CqeSize.MIXED && first)) {
                        assertNotNull(extraCqeData);
                        assertEquals(0, extraCqeData.position());
                        assertEquals(16, extraCqeData.limit());
                    } else {
                        assertNull(extraCqeData);
                    }
                    first = false;
                }
            });
            assertEquals(2, processed);
            assertFalse(completionQueue.hasCompletions());
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void testGetOp() {
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        submissionQueue.addEventFdRead(10, 0L, 0, 8, 1);
        String expect = "SubmissionQueue [READ(fd=10)]";
        assertEquals(expect, submissionQueue.toString());
    }
}
