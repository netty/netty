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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.ReadableComponent;
import io.netty5.buffer.api.ReadableComponentProcessor;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty5.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
 *
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
public final class IovArray implements MessageProcessor, ReadableComponentProcessor<RuntimeException>,
                                       WritableComponentProcessor<RuntimeException> {

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
            memory = memory.order(ByteOrder.nativeOrder());
        }
        this.memory = memory;
        memoryAddress = nativeAddressOf(memory);
    }

    public void clear() {
        count = 0;
        size = 0;
    }

    private boolean add(long memoryAddress, long addr, int len) {
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
        final int baseOffset = idx(count);
        final int lengthOffset = baseOffset + ADDRESS_SIZE;

        size += len;
        ++count;

        if (ADDRESS_SIZE == 8) {
            // 64bit
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putLong(baseOffset + memoryAddress, addr);
                PlatformDependent.putLong(lengthOffset + memoryAddress, len);
            } else {
                memory.putLong(baseOffset, addr);
                memory.putLong(lengthOffset, len);
            }
        } else {
            assert ADDRESS_SIZE == 4;
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putInt(baseOffset + memoryAddress, (int) addr);
                PlatformDependent.putInt(lengthOffset + memoryAddress, len);
            } else {
                memory.putInt(baseOffset, (int) addr);
                memory.putInt(lengthOffset, len);
            }
        }
        return true;
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
     * Set the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(long, long, int)} or
     * {@link #processMessage(Object)}.
     * <p>
     * This will not impact the existing state of the {@link IovArray}, and only applies to subsequent calls to
     * {@link #add(long, long, int)} or {@link #processMessage(Object)}.
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
    public boolean processMessage(Object msg) {
        if (msg instanceof io.netty5.buffer.api.Buffer) {
            var buffer = (io.netty5.buffer.api.Buffer) msg;
            if (buffer.readableBytes() == 0) {
                return true;
            }
            return buffer.forEachReadable(0, this) >= 0;
        }
        return false;
    }

    private static int idx(int index) {
        return IOV_SIZE * index;
    }

    @Override
    public boolean process(int index, ReadableComponent component) {
        return process(component, component.readableBytes());
    }

    public boolean process(ReadableComponent component, int byteCount) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        long nativeAddress = component.readableNativeAddress();
        assert nativeAddress != 0;
        return add(memoryAddress, nativeAddress, byteCount);
    }

    @Override
    public boolean process(int index, WritableComponent component) {
        return process(component, component.writableBytes());
    }

    public boolean process(WritableComponent component, int byteCount) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        long nativeAddress = component.writableNativeAddress();
        assert nativeAddress != 0;
        return add(memoryAddress, nativeAddress, byteCount);
    }
}
