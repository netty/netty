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

final class MsgHdrMemoryArray {
    private int idx;
    private final MsgHdrMemory[] hdrs;
    private final int capacity;

    MsgHdrMemoryArray(int capacity) {
        this.capacity = capacity;
        hdrs = new MsgHdrMemory[capacity];
        for (int i = 0; i < hdrs.length; i++) {
            hdrs[i] = new MsgHdrMemory(i);
        }
    }

    MsgHdrMemory nextHdr() {
        if (idx == hdrs.length - 1) {
            return null;
        }
        return hdrs[idx++];
    }

    MsgHdrMemory hdr(int idx) {
        return hdrs[idx];
    }

    void clear() {
        idx = 0;
    }

    int length() {
        return idx;
    }

    void release() {
        for (MsgHdrMemory hdr: hdrs) {
            hdr.release();
        }
    }

    int capacity() {
        return capacity;
    }
}
