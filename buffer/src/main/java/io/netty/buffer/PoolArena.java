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

import io.netty.util.internal.LongCounter;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;

//
abstract class PoolArena<T> implements PoolArenaMetric {

    // 引入魔法类；此类提供了一些绕开JVM的更底层功能，基于它的实现可以提高效率；
    // 它是Unsafe的，它所分配的内存需要手动free（不被GC回收）
    static final boolean HAS_UNSAFE = PlatformDependent.hasUnsafe();

    //Netty使用一个枚举来表示每次请求大小的类别：
    enum SizeClass {
        Tiny,
        Small,
        Normal
        // 除此之外的请求为Huge, 直接进行内存分配；
    }

    //
    static final int numTinySubpagePools = 512 >>> 4; // TinySubpagePools数组的长度；512/16

    //
    final PooledByteBufAllocator parent;
    // 下面这几个参数用来控制PoolChunk的总内存大小、page大小等
    private final int maxOrder; // chunk相关满二叉树的高度
    final int pageSize; //单个page的大小
    final int pageShifts; //用于辅助计算
    final int chunkSize; // chunk的大小16m
    final int subpageOverflowMask;// 用于判断请求是否为Small/Tiny
    final int numSmallSubpagePools;// small请求的双向链表头个数 4
    final int directMemoryCacheAlignment;// 对齐基准
    final int directMemoryCacheAlignmentMask;// 用于对齐内存
    // 不同大小的缓存
    private final PoolSubpage<T>[] tinySubpagePools;  // 数组长度为32,存储长度在16-512的 subPagePool;
    private final PoolSubpage<T>[] smallSubpagePools; // 数组长度为4，存储长度为1024-的 subPagePool；

    // Netty使用PoolChunkList作为容器存放相同状态的Chunk块
    // 按照内存的使用率来取名的；
    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;// 一个chunk最开始分配后会进入它
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q100;

    private final List<PoolChunkListMetric> chunkListMetrics;

    // Metrics for allocations and deallocations
    private long allocationsNormal;
    // We need to use the LongCounter here as this is not guarded via synchronized block.
    // 计数器，
    private final LongCounter allocationsTiny = PlatformDependent.newLongCounter();
    private final LongCounter allocationsSmall = PlatformDependent.newLongCounter();
    private final LongCounter allocationsHuge = PlatformDependent.newLongCounter();
    private final LongCounter activeBytesHuge = PlatformDependent.newLongCounter();

    private long deallocationsTiny;
    private long deallocationsSmall;
    private long deallocationsNormal;

    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private final LongCounter deallocationsHuge = PlatformDependent.newLongCounter();

    // Number of thread caches backed by this arena.
    final AtomicInteger numThreadCaches = new AtomicInteger();

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    protected PoolArena(PooledByteBufAllocator parent, int pageSize,
          int maxOrder, int pageShifts, int chunkSize, int cacheAlignment) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        directMemoryCacheAlignment = cacheAlignment;
        directMemoryCacheAlignmentMask = cacheAlignment - 1; //
        subpageOverflowMask = ~(pageSize - 1); // 掩码

        tinySubpagePools = newSubpagePoolArray(numTinySubpagePools);// 长为32的数组

        for (int i = 0; i < tinySubpagePools.length; i ++) {
            tinySubpagePools[i] = newSubpagePoolHead(pageSize); //
        }

