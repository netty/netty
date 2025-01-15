/*
 * Copyright 2025 The Netty Project
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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringBufferRingTest {
    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testRegister() {
        RingBuffer ringBuffer = Native.createRingBuffer();
        try {
            int ringFd = ringBuffer.fd();
            long ioUringBufRingAddr = Native.ioUringRegisterBuffRing(ringFd, 4, (short) 1, 0);
            assumeTrue(
                    ioUringBufRingAddr > 0,
                    "ioUringSetupBufRing result must great than 0, but now result is " + ioUringBufRingAddr);
            int freeRes = Native.ioUringUnRegisterBufRing(ringFd, ioUringBufRingAddr, 4, 1);
            assumeTrue(
                    freeRes == 0,
                    "ioUringFreeBufRing result must be 0, but now result is " + freeRes
            );
        } finally {
            ringBuffer.close();
        }
    }
}
