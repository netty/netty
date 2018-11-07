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

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 * 表述 从PoolChunk分配PageRun/PoolSubpage的算法；
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > chunk - a chunk is a collection of pages
 * > in this code chunkSize = 2^{maxOrder} * pageSize
 *
 * To begin we allocate  a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 *
 * //
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 *
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 *
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 *
 * depth=0        1 node (chunkSize)
 * depth=1        2 nodes (chunkSize/2)
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d)
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize)
 *
 * depth=maxOrder is the last level and the leafs consist of pages
 *
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 *
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 *   memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 *   is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 *
 *  As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 *
 * Initialization -
 *   In the beginning we construct the memoryMap array by storing the depth of a node at each node
 *     i.e., memoryMap[id] = depth_of_id
 *
 * Observations:
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 *                                    some of its children can still be allocated based on their availability
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 *                                    is thus marked as unusable
 *
 * Algorithm: [allocateNode(d) => we want to find the first node (from left) at height h that can be allocated]
 * ----------
 * 1) start at root (i.e., depth = 0 or id = 1)
 * 2) if memoryMap[1] > d => cannot be allocated from this chunk
 * 3) if left node value <= h; we can allocate from left subtree so move to left and repeat until found
 * 4) else try in right subtree
 *
 * Algorithm: [allocateRun(size)]
 * ----------
 * 1) Compute d = log_2(chunkSize/size)
 * 2) Return allocateNode(d)
 *
 * Algorithm: [allocateSubpage(size)]
 * ----------
 * 1) use allocateNode(maxOrder) to find an empty (i.e., unused) leaf (i.e., page)
 * 2) use this handle to construct the PoolSubpage object or if it already exists just call init(normCapacity)
 *    note that this PoolSubpage object is added to subpagesPool in the PoolArena when we init() it
 *
 * Note:
 * -----
 * In the implementation for improving cache coherence,
 * we store 2 pieces of information depth_of_id and x as two byte values in memoryMap and depthMap respectively
 *
 * memoryMap[id]= depth_of_id  is defined above
 * depthMap[id]= x  indicates that the first node which is free to be allocated is at depth x (from root)
 */
