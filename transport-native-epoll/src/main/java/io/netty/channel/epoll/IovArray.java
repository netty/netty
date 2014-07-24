/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

/**
 * Represent an array of struct array and so can be passed directly over via JNI without the need to do any more
 * array copies.
 *
 * The buffers are written out directly into direct memory to match the struct iov. See also <code>man writev</code>.
 *
 * <pre>
 * struct iovec {
 *   void  *iov_base;
 *   size_t iov_len;
 * };
 * </pre>
 *
 * See also
 * <a href="http://rkennke.wordpress.com/2007/07/30/efficient-jni-programming-iv-wrapping-native-data-objects/">
 *     Efficient JNI programming IV: Wrapping native data objects</a>.
 */
final class IovArray {
    // Maximal number of struct iov entries that can be passed to writev(...)
    private static final int IOV_MAX = Native.IOV_MAX;
    // The size of an address which should be 8 for 64 bits and 4 for 32 bits.
    private static final int ADDRESS_SIZE = PlatformDependent.addressSize();
    // The size of an struct iov entry in bytes. This is calculated as we have 2 entries each of the size of the
    // address.
    private static final int IOV_SIZE = 2 * ADDRESS_SIZE;
    // The needed memory to hold up to IOV_MAX iov entries.
    private static final int CAPACITY = IOV_MAX * IOV_SIZE;

    private static final FastThreadLocal<IovArray> ARRAY = new FastThreadLocal<IovArray>() {
        @Override
        protected IovArray initialValue() throws Exception {
            return new IovArray();
        }

        @Override
        protected void onRemoval(IovArray value) throws Exception {
            // free the direct memory now
            PlatformDependent.freeMemory(value.memoryAddress);
        }
    };

    private final long memoryAddress;
    private int count;
    private long size;

    private IovArray() {
        memoryAddress = PlatformDependent.allocateMemory(CAPACITY);
    }

    /**
     * Try to add the given {@link ByteBuf}. Returns {@code true} on success,
     * {@code false} otherwise.
     */
    boolean add(ByteBuf buf) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        int len = buf.readableBytes();
        long addr = buf.memoryAddress();
        int offset = buf.readerIndex();

        long baseOffset = memoryAddress(count++);
        long lengthOffset = baseOffset + ADDRESS_SIZE;
        if (ADDRESS_SIZE == 8) {
            // 64bit
            PlatformDependent.putLong(baseOffset, addr + offset);
            PlatformDependent.putLong(lengthOffset, len);
        } else {
            assert ADDRESS_SIZE == 4;
            PlatformDependent.putInt(baseOffset, (int) addr + offset);
            PlatformDependent.putInt(lengthOffset, len);
        }
        size += len;
        return true;
    }

    /**
     * Process the written iov entries. This will return the length of the iov entry on the given index if it is
     * smaller then the given {@code written} value. Otherwise it returns {@code -1}.
     */
    long processWritten(int index, long written) {
        long baseOffset = memoryAddress(index);
        long lengthOffset = baseOffset + ADDRESS_SIZE;
        if (ADDRESS_SIZE == 8) {
            // 64bit
            long len = PlatformDependent.getLong(lengthOffset);
            if (len > written) {
                long offset = PlatformDependent.getLong(baseOffset);
                PlatformDependent.putLong(baseOffset, offset + written);
                PlatformDependent.putLong(lengthOffset, len - written);
                return -1;
            }
            return len;
        } else {
            assert ADDRESS_SIZE == 4;
            long len = PlatformDependent.getInt(lengthOffset);
            if (len > written) {
                int offset = PlatformDependent.getInt(baseOffset);
                PlatformDependent.putInt(baseOffset, (int) (offset + written));
                PlatformDependent.putInt(lengthOffset, (int) (len - written));
                return -1;
            }
            return len;
        }
    }

    /**
     * Returns the number if iov entries.
     */
    int count() {
        return count;
    }

    /**
     * Returns the size in bytes
     */
    long size() {
        return size;
    }

    /**
     * Returns the {@code memoryAddress} for the given {@code offset}.
     */
    long memoryAddress(int offset) {
        return memoryAddress + IOV_SIZE * offset;
    }

    /**
     * Returns a {@link IovArray} which can be filled.
     */
    static IovArray get() {
        IovArray array = ARRAY.get();
        array.size = 0;
        array.count = 0;
        return array;
    }
}
