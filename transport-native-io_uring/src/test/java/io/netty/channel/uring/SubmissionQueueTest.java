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
            submissionQueue.submitAndWait();
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
        assertThrows(IllegalStateException.class, submissionQueue::submitAndWait);

        assertEquals(0, completionQueue.count());
        assertFalse(completionQueue.hasCompletions());
        assertEquals(0, completionQueue.process((res, flags, data) -> {
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
                    ringBuffer.ioUringSubmissionQueue().submitAndWait();
                }
            }

            ringBuffer.ioUringSubmissionQueue().submit();
            assertEquals(cqSize, ringBuffer.ioUringCompletionQueue().count());
        } finally {
            ringBuffer.close();
        }
    }

    private static boolean setUpCQSizeUnavailable() {
        return !IoUring.isSetupCqeSizeSupported();
    }
}
