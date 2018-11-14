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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.lang.Math.*;

//管理一个PoolArena上的5条PoolChunkList中的某一个实例
final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();
    private final PoolArena<T> arena;//chunkList所属PoolArena
    private final PoolChunkList<T> nextList;//接下来比他使用率大的chunkList队列
    private final int minUsage;
    private final int maxUsage;
    private final int maxCapacity;
    private PoolChunk<T> head;//chunkList的第一个chunk

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;//双线链表的前一个,用于内存回收的时候,使用率低了,就将该chunk移动

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        assert minUsage <= maxUsage;
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize);
    }

    /**
     * Calculates the maximum capacity of a buffer that will ever be possible to allocate out of the {@link PoolChunk}s
     * that belong to the {@link PoolChunkList} with the given {@code minUsage} and {@code maxUsage} settings.
     * chunkSize = 16777216 ,即16M  = 16 * 1024 * 1024,单位是字节
     */
    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);

        if (minUsage == 100) {//如果使用率最小都是100了,则能在给他分配任何资源了,因此结果就是0
            // If the minUsage is 100 we can not allocate anything out of this list.
            return 0;
        }

        // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
        //
        // As an example:
        // - If a PoolChunkList has minUsage == 25 we are allowed to allocate at most 75% of the chunkSize because
        //   this is the maximum amount available in any PoolChunk in this PoolChunkList.
        return  (int) (chunkSize * (100L - minUsage) / 100L);//输出 4194304  即16M的0.25倍就是他的最大值,即他只能最多扩容16M的25%,因为他的最小值是75%了
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (head == null || normCapacity > maxCapacity) {//说明目前chunkList没有办法分配normCapacity内存
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.
            return false;
        }

        for (PoolChunk<T> cur = head;;) {//从头开始不断找,找到能分配normCapacity的chunk
            long handle = cur.allocate(normCapacity);
            if (handle < 0) {//没找到
                cur = cur.next;//不断查找
                if (cur == null) {
                    return false;
                }
            } else {//说明该cur的chunk是可以分配的
                cur.initBuf(buf, handle, reqCapacity);//分配buffer
                if (cur.usage() >= maxUsage) {//超过最大容量,则移动到下一个list上
                    remove(cur);//本list中删除该chunk
                    nextList.add(cur);//该chunk移动到下一个list上
                }
                return true;
            }
        }
    }

    boolean free(PoolChunk<T> chunk, long handle) {
        chunk.free(handle);//释放内存
        if (chunk.usage() < minUsage) {//是否后 如果空间少了,移动到下一个list队列上
            remove(chunk);//在该list上删除该chunk
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);//向上一个list移动该chunk
        }
        return true;
    }

    //移动--两层含义,1是将该chunk依次移动到下一个list上,如果下一个list满足,则添加到下一个list上,如果不满足，继续下一个list的下一个list处理
    private boolean move(PoolChunk<T> chunk) {
        assert chunk.usage() < maxUsage;

        if (chunk.usage() < minUsage) {//如果不满足，继续下一个list的下一个list处理
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }

        // PoolChunk fits into this PoolChunkList, adding it here.
        add0(chunk);//如果下一个list满足,则添加到下一个list上
        return true;
    }

    /**
     * Moves the {@link PoolChunk} down the {@link PoolChunkList} linked-list so it will end up in the right
     * {@link PoolChunkList} that has the correct minUsage / maxUsage in respect to {@link PoolChunk#usage()}.
     * 向上一个list移动该chunk
     */
    private boolean move0(PoolChunk<T> chunk) {
        if (prevList == null) {
            // There is no previous PoolChunkList so return false which result in having the PoolChunk destroyed and
            // all memory associated with the PoolChunk will be released.
            assert chunk.usage() == 0;
            return false;
        }
        return prevList.move(chunk);//向上一个list移动该chunk
    }

    void add(PoolChunk<T> chunk) {
        if (chunk.usage() >= maxUsage) {//超过该chunkList的使用频率了
            nextList.add(chunk);//推到下一个队列上
            return;
        }
        add0(chunk);//在头部第一个位置添加该chunk
    }

    /**
     * Adds the {@link PoolChunk} to this {@link PoolChunkList}.
     */
    void add0(PoolChunk<T> chunk) {
        chunk.parent = this;//将chunk添加到该队列上,即chunk的父设置为chunkList
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {//挂到第一个头的位置上
            chunk.prev = null;//前一个是null
            chunk.next = head;//后一个就是以前的head头
            head.prev = chunk;//以前的头的前一个就是此时的chunk
            head = chunk;
        }
    }

    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;//已经是head了,因此前一个就是null
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    @Override
    public int minUsage() {
        return minUsage0(minUsage);
    }

    //最多100使用率
    @Override
    public int maxUsage() {
        return min(maxUsage, 100);
    }

    //最小使用率最小也是1
    private static int minUsage0(int value) {
        return max(1, value);
    }

    //该chunkList的所有chunk统计对象集合
    @Override
    public Iterator<PoolChunkMetric> iterator() {
        synchronized (arena) {
            if (head == null) {
                return EMPTY_METRICS;
            }
            List<PoolChunkMetric> metrics = new ArrayList<PoolChunkMetric>();
            for (PoolChunk<T> cur = head;;) {
                metrics.add(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
            }
            return metrics.iterator();
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        synchronized (arena) {
            if (head == null) {
                return "none";
            }

            for (PoolChunk<T> cur = head;;) {//从chunkList的第一个chunk开始,不断迭代
                buf.append(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
                buf.append(StringUtil.NEWLINE);
            }
        }
        return buf.toString();
    }

    //销毁每一个arena所属的chunk
    void destroy(PoolArena<T> arena) {
        PoolChunk<T> chunk = head;
        while (chunk != null) {
            arena.destroyChunk(chunk);
            chunk = chunk.next;
        }
        head = null;
    }
}
