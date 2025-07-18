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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
            return false;
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
            assertEquals(cqSize, ringBuffer.ioUringCompletionQueue().ringEntries);

            assertThat(ringBuffer.ioUringSubmissionQueue().addNop((byte) 0, 1)).isNotZero();
            assertThat(ringBuffer.ioUringSubmissionQueue().addNop((byte) 0, 1)).isNotZero();
            ringBuffer.ioUringSubmissionQueue().submitAndGet();
            assertEquals(0, ringBuffer.ioUringSubmissionQueue().flags() & Native.IORING_SQ_CQ_OVERFLOW);

            assertThat(ringBuffer.ioUringSubmissionQueue().addNop((byte) 0, 1)).isNotZero();
            assertThat(ringBuffer.ioUringSubmissionQueue().addNop((byte) 0, 1)).isNotZero();
            ringBuffer.ioUringSubmissionQueue().submitAndGet();
            assertNotEquals(0, ringBuffer.ioUringSubmissionQueue().flags() & Native.IORING_SQ_CQ_OVERFLOW);
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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void testCqe(boolean useCqe32) {
        RingBuffer ringBuffer = Native.createRingBuffer(8, useCqe32 ? Native.IORING_SETUP_CQE32 : 0);
        ringBuffer.enable();
        try {
            SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
            final CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();

            assertNotNull(ringBuffer);
            assertNotNull(submissionQueue);
            assertNotNull(completionQueue);

            assertThat(submissionQueue.addNop((byte) 0, 1)).isNotZero();
            assertEquals(1, submissionQueue.submitAndGet());
            assertEquals(1, completionQueue.count());
            int processed = completionQueue.process((res, flags, udata, extraCqeData) -> {
                assertEquals(0, res);
                assertEquals(0, flags);
                assertEquals(1, udata);
                if (useCqe32) {
                    assertNotNull(extraCqeData);
                    assertEquals(0, extraCqeData.position());
                    assertEquals(16, extraCqeData.limit());
                } else {
                    assertNull(extraCqeData);
                }
                return true;
            });
            assertEquals(1, processed);
            assertFalse(completionQueue.hasCompletions());
        } finally {
            ringBuffer.close();
        }
    }
}
