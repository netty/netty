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

/**
 * Reusable metadata needed to recover a submitted write's resources, without retaining the submission object.
 * Only fields used by the current write resource recovery paths are copied; this is not a complete SQE snapshot.
 */
final class WriteOpsSnapshot {
    private byte opcode;
    private int len;
    private int union3;
    private long data;

    void copyFrom(IoUringIoOps ops) {
        opcode = ops.opcode();
        len = ops.len();
        union3 = ops.union3();
        data = ops.userData();
    }

    byte opcode() {
        return opcode;
    }

    int len() {
        return len;
    }

    int union3() {
        return union3;
    }

    long userData() {
        return data;
    }
}
