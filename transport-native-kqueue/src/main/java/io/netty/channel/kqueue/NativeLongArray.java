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

import static io.netty.channel.unix.Limits.SIZEOF_JLONG;

final class NativeLongArray {
    private long memoryAddress;
    private int capacity;
    private int size;

    NativeLongArray(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        memoryAddress = PlatformDependent.allocateMemory(capacity * SIZEOF_JLONG);
        this.capacity = capacity;
    }

    void add(long value) {
        checkSize();
        PlatformDependent.putLong(memoryOffset(size++), value);
    }

    void clear() {
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    void free() {
        PlatformDependent.freeMemory(memoryAddress);
        memoryAddress = 0;
    }

    long memoryAddress() {
        return memoryAddress;
    }

    long memoryAddressEnd() {
        return memoryOffset(size);
    }

    private long memoryOffset(int index) {
        return memoryAddress + index * SIZEOF_JLONG;
    }

    private void checkSize() {
        if (size == capacity) {
            realloc();
        }
    }

    private void realloc() {
        // Double the capacity while it is "sufficiently small", and otherwise increase by 50%.
        int newLength = capacity <= 65536 ? capacity << 1 : capacity + capacity >> 1;
        long newMemoryAddress = PlatformDependent.reallocateMemory(memoryAddress, newLength * SIZEOF_JLONG);
        if (newMemoryAddress == 0) {
            throw new OutOfMemoryError("unable to allocate " + newLength + " new bytes! Existing capacity is: "
                    + capacity);
        }
        memoryAddress = newMemoryAddress;
        capacity = newLength;
    }

    @Override
    public String toString() {
        return "memoryAddress: " + memoryAddress + " capacity: " + capacity + " size: " + size;
    }
}