        numSmallSubpagePools = pageShifts - 9;//
        smallSubpagePools = newSubpagePoolArray(numSmallSubpagePools);//长为4的数组；
        for (int i = 0; i < smallSubpagePools.length; i ++) {
            smallSubpagePools[i] = newSubpagePoolHead(pageSize);
        }



        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<T>(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<T>(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<T>(this, q050, 25, 75, chunkSize);

        // q000没有前置节点，则当一个chunk进入q000后，如果其内存被完全释放，则不再保留在内存中，其分配的内存被完全回收
        q000 = new PoolChunkList<T>(this, q025, 1, 50, chunkSize);

        // qInit前置节点为自己，且minUsage=Integer.MIN_VALUE，意味着一个初分配的chunk，在最开始的内存分配过程中(内存使用率<25%)，
        // 即使完全回收也不会被释放，这样始终保留在内存中，后面的分配就无需新建chunk,减小了分配的时间
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        // q000没有前置节点，则当一个chunk进入q000后，如果其内存被完全释放，则不再保留在内存中，其分配的内存被完全回收
        q000.prevList(null);
        // qInit前置节点为自己，且minUsage=Integer.MIN_VALUE，意味着一个初分配的chunk，在最开始的内存分配过程中(内存使用率<25%)，
        // 即使完全回收也不会被释放，这样始终保留在内存中，后面的分配就无需新建chunk,减小了分配的时间
        qInit.prevList(qInit);

        List<PoolChunkListMetric> metrics = new ArrayList<PoolChunkListMetric>(6);
        metrics.add(qInit);
        metrics.add(q000);
        metrics.add(q025);
        metrics.add(q050);
        metrics.add(q075);
        metrics.add(q100);
        chunkListMetrics = Collections.unmodifiableList(metrics);
    }

    private PoolSubpage<T> newSubpagePoolHead(int pageSize) {
        PoolSubpage<T> head = new PoolSubpage<T>(pageSize); // 需要注意的是链表头结点head是一个特殊的PoolSubpage，不进行实际的内存分配任务。
        head.prev = head;
        head.next = head;
        return head;
    }

    @SuppressWarnings("unchecked")
    private PoolSubpage<T>[] newSubpagePoolArray(int size) {
        return new PoolSubpage[size];
    }
    // 判断子类实现Heap还是Direct
    abstract boolean isDirect();

    // 内存分配方法allocate()
    PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    static int tinyIdx(int normCapacity) {
        // 需求size<512则是从16开始，每次加16字节，这样从[512,8192)有四个不同值，而从[16,512)有32个不同值
        // 所以index是除以十六；
        return normCapacity >>> 4; // %16;
    }

    static int smallIdx(int normCapacity) {
        int tableIdx = 0;
        int i = normCapacity >>> 10;
        // 当需求的size >= 512时，size成倍增长，及512->1024->2048->4096->8192->..
        while (i != 0) {
            i >>>= 1;
            tableIdx ++;
        }
        return tableIdx;
    }

    // 根据请求分配大小判断所属分类的代码如下，体会其中的位运算：
    // capacity < pageSize
    boolean isTinyOrSmall(int normCapacity) {
        // sub page OverflowMask = ~(pageSize - 1); //掩码
        return (normCapacity & subpageOverflowMask) == 0;
    }

    // normCapacity < 512
    static boolean isTiny(int normCapacity) {
        return (normCapacity & 0xFFFFFE00) == 0;
    }

    //内存分配方法allocate()
    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
        // 规范化请求容量
        final int normCapacity = normalizeCapacity(reqCapacity);
        // capacity < pageSize, Tiny/Small请求
        if (isTinyOrSmall(normCapacity)) { // capacity < pageSize
            int tableIdx;
            PoolSubpage<T>[] table;

            boolean tiny = isTiny(normCapacity);
            if (tiny) { // < 512Tiny请求
                // 小内存从tinySubpagePools中分配
                // 都是先尝试从cache中分配，如果无法完成分配则再走其他流程。
                // 这个cache有何作用？ 它是利用ThreadLocal的特性，去除锁竞争，提高内存分配的效率
                if (cache.allocateTiny(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    return;// 尝试从ThreadCache进行分配
                }
                tableIdx = tinyIdx(normCapacity);
                table = tinySubpagePools;
            } else {// Small请求
                if (cache.allocateSmall(this, buf, reqCapacity, normCapacity)) {
                    // was able to allocate out of the cache so move on
                    return;// 尝试从ThreadCache进行分配
                }
                tableIdx = smallIdx(normCapacity);
                table = smallSubpagePools;
            }
            // 分组的Subpage双向链表的头结点
            final PoolSubpage<T> head = table[tableIdx];

            /**
             * Synchronize on the head. This is needed as {@link PoolChunk#allocateSubpage(int)} and
             * {@link PoolChunk#free(long)} may modify the doubly 双重地；加倍地linked list as well.
             */
            // 锁定防止其他操作修改head结点
            synchronized (head) {
                final PoolSubpage<T> s = head.next;
                if (s != head) {

                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    // 进行分配
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubpage(buf, handle, reqCapacity);
                    incTinySmallAllocation(tiny);
                    return;
                }
            }
            synchronized (this) {
                // 双向循环链表还没初始化，使用normal分配
                allocateNormal(buf, reqCapacity, normCapacity);
            }

            incTinySmallAllocation(tiny);
            return;
        }
        // Normal请求
        if (normCapacity <= chunkSize) {
            if (cache.allocateNormal(this, buf, reqCapacity, normCapacity)) {
                // was able to allocate out of the cache so move on
                return; // 尝试从ThreadCache进行分配
            }
            synchronized (this) {
                allocateNormal(buf, reqCapacity, normCapacity);
                ++allocationsNormal;
            }
        } else {
            // Huge allocations are never served via the cache so just call allocateHuge
            // Huge请求直接分配
            allocateHuge(buf, reqCapacity);
        }
    }

    // Method must be called inside synchronized(this) { ... } block
    // 分配内存时先从内存占用率相对较低的chunk list中开始查找，这样查找的平均用时就会更短：
    // 从内存使用率相对较低的chunklist中查找;
    private void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
            q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
            q075.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }

