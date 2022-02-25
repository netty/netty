/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.pool;

import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.util.internal.StringUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static io.netty5.buffer.api.pool.PoolChunk.isSubpage;
import static java.lang.Math.max;

class PoolArena extends SizeClasses implements PoolArenaMetric {
    private static final VarHandle SUBPAGE_ARRAY = MethodHandles.arrayElementVarHandle(PoolSubpage[].class);
    enum SizeClass {
        Small,
        Normal
    }

    final PooledBufferAllocator parent;
    final MemoryManager manager;
    final AllocationType allocationType;

    final int numSmallSubpagePools;
    final int directMemoryCacheAlignment;
    private final PoolSubpage[] smallSubpagePools;

    private final PoolChunkList q050;
    private final PoolChunkList q025;
    private final PoolChunkList q000;
    private final PoolChunkList qInit;
    private final PoolChunkList q075;
    private final PoolChunkList q100;

    private final List<PoolChunkListMetric> chunkListMetrics;

    // Metrics for allocations and deallocations
    private long allocationsNormal;

    // We need to use the LongAdder here as this is not guarded via synchronized block.
    private final LongAdder allocationsSmall = new LongAdder();
    private final LongAdder allocationsHuge = new LongAdder();
    private final LongAdder activeBytesHuge = new LongAdder();

    private long deallocationsSmall;
    private long deallocationsNormal;

    // We need to use the LongAdder here as this is not guarded via synchronized block.
    private final LongAdder deallocationsHuge = new LongAdder();

    // Number of thread caches backed by this arena.
    final AtomicInteger numThreadCaches = new AtomicInteger();

    protected PoolArena(PooledBufferAllocator parent, MemoryManager manager, AllocationType allocationType,
                        int pageSize, int pageShifts, int chunkSize, int cacheAlignment) {
        super(pageSize, pageShifts, chunkSize, cacheAlignment);
        this.parent = parent;
        this.manager = manager;
        this.allocationType = allocationType;
        directMemoryCacheAlignment = cacheAlignment;

        numSmallSubpagePools = nSubpages;
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);

        q100 = new PoolChunkList(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList(this, q050, 25, 75, chunkSize);
        q000 = new PoolChunkList(this, q025, 1, 50, chunkSize);
        qInit = new PoolChunkList(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);

        chunkListMetrics = List.of(qInit, q000, q025, q050, q075, q100);
    }

    private static PoolSubpage newSubpagePoolHead() {
        PoolSubpage head = new PoolSubpage();
        head.prev = head;
        head.next = head;
        return head;
    }

    private static PoolSubpage[] newSubpagePoolArray(int size) {
        return new PoolSubpage[size];
    }

    UntetheredMemory allocate(PoolThreadCache cache, int size) {
        final int sizeIdx = size2SizeIdx(size);

        if (sizeIdx <= smallMaxSizeIdx) {
            return tcacheAllocateSmall(cache, size, sizeIdx);
        } else if (sizeIdx < nSizes) {
            return tcacheAllocateNormal(cache, size, sizeIdx);
        } else {
            int normCapacity = directMemoryCacheAlignment > 0
                    ? normalizeSize(size) : size;
            // Huge allocations are never served via the cache so just call allocateHuge
            return allocateHuge(normCapacity);
        }
    }

    private UntetheredMemory tcacheAllocateSmall(PoolThreadCache cache, final int size, final int sizeIdx) {
        UntetheredMemory memory = cache.allocateSmall(size, sizeIdx);
        if (memory != null) {
            // was able to allocate out of the cache so move on
            return memory;
        }

        /*
         * Synchronize on the head. This is needed as {@link PoolChunk#allocateSubpage(int)} and
         * {@link PoolChunk#free(long)} may modify the doubly linked list as well.
         */
        PoolSubpage head = findSubpagePoolHead(sizeIdx);
        final boolean needsNormalAllocation;
        synchronized (head) {
            final PoolSubpage s = head.next;
            needsNormalAllocation = s == head;
            if (!needsNormalAllocation) {
                assert s.doNotDestroy && s.elemSize == sizeIdx2size(sizeIdx) :
                        "doNotDestroy=" + s.doNotDestroy + ", elemSize=" + s.elemSize + ", sizeIdx=" + sizeIdx;
                long handle = s.allocate();
                assert handle >= 0;
                memory = s.chunk.allocateBufferWithSubpage(handle, size, cache);
            }
        }

        if (needsNormalAllocation) {
            synchronized (this) {
                memory = allocateNormal(size, sizeIdx, cache);
            }
        }

        incSmallAllocation();
        return memory;
    }

