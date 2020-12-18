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
package io.netty.channel.unix;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static io.netty.channel.unix.Limits.IOV_MAX;
import static io.netty.channel.unix.Limits.SSIZE_MAX;
import static io.netty.util.internal.ObjectUtil.checkPositive;
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
public final class IovArray implements MessageProcessor {

    /** The size of an address which should be 8 for 64 bits and 4 for 32 bits. */
    private static final int ADDRESS_SIZE = Buffer.addressSize();

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
    private final ByteBuf memory;
    private int count;
    private long size;
    private long maxBytes;

    public IovArray() {
        this(Unpooled.wrappedBuffer(Buffer.allocateDirectWithNativeOrder(MAX_CAPACITY)).setIndex(0, 0));
    }

    @SuppressWarnings("deprecation")
    public IovArray(ByteBuf memory) {
        Unix.ensureAvailability();
        assert memory.writerIndex() == 0;
        assert memory.readerIndex() == 0;
        this.memory = PlatformDependent.hasUnsafe() ? memory : memory.order(
                PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        if (memory.hasMemoryAddress()) {
            memoryAddress = memory.memoryAddress();
        } else {
            // Fallback to using JNI as we were not be able to access the address otherwise.
            memoryAddress = Buffer.memoryAddress(memory.internalNioBuffer(0, memory.capacity()));
        }
        maxBytes = SSIZE_MAX;
    }

    public void clear() {
        count = 0;
        size = 0;
    }

    /**
     * @deprecated Use {@link #add(ByteBuf, int, int)}
     */
    @Deprecated
    public boolean add(ByteBuf buf) {
        return add(buf, buf.readerIndex(), buf.readableBytes());
    }

    public boolean add(ByteBuf buf, int offset, int len) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        }
        if (buf.nioBufferCount() == 1) {
            if (len == 0) {
                return true;
            }
            if (buf.hasMemoryAddress()) {
                return add(memoryAddress, buf.memoryAddress() + offset, len);
            } else {
                ByteBuffer nioBuffer = buf.internalNioBuffer(offset, len);
                return add(memoryAddress, Buffer.memoryAddress(nioBuffer) + nioBuffer.position(), len);
            }
        } else {
            ByteBuffer[] buffers = buf.nioBuffers(offset, len);
            for (ByteBuffer nioBuffer : buffers) {
                final int remaining = nioBuffer.remaining();
                if (remaining != 0 &&
                        (!add(memoryAddress, Buffer.memoryAddress(nioBuffer) + nioBuffer.position(), remaining)
                                || count == IOV_MAX)) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean add(long memoryAddress, long addr, int len) {
        assert addr != 0;

        // If there is at least 1 entry then we enforce the maximum bytes. We want to accept at least one entry so we
        // will attempt to write some data and make progress.
        if ((maxBytes - len < size && count > 0) ||
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
                memory.setLong(baseOffset, addr);
                memory.setLong(lengthOffset, len);
            }
        } else {
            assert ADDRESS_SIZE == 4;
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putInt(baseOffset + memoryAddress, (int) addr);
                PlatformDependent.putInt(lengthOffset + memoryAddress, len);
            } else {
                memory.setInt(baseOffset, (int) addr);
                memory.setInt(lengthOffset, len);
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
     * Set the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(ByteBuf, int, int)}
     * <p>
     * This will not impact the existing state of the {@link IovArray}, and only applies to subsequent calls to
     * {@link #add(ByteBuf)}.
     * <p>
     * In order to ensure some progress is made at least one {@link ByteBuf} will be accepted even if it's size exceeds
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
     * Returns the {@code memoryAddress} for the given {@code offset}.
     */
    public long memoryAddress(int offset) {
        return memoryAddress + idx(offset);
    }

    /**
     * Release the {@link IovArray}. Once release further using of it may crash the JVM!
     */
    public void release() {
        memory.release();
    }

    @Override
    public boolean processMessage(Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) msg;
            return add(buffer, buffer.readerIndex(), buffer.readableBytes());
        }
        return false;
    }

    private static int idx(int index) {
        return IOV_SIZE * index;
    }
}