        // Add a new chunk.
        // 无Chunk或已存Chunk不能满足分配，新增一个Chunk
        PoolChunk <T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
        long handle = c.allocate(normCapacity);
        assert handle > 0;
        c.initBufinitBuf(buf, handle, reqCapacity);
        qInit.add(c);
    }

    private void incTinySmallAllocation(boolean tiny) {
        if (tiny) {
            allocationsTiny.increment();
        } else {
            allocationsSmall.increment();
        }
    }

    // 超大内存由于本身复用性并不高，因此没有做其他任何策略。不用pool而是直接分配一个大内存，用完后直接回收
    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        // 分配不在池中的大的chunk;
        PoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        activeBytesHuge.add(chunk.chunkSize());
        buf.initUnpooled(chunk, reqCapacity);
        allocationsHuge.increment();
    }

    //内存释放：
    void free(PoolChunk<T> chunk, long handle, int normCapacity, PoolThreadCache cache) {
        if (chunk.unpooled) { // Huge
            // 1、如果chunk不是pool的（如上面的huge方式分配的)，则直接销毁（回收）；
            int size = chunk.chunkSize();
            destroyChunk(chunk);// 模板方法，子类实现具体释放过程
            activeBytesHuge.add(-size);
            deallocationsHuge.increment();
        } else {
            // Normal, Small/Tiny
            SizeClass sizeClass = sizeClass(normCapacity);
            if (cache != null && cache.add(this, chunk, handle, normCapacity, sizeClass)) {
                // cached so not free it.
                return;// 可以缓存则不释放
            }
            // 否则释放
            freeChunk(chunk, handle, sizeClass);
        }
    }

    private SizeClass sizeClass(int normCapacity) {
        if (!isTinyOrSmall(normCapacity)) {
            return SizeClass.Normal;
        }
        return isTiny(normCapacity) ? SizeClass.Tiny : SizeClass.Small;
    }
    // 通过chunk 所在的chunk list进行释放，这里的chunk list释放会涉及到对应内存块的释放，chunk在chunk list之间的移动和chunk的销毁，
    void freeChunk(PoolChunk<T> chunk, long handle, SizeClass sizeClass) {

        final boolean destroyChunk;
        synchronized (this) {

            switch (sizeClass) {
                case Normal:
                    ++deallocationsNormal;
                    break;
                case Small:
                    ++deallocationsSmall;
                    break;
                case Tiny:
                    ++deallocationsTiny;
                    break;
                default:
                    throw new Error();
            }
            // Parent为所属的chunk List，destroy Chunk为true表示Chunk内存使用装填
            // 从QINIT->Q0->...->Q0，最后释放;
            destroyChunk = !chunk.parent.free(chunk, handle);
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.因为不会在这个chunk中进行分配
            destroyChunk(chunk);// 模板方法，子类实现具体释放过程
        }
    }
    //得到链表head节点的代码如下：
    PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubpage<T>[] table;
        if (isTiny(elemSize)) { // < 512
            tableIdx = elemSize >>> 4;
            table = tinySubpagePools;
        } else {
            tableIdx = 0;
            elemSize >>>= 10;
            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx ++;
            }
            table = smallSubpagePools;
        }

        return table[tableIdx];
    }

    // 对容量进行规范化的代码如下：
    int normalizeCapacity(int reqCapacity) {

        if (reqCapacity < 0) {
            throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
        }
        // Huge 直接返回（直接内存需要对齐）;
        if (reqCapacity >= chunkSize) {
            //Alignment 队列，成直线；校准；结盟
            return directMemoryCacheAlignment == 0 ? reqCapacity : alignCapacity(reqCapacity);
        }
        // Small 和 Normal 规范化到大于2的n次方的最小值
        if (!isTiny(reqCapacity)) { // >= 512
            // Doubled

            /*
             摘自HashMap:
                int n = cap - 1; // 避免 n=2^m 这种情况，经过下面运算后导致结果比 n 本身大一倍
                n |= n >>> 1; // 确保第一次出现1的位及其后一位都是1；
                n |= n >>> 2; // 确保第一次出现1的位及其后三位都是1；
                n |= n >>> 4; // 确保第一次出现1的位及其后7位都是1；
                n |= n >>> 8; // 确保第一次出现1的位及其后15位都是1;
                n |= n >>> 16;// 确保第一次出现1的位及其后面所有位都是1；

                return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1; //n+1即为0x0100000......00就是大于等于n的最小2^次幂
            * */
            int normalizedCapacity = reqCapacity;
            normalizedCapacity --;
            normalizedCapacity |= normalizedCapacity >>>  1;
            normalizedCapacity |= normalizedCapacity >>>  2;
            normalizedCapacity |= normalizedCapacity >>>  4;
            normalizedCapacity |= normalizedCapacity >>>  8;
            normalizedCapacity |= normalizedCapacity >>> 16;
            normalizedCapacity ++;

            if (normalizedCapacity < 0) {
                // 处理溢出的情况
                normalizedCapacity >>>= 1;
            }
            assert directMemoryCacheAlignment == 0 || (normalizedCapacity & directMemoryCacheAlignmentMask) == 0;

            return normalizedCapacity;
        }
        // Tiny 且直接内存需要对齐
        if (directMemoryCacheAlignment > 0) {
            return alignCapacity(reqCapacity);
        }

        // Quantum-spaced
        // Tiny 且已经是16B的倍数
        // 低四位为零；
        if ((reqCapacity & 15) == 0) { //15 = 1111
            return reqCapacity;
        }
        // Tiny 不是16B的倍数则规范化到16B的倍数；
        // 首先清除低四位
        return (reqCapacity & ~15) + 16;
    }

    // 直接内存对齐后的请求容量为基准的倍数，比如基准为64B，则分配的内存都需要为64B的整数倍，也就是常说的按64字节对齐
    int alignCapacity(int reqCapacity) {
        // directMemoryCacheAlignmentMask为对齐的掩码。 111111
        int delta = reqCapacity & directMemoryCacheAlignmentMask;
        return delta == 0 ? reqCapacity : reqCapacity + directMemoryCacheAlignment - delta;
    }

    //当PooledByteBuf容量扩增时，内存需要重新分配，代码如下：
    void reallocate(PooledByteBuf<T> buf, int newCapacity, boolean freeOldMemory) {

        if (newCapacity < 0 || newCapacity > buf.maxCapacity()) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity);
        }
        // 新老内存相同，不用重新分配
        int oldCapacity = buf.length;
        if (oldCapacity == newCapacity) {
            return;
        }

        PoolChunk<T> oldChunk = buf.chunk;
        long oldHandle = buf.handle;
        T oldMemory = buf.memory;
        int oldOffset = buf.offset;
        int oldMaxLength = buf.maxLength;
        int readerIndex = buf.readerIndex();
        int writerIndex = buf.writerIndex();

        // 重新申请一块内存
        allocate(parent.threadCache(), buf, newCapacity);
        if (newCapacity > oldCapacity) {
            // 将老数据copy到新内存
            // 如果新申请的内存比之前小则直接将之前内存中的数据拷贝到新的内存中
            memoryCopy(
                    oldMemory, oldOffset,
                    buf.memory, buf.offset, oldCapacity);
        } else if (newCapacity < oldCapacity) {
            if (readerIndex < newCapacity) {
                if (writerIndex > newCapacity) {
                    writerIndex = newCapacity;
                }

                // 将可读的数据拷贝过来，不可读的不拷贝
                memoryCopy(
                        oldMemory, oldOffset + readerIndex,
                        buf.memory, buf.offset + readerIndex, writerIndex - readerIndex);
            } else {
                // 如果之前的readerIndex比新的内存总大小还大，则没有可以读取的数据了，也无法写
                readerIndex = writerIndex = newCapacity;
            }
        }
        // 重新设置读写索引
        buf.setIndex(readerIndex, writerIndex);
        // 如有必要，释放老的内存
        if (freeOldMemory) {
            // 释放之前的内存
            free(oldChunk, oldHandle, oldMaxLength, buf.cache);
        }
    }

    @Override
    public int numThreadCaches() {
        return numThreadCaches.get();
    }

    @Override
    public int numTinySubpages() {
        return tinySubpagePools.length;
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
    public List<PoolSubpageMetric> tinySubpages() {
        return subPageMetricList(tinySubpagePools);
    }

    @Override
    public List<PoolSubpageMetric> smallSubpages() {
        return subPageMetricList(smallSubpagePools);
    }

    @Override
    public List<PoolChunkListMetric> chunkLists() {
        return chunkListMetrics;
    }

    private static List<PoolSubpageMetric> subPageMetricList(PoolSubpage<?>[] pages) {
        List<PoolSubpageMetric> metrics = new ArrayList<PoolSubpageMetric>();
        for (PoolSubpage<?> head : pages) {
            if (head.next == head) {
                continue;
            }
            PoolSubpage<?> s = head.next;
            for (;;) {
                metrics.add(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
        return metrics;
    }

    @Override
    public long numAllocations() {
        final long allocsNormal;
        synchronized (this) {
            allocsNormal = allocationsNormal;
        }
        return allocationsTiny.value() + allocationsSmall.value() + allocsNormal + allocationsHuge.value();
    }

    @Override
    public long numTinyAllocations() {
        return allocationsTiny.value();
    }

    @Override
    public long numSmallAllocations() {
        return allocationsSmall.value();
    }

    @Override
    public synchronized long numNormalAllocations() {
        return allocationsNormal;
    }

    @Override
    public long numDeallocations() {
        final long deallocs;
        synchronized (this) {
            deallocs = deallocationsTiny + deallocationsSmall + deallocationsNormal;
        }
        return deallocs + deallocationsHuge.value();
    }

    @Override
    public synchronized long numTinyDeallocations() {
        return deallocationsTiny;
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
        return allocationsHuge.value();
    }

    @Override
    public long numHugeDeallocations() {
        return deallocationsHuge.value();
    }

    @Override
    public  long numActiveAllocations() {
        long val = allocationsTiny.value() + allocationsSmall.value() + allocationsHuge.value()
                - deallocationsHuge.value();
        synchronized (this) {
            val += allocationsNormal - (deallocationsTiny + deallocationsSmall + deallocationsNormal);
        }
        return max(val, 0);
    }

    @Override
    public long numActiveTinyAllocations() {
        return max(numTinyAllocations() - numTinyDeallocations(), 0);
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
        long val = activeBytesHuge.value();
        synchronized (this) {
            for (int i = 0; i < chunkListMetrics.size(); i++) {
                for (PoolChunkMetric m: chunkListMetrics.get(i)) {
                    val += m.chunkSize();
                }
            }
        }
        return max(0, val);
    }

    //由于该类是一个抽象类，其中的抽象方法如下：
    // 新建一个Chunk，Tiny/Small，Normal请求请求分配时调用
    protected abstract PoolChunk<T> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize);
    // 新建一个Chunk，Huge请求分配时调用
    protected abstract PoolChunk<T> newUnpooledChunk(int capacity);

    protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);
    // 复制内存，当ByteBuf扩充容量时调用
    protected abstract void memoryCopy(T src, int srcOffset, T dst, int dstOffset, int length);
    // 销毁Chunk，释放内存时调用
    protected abstract void destroyChunk(PoolChunk<T> chunk);

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
            .append("tiny subpages:");
        appendPoolSubPages(buf, tinySubpagePools);
        buf.append(StringUtil.NEWLINE)
           .append("small subpages:");
        appendPoolSubPages(buf, smallSubpagePools);
        buf.append(StringUtil.NEWLINE);

        return buf.toString();
    }

    private static void appendPoolSubPages(StringBuilder buf, PoolSubpage<?>[] subpages) {
        for (int i = 0; i < subpages.length; i ++) {
            PoolSubpage<?> head = subpages[i];
            if (head.next == head) {
                continue;
            }

            buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ");
            PoolSubpage<?> s = head.next;
            for (;;) {
                buf.append(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
    }


    // 该方法是Object中的方法，在对象被GC回收时调用，可知在该方法中需要清理资源，本类中此方法负责清理内存，代码如下：
    @Override
    protected final void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            destroyPoolSubPages(smallSubpagePools);
            destroyPoolSubPages(tinySubpagePools);
            destroyPoolChunkLists(qInit, q000, q025, q050, q075, q100);
        }
    }

    private static void destroyPoolSubPages(PoolSubpage<?>[] pages) {
        for (PoolSubpage<?> page : pages) {
            page.destroy();
        }
    }

    private void destroyPoolChunkLists(PoolChunkList<T>... chunkLists) {
        for (PoolChunkList<T> chunkList: chunkLists) {
            chunkList.destroy(this);
        }
    }


    //
    static final class HeapArena extends PoolArena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                    directMemoryCacheAlignment);
        }

        //  直接在堆中 分配数组；
        private static byte[] newByteArray(int size) {
            return PlatformDependent.allocateUninitializedArray(size);
        }

        @Override
        boolean isDirect() { //Direct直接的
            return false;
        }

        @Override
        // 生成chunk时传入了内存；
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int pageShifts, int chunkSize) {
            return new PoolChunk<byte[]>(this, newByteArray(chunkSize), pageSize, maxOrder, pageShifts, chunkSize, 0);
        }

        @Override
        protected PoolChunk<byte[]> newUnpooledChunk(int capacity) {
            return new PoolChunk<byte[]>(this, newByteArray(capacity), capacity, 0);
        }

        @Override
        protected void destroyChunk(PoolChunk<byte[]> chunk) {
            // 依赖GC进行内存回收；
            // Rely on GC.
        }

        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return HAS_UNSAFE ? PooledUnsafeHeapByteBuf.newUnsafeInstance(maxCapacity)
                    : PooledHeapByteBuf.newInstance(maxCapacity);
        }

        @Override
        protected void memoryCopy(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
            if (length == 0) {
                return;
            }

            // 直接使用数组拷贝；
            System.arraycopy(src, srcOffset, dst, dstOffset, length);
        }
    }

    static final class DirectArena extends PoolArena<ByteBuffer> {

        DirectArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                    directMemoryCacheAlignment);
        }

        @Override
        boolean isDirect() {
            return true;
        }

        private int offsetCacheLine(ByteBuffer memory) {
            // We can only calculate the offset if Unsafe is present as otherwise directBufferAddress(...) will
            // throw an NPE.
            // 当魔术方法可以使用时，计算出内存对齐后的地址；
            return HAS_UNSAFE ?
                    (int) (PlatformDependent.directBufferAddress(memory) & directMemoryCacheAlignmentMask) : 0;
        }

        @Override
        protected PoolChunk<ByteBuffer> newChunk(int pageSize, int maxOrder,
                int pageShifts, int chunkSize) {
            // 分配的direct memory可能不是对齐的；
            // directMemoryCacheAlignment== 0表示不需要进行对齐处理；
            if (directMemoryCacheAlignment == 0) {
                //
                return new PoolChunk<ByteBuffer>(this,
                        allocateDirect(chunkSize), pageSize, maxOrder,
                        pageShifts, chunkSize, 0);
            }
            //
            final ByteBuffer memory = allocateDirect(chunkSize
                    + directMemoryCacheAlignment); // 需要多分配些，用于截断；
            return new PoolChunk<ByteBuffer>(this, memory, pageSize,
                    maxOrder, pageShifts, chunkSize,
                    offsetCacheLine(memory));
        }

        @Override
        protected PoolChunk<ByteBuffer> newUnpooledChunk(int capacity) {
            if (directMemoryCacheAlignment == 0) {
                return new PoolChunk<ByteBuffer>(this,
                        allocateDirect(capacity), capacity, 0);
            }
            final ByteBuffer memory = allocateDirect(capacity
                    + directMemoryCacheAlignment);
            return new PoolChunk<ByteBuffer>(this, memory, capacity,
                    offsetCacheLine(memory));
        }

        // 分配堆外缓冲区；
        private static ByteBuffer allocateDirect(int capacity) {
            //
            return PlatformDependent.useDirectBufferNoCleaner() ?
                    // 分配堆外缓冲区；
                    PlatformDependent.allocateDirectNoCleaner(capacity) : ByteBuffer.allocateDirect(capacity);
        }

        //
        @Override
        protected void destroyChunk(PoolChunk<ByteBuffer> chunk) {
            if (PlatformDependent.useDirectBufferNoCleaner()) {
                PlatformDependent.freeDirectNoCleaner(chunk.memory);
            } else {
                PlatformDependent.freeDirectBuffer(chunk.memory);
            }
        }

        @Override
        protected PooledByteBuf<ByteBuffer> newByteBuf(int maxCapacity) {
            if (HAS_UNSAFE) {
                return PooledUnsafeDirectByteBuf.newInstance(maxCapacity);
            } else {
                return PooledDirectByteBuf.newInstance(maxCapacity);
            }
        }

        @Override
        protected void memoryCopy(ByteBuffer src, int srcOffset, ByteBuffer dst, int dstOffset, int length) {
            if (length == 0) {
                return;
            }

            if (HAS_UNSAFE) {
                PlatformDependent.copyMemory(
                        PlatformDependent.directBufferAddress(src) + srcOffset,
                        PlatformDependent.directBufferAddress(dst) + dstOffset, length);
            } else {
                // We must duplicate the NIO buffers because they may be accessed by other Netty buffers.
                src = src.duplicate();
                dst = dst.duplicate();
                src.position(srcOffset).limit(srcOffset + length);
                dst.position(dstOffset);
                dst.put(src);
            }
        }
    }
}
