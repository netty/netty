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

import static io.netty.channel.unix.Limits.SIZEOF_JLONG;

final class NativeLongArray {
    private ByteBuffer memory;
    private long memoryAddress;
    private int capacity;
    private int size;

    NativeLongArray(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 but was " + capacity);
        }
        memory = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(capacity));
        memoryAddress = Buffer.memoryAddress(memory);
        this.capacity = capacity;
    }

    private static int idx(int index) {
        return index * SIZEOF_JLONG;
    }

    private static int calculateBufferCapacity(int capacity) {
        return capacity * SIZEOF_JLONG;
    }

    void add(long value) {
        reallocIfNeeded();
        if (PlatformDependent.hasUnsafe()) {
            PlatformDependent.putLong(memoryOffset(size), value);
        } else {
            memory.putLong(idx(size), value);
        }
        ++size;
    }

    void clear() {
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void free() {
        Buffer.free(memory);
        memoryAddress = 0;
    }

    long memoryAddress() {
        return memoryAddress;
    }

    long memoryAddressEnd() {
        return memoryOffset(size);
    }

    private long memoryOffset(int index) {
        return memoryAddress + idx(index);
    }

    private void reallocIfNeeded() {
        if (size == capacity) {
            // Double the capacity while it is "sufficiently small", and otherwise increase by 50%.
            int newLength = capacity <= 65536 ? capacity << 1 : capacity + capacity >> 1;
            ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(newLength));
            // Copy over the old content of the memory and reset the position as we always act on the buffer as if
            // the position was never increased.
            memory.position(0).limit(size);
            buffer.put(memory);
            buffer.position(0);

            Buffer.free(memory);
            memory = buffer;
            memoryAddress = Buffer.memoryAddress(buffer);
            capacity = newLength;
        }
    }

    @Override
    public String toString() {
        return "memoryAddress: " + memoryAddress + " capacity: " + capacity + " size: " + size;
    }
}
