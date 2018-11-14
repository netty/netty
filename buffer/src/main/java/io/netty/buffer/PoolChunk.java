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
 * 描述PoolChunk中如何分配内存
 *
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated page为最小的chunk分配的单元
 * > chunk - a chunk is a collection of pages 是apge组成的集合
 * > in this code chunkSize = 2^{maxOrder} * pageSize ,chunkSize大小等于2的n次方个page组成
 *
 * To begin we allocate a byte array of size = chunkSize
 * 我们开始的时候回分配一个chunkSize的字节数组
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 * 当我们需要一个给定size的ByteBuf的时候,我们就在字节数组chunkSize中查找满足给定size空间的第一个位置。
 * 然后返回一个long类型的handler,他是一个offset编码
 *
 * For simplicity all sizes are normalized according to PoolArena#normalizeCapacity method
 * This ensures that when we request for memory segments of size >= pageSize the normalizedCapacity
 * equals the next nearest power of 2
 * 最简单的方式是所有的size都归一化,通过PoolArena#normalizeCapacity方法,我们确定,当我们请求size 大于一个page的时候,我们就获取多个page,刚好满足给定size即可
 * 即此时分配的就是按照page去划分chunk
 *
 * To search for the first offset in chunk that has at least requested size available we construct a
 * complete balanced binary tree and store it in an array (just like heaps) - memoryMap
 * 去搜索第一个满足给定size的空闲空间的位置,即offset,我们构建了一个完美二叉树
 *
 * The tree looks like this (the size of each node being mentioned in the parenthesis)
 * 这棵树看起来是这样的,每一个节点的大小在括号里面
 *
 * depth=0        1 node (chunkSize) 第0层有1个node,大小是chunkSize
 * depth=1        2 nodes (chunkSize/2) 第1层有2个node,分别大小是chunkSize/2
 * ..
 * ..
 * depth=d        2^d nodes (chunkSize/2^d) 第d层有2的d次方个节点,分别大小是chunkSize/2的d次方
 * ..
 * depth=maxOrder 2^maxOrder nodes (chunkSize/2^{maxOrder} = pageSize) 等于11层,则是一个page大小,即8K,属于叶子节点
 *
 * depth=maxOrder is the last level and the leafs consist of pages 叶子节点组成的page大小的内存,在maxOrder层上
 *
 * 即maxOrder = 11 ,说明有2的11次方,即2*1024个叶子节点,每一个节点大小是8*1024,即8k。因此一个chunk = 2*1024 * 8 * 1024 = 16 M
 * 即chunk 由2048个 8k的page组成
 *
 * 由二叉树的方式组成这2048个page，需要2 * 2048个节点组成。因为是二叉树，所以找到父节点，就是/2即可，父节点分左右两个子节点
 *
 * With this tree available searching in chunkArray translates like this:
 * To allocate a memory segment of size chunkSize/2^k we search for the first node (from left) at height k
 * which is unused
 * 比如我要找第3层，那么他的每一个节点的大小应该是 chunkSize/2^k,k=3,即chunkSize/8 = 16M / 8 = 2M
 * 比如:第0层是16M，第1层是8M。第2层是4M，第3层是2M
 *
 * Algorithm:
 * ----------
 * Encode the tree in memoryMap with the notation
 *   memoryMap[id] = x => in the subtree rooted at id, the first node that is free to be allocated
 *   is at depth x (counted from depth=0) i.e., at depths [depth_of_id, x), there is no node that is free
 *   id表示从根节点开始计数器,即二叉树的序号,最多4048,x表示以他为根节点的下面子树中,第一个可以分配内存的深度
 *
 *  As we allocate & free nodes, we update values stored in memoryMap so that the property is maintained
 *  当我们分配和释放节点时，我们更新存储在内存中的值
 *
 * Initialization -
 *   In the beginning we construct the memoryMap array by storing the depth of a node at each node
 *     i.e., memoryMap[id] = depth_of_id
 *  初始化，每一个x都是树的深度作为它的值,即最多11,因为每一个节点都是有可以分配的内存的，比如第11个节点有内存，则该值为11，第0个节点有内存，因此该值为0
 *
 * Observations:观察发现
 * -------------
 * 1) memoryMap[id] = depth_of_id  => it is free / unallocated 等于层数,表示该节点下面所有的内存都没有被分配
 * 2) memoryMap[id] > depth_of_id  => at least one of its child nodes is allocated, so we cannot allocate it, but
 *                                    some of its children can still be allocated based on their availability
 *                                    如果数组存储的value大于对应的层数,说明该子节点至少有一个被分配了，某一些子节点不能被分配了，但是其他子节点还是可以被分配的
 * 3) memoryMap[id] = maxOrder + 1 => the node is fully allocated & thus none of its children can be allocated, it
 *                                    is thus marked as unusable 如果等于12,表示该节点所有的子节点的内容都已经分配完了,没有空余内存了,因此被标记为不可用
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
 * 1) Compute d = log_2(chunkSize/size)  获取刚好容纳size的节点应该在第几层;因为 第k层每一个节点的大小是 chunkSize/2^k次方,因此可以转换成已知一个大小,他在第几层,即 chunkSize/size = 2^k ,因此k等于log以2为底,chunkSize/size获取对数的值
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
 * we store 2 pieces of information (i.e, 2 byte vals) as a short value in memoryMap
 *
 * memoryMap[id]= (depth_of_id, x)
 * where as per convention defined above
 * the second value (i.e, x) indicates that the first node which is free to be allocated is at depth x (from root)
 */
