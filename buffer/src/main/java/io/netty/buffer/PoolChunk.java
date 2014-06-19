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

import io.netty.util.collection.IntObjectHashMap;

final class PoolChunk<T> {

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;

    private final byte[] memoryMap;
    private final byte[] depths;
    private final PoolSubpage<T>[] subpages;
    private final IntObjectHashMap<PoolSubpage<T>> elemSubpages;
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;
    private final int pageSize;
    private final int pageShifts;
    private final int maxOrder;

    private final int chunkSize;
    private final int log2ChunkSize;
    private final int maxSubpageAllocs;

    private int freeBytes;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    @SuppressWarnings("unchecked")
    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        log2ChunkSize = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is : " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        // store depths for ids of memoryMap
        depths = new byte[maxSubpageAllocs << 1];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++d) { // move down the tree one level at a time
            byte depth = (byte) d;
            for (int p = 0; p < (1 << d); ++p) {
                depths[memoryMapIndex] = depth;
                // in each level traverse left to right and set the height of subtree
                // that is completely free to be my height since I am totally free to start with
                memoryMap[memoryMapIndex] = depth;
                memoryMapIndex += 1;
            }
        }

        subpages = new PoolSubpage[maxSubpageAllocs];
        elemSubpages = new IntObjectHashMap<PoolSubpage<T>>(pageShifts);
    }

    /** Creates a special chunk that is not pooled. */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        memoryMap = null;
        depths = null;
        subpages = null;
        elemSubpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        chunkSize = size;
        log2ChunkSize = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(chunkSize);
        maxSubpageAllocs = 0;
    }

    int usage() {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    long allocate(int normCapacity) {
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize
            return allocateRun(normCapacity);
        } else {
            return allocateSubpage(normCapacity);
        }
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal height at which subtree rooted at id has some free space
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;
            byte mem1 = memoryMap[id];
            byte mem2 = memoryMap[id ^ 1];
            memoryMap[parentId] = mem1 < mem2 ? mem1 : mem2;
            id = parentId;
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depths[id] + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte mem1 = memoryMap[id];
            byte mem2 = memoryMap[id ^ 1];
            memoryMap[parentId] = mem1 < mem2 ? mem1 : mem2;
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up
            memoryMap[parentId] = (byte) (mem1 == logChild && mem2 == logChild ?
                logChild - 1 : memoryMap[parentId]);
            id = parentId;
        }
    }

    private int allocateNode(int h) {
        int id = 1;
        byte mem = memoryMap[id];
        if (mem > h) { // unusable
            return -1;
        }
        while (mem < h || (id & (1 << h)) == 0) {
            id = id << 1;
            mem = memoryMap[id];
            if (mem > h) {
                id = id ^ 1;
                mem = memoryMap[id];
            }
        }
        memoryMap[id] = (byte) (maxOrder + 1); // mark as unusable : because, maximum input h = maxOrder
        updateParentsAlloc(id);
        return id;
    }

    private long allocateRun(int normCapacity) {
        int numPages = normCapacity >>> pageShifts;
        int h = maxOrder -  (Integer.SIZE - 1 - Integer.numberOfLeadingZeros(numPages));
        int id = allocateNode(h);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);
        return id;
    }

    private long allocateSubpage(int normCapacity) {
        PoolSubpage<T> subpage = elemSubpages.get(normCapacity);
        if (subpage != null) {
            long handle = subpage.allocate();
            if (handle >= 0) {
                return handle;
            }
            // if subpage full (i.e., handle < 0) then replace in elemSubpage with new subpage
        }
        return allocateSubpageSimple(normCapacity);
    }

    private long allocateSubpageSimple(int normCapacity) {
        int h = maxOrder; // subpages are only be allocated from pages i.e., leaves
        int id = allocateNode(h);
        if (id < 0) {
            return id;
        }
        freeBytes -= pageSize;

        int subpageIdx = subpageIdx(id);
        PoolSubpage<T> subpage = subpages[subpageIdx];
        if (subpage == null) {
            subpage = new PoolSubpage<T>(this, id, runOffset(id), pageSize, normCapacity);
            subpages[subpageIdx] = subpage;
        } else {
            subpage.init(normCapacity);
        }
        elemSubpages.put(normCapacity, subpage); // store subpage at proper elemSize pos
        return subpage.allocate();
    }

    void free(long handle) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> 32);

        if (bitmapIdx != 0) { // free a subpage
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;
            if (subpage.free(bitmapIdx & 0x3FFFFFFF)) {
                return;
            }
        }

        freeBytes += runLength(memoryMapIdx);
        memoryMap[memoryMapIdx] = depths[memoryMapIdx];
        updateParentsFree(memoryMapIdx);
    }

    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        int memoryMapIdx = (int) handle;
        int bitmapIdx = (int) (handle >>> 32);
        if (bitmapIdx == 0) {
            byte val = memoryMap[memoryMapIdx];
            assert val == (maxOrder + 1) : String.valueOf(val);
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx));
        } else {
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, (int) (handle >>> 32), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = (int) handle;

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
            this, handle,
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize, reqCapacity, subpage.elemSize);
    }

    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << (log2ChunkSize - depths[id]);
    }

    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id - (1 << depths[id]);
        return shift * runLength(id);
    }

    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx - maxSubpageAllocs;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Chunk(");
        buf.append(Integer.toHexString(System.identityHashCode(this)));
        buf.append(": ");
        buf.append(usage());
        buf.append("%, ");
        buf.append(chunkSize - freeBytes);
        buf.append('/');
        buf.append(chunkSize);
        buf.append(')');
        return buf.toString();
    }
}
