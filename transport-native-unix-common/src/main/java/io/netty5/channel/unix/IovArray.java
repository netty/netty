/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.unix;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferComponent;
import io.netty5.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Predicate;

import static io.netty5.channel.unix.Buffer.addressSize;
import static io.netty5.channel.unix.Buffer.allocateDirectWithNativeOrder;
import static io.netty5.channel.unix.Buffer.free;
import static io.netty5.channel.unix.Buffer.nativeAddressOf;
import static io.netty5.channel.unix.Limits.IOV_MAX;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.min;

/**
 * Represent an array of struct array and so can be passed directly over via JNI without the need to do any more
 * array copies.
 * <p>
 * The buffers are written out directly into direct memory to match the struct iov. See also {@code man writev}.
 *
 * <pre>
 * struct iovec {
 *   void  *iov_base;
 *   size_t iov_len;
 * };
 * </pre>
 *
 * See also
 * <a href="https://rkennke.wordpress.com/2007/07/30/efficient-jni-programming-iv-wrapping-native-data-objects/"
 * >Efficient JNI programming IV: Wrapping native data objects</a>.
 */
public final class IovArray implements Predicate<Object> {

    /** The size of an address which should be 8 for 64 bits and 4 for 32 bits. */
    private static final int ADDRESS_SIZE = addressSize();

    /**
     * The size of an {@code iovec} struct in bytes. This is calculated as we have 2 entries each of the size of the
     * address.
     */
    public static final int IOV_SIZE = 2 * ADDRESS_SIZE;

    /**
     * The needed memory to hold up to {@code IOV_MAX} iov entries, where {@code IOV_MAX} signified
     * the maximum number of {@code iovec} structs that can be passed to {@code writev(...)}.
     */
    private static final int MAX_CAPACITY = IOV_MAX * IOV_SIZE;

    private final long memoryAddress;
    private final ByteBuffer memory;
    private int count;
    private long size;
    private long maxBytes = SSIZE_MAX;

    public IovArray() {
        this(allocateDirectWithNativeOrder(MAX_CAPACITY));
    }

    public IovArray(ByteBuffer memory) {
        assert memory.position() == 0;
        if (!memory.isDirect()) {
            memory = ByteBuffer.allocateDirect(memory.capacity());
        }
        if (memory.order() != ByteOrder.nativeOrder()) {
            memory.order(ByteOrder.nativeOrder());
        }
        this.memory = memory;
        memoryAddress = nativeAddressOf(memory);
    }

    public void clear() {
        count = 0;
        size = 0;
    }

    private boolean add(long addr, int len) {
        assert addr != 0;

        // If there is at least 1 entry then we enforce the maximum bytes. We want to accept at least one entry so we
        // will attempt to write some data and make progress.
        if (maxBytes - len < size && count > 0 ||
            // Check if we have enough space left
                memory.capacity() < (count + 1) * IOV_SIZE) {
            // If the size + len will overflow SSIZE_MAX we stop populate the IovArray. This is done as linux
            //  not allow to write more bytes then SSIZE_MAX with one writev(...) call and so will
            // return 'EINVAL', which will raise an IOException.
            //
            // See also:
            // - https://linux.die.net//man/2/writev
            return false;
        }

        putAddr(count, addr);
        putLen(count, len);

        size += len;
        ++count;

        return true;
    }

    /**
     * Update the iovec array to reflect that the given number of bytes have completed their IO successfully.
     * <p>
     * The iovec array is updated such that the first iovecs in the array are completed first.
     * A partially completed iovec will have its address and left updated to reference the remaining bytes of its
     * buffer.
     * And all incomplete iovecs will be moved down the array, so that index 0 continues to reference the first
     * incomplete iovec.
     * <p>
     * If the given number of bytes is equal to the size of this iovec array, then this method has the same effect as
     * calling {@link #clear()}.
     * <p>
     * If the given number of bytes is greater than the size of this iovec array, then an {@link IllegalStateException}
     * is thrown.
     *
     * @param bytes the number of bytes completed by the most recent IO.
     * @return {@code true} if all iovecs were fully completed, otherwise {@code false} if there is still more IO
     * left to be done.
     */
    public boolean completeBytes(int bytes) {
        if (bytes > size) {
            throw new IllegalStateException("Recent IO moved " + bytes + " bytes, " +
                    "but this ioved array only keeps track of " + size + " bytes.");
        }
        if (bytes == size) {
            clear();
            return true;
        }
        int leftToComplete = bytes;
        int completed = 0;
        for (int i = 0; i < count; i++) {
            final int len = getLen(i);
            final boolean currentIsComplete;
            if (len <= leftToComplete) {
                completed++;
                leftToComplete -= len;
                currentIsComplete = true;
            } else {
                putAddr(i, getAddr(i) + leftToComplete);
                putLen(i, len - leftToComplete);
                leftToComplete = 0;
                currentIsComplete = false;
            }
            if (leftToComplete == 0) {
                // Move incomplete iovecs to the beginning.
                if (currentIsComplete || i > 0) {
                    for (int w = 0, r = i  + (currentIsComplete? 1 : 0); r < count; w++, r++) {
                        putAddr(w, getAddr(r));
                        putLen(w, getLen(r));
                    }
                }
                break;
            }
        }

        count -= completed;
        size -= bytes;

        return false;
    }

