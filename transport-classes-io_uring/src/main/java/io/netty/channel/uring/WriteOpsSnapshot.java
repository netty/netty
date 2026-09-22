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
 * Reusable copy of a submitted write's fields, without retaining the submission object.
 */
final class WriteOpsSnapshot {
    private byte opcode;
    private byte flags;
    private short ioPrio;
    private int fd;
    private long union1;
    private long union2;
    private int len;
    private int union3;
    private long data;
    private short personality;
    private short union4;
    private int union5;
    private long union6;

    void copyFrom(IoUringIoOps ops) {
        opcode = ops.opcode();
        flags = ops.flags();
        ioPrio = ops.ioPrio();
        fd = ops.fd();
        union1 = ops.union1();
        union2 = ops.union2();
        len = ops.len();
        union3 = ops.union3();
        data = ops.userData();
        union4 = ops.union4();
        personality = ops.personality();
        union5 = ops.union5();
        union6 = ops.union6();
    }

    byte opcode() {
        return opcode;
    }

    byte flags() {
        return flags;
    }

    short ioPrio() {
        return ioPrio;
    }

    int fd() {
        return fd;
    }

    long union1() {
        return union1;
    }

    long union2() {
        return union2;
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

    short personality() {
        return personality;
    }

    short union4() {
        return union4;
    }

    int union5() {
        return union5;
    }

    long union6() {
        return union6;
    }
}