    private UntetheredMemory tcacheAllocateNormal(
            PoolThreadCache cache, int size, int sizeIdx) {
        UntetheredMemory memory = cache.allocateNormal(this, size, sizeIdx);
        if (memory != null) {
            // was able to allocate out of the cache so move on
            return memory;
        }
        synchronized (this) {
            memory = allocateNormal(size, sizeIdx, cache);
            allocationsNormal++;
        }
        return memory;
    }

    // Method must be called inside synchronized(this) { ... } block
    private UntetheredMemory allocateNormal(int size, int sizeIdx, PoolThreadCache threadCache) {
        UntetheredMemory memory = q050.allocate(size, sizeIdx, threadCache);
        if (memory != null) {
            return memory;
        }
        memory = q025.allocate(size, sizeIdx, threadCache);
        if (memory != null) {
            return memory;
        }
        memory = q000.allocate(size, sizeIdx, threadCache);
        if (memory != null) {
            return memory;
        }
        memory = qInit.allocate(size, sizeIdx, threadCache);
        if (memory != null) {
            return memory;
        }
        memory = q075.allocate(size, sizeIdx, threadCache);
        if (memory != null) {
            return memory;
        }

        // Add a new chunk.
        PoolChunk c = newChunk(pageSize, nPSizes, pageShifts, chunkSize);
        memory = c.allocate(size, sizeIdx, threadCache);
        assert memory != null;
        qInit.add(c);
        return memory;
    }

    private void incSmallAllocation() {
        allocationsSmall.increment();
    }

    private UntetheredMemory allocateHuge(int size) {
        activeBytesHuge.add(size);
        allocationsHuge.increment();
        return new UnpooledUntetheredMemory(parent, manager, allocationType, size);
    }

    void free(PoolChunk chunk, long handle, int normCapacity, PoolThreadCache cache) {
        SizeClass sizeClass = sizeClass(handle);
        if (cache != null && cache.add(this, chunk, handle, normCapacity, sizeClass)) {
            // cached so not free it.
            return;
        }
        freeChunk(chunk, handle, normCapacity, sizeClass);
    }

    private static SizeClass sizeClass(long handle) {
        return isSubpage(handle) ? SizeClass.Small : SizeClass.Normal;
    }

    void freeChunk(PoolChunk chunk, long handle, int normCapacity, SizeClass sizeClass) {
        final boolean destroyChunk;
        synchronized (this) {
            if (sizeClass == SizeClass.Normal) {
                ++deallocationsNormal;
            } else if (sizeClass == SizeClass.Small) {
                ++deallocationsSmall;
            } else {
                throw new AssertionError("Unexpected size class: " + sizeClass);
            }
            destroyChunk = !chunk.parent.free(chunk, handle, normCapacity);
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            chunk.destroy();
        }
    }

    PoolSubpage findSubpagePoolHead(int sizeIdx) {
        PoolSubpage head = (PoolSubpage) SUBPAGE_ARRAY.getVolatile(smallSubpagePools, sizeIdx);
        if (head == null) {
            head = newSubpagePoolHead();
            if (!SUBPAGE_ARRAY.compareAndSet(smallSubpagePools, sizeIdx, null, head)) {
                // We lost the race. Read the winning value.
                head = (PoolSubpage) SUBPAGE_ARRAY.getVolatile(smallSubpagePools, sizeIdx);
            }
        }
        return head;
    }

    @Override
    public int numThreadCaches() {
        return numThreadCaches.get();
    }

    @Override
    public int numSmallSubpages() {
        return smallSubpagePools.length;
    }

    @Override
    public int numChunkLists() {
        return chunkListMetrics.size();
    }

    @Override
    public List<PoolSubpageMetric> smallSubpages() {
        return subPageMetricList(smallSubpagePools);
    }

    @Override
    public List<PoolChunkListMetric> chunkLists() {
        return chunkListMetrics;
    }

    private static List<PoolSubpageMetric> subPageMetricList(PoolSubpage[] pages) {
        List<PoolSubpageMetric> metrics = new ArrayList<>();
        for (int i = 0, len = pages.length; i < len; i++) {
            PoolSubpage head = (PoolSubpage) SUBPAGE_ARRAY.getVolatile(pages, i);
            if (head == null || head.next == head) {
                continue;
            }
            PoolSubpage s = head.next;
            do {
                metrics.add(s);
                s = s.next;
            } while (s != head);
        }
        return metrics;
    }