//
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;// chunk所属的arena
    // memory是一个容量为chunkSize的byte[](heap方式)或ByteBuffer(direct方式)
    final T memory;// 实际的内存块
    final boolean unpooled; // 是否非池化
    final int offset;

    private final byte[] memoryMap;// 分配信息二叉树
    private final byte[] depthMap;// 高度信息二叉树
    private final PoolSubpage<T>[] subpages; // sub page节点数组,一整块chunk内存被分配；
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;// 判断分配请求为Tiny/Small即分配subpage
    private final int pageSize; // 页大小，默认为8KB=8192
    private final int pageShifts;// 从1开始左移到页大小的位置，默认13，1<<13 = 8192
    private final int maxOrder;// 最大高度，默认11
    private final int chunkSize;// chunk块大小，默认16MB
    private final int log2ChunkSize;// log2(16MB) = 24
    private final int maxSubpageAllocs;// 可分配subpage的最大节点数即11层节点数，默认2048
    /** Used to mark memory as unusable */
    private final byte unusable;// 标记节点不可用，最大高度 + 1， 默认12

    private int freeBytes;// 可分配字节数

    PoolChunkList<T> parent; // poolChunkList专用
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    //用于普通初始化
    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize, int offset) {
        unpooled = false;
        this.arena = arena;
        // memory是一个容量为chunkSize的byte[](heap方式)或ByteBuffer(direct方式)
        this.memory = memory;
        // 每个 page的大小，默认为8192 8k
        // 00000000 00000000 00100000 00000000;
        this.pageSize = pageSize;
        // 13,   2 ^ 13 = 8192 移动；转变；转换
        this.pageShifts = pageShifts;
        // 默认11
        this.maxOrder = maxOrder;
        // 默认 8192 << 11 = 16MB; 8k * 2048; 2^13 * 2^11=2^24=16M  ; 2^10= 1024
        this.chunkSize = chunkSize;
        this.offset = offset;
        // 12, 当memoryMap[id] = unusable时，则表示id节点已被分配
        unusable = (byte) (maxOrder + 1); //
        // 24, 2 ^ 24 = 16M
        log2ChunkSize = log2(chunkSize); //
        // -8192
        // 00000000 00000000 00100000 00000000;
        // 00000000 00000000 00011111 11111111;
        // 11111111 11111111 11100000 00000000;
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        // 2048, 最多能被分配的Subpage个数,满二叉树叶子节点的个数；
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        // 满二叉树节点的个数[maxSubpageAllocs << 1 = 4096；
        memoryMap = new byte[maxSubpageAllocs << 1];
        //
        depthMap = new byte[memoryMap.length];

        int memoryMapIndex = 1;
        // 分配完成后，memoryMap->[0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3…] //memoryMap[0]无效
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time

            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;// 设置高度
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }
        // sub pages包含了max Sub page Allocs(2048)个Pool Sub page,
        // 每个sub page会从chunk中分配到自己自有的内存段，两个sub page不会操作相同的段，
        // 此处只是初始化一个数组，还没有实际的实例化各个元素
        subpages = newSubpageArray(maxSubpageAllocs);
    }

    /** Creates a special chunk that is not pooled. */
    // 用于非池化初始化（Huge分配请求）
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        memoryMap = null;
        depthMap = null;
        subpages = null;
        subpageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubpageAllocs = 0;
    }

    @SuppressWarnings("unchecked")
    //
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    // 向PoolChunk申请一段内存：
    // normCapacity已经处理过
    long allocate(int normCapacity) {
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize 即Normal请求
            // 大于等于pageSize时,返回可分配normCapacity的节点id;
            return allocateRun(normCapacity);
        } else {
            // 小于pageSize时,返回的是一个特殊的long型handle
            // handle = 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
            // 与上面直接返回节点id的逻辑对比可以知道，当handle<Integer.MAX_VALUE时，它表示chunk的节点id；
            // 当handle>Integer.MAX_VALUE，他分配的是一个Subpage,节点id=memoryMapIdx, 且能得到Subpage的bitmapIdx，bitmapIdx后面会讲其用处
            return allocateSubpage(normCapacity);
        }
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    // 更新祖先节点的分配信息的代码如下：
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1; // 找到父节点
            byte val1 = value(id);// 父节点值
            byte val2 = value(id ^ 1); // 父节点的兄弟（左或者右）节点值
            // 得到左节点和右节点较小的值赋给父节点，即两个节点只要有一个可分配，则父节点的值设为可分配的这个节点的值
            byte val = val1 < val2 ? val1 : val2; // 取较小值
            setValue(parentId, val);
            id = parentId;// 递归更新
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */

    // 释放过程相对简单，释放时更新祖先节点的分配信息是分配时的逆过程;
    private void updateParentsFree(int id) {
        //
        int logChild = depth(id) + 1;
        while (id > 1) {
            //
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            if (val1 == logChild && val2 == logChild) {
                //当两个子节点都可分配时，该节点变回自己所在层的depth，表示该节点也可被分配
                setValue(parentId, (byte) (logChild - 1));
            } else {
                // 否则与上面的updateParentsAlloc逻辑相同
                // 此时至少有一个子节点被分配，取最小值
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * Algorithm to allocate分配 an index in memoryMap when we query for a free node
     * at depth d
     * 查询一个空闲的节点分配
     * @param d depth
     * @return index in memoryMap
     */
    // 在某一层寻找可用节点的代码如下：
    //allocateNode(int d)传入的参数为depth, 通过depth来搜索对应层级第一个可用的node id
    private int allocateNode(int d) {

        // 如 d = 11, 则initial  = -2048
        //    0000000100 00000000
        //    1111111011 11111111 取反
        //    1111111100 00000000 加1
        // 作用：所有高度 < d 的节点 id & initial = 0
        int initial = - (1 << d); // has last d bits = 0 and rest all = 1
        // value(id)=memoryMap[id]
        int id = 1;
        byte val = value(id);// = memoryMap[id]
        // 第一层节点的值大于d, 表示d 层及以上都不可分配，此次分配失败;
        if (val > d) { // unusable// 没有满足需求的节点
            return -1;
        }
        // val<d 子节点可满足需求
        // id & initial == 0 高度<d
        // 这里从第二层开始从上往下查找, 一直找到指定层
        // 沿着一条路径进行寻找
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            // 往下一层
            id <<= 1; // 下一层第一个节点ID// 高度加1，进入子节点

            val = value(id); // = memoryMap[id]
            if (val > d) {// 左节点不满足
                // 上面对一层节点的值判断已经表示有可用内存可分配，因此发现当前节点不可分配时，
                // 直接切换到父节点的另一子节点，即id ^= 1
                id ^= 1;// 右节点
                val = value(id);
            }
        }
        // 此时val = d
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        // 该节点本次分配成功，将其标为不可用
        setValue(id, unusable); // mark as unusable 12// 找到符合需求的节点并标记为不可用
        // 更新父节点的值
        updateParentsAlloc(id);// 更新祖先节点的分配信息
        return id;
    }

    /**
     * Allocate a run  of pages 连续的、一系列的(>=1)
     *
     * @param normCapacity normalized 标准化的；正规化的；规格化的 capacity
     * @return index in memoryMap
     */
    // 首先看Normal请求，该请求需要分配至少一个Page的内存，代码实现如下：
    private long allocateRun(int normCapacity) {
        // 计算满足需求的节点的高度
        // log2(val) == Integer.SIZE - 1 - Integer.numberOfLeadingZeros(val)
        // 如 normCapacity = 8192, log2(8192)=13, d=11;pageShifts=13
        int d = maxOrder - (log2(normCapacity) - pageShifts);
        // 通过上面一行算出对应大小需要从那一层分配，得到d后，调用allocateNode来获取对应id
        // 在该高度层找到空闲的节点
        int id = allocateNode(d);
        if (id < 0) {
            return id;// 没有找到
        }
        // run Length = id所在深度占用的字节数
        freeBytes -= runLength(id);// 分配后剩余的字节数
        return id;
    }

    /**
     * Create/ initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created/ initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     */
    // 接着分析Tiny/Small请求的分配实现allocateSubpage()，代码如下：
    private long allocateSubpage(int normCapacity) {
        // the PoolSubPage pool
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.

        // 找到arena中对应的subpage头节点
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        // 加锁，分配过程会修改链表结构
        synchronized (head) {
            // subpage 只能在二叉树的最大高度分配即分配叶子节点
            int d = maxOrder; // subpages are only be allocated from pages i.e., leaves// subpage 只能在二叉树的最大高度分配,即分配叶子节点
            int id = allocateNode(d);
            if (id < 0) {
                return id;// 叶子节点全部分配完毕
            }

            //
            final PoolSubpage<T>[] subpages = this.subpages;

            final int pageSize = this.pageSize;

            freeBytes -= pageSize;

            // 存储-数据的节点在最后一层，而最后一层的左边第一个节点index=2048,因此若id=2048则sub pageIdx=0，id=2049，sub pageIdx=1
            // 根据这个规律找到对应位置的PoolSub page;
            // 得到节点在sub pages中的index；
            // 得到-叶子节点的偏移索引，从0开始，即2048-0,2049-1,...
            int subpageIdx = subpageIdx(id);
            PoolSubpage<T> subpage = subpages[subpageIdx];

            //subpage != null的情况产生的原因是：subpage初始化后分配了内存，
            // 但一段时间后该subpage分配的内存释放并从arena的双向链表中删除，此时subpage不为nul

            if (subpage == null) {
                //新建或初始化subpage并加入到chunk的subpages数组，同时将subpage加入到arena的subpage双向链表中
                // 如果PoolSub page未创建则创建一个，创建时传入当前id对应的offset,pageSize,本次分配的大小normCapacity;
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);

                subpages[subpageIdx] = subpage;

            } else {
                // 已经创建,则初始化数据 ,normCapacity为每个element的大小；
                subpage.init(head, normCapacity);
            }
            // 调用此方法得到一个可以操作的handle
            // 在小size的对象分配时会先分配一个PoolSubpage，最终返回一个包含该PoolSubpage信息的handle，后面的操作也是通过此handle进行操作。

            return subpage.allocate();
        }
    }

    /**
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    // 分配一个节点后如何释放
    // 传入的handle即allocate得到的handle
    void free(long handle) {
        // 传入的handle即allocate得到的handle
        int memoryMapIdx = memoryMapIdx(handle);
        //
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) { // free a subpage// 需要释放subpage
            // !=0表示分配的是一个subpage
            //
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            //
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            synchronized (head) {
                // 0011 1111   1111 1111   1111 1111   1111 1111
                // 0100 0000   0000 0000   0000 0000   0000 0000
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {  // 此时subpage完全释放，可以删除二叉树中的节点
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);

        // 将节点的值改为可用，即其depth
        setValue(memoryMapIdx, depth(memoryMapIdx));// 节点分配信息还原为高度值
        // 修改其父节点的值
        updateParentsFree(memoryMapIdx);// 更新祖先节点的分配信息
    }

    // 最终传递给PooledByteBuf的信息包括，PoolChunk中的memory: 完整的byte数组或ByteBuf；handle：
    // 表示node id的数值，释放内存段的时候使用这个参数； offset：该PooledByteBuf可操作memory的第一个位置；
    // length：该PooledByteBuf本次申请的长度；maxLength：该PooledByteBuf最大可用的长度。
    //
    void initBufinitBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {

        int memoryMapIdx = memoryMapIdx(handle);

        int bitmapIdx = bitmapIdx(handle);
        if (bitmapIdx == 0) {
            // 到这里表示分配的是>=pageSize的数据
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);

            // PooledByteBuf 通过这些参数完成自身的初始化后，就可以开始实际的读写了，
            // 它的可读写区域就是memory数组的offset 到 offset + length,  如果写入的数据超过length,
            // 可以扩容至 maxLength, 即可读写的区域变为offset 到 offset + maxLength， 如果超过 maxLength, 则需要重新申请数据块了，这个后面再说。

            // runLength(memoryMapIdx) 表示分配得到的内存 大小；
            buf.init(this, handle, runOffset(memoryMapIdx) + offset, reqCapacity, runLength(memoryMapIdx),
                     arena.parent.threadCache());
        } else {
            // 到这里表示分配的是<pageSize的数据
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    //
    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
            this, handle,
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset,
                reqCapacity, subpage.elemSize, arena.parent.threadCache());
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private static int log2(int val) {
        // compute the (0-based, with lsb = 0) position of highest set bit i.e, log2
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    // 得到节点对应可分配的字节，1号节点为16MB-ChunkSize，2048节点为8KB-PageSize
    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        // depth(id)节点的深度
        // 1 << (log2ChunkSize - depth(id)) = 2 ^ (24 - depth(id))
        // 当 depth(id) = 0时，为16M

        return 1 << log2ChunkSize - depth(id);
    }

    //计算到chunk起始位置的距离
    // 得到节点在chunk底层的字节数组中的偏移量
    // 2048-0, 2049-8K，2050-16K
    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);
    }

    //  得到第11层节点的偏移索引，= id - 2048
    private int subpageIdx(int memoryMapIdx) {
        // maxSubpageAllocs = 2048; 100 00000000
        // memoryMapIdx< 4096;
        // 即移除最高的bit;
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }


    // 注意到PoolSubpage分配的最后结果是一个long整数，
    // 其中低32位表示二叉树中的分配的节点，高32位表示subPage中分配的具体位置。相关的计算如下：
    private static int memoryMapIdx(long handle) {
        // 获取memoryMapIdx；
        return (int) handle;
    }

    // 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    private static int bitmapIdx(long handle) {
        // 0100 0000 0000 0000；
        return (int) (handle >>> Integer.SIZE); // 获取高32 位；
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    @Override
    public String toString() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }

        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage(freeBytes))
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
