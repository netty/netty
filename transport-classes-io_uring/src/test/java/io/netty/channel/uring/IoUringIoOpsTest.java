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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IoUringIoOpsTest {

    private static final long OVERFLOW_DATA = 0x1_0000L + 5;
    private static final long SHORT_RANGE_DATA = 100L;

    @Test
    void sendZcKeepsUserDataOutsideShortRange() {
        IoUringIoOps ops = IoUringIoOps.newSendZc(1, 0xdeadbeefL, 128, 0, OVERFLOW_DATA, 0);

        assertEquals(Native.IORING_OP_SEND_ZC, ops.opcode());
        assertEquals(OVERFLOW_DATA, ops.userData());
    }

    @Test
    void sendmsgZcKeepsUserDataOutsideShortRange() {
        IoUringIoOps ops = IoUringIoOps.newSendmsgZc(1, (byte) 0, 0, 0xdeadbeefL, OVERFLOW_DATA);

        assertEquals(Native.IORING_OP_SENDMSG_ZC, ops.opcode());
        assertEquals(OVERFLOW_DATA, ops.userData());
    }

    @Test
    void zeroCopyOpsKeepUserDataInsideShortRange() {
        IoUringIoOps sendZc = IoUringIoOps.newSendZc(1, 0xdeadbeefL, 128, 0, SHORT_RANGE_DATA, 0);
        assertEquals(SHORT_RANGE_DATA, sendZc.userData());

        IoUringIoOps sendmsgZc = IoUringIoOps.newSendmsgZc(1, (byte) 0, 0, 0xdeadbeefL, SHORT_RANGE_DATA);
        assertEquals(SHORT_RANGE_DATA, sendmsgZc.userData());
    }
}
