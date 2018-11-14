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

//每一个PoolSubpage 表示一个page对象,即该对象可以在page=8k的基础上进一步分配存储空间
final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;//所属chunk
    private final int memoryMapIdx;//所属chunk下的第几个page页面
    private final int runOffset;//该page在chunk下的偏移量
    private final int pageSize;//pagesize
    private final long[] bitmap;//该page根据subpage的元素大小,创建多少个小内存空间

    PoolSubpage<T> prev;//我的前一个
    PoolSubpage<T> next;//我的后一个

    boolean doNotDestroy;
    int elemSize;
    private int maxNumElems;//总元素个数
    private int bitmapLength;//需要多少个long表示maxNumElems个数
    private int nextAvail;//下一个有效的元素位置
    private int numAvail;//有效的元素个数

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head */
    PoolSubpage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        runOffset = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int runOffset, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.runOffset = runOffset;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10]; // pageSize / 16 / 64 --- 64表示用一个long表示64位,16表示最少elemSize也必须是16个字节
        init(head, elemSize);
    }

    void init(PoolSubpage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;
            bitmapLength = maxNumElems >>> 6; //最大的元素数量 / 64,表示用多少个long表示,因为一个long表示64个wei
            if ((maxNumElems & 63) != 0) {//追加一个long
                bitmapLength ++;
            }

            for (int i = 0; i < bitmapLength; i ++) {
                bitmap[i] = 0;
            }
        }
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     * 分配一个subPage位置
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();//找到一个空闲的位置
        int q = bitmapIdx >>> 6;//转换成第几个long
        int r = bitmapIdx & 63;//第几个位置
        assert (bitmap[q] >>> r & 1) == 0;//确定该位置在long上的值为0
        bitmap[q] |= 1L << r;//设置该位置为1

        if (-- numAvail == 0) {//减少一个可用的有效数据
            removeFromPool();
        }

        return toHandle(bitmapIdx);//直接返回handler
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     * 回收第bitmapIdx个序号的元素内存
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;//找到第几个long
        int r = bitmapIdx & 63;//该long 的第几个位置
        assert (bitmap[q] >>> r & 1) != 0;//该位置必须是1,因为是1才是被使用了,才需要被回收
        bitmap[q] ^= 1L << r;//将其位置设置为0

        setNextAvail(bitmapIdx);//设置下一次就可以使用该位置

        if (numAvail ++ == 0) {//增加一个
            addToPool(head);
            return true;
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {//说明该subpage都是空的,没有被使用
            // Subpage not in use (numAvail == maxNumElems)
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    //原来是head1  head2  head3 调用完该方法后变成head1 this head2  head3
    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        //因此this的前一个是head,后一个是head2
        prev = head;
        next = head.next;
        //head2的前一个是this
        //head的next是this
        next.prev = this;
        head.next = this;
    }

    //移除该subpage
    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;//前一个的下一个，就是我的下一个
        next.prev = prev;//下一个的前一个，就是我的前一个
        next = null;
        prev = null;
    }

    //设置下一个被拿来使用的位置
    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    //找到下一个空的位置,可以被拿来使用
    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    //找到下一个空的位置,可以被拿来使用
    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];//获取每一个long值
            if (~bits != 0) {//true,说明该long值的64个位有0的位置,因此翻转后才不等于0
                return findNextAvail0(i, bits);//找到该0的位置
            }
        }
        return -1;
    }

    //找到哪个位置是0的
    //比如i是2,表示第2个元素,计算后j=5是0的位置,因此返回值是2*64+5 = 133,即第133位置是空
    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j ++) {//每一个位进行循环
            if ((bits & 1) == 0) {//拿最后一位比较,一旦等于0,说明该位置是0,因此说明找到了,进入if里面
                int val = baseVal | j;//说明base+现在是0的位置,组成的序号
                if (val < maxNumElems) {//该序号满足不超过最大值,则返回该值即可
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;//继续下一个j位
        }
        return -1;
    }

    //使用subpage序号 | page序号,组成handle,即表示在哪个page上的第几个subpage,可以定位内存位置
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
