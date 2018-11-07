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





//
final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;// 所属的Chunk
    private final int memoryMapIdx; // 当前page在chunk中的id；// 所属Page的标号
    private final int runOffset; // 当前page 在chunk.memory的偏移量 // 在整个Chunk的偏移字节数
    private final int pageSize; // page大小
    private final long[] bitmap;// 这个bitmap的实现和BitSet相同，通过对每一个二进制位的标记来修改一段内存的占用状态

    PoolSubpage<T> prev;// 前一个节点，这里要配合PoolArena看// arena双向链表的后继节点
    PoolSubpage<T> next;// arena双向链表的前驱节点

    boolean doNotDestroy;// 表示该page在使用中，不能被清除 // 是否需要释放整个Page
    int elemSize;// 该page切分后每一段的大小// 均等切分的大小
    private int maxNumElems;// 该page包含的段数量// 最多可以切分的小块数
    private int bitmapLength;// bitmap需要用到的长度 // 位图信息的长度,long的个数
    private int nextAvail;// 下一个可用的位置 // 下一个可分配的小块位置信息
    private int numAvail; // 可用的段element数量// 可用的小块数

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    //
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    // 构造方法有两个，其中一个用于-构造双向链表的头节点Head，这是一个特殊节点。重点关注普通节点构造方法：
    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        // long = 64byte
        // 这里为什么是16,64两个数字呢，elemSize是经过normCapacity处理的数字，最小值为16；
        // 所以一个page最多可能被分成pageSize/16段内存，而一个long可以表示64个内存段的状态；
        // 因此最多需要pageSize/16/64个元素就能保证所有段的状态都可以管理
        // 此处使用最大值，最小分配16B所需的long个数
        /*
        * Netty 使用了多个long整数的位数表示位图信息，
        * 这部分代码主要是在初始化位图结构。bitmap的最大长度为pageSize >>> 10表示最小分配16(1>>>4)B所需的long(1>>>6)个数，
        * 此处不使用pageSize/elemSize/64是因为考虑到复用。
        * 当一个PoolSubpage以32B均等切分，然后释放返回给Chunk，
        * 当Chunk再次被分配时，比如16B，此时只需调用init()方法即可而不再需要初始其他数据。
        * */

        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64
        init(head, elemSize);
    }

    // 这个方法有两种情况下会调用
    // 1、类初始化时
    // 2、整个subpage被回收后重新分配
    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;// 表示该page在使用中，不能被清除
        this.elemSize = elemSize;// 该page切分后每一段的大小
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize; //该page包含的段数量 = 可用的段数量
            nextAvail = 0;
            //
            bitmapLength = maxNumElems >>> 6; // 64  bitmap需要用到的长度 /64 表示long的个数
            if ((maxNumElems & 63) != 0) { // 111111
                bitmapLength ++;// subpage不是64倍，多需要一个long
            }
            // 用来表示段状态的值全部需要被清零
            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }
        // chunk在分配page时，如果是8K以下的段则交给subpage管理，然而chunk并没有将subpage暴露给外部，subpage只好自谋生路，
        // 在初始化或重新分配时将自己加入到chunk.arena的pool中，通过arena进行后续的管理（包括复用subpage上的其他element，arena目前还没讲到，后面会再提到）

        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     *
     */
    // 下面看看subpage是如何进行内部的内存分配的：
    // 分配一个可用的element并标记
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }
        // 1.没有可分配的均等小块 2.需要销毁(arena池中有其他可分配subpage)
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        // 找到当前page中可分配段的index
        final int bitmapIdx = getNextAvail();
        // 算出对应index的标志位在数组中的位置q
        int q = bitmapIdx >>> 6; // 高24为表示long数组索引。bitmapIdx 为 element 在bitmap中的序号；
        // / 低6位表示在long中实际分配的二进制位，将>=64的那一部分二进制抹掉得到一个小于64的数
        int r = bitmapIdx & 63; // 11111

        assert (bitmap[q] >>> r & 1) == 0;
        //把对应位置值设置为1表示当前element已经被配， 这几句看起来很郁闷，转换成我们常见的BitSet，其实就是bitSet.set(q, true)
        bitmap[q] |= 1L << r; // 把 第q个long 中的第r个byte，将该信息加入到分配信息中

        // 如果当前page没有可用的内存则从arena的pool中移除
        if (-- numAvail == 0) {
            removeFromPool();// 没有可分配的均等块则从arena双向链表删除
        }

        return toHandle(bitmapIdx);// 转换为64位分配信息
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    // 释放指定element
    // 需要注意的是该方法的返回值，返回true表示该subpage在使用中，返回false表示该subPage不再由chunk使用可以释放。
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        // 下面这几句转换成我们常见的BitSet，其实就是bitSet.set(q, false)

        int q = bitmapIdx >>> 6;// long数组索引
        int r = bitmapIdx & 63; // long的二进制位偏移
        assert (bitmap[q] >>> r & 1) != 0;

        //换成我们常见的BitSet，其实就是bitSet.set(q, false)
        bitmap[q] ^= 1L << r; // 异或运算清除改位
        // 将这个index设置为可用, 下次分配时会直接分配这个位置的内存
        setNextAvail(bitmapIdx);// 该位置的小块可用于下次分配
        // numAvail=0 说明之前已经从arena的pool中移除了，现在变回可用，则再次交给arena管理
        if (numAvail ++ == 0) {
            // 该page已分配了至少一个subpage，加入到arena双向链表
            addToPool(head);
            return true;
        }

        if (numAvail != maxNumElems) {
            //
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            // 注意这里的特殊处理，如果arena的pool中没有可用的subpage，则保留，否则将其从pool中移除。
            // 这样尽可能的保证arena分配小内存时能直接从pool中取，而不用再到chunk中去获取。
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                // prev==next==head 只有头结点和该节点
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;
            // 从双向链表中释放，因为双向链表中至少有一个可用节点
            removeFromPool();
            return false;
        }
    }

    // chunk在分配page时，如果是8K以下的段则交给subpage管理，然而chunk并没有将subpage暴露给外部，subpage只好自谋生路，
    // 在初始化或重新分配时将自己加入到chunk.arena的pool中，通过arena进行后续的管理（包括复用subpage上的其他element，arena目前还没讲到，后面会再提到）
    // 将该PoolSubpage加入到Arena的双向链表中，代码如下
    private void addToPool(PoolSubpage<T> head) {
        // 经典的双向链表操作，只需注意每次新加入的节点都在Head节点之后。
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    // 用来寻找在位图中可用的下一个位，代码如下：
    private int getNextAvail() {

        // nextAvail  >= 0时，表示明确的知道这个element未被分配，此时直接返回就可以了
        // >=0 有两种情况：1、刚初始化；2、有element被释放且还未被分配
        // 每次分配完成nextAvail就被置为-1，因为这个时候除非计算一次，否则无法知道下一个可用位置在哪
        // 此时的nextAvail是上一个释放的均等小块
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        // 没有明确的可用位置时则挨个查找
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            // 说明这个位置段中还有可以分配的element
            // 还有可用的均等小块
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    // long从低位开始表示分配信息，最低位表示第1块分配
    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        // 用于拼handle
        final int baseVal = i << 6;
        for (int j = 0; j < 64; j ++) {
            // 如果该位置的值为0，表示还未分配
            if ((bits & 1) == 0) {
                int val = baseVal | j; // baseVal 其提供前58位，j 提供后六位
                if (val < maxNumElems) { // maxNumElems值不一定是与64对齐的；
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1; //
        }
        return -1;
    }

    // handle有两种含义，1、handle<Integer.MAX_VALUE, 表示一个node id; 2、handle>Integer.MAX_VALUE,
    // 则里面包含node id + 对应的subPage的bitmapIdx;
    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    @Override
    public String toString() {
        final boolean doNotDestroy;
        final int maxNumElems;
        final int numAvail;
        final int elemSize;
        synchronized (chunk.arena) {
            if (!this.doNotDestroy) {
                doNotDestroy = false;
                // Not used for creating the String.
                maxNumElems = numAvail = elemSize = -1;
            } else {
                doNotDestroy = true;
                maxNumElems = this.maxNumElems;
                numAvail = this.numAvail;
                elemSize = this.elemSize;
            }
        }

        if (!doNotDestroy) {
            return "(" + memoryMapIdx + ": not in use)";
        }

        return "(" + memoryMapIdx + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + pageSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        synchronized (chunk.arena) {
            return maxNumElems;
        }
    }

    @Override
    public int numAvailable() {
        synchronized (chunk.arena) {
            return numAvail;
        }
    }

    @Override
    public int elementSize() {
        synchronized (chunk.arena) {
            return elemSize;
        }
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }
}
