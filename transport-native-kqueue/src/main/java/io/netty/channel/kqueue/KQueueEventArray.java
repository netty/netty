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

import io.netty.util.internal.PlatformDependent;

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

    private long memoryAddress;
    private int size;
    private int capacity;

    KQueueEventArray(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        memoryAddress = PlatformDependent.allocateMemory(capacity * KQUEUE_EVENT_SIZE);
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
        checkSize();
        evSet(getKEventOffset(size++), ch, ch.socket.intValue(), filter, flags, fflags);
    }

    private void checkSize() {
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
        long newMemoryAddress = PlatformDependent.reallocateMemory(memoryAddress, newLength * KQUEUE_EVENT_SIZE);
        if (newMemoryAddress != 0) {
            memoryAddress = newMemoryAddress;
            capacity = newLength;
            return;
        }
        if (throwIfFail) {
            throw new OutOfMemoryError("unable to allocate " + newLength + " new bytes! Existing capacity is: "
                    + capacity);
        }
    }

    /**
     * Free this {@link KQueueEventArray}. Any usage after calling this method may segfault the JVM!
     */
    void free() {
        PlatformDependent.freeMemory(memoryAddress);
        memoryAddress = size = capacity = 0;
    }

    long getKEventOffset(int index) {
        return memoryAddress + index * KQUEUE_EVENT_SIZE;
    }

    short flags(int index) {
        return PlatformDependent.getShort(getKEventOffset(index) + KQUEUE_FLAGS_OFFSET);
    }

    short filter(int index) {
        return PlatformDependent.getShort(getKEventOffset(index) + KQUEUE_FILTER_OFFSET);
    }

    short fflags(int index) {
        return PlatformDependent.getShort(getKEventOffset(index) + KQUEUE_FFLAGS_OFFSET);
    }

    int fd(int index) {
        return PlatformDependent.getInt(getKEventOffset(index) + KQUEUE_IDENT_OFFSET);
    }

    long data(int index) {
        return PlatformDependent.getLong(getKEventOffset(index) + KQUEUE_DATA_OFFSET);
    }

    AbstractKQueueChannel channel(int index) {
        return getChannel(getKEventOffset(index));
    }

    private static native void evSet(long keventAddress, AbstractKQueueChannel ch,
                                     int ident, short filter, short flags, int fflags);
    private static native AbstractKQueueChannel getChannel(long keventAddress);
    static native void deleteGlobalRefs(long channelAddressStart, long channelAddressEnd);
}
