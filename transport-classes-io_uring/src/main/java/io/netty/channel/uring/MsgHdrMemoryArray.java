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

import io.netty.channel.unix.Buffer;
import io.netty.util.internal.CleanableDirectBuffer;

import java.nio.ByteBuffer;
import java.util.Arrays;

final class MsgHdrMemoryArray {
    static final long NO_ID = 0;

    private final MsgHdrMemory[] hdrs;
    private final int capacity;
    private final long[] ids;
    private final CleanableDirectBuffer msgHdrMemoryArrayMemoryCleanable;
    private boolean released;
    private int idx;

    MsgHdrMemoryArray(short capacity) {
        assert capacity >= 0;
        this.capacity = capacity;
        hdrs = new MsgHdrMemory[capacity];
        ids = new long[capacity];
        int total = MsgHdrMemory.MSG_HDR_SIZE * capacity;
        this.msgHdrMemoryArrayMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(total);
        ByteBuffer msgHdrMemoryArrayMemory = msgHdrMemoryArrayMemoryCleanable.buffer();
        for (int i = 0; i < hdrs.length; i++) {
            hdrs[i] = new MsgHdrMemory((short) i, msgHdrMemoryArrayMemory);
            ids[i] = NO_ID;
        }
    }

    boolean isFull() {
        return idx == hdrs.length;
    }

    MsgHdrMemory nextHdr() {
        if (isFull()) {
            return null;
        }
        return hdrs[idx++];
    }

    void restoreNextHdr(MsgHdrMemory hdr) {
        assert hdr.idx() == idx - 1;
        idx--;
    }

    MsgHdrMemory hdr(int idx) {
        return hdrs[idx];
    }

    long id(int idx) {
        return ids[idx];
    }

    void setId(int idx, long id) {
        ids[idx] = id;
    }

    void clear() {
        Arrays.fill(ids, 0, idx, NO_ID);
        idx = 0;
    }

    int length() {
        return idx;
    }

    void release() {
        assert !released;
        released = true;
        for (MsgHdrMemory hdr: hdrs) {
            hdr.release();
        }
        msgHdrMemoryArrayMemoryCleanable.clean();
    }

    int capacity() {
        return capacity;
    }
}
