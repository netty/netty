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

// PoolChunkList负责管理多个chunk的生命周期，在此基础上对内存分配进行进一步的优化;
// PoolChunkList主要是为了提高内存分配的效率，每个list中包含多个chunk，
// 而多个list又可以形成一个大的link list，在进行内存分配时，我们可以先从比较靠前的list中分配内存，
// 这样分配到的几率更大。在高峰期申请过多的内存后，随着流量下降慢慢的释放掉多余内存，形成一个良性的循环。
// 需要注意的时由于需要对无用的chunk进行释放，PoolChunkList形成的link list并不是一个完整的双向链表，
// 而是一个包含出口的链表（这里说法可能不够准确，意思就是这个双向链表中的其中一个分头结点的节点只有一个next节点没有prev节点。

final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();



    private final PoolArena<T> arena;
    // list还有自己的next和prev节点，最终组成一个list的link list
    private final PoolChunkList<T> nextList;
    // 当前list中的chunk最小使用比例
    private final int minUsage;
    // 当前list中的chunk最大使用比例
    private final int maxUsage;
    private final int maxCapacity;
    // chunk有prev和next两个属性，因此这里只用一个节点就可以维护一个chunk链
    private PoolChunk<T> head;

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;

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
     */
    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);

        if (minUsage == 100) {
            // If the minUsage is 100 we can not allocate anything out of this list.
            return 0;
        }

        // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
        //
        // As an example:
        // - If a PoolChunkList has minUsage == 25 we are allowed to allocate at most 75% of the chunkSize because
        //   this is the maximum amount available in any PoolChunk in this PoolChunkList.
        return  (int) (chunkSize * (100L - minUsage) / 100L);
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }


    // 为buf分配指定大小的内存
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {

        // 如果list中没有chunk则直接返回，看来这个list本身没有创建chunk的能力啊，只是负责维护chunk链。

        if (head == null || normCapacity > maxCapacity) {
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.
            return false;
        }
        // 我们一个一个chunk开始找
        for (PoolChunk<T> cur = head;;) {
            long handle = cur.allocate(normCapacity);
            if (handle < 0) {
                // handle < 0表示分配失败，继续到下一个chunk尝试
                cur = cur.next;
                if (cur == null) {
                    return false;
                }
            } else {
                // 分配成功则将分配到的资源赋给ByteBuf
                cur.initBufinitBuf(buf, handle, reqCapacity);
                // 当前chunk的使用量超过一个上限阈值，则将其从当前list转移到下一个list

                // 当一个chunk的用量超过一定的比例，会将该chunk从当前list挪到下一个list中，这样挪有什么好处呢？
                // 我们知道chunk本身是从连续的内存中分配一小段连续的内存，
                // 这样实际使用内存者读写很方便，然而这种策略也带来了一个坏处，
                // 随着内存的不断分配和回收，chunk中可能存在很多碎片。
                // 碎片越来越多后我们想分配一段连续内存的失败几率就会提高。
                // 针对这种情况我们可以把使用比例较大的chunk放到更后面，
                // 而先从使用比例更小的chunk中更早，这样成功的几率就提高了。
                // 然而光把chunk往后放是不科学的，因为随着内存的释放，
                // 原先被严重瓜分的chunk中会存在越来越多的大块连续内存，
                // 所以还得在特定条件下把chunk从后往前调。调整的时机当然就是在内存释放的时候了：
                if (cur.usage() >= maxUsage) {
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
    }

    // 释放指定chunk内的指定page或page内的subpage
    boolean free(PoolChunk<T> chunk, long handle) {
        // handle代表了chunk中的某个page或sugPage;
        chunk.free(handle);
        // 用量少于阈值则从当前list移到前一个list,如果不存在前一个list,则销毁chunk
        if (chunk.usage() < minUsage) {
            remove(chunk);
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }
        return true;
    }

    private boolean move(PoolChunk<T> chunk) {
        assert chunk.usage() < maxUsage;

        if (chunk.usage() < minUsage) {
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }

        // PoolChunk fits into this PoolChunkList, adding it here.
        add0(chunk);
        return true;
    }

    /**
     * Moves the {@link PoolChunk} down the {@link PoolChunkList} linked-list so it will end up in the right
     * {@link PoolChunkList} that has the correct minUsage / maxUsage in respect to {@link PoolChunk#usage()}.
     */
    private boolean move0(PoolChunk<T> chunk) {
        // 从这里我们可以看出在一个chunk经历了一些列的分配内存、释放内存之后，list会将整个chunk释放掉
        // 这样如果在流量高峰期分配了较多内存，随着流量的慢慢回落，内存会慢慢的释放出来。
        if (prevList == null) {
            // There is no previous PoolChunkList so return false which result in having the PoolChunk destroyed and
            // all memory associated with the PoolChunk will be released.
            assert chunk.usage() == 0;
            return false;
        }
        return prevList.move(chunk);
    }

    // // 增加节点
    void add(PoolChunk<T> chunk) {
        // 如果超过当前list的上限阈值，则放入下一个list
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk);
            return;
        }
        add0(chunk);
    }

    /**
     * Adds the {@link PoolChunk} to this {@link PoolChunkList}.
     */
    void add0(PoolChunk<T> chunk) {

        chunk.parent = this;
        if (head == null) {
            //  不存在头结点则该节点作为头结点
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            // 存在头结点则将该节点放到头结点之前，该节点成为头结点。
            // 刚放入的节点使用比例相对更小，分配到资源的可能性更大，因此放到头结点
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
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

    @Override
    public int maxUsage() {
        return min(maxUsage, 100);
    }

    private static int minUsage0(int value) {
        return max(1, value);
    }

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

            for (PoolChunk<T> cur = head;;) {
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

    void destroy(PoolArena<T> arena) {
        PoolChunk<T> chunk = head;
        while (chunk != null) {
            arena.destroyChunk(chunk);
            chunk = chunk.next;
        }
        head = null;
    }
}
