/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import java.io.Closeable;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Limits;
import io.netty.util.internal.PlatformDependent;

public class FixedBufferTracker implements PooledByteBufAllocator.AllocationCallback, Closeable {

    private static final int ADDRESS_SIZE = Buffer.addressSize();
    private static final int IOV_SIZE = 2 * ADDRESS_SIZE;
    private static final int MIN_ITERATIONS_BETWEEN_UPDATES = 8;

    private final PooledByteBufAllocator alloc;
    private final EventLoop loop;

    // linear hash probe map of addr to size,index
    private long[] bufMap;
    private int count;
    private boolean dirty;

    public FixedBufferTracker(EventLoop loop, PooledByteBufAllocator alloc) {
        bufMap = loop != null ? new long[4 * Limits.IOV_MAX] : null; //TODO start smaller and resize
        this.loop = loop;
        this.alloc = alloc;
    }

    public void start() {
        if (alloc != null) {
            alloc.registerAllocationCallback(this);
        }
    }

    @Override
    public void close() throws IOException {
        if (alloc != null) {
            alloc.unregisterAllocationCallback(this);
        }
    }

    boolean isDirty() {
        return dirty;
    }

    int getCount() {
        return count;
    }

    ByteBuf getIoVecs() {
        if (count == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf buf = alloc.directBuffer(count * IOV_SIZE);
        long iovAddr = buf.memoryAddress();
        for (int i = 0, j = 1; i < bufMap.length; i += 2) {
            long addr = bufMap[i];
            if (addr != 0L) {
                long val = bufMap[i + 1];
                PlatformDependent.putLong(iovAddr, addr);
                PlatformDependent.putLong(iovAddr + ADDRESS_SIZE, val >>> 32);
                iovAddr += IOV_SIZE;
                bufMap[i + 1] = val & (-1L << 32) | j++;
            }
        }
        dirty = false;
        return buf.setIndex(0, buf.capacity());
    }

    short getIndex(long addr) {
        if (addr == 0L) {
            return -1;
        }
        final long[] table = bufMap;
        for (int len = table.length, i = hash(addr, len);; i = next(i, len)) {
            long key = table[i];
            if (key == addr) {
                return (short) ((table[i + 1] & 0xffff) - 1); // can also be -1
            }
            if (key == 0L) {
                return -1;
            }
        }
    }

    @Override
    public void bufferAllocated(final long address, final int length) {
        if (loop.inEventLoop()) {
            add(address, length);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    add(address, length);
                }
            });
        }
    }

    @Override
    public void bufferDeallocated(final long address) {
        if (loop.inEventLoop()) {
            remove(address);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    remove(address);
                }
            });
        }
    }

    public void add(long addr, int size) {
        if (size == 0) {
            return;
        }
        final long[] table = bufMap;
        for (int len = table.length, i = hash(addr, len);; i = next(i, len)) {
            long key = table[i];
            if (key == 0L) {
                set(table, i, addr, ((long) size) << 32);
                count++;
                dirty = true;
                return;
            }
            if (key == addr) {
                table[i + 1] = ((long) size) << 32;
                dirty = true;
                return;
            }
        }
    }

    public void remove(long addr) {
        final long[] table = bufMap;
        long key;
        int len = table.length;
        for (int i = hash(addr, len), d = -1; (key = table[i]) != 0L; i = next(i, len)) {
            if (d >= 0) {
                int r = hash(key, len);
                if ((i >= r || r > d && d > i) && (r > d || d > i)) {
                    continue;
                }
                set(table, d, key, table[i + 1]);
            } else if (key != addr) {
                continue;
            } else {
                count--;
                dirty = true;
            }
            set(table, i, 0L, 0L);
            d = i;
        }
    }

    private static void set(long[] table, int i, long addr, long val) {
        table[i] = addr;
        table[i + 1] = val;
    }

    private static int next(int i, int len) {
        return i == len - 2 ? 0 : i + 2;
    }

    private static int hash(long addr, int len) {
        //TODO improve hash maybe
        int h = (int) (addr ^ (addr >>> 32));
        return ((h << 1) - (h << 8)) & (len - 1);
    }

    static final FixedBufferTracker NOOP_TRACKER = new FixedBufferTracker(null, null) {
        @Override
        short getIndex(long addr) {
            return -1;
        }
        @Override
        boolean isDirty() {
            return false;
        }
    };
}
