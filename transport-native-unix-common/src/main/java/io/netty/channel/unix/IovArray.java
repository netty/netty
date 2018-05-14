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
package io.netty.channel.unix;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

import static io.netty.channel.unix.Limits.IOV_MAX;
import static io.netty.channel.unix.Limits.SSIZE_MAX;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.PlatformDependent.allocateMemory;
import static io.netty.util.internal.PlatformDependent.directBufferAddress;
import static io.netty.util.internal.PlatformDependent.freeMemory;
import static io.netty.util.internal.PlatformDependent.putInt;
import static io.netty.util.internal.PlatformDependent.putLong;
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
 * <a href="http://rkennke.wordpress.com/2007/07/30/efficient-jni-programming-iv-wrapping-native-data-objects/"
 * >Efficient JNI programming IV: Wrapping native data objects</a>.
 */
public final class IovArray implements MessageProcessor {

    /** The size of an address which should be 8 for 64 bits and 4 for 32 bits. */
    private static final int ADDRESS_SIZE = PlatformDependent.addressSize();

    /**
     * The size of an {@code iovec} struct in bytes. This is calculated as we have 2 entries each of the size of the
     * address.
     */
    private static final int IOV_SIZE = 2 * ADDRESS_SIZE;

    /**
     * The needed memory to hold up to {@code IOV_MAX} iov entries, where {@code IOV_MAX} signified
     * the maximum number of {@code iovec} structs that can be passed to {@code writev(...)}.
     */
    private static final int CAPACITY = IOV_MAX * IOV_SIZE;

    private final long memoryAddress;
    private int count;
    private long size;
    private long maxBytes = SSIZE_MAX;

    public IovArray() {
        memoryAddress = allocateMemory(CAPACITY);
    }

    public void clear() {
        count = 0;
        size = 0;
    }

    /**
     * Add a {@link ByteBuf} to this {@link IovArray}.
     * @param buf The {@link ByteBuf} to add.
     * @return {@code true} if the entire {@link ByteBuf} has been added to this {@link IovArray}. Note in the event
     * that {@link ByteBuf} is a {@link CompositeByteBuf} {@code false} may be returned even if some of the components
     * have been added.
     */
    public boolean add(ByteBuf buf) {
        if (count == IOV_MAX) {
            // No more room!
            return false;
        } else if (buf.hasMemoryAddress() && buf.nioBufferCount() == 1) {
            final int len = buf.readableBytes();
            return len == 0 || add(buf.memoryAddress(), buf.readerIndex(), len);
        } else {
            ByteBuffer[] buffers = buf.nioBuffers();
            for (ByteBuffer nioBuffer : buffers) {
                final int len = nioBuffer.remaining();
                if (len != 0 && (!add(directBufferAddress(nioBuffer), nioBuffer.position(), len) || count == IOV_MAX)) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean add(long addr, int offset, int len) {
        final long baseOffset = memoryAddress(count);
        final long lengthOffset = baseOffset + ADDRESS_SIZE;

        // If there is at least 1 entry then we enforce the maximum bytes. We want to accept at least one entry so we
        // will attempt to write some data and make progress.
        if (maxBytes - len < size && count > 0) {
            // If the size + len will overflow SSIZE_MAX we stop populate the IovArray. This is done as linux
            //  not allow to write more bytes then SSIZE_MAX with one writev(...) call and so will
            // return 'EINVAL', which will raise an IOException.
            //
            // See also:
            // - http://linux.die.net/man/2/writev
            return false;
        }
        size += len;
        ++count;

        if (ADDRESS_SIZE == 8) {
            // 64bit
            putLong(baseOffset, addr + offset);
            putLong(lengthOffset, len);
        } else {
            assert ADDRESS_SIZE == 4;
            putInt(baseOffset, (int) addr + offset);
            putInt(lengthOffset, len);
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
     * Set the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(ByteBuf)}.
     * <p>
     * This will not impact the existing state of the {@link IovArray}, and only applies to subsequent calls to
     * {@link #add(ByteBuf)}.
     * <p>
     * In order to ensure some progress is made at least one {@link ByteBuf} will be accepted even if it's size exceeds
     * this value.
     * @param maxBytes the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(ByteBuf)}.
     */
    public void maxBytes(long maxBytes) {
        this.maxBytes = min(SSIZE_MAX, checkPositive(maxBytes, "maxBytes"));
    }

    /**
     * Get the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(ByteBuf)}.
     * @return the maximum amount of bytes that can be added to this {@link IovArray} via {@link #add(ByteBuf)}.
     */
    public long maxBytes() {
        return maxBytes;
    }

    /**
     * Returns the {@code memoryAddress} for the given {@code offset}.
     */
    public long memoryAddress(int offset) {
        return memoryAddress + IOV_SIZE * offset;
    }

    /**
     * Release the {@link IovArray}. Once release further using of it may crash the JVM!
     */
    public void release() {
        freeMemory(memoryAddress);
    }

    @Override
    public boolean processMessage(Object msg) throws Exception {
        return (msg instanceof ByteBuf) && add((ByteBuf) msg);
    }
}