final class PoolChunk<T> implements PoolChunkMetric {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;
    final int offset;//该chunk在arena中的开始偏移量

    private final byte[] memoryMap;
    private final byte[] depthMap;//表示该序号的二叉树所在深度
    private final PoolSubpage<T>[] subpages;//创建2048个PoolSubpage,因为每一个都是一个page,即根节点page才可以进行subPage划分
    /** Used to determine if the requested capacity is equal to or greater than pageSize. */
    private final int subpageOverflowMask;
    private final int pageSize;//8K
    private final int pageShifts;//page占用8k,需要13个比特位
    private final int maxOrder;//11 表示2048个8k的page
    private final int chunkSize;//16M
    private final int log2ChunkSize;//24,即16M需要24位
    private final int maxSubpageAllocs;//2048,即有2048个page=8k的独立内存块
    /** Used to mark memory as unusable */
    private final byte unusable;//默认值12,即比11个二叉树层次还要大1个,表示该节点没有空间可以分配

    private int freeBytes;

    PoolChunkList<T> parent;//该chunk挂在到哪个chunkList上
    PoolChunk<T> prev;//chunk的双向链表
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int pageShifts, int chunkSize, int offset) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        this.offset = offset;
        unusable = (byte) (maxOrder + 1);
        log2ChunkSize = log2(chunkSize);
        subpageOverflowMask = ~(pageSize - 1);
        freeBytes = chunkSize;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubpageAllocs = 1 << maxOrder;

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }

        subpages = newSubpageArray(maxSubpageAllocs);
    }

    /** Creates a special chunk that is not pooled. */
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
    private PoolSubpage<T>[] newSubpageArray(int size) {
        return new PoolSubpage[size];
    }

    //返回赢使用的占比,比如4 表示4%
    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {//因为arena可能被多线程控制
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);//chunk的剩余字节占比
        if (freePercentage == 0) {//表示没有剩余了,因此默认值是使用了99%
            return 99;
        }
        return 100 - freePercentage;
    }

    //返回一个可以定位内存的位置的序号
    long allocate(int normCapacity) {
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize
            return allocateRun(normCapacity);//返回分配给我的是哪个序号对应的8k page序号
        } else {//分配的内存小于page
            return allocateSubpage(normCapacity);
        }
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space 子树中有空间的最小的深度
     * 向上更新值
     * @param id id 二叉树序号
     *           16 -----0
     *        8           8   ----1
     *    4      4     4     4 ---2
     * 2    2  2   2  2 2   2 2 ---3
     * 比如这次id是第3层第2个节点,因此为该节点用完了,因此值就是12
     * 因此他的父节点第2层第1个4的值,就是3,因为第3层第3个2,他的值是3,这就表示说 第2层第一个4的某一个子类已经被用过了，但是平级,即第3层还是可以有节点去用的
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;//找到父节点序号
            byte val1 = value(id);//该序号对应的值
            byte val2 = value(id ^ 1);//该序号右边对应的值
            byte val = val1 < val2 ? val1 : val2; //获取最小的值
            setValue(parentId, val);//重新为父节点赋值
            id = parentId;
        }
    }

    /**
     * Update method used by free
     * This needs to handle the special case when both children are completely free
     * in which case parent be directly allocated on request of size = child-size * 2
     *
     * @param id id
     */
    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            logChild -= 1; // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up

            if (val1 == logChild && val2 == logChild) {
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * Algorithm to allocate an index in memoryMap when we query for a free node
     * at depth d
     * 在d层找到一个空余的节点,分配一个空间
     * @param d depth
     * @return index in memoryMap
     * 返回分配给我的是哪个序号对应的8k page序号
     */
    private int allocateNode(int d) {
        int id = 1;
        int initial = - (1 << d); // has last d bits = 0 and rest all = 1 ---结果是d个0,前面都是1,比如d=10,结果是111111111.... 00 0000 0000
        byte val = value(id);//获取第1层需要的层次
        if (val > d) { // unusable 表示不足第d层需要的空间
            return -1;
        }
        while (val < d || (id & initial) == 0) { // id & initial == 1 << d for all ids at depth d, for < d it is 0
            id <<= 1;//一层一层找
            val = value(id);
            if (val > d) {
                id ^= 1;//加1---同一层的横向找
                val = value(id);
            }
        }
        byte value = value(id);//纯粹为了校验使用
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
                value, id & initial, d);
        setValue(id, unusable); // mark as unusable 标记该节点被使用了
        updateParentsAlloc(id);
        return id;
    }

    /**
     * Allocate a run of pages (>=1)
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     * 返回分配给我的是哪个序号对应的8k page序号
     */
    private long allocateRun(int normCapacity) {
        /**
         * pageShifts 表示8k的空间需要13位,log2(normCapacity)表示容纳normCapacity的节点节点需要多少位,比如16k,则需要14位，做差,说明和8k比起来差了几层,一共11层,减去差了几层,就是容纳该空间所在的层
         * 比如16k作为normCapacity,计算结果是11 - (14-13) = 10层，因为第11层是8k,因此第10层就是16k
         */
        int d = maxOrder - (log2(normCapacity) - pageShifts);
        int id = allocateNode(d);//返回分配给我的是哪个序号对应的8k page序号
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);//减少剩余空间
        return id;
    }

    /**
     * Create/ initialize a new PoolSubpage of normCapacity
     * Any PoolSubpage created/ initialized here is added to subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param normCapacity normalized capacity
     * @return index in memoryMap
     * 分配小于一个page的数据
     */
    private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);//根据规格找到满足该size规格的PoolSubpage列表的头
        synchronized (head) {//多线程同步
            int d = maxOrder; // subpages are only be allocated from pages i.e., leaves 直接在叶子节点进行分配
            int id = allocateNode(d);//找到满足条件的叶子节点 二叉树的序号
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;//减少一个page,因为该page都用来做subpage使用

            int subpageIdx = subpageIdx(id);//定位到第几个叶子节点page
            PoolSubpage<T> subpage = subpages[subpageIdx];//获取该叶子节点page ,其一定是PoolSubpage类型的对象
            if (subpage == null) {//如果不存在,则创建一个
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;//并且用内存保留一份映射
            } else {
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();//对其分配一个小内存片段
        }
    }

    /**
     * Free a subpage or a run of pages
     * When a subpage is freed from PoolSubpage, it might be added back to subpage pool of the owning PoolArena
     * If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize, we can
     * completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     * 释放一个分配的内存空间
     */
    void free(long handle) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) { // free a subpage
            PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];
            assert subpage != null && subpage.doNotDestroy;

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            PoolSubpage<T> head = arena.findSubpagePoolHead(subpage.elemSize);
            synchronized (head) {
                if (subpage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);
        setValue(memoryMapIdx, depth(memoryMapIdx));
        updateParentsFree(memoryMapIdx);
    }

    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        int memoryMapIdx = memoryMapIdx(handle);//二叉树序号
        int bitmapIdx = bitmapIdx(handle);//subpage序号
        if (bitmapIdx == 0) {//获取正块8k的内存
            byte val = value(memoryMapIdx);//二叉树序号对应的层数值
            assert val == unusable : String.valueOf(val);//该值一定是不可用的,因为他每次都获取8k
            //参数runLength(memoryMapIdx)表示最多占用8k
            //参数runOffset(memoryMapIdx) + offset 表示在该chunk的偏移量 + chunk在整个arena的偏移量
            buf.init(this, handle, runOffset(memoryMapIdx) + offset, reqCapacity, runLength(memoryMapIdx),
                     arena.parent.threadCache());
        } else {//获取subpage的内存
            initBufWithSubpage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    //获取subpage的内存
    void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubpage(buf, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubpage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);//二叉树序号

        PoolSubpage<T> subpage = subpages[subpageIdx(memoryMapIdx)];//找到该序号对应的叶子节点,他一定是subpage
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        //offset是chunk该page所在offset + subpage的offset + chunk在整个arena中的offset
        //0x3FFFFFFF表示30个1
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

    //计算该id对应的序号的层,每一个节点占用多少字节
    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);//depth(id)第几层.
    }

    //该二叉树的某一个序号节点,在整个chunk的16M中的开始偏移量位置
    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        //先计算1 << depth(id),直接到某一层
        //深度1 << depth(id),因为是2的n次方,因此二进制一定是10000后面深度个0,然后与id处理,就可以知道该层第几个序号
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);//第几个序号节点 * 该层每一个的大小,因此就是在整个chunk的16M中的开始偏移量位置
    }

    //相当于知道叶子节点的二叉树序号 - 2048个叶子节点,就是第几个叶子节点,即定位到第几个叶子节点page
    private int subpageIdx(int memoryMapIdx) {
        return memoryMapIdx ^ maxSubpageAllocs; // remove highest set bit, to get offset
    }

    //返回二叉树的序号
    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    //获取subpage序号,因为subpage的handler是由subpage序号+二叉树序号组成的
    //非subpage的结果一定是0
    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
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