    @Override
    public long numAllocations() {
        final long allocsNormal;
        synchronized (this) {
            allocsNormal = allocationsNormal;
        }

        return allocationsSmall.longValue() + allocsNormal + allocationsHuge.longValue();
    }

    @Override
    public long numSmallAllocations() {
        return allocationsSmall.longValue();
    }

    @Override
    public synchronized long numNormalAllocations() {
        return allocationsNormal;
    }

    @Override
    public long numDeallocations() {
        final long deallocs;
        synchronized (this) {
            deallocs = deallocationsSmall + deallocationsNormal;
        }
        return deallocs + deallocationsHuge.longValue();
    }

    @Override
    public synchronized long numSmallDeallocations() {
        return deallocationsSmall;
    }

    @Override
    public synchronized long numNormalDeallocations() {
        return deallocationsNormal;
    }

    @Override
    public long numHugeAllocations() {
        return allocationsHuge.longValue();
    }

    @Override
    public long numHugeDeallocations() {
        return deallocationsHuge.longValue();
    }

    @Override
    public  long numActiveAllocations() {
        long val = allocationsSmall.longValue() + allocationsHuge.longValue()
                - deallocationsHuge.longValue();
        synchronized (this) {
            val += allocationsNormal - (deallocationsSmall + deallocationsNormal);
        }
        return max(val, 0);
    }

    @Override
    public long numActiveSmallAllocations() {
        return max(numSmallAllocations() - numSmallDeallocations(), 0);
    }

    @Override
    public long numActiveNormalAllocations() {
        final long val;
        synchronized (this) {
            val = allocationsNormal - deallocationsNormal;
        }
        return max(val, 0);
    }

    @Override
    public long numActiveHugeAllocations() {
        return max(numHugeAllocations() - numHugeDeallocations(), 0);
    }

    @Override
    public long numActiveBytes() {
        long val = activeBytesHuge.longValue();
        synchronized (this) {
            for (int i = 0; i < chunkListMetrics.size(); i++) {
                for (PoolChunkMetric m: chunkListMetrics.get(i)) {
                    val += m.chunkSize();
                }
            }
        }
        return max(0, val);
    }

    protected final PoolChunk newChunk(int pageSize, int maxPageIdx, int pageShifts, int chunkSize) {
        return new PoolChunk(this, pageSize, pageShifts, chunkSize, maxPageIdx);
    }

    @Override
    public synchronized String toString() {
        StringBuilder buf = new StringBuilder()
            .append("Chunk(s) at 0~25%:")
            .append(StringUtil.NEWLINE)
            .append(qInit)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 0~50%:")
            .append(StringUtil.NEWLINE)
            .append(q000)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 25~75%:")
            .append(StringUtil.NEWLINE)
            .append(q025)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 50~100%:")
            .append(StringUtil.NEWLINE)
            .append(q050)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 75~100%:")
            .append(StringUtil.NEWLINE)
            .append(q075)
            .append(StringUtil.NEWLINE)
            .append("Chunk(s) at 100%:")
            .append(StringUtil.NEWLINE)
            .append(q100)
            .append(StringUtil.NEWLINE)
            .append("small subpages:");
        appendPoolSubPages(buf, smallSubpagePools);
        buf.append(StringUtil.NEWLINE);

        return buf.toString();
    }

    private static void appendPoolSubPages(StringBuilder buf, PoolSubpage[] subpages) {
        for (int i = 0; i < subpages.length; i ++) {
            PoolSubpage head = (PoolSubpage) SUBPAGE_ARRAY.getVolatile(subpages, i);
            if (head == null || head.next == head) {
                continue;
            }

            buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ");
            PoolSubpage s = head.next;
            do {
                buf.append(s);
                s = s.next;
            } while (s != head);
        }
    }

    public void close() {
        for (int i = 0, len = smallSubpagePools.length; i < len; i++) {
            PoolSubpage page = (PoolSubpage) SUBPAGE_ARRAY.getVolatile(smallSubpagePools, i);
            if (page != null) {
                page.destroy();
            }
        }
        for (PoolChunkList list : new PoolChunkList[] {qInit, q000, q025, q050, q100}) {
            list.destroy();
        }
    }
}
