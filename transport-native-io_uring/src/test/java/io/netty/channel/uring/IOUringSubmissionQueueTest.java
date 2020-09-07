/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.uring;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class IOUringSubmissionQueueTest {

    @BeforeClass
    public static void loadJNI() {
        assumeTrue(IOUring.isAvailable());
    }

    @Test
    public void sqeFullTest() {
        RingBuffer ringBuffer = Native.createRingBuffer(8);
        try {
            IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();
            final IOUringCompletionQueue completionQueue = ringBuffer.getIoUringCompletionQueue();

            assertNotNull(ringBuffer);
            assertNotNull(submissionQueue);
            assertNotNull(completionQueue);

            int counter = 0;
            while (!submissionQueue.addAccept(-1)) {
                counter++;
            }
            assertEquals(8, counter);
        } finally {
            ringBuffer.close();
        }
    }
}
