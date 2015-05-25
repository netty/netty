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

final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();
    private final PoolChunkList<T> nextList;
    private final int minUsage;
    private final int maxUsage;

    private PoolChunk<T> head;

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunkList(PoolChunkList<T> nextList, int minUsage, int maxUsage) {
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (head == null) {
            return false;
        }

        for (PoolChunk<T> cur = head;;) {
            long handle = cur.allocate(normCapacity);
            if (handle < 0) {
                cur = cur.next;
                if (cur == null) {
                    return false;
                }
            } else {
                cur.initBuf(buf, handle, reqCapacity);
                if (cur.usage() >= maxUsage) {
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
    }

    boolean free(PoolChunk<T> chunk, long handle) {
        chunk.free(handle);
        if (chunk.usage() < minUsage) {
            remove(chunk);
            if (prevList == null) {
                assert chunk.usage() == 0;
                return false;
            } else {
                prevList.add(chunk);
                return true;
            }
        }
        return true;
    }

    void add(PoolChunk<T> chunk) {
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk);
            return;
        }

        chunk.parent = this;
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
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
        return minUsage;
    }

    @Override
    public int maxUsage() {
        return maxUsage;
    }

    @Override
    public Iterator<PoolChunkMetric> iterator() {
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

    @Override
    public String toString() {
        if (head == null) {
            return "none";
        }

        StringBuilder buf = new StringBuilder();
        for (PoolChunk<T> cur = head;;) {
            buf.append(cur);
            cur = cur.next;
            if (cur == null) {
                break;
            }
            buf.append(StringUtil.NEWLINE);
        }

        return buf.toString();
    }
}
