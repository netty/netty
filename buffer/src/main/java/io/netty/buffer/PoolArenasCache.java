/*
 * Copyright 2023 The Netty Project
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

package io.netty.buffer;

import io.netty.buffer.PoolArena.SizeClass;

import java.nio.ByteBuffer;

class PoolArenasCache {
    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;
    final PoolArena<byte[]> heapArena;
    final PoolArena<ByteBuffer> directArena;

    PoolArenasCache(PoolArena<byte[]> heapArena, PoolArena<ByteBuffer> directArena) {
        this.heapArena = heapArena;
        this.directArena = directArena;
    }

    boolean allocateSmall(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int sizeIdx) {
        return false;
    }

    boolean allocateNormal(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int sizeIdx) {
        return false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    boolean add(PoolArena<?> area, PoolChunk chunk, ByteBuffer nioBuffer,
                long handle, int normCapacity, SizeClass sizeClass) {
        return false;
    }

    void trim() {
    }

    void free(boolean b) {
    }

    // val > 0
    static int log2(int val) {
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

}
