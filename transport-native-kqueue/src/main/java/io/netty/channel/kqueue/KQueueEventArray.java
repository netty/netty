/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.unix.Buffer;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

/**
 * Represents an array of kevent structures, backed by offheap memory.
 *
 * struct kevent {
 *  uintptr_t ident;
 *  short     keventFilter;
 *  u_short   flags;
 *  u_int     fflags;
 *  intptr_t  data;
 *  void      *udata;
 * };
 */
final class KQueueEventArray {
    private static final int KQUEUE_EVENT_SIZE = Native.sizeofKEvent();
    private static final int KQUEUE_IDENT_OFFSET = Native.offsetofKEventIdent();
    private static final int KQUEUE_FILTER_OFFSET = Native.offsetofKEventFilter();
    private static final int KQUEUE_FFLAGS_OFFSET = Native.offsetofKEventFFlags();
    private static final int KQUEUE_FLAGS_OFFSET = Native.offsetofKEventFlags();
    private static final int KQUEUE_DATA_OFFSET = Native.offsetofKeventData();

    private ByteBuffer memory;
    private long memoryAddress;
    private int size;
    private int capacity;

    KQueueEventArray(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        memory = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(capacity));
        memoryAddress = Buffer.memoryAddress(memory);
        this.capacity = capacity;
    }

    /**
     * Return the {@code memoryAddress} which points to the start of this {@link KQueueEventArray}.
     */
    long memoryAddress() {
        return memoryAddress;
    }

    /**
     * Return the capacity of the {@link KQueueEventArray} which represent the maximum number of {@code kevent}s
     * that can be stored in it.
     */
    int capacity() {
        return capacity;
    }

    int size() {
        return size;
    }

    void clear() {
        size = 0;
    }

    void evSet(AbstractKQueueChannel ch, short filter, short flags, int fflags) {
        reallocIfNeeded();
        evSet(getKEventOffset(size++) + memoryAddress, ch, ch.socket.intValue(), filter, flags, fflags);
    }

    private void reallocIfNeeded() {
        if (size == capacity) {
            realloc(true);
        }
    }

    /**
     * Increase the storage of this {@link KQueueEventArray}.
     */
    void realloc(boolean throwIfFail) {
        // Double the capacity while it is "sufficiently small", and otherwise increase by 50%.
        int newLength = capacity <= 65536 ? capacity << 1 : capacity + capacity >> 1;

        try {
            ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(newLength));
            // Copy over the old content of the memory and reset the position as we always act on the buffer as if
            // the position was never increased.
            memory.position(0).limit(size);
            buffer.put(memory);
            buffer.position(0);

            Buffer.free(memory);
            memory = buffer;
            memoryAddress = Buffer.memoryAddress(buffer);
        } catch (OutOfMemoryError e) {
            if (throwIfFail) {
                OutOfMemoryError error = new OutOfMemoryError(
                        "unable to allocate " + newLength + " new bytes! Existing capacity is: " + capacity);
                error.initCause(e);
                throw error;
            }
        }
    }

    /**
     * Free this {@link KQueueEventArray}. Any usage after calling this method may segfault the JVM!
     */
    void free() {
        Buffer.free(memory);
        memoryAddress = size = capacity = 0;
    }

    private static int getKEventOffset(int index) {
        return index * KQUEUE_EVENT_SIZE;
    }

    private long getKEventOffsetAddress(int index) {
        return getKEventOffset(index) + memoryAddress;
    }

    private short getShort(int index, int offset) {
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getShort(getKEventOffsetAddress(index) + offset);
        }
        return memory.getShort(getKEventOffset(index) + offset);
    }

    short flags(int index) {
        return getShort(index, KQUEUE_FLAGS_OFFSET);
    }

    short filter(int index) {
        return getShort(index, KQUEUE_FILTER_OFFSET);
    }

    short fflags(int index) {
        return getShort(index, KQUEUE_FFLAGS_OFFSET);
    }

    int fd(int index) {
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getInt(getKEventOffsetAddress(index) + KQUEUE_IDENT_OFFSET);
        }
        return memory.getInt(getKEventOffset(index) + KQUEUE_IDENT_OFFSET);
    }

    long data(int index) {
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getLong(getKEventOffsetAddress(index) + KQUEUE_DATA_OFFSET);
        }
        return memory.getLong(getKEventOffset(index) + KQUEUE_DATA_OFFSET);
    }

    AbstractKQueueChannel channel(int index) {
        return getChannel(getKEventOffsetAddress(index));
    }

    private static int calculateBufferCapacity(int capacity) {
        return capacity * KQUEUE_EVENT_SIZE;
    }

    private static native void evSet(long keventAddress, AbstractKQueueChannel ch,
                                     int ident, short filter, short flags, int fflags);
    private static native AbstractKQueueChannel getChannel(long keventAddress);
    static native void deleteGlobalRefs(long channelAddressStart, long channelAddressEnd);
}
