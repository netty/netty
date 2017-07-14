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
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

import static io.netty.channel.unix.Limits.IOV_MAX;
import static io.netty.channel.unix.Limits.SSIZE_MAX;

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

    public IovArray() {
        memoryAddress = PlatformDependent.allocateMemory(CAPACITY);
    }

    public void clear() {
        count = 0;
        size = 0;
    }

    /**
     * Try to add the given {@link ByteBuf}. Returns {@code true} on success,
     * {@code false} otherwise.
     */
    public boolean add(ByteBuf buf) {
        int nioBufferCount = buf.nioBufferCount();
        if (count + nioBufferCount > IOV_MAX) {
            // No more room!
            return false;
        }

        if (nioBufferCount == 1) {
            final int len = buf.readableBytes();
            if (len == 0) {
                // No need to add an empty buffer.
                // We return true here because we want ChannelOutboundBuffer.forEachFlushedMessage() to continue
                // fetching the next buffers.
                return true;
            }

            final long addr = buf.memoryAddress();
            final int offset = buf.readerIndex();
            return add(addr, offset, len);
        } else {
            ByteBuffer[] buffers = buf.nioBuffers();
            for (ByteBuffer nioBuffer : buffers) {
                int len = nioBuffer.remaining();
                if (len == 0) {
                    // No need to add an empty buffer so just continue
                    continue;
                }
                int offset = nioBuffer.position();
                long addr = PlatformDependent.directBufferAddress(nioBuffer);

                if (!add(addr, offset, len)) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean add(long addr, int offset, int len) {
        if (len == 0) {
            // No need to add an empty buffer.
            return true;
        }

        final long baseOffset = memoryAddress(count++);
        final long lengthOffset = baseOffset + ADDRESS_SIZE;

        if (SSIZE_MAX - len < size) {
            // If the size + len will overflow an SSIZE_MAX we stop populate the IovArray. This is done as linux
            //  not allow to write more bytes then SSIZE_MAX with one writev(...) call and so will
            // return 'EINVAL', which will raise an IOException.
            //
            // See also:
            // - http://linux.die.net/man/2/writev
            return false;
        }
        size += len;

        if (ADDRESS_SIZE == 8) {
            // 64bit
            PlatformDependent.putLong(baseOffset, addr + offset);
            PlatformDependent.putLong(lengthOffset, len);
        } else {
            assert ADDRESS_SIZE == 4;
            PlatformDependent.putInt(baseOffset, (int) addr + offset);
            PlatformDependent.putInt(lengthOffset, len);
        }
        return true;
    }

    /**
     * Process the written iov entries. This will return the length of the iov entry on the given index if it is
     * smaller then the given {@code written} value. Otherwise it returns {@code -1}.
     */
    public long processWritten(int index, long written) {
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
     * Returns the {@code memoryAddress} for the given {@code offset}.
     */
    public long memoryAddress(int offset) {
        return memoryAddress + IOV_SIZE * offset;
    }

    /**
     * Release the {@link IovArray}. Once release further using of it may crash the JVM!
     */
    public void release() {
        PlatformDependent.freeMemory(memoryAddress);
    }

    @Override
    public boolean processMessage(Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            return add((ByteBuf) msg);
        }
        return false;
    }
}
