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

import io.netty.util.ReferenceCounted;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;

import java.util.List;

final class PendingZeroCopyWrites {
    private static final long USER_DATA_STRIDE = 1L << 16;

    private final LongObjectMap<List<ReferenceCounted>> pendingWrites = new LongObjectHashMap<>(4);

    long nextUserData(short candidate) {
        long userData = candidate;
        while (pendingWrites.containsKey(userData)) {
            userData += USER_DATA_STRIDE;
        }
        return userData;
    }

    void register(long userData, List<ReferenceCounted> buffers) {
        assert !pendingWrites.containsKey(userData);
        pendingWrites.put(userData, buffers);
    }

    void release(long userData) {
        List<ReferenceCounted> buffers = pendingWrites.remove(userData);
        for (int i = 0; i < buffers.size(); i++) {
            buffers.get(i).release();
        }
    }

    boolean isEmpty() {
        return pendingWrites.isEmpty();
    }
}