    /**
     * Returns the number if iov entries.
     */
    public int count() {
        return count;
    }

    /**
     * Returns the size in bytes
     */
    public long size() {
        return size;
    }

    /**
     * Set the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(long, int)} or
     * {@link #test(Object)}.
     * <p>
     * This will not impact the existing state of the {@link IovArray}, and only applies to subsequent calls to
     * {@link #add(long, int)} or {@link #test(Object)}.
     * <p>
     * In order to ensure some progress is made at least one {@link Buffer} will be accepted even if it's size exceeds
     * this value.
     * @param maxBytes the maximum amount of bytes that can be added to this {@link IovArray}.
     */
    public void maxBytes(long maxBytes) {
        this.maxBytes = min(SSIZE_MAX, checkPositive(maxBytes, "maxBytes"));
    }

    /**
     * Get the maximum amount of bytes that can be added to this {@link IovArray}.
     * @return the maximum amount of bytes that can be added to this {@link IovArray}.
     */
    public long maxBytes() {
        return maxBytes;
    }

    /**
     * Returns the {@code memoryAddress} for the given {@code index}.
     */
    public long memoryAddress(int index) {
        return memoryAddress + idx(index);
    }

    /**
     * Release the {@link IovArray}. Once release further using of it may crash the JVM!
     */
    public void release() {
        free(memory);
    }

    @Override
    public boolean test(Object msg) {
        if (msg instanceof Buffer) {
            var buffer = (Buffer) msg;
            if (buffer.readableBytes() == 0) {
                return true;
            }
            return addReadable(buffer);
        }
        return false;
    }

    public boolean addReadable(Buffer buffer) {
        try (var iteration = buffer.forEachComponent()) {
            for (var c = iteration.firstReadable(); c != null; c = c.nextReadable()) {
                if (!addReadable(c, c.readableBytes())) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean addReadable(BufferComponent component, int byteCount) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        long nativeAddress = component.readableNativeAddress();
        assert nativeAddress != 0;
        return add(nativeAddress, byteCount);
    }

    public boolean addWritable(Buffer buffer) {
        try (var iteration = buffer.forEachComponent()) {
            for (var c = iteration.firstWritable(); c != null; c = c.nextWritable()) {
                if (!addWritable(c, c.writableBytes())) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean addWritable(BufferComponent component, int byteCount) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        long nativeAddress = component.writableNativeAddress();
        assert nativeAddress != 0;
        return add(nativeAddress, byteCount);
    }

    private void putAddr(int index, long addr) {
        put(idx(index), addr);
    }

    private void putLen(int index, int len) {
        put(idx(index) + ADDRESS_SIZE, len);
    }

    private long getAddr(int index) {
        return get(idx(index));
    }

    private int getLen(int index) {
        return Math.toIntExact(get(idx(index) + ADDRESS_SIZE));
    }

    private void put(int off, long val) {
        if (ADDRESS_SIZE == 8) {
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putLong(memoryAddress + off, val);
            } else {
                memory.putLong(off, val);
            }
        } else {
            assert ADDRESS_SIZE == 4;
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putInt(memoryAddress + off, (int) val);
            } else {
                memory.putInt(off, (int) val);
            }
        }
    }

    private long get(int off) {
        if (ADDRESS_SIZE == 8) {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.getLong(memoryAddress + off);
            } else {
                return memory.getLong(off);
            }
        } else {
            assert ADDRESS_SIZE == 4;
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.getInt(memoryAddress + off);
            } else {
                return memory.getInt(off);
            }
        }
    }

    private static int idx(int index) {
        return IOV_SIZE * index;
    }
}
