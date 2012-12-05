/*
 * Copyright 2012 The Netty Project
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

package io.netty.buffer;

import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class PooledByteBufAllocator extends AbstractByteBufAllocator {

    private static final int DEFAULT_NUM_HEAP_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_NUM_DIRECT_ARENA = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_PAGE_SIZE = 8192;
    private static final int DEFAULT_MAX_ORDER = 11; // 8192 << 11 = 16 MiB per chunk

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    public static final PooledByteBufAllocator DEFAULT = new PooledByteBufAllocator();

    private final PoolArena<byte[]>[] heapArenas;
    private final PoolArena<ByteBuffer>[] directArenas;

    final ThreadLocal<PoolThreadCache> threadCache = new ThreadLocal<PoolThreadCache>() {
        private final AtomicInteger index = new AtomicInteger();
        @Override
        protected PoolThreadCache initialValue() {
            int idx = Math.abs(index.getAndIncrement() % heapArenas.length);
            return new PoolThreadCache(heapArenas[idx], directArenas[idx]);
        }
    };

    public PooledByteBufAllocator() {
        this(false);
    }

    public PooledByteBufAllocator(boolean directByDefault) {
        this(directByDefault, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER);
    }

    public PooledByteBufAllocator(int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        this(false, nHeapArena, nDirectArena, pageSize, maxOrder);
    }

    public PooledByteBufAllocator(
            boolean directByDefault, int nHeapArena, int nDirectArena, int pageSize, int maxOrder) {
        super(validateAndCalculateChunkSize(pageSize, maxOrder), directByDefault);
        if (nHeapArena <= 0) {
            throw new IllegalArgumentException("nHeapArena: " + nHeapArena + " (expected: 1+)");
        }
        if (nDirectArena <= 0) {
            throw new IllegalArgumentException("nDirectArea: " + nDirectArena + " (expected: 1+)");
        }

        int pageShifts = validateAndCalculatePageShifts(pageSize);
        int chunkSize = bufferMaxCapacity();

        heapArenas = newArenaArray(nHeapArena);
        for (int i = 0; i < heapArenas.length; i ++) {
            heapArenas[i] = new PoolArena.HeapArena(this, pageSize, maxOrder, pageShifts, chunkSize);
        }

        directArenas = newArenaArray(nDirectArena);
        for (int i = 0; i < directArenas.length; i ++) {
            directArenas[i] = new PoolArena.DirectArena(this, pageSize, maxOrder, pageShifts, chunkSize);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> PoolArena<T>[] newArenaArray(int size) {
        return new PoolArena[size];
    }

    private static int validateAndCalculatePageShifts(int pageSize) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: 4096+)");
        }

        // Ensure pageSize is power of 2.
        boolean found1 = false;
        int pageShifts = 0;
        for (int i = pageSize; i != 0 ; i >>= 1) {
            if ((i & 1) != 0) {
                if (!found1) {
                    found1 = true;
                } else {
                    throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2");
                }
            } else {
                if (!found1) {
                    pageShifts ++;
                }
            }
        }
        return pageShifts;
    }

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i --) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        return cache.heapArena.allocate(cache, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        return cache.directArena.allocate(cache, initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf ioBuffer() {
        return directBuffer(0);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(heapArenas.length);
        buf.append(" arena(s):");
        buf.append(StringUtil.NEWLINE);
        for (PoolArena<byte[]> a: heapArenas) {
            buf.append(a);
        }
        return buf.toString();
    }
}
