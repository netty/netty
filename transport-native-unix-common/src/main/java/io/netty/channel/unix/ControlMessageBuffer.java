/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.internal.CleanableDirectBuffer;
import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;

/**
 * Native memory that is used to store the control messages of one or multiple datagrams, so these can be passed to
 * {@code sendmsg(...)} / {@code recvmsg(...)} (and their friends) via {@code msg_control}.
 * <p>
 * Space is reserved via {@link #reserve(int)} until {@link #clear()} is called. The memory is allocated lazily and
 * grows as needed. Growing is only possible while nothing is reserved, as otherwise the memory addresses which were
 * handed out before would no longer be valid.
 * <p>
 * <strong>Internal usage only!</strong>
 */
public final class ControlMessageBuffer {

    private static final int MIN_CAPACITY = 1024;
    // The maximal capacity we grow to by ourselves if we run out of space when reserving for multiple datagrams.
    private static final int MAX_GROW_CAPACITY = 64 * 1024;

    private CleanableDirectBuffer cleanable;
    private ByteBuffer buffer;
    private long memoryAddress;
    private int length;
    private boolean overflow;

    /**
     * Reserve the given number of bytes.
     *
     * @param length    the number of bytes.
     * @return          the offset of the reserved space or {@code -1} if there is not enough space left. In this case
     *                  {@link #clear()} needs to be called before trying again, which will also grow the memory if
     *                  needed.
     */
    public int reserve(int length) {
        ObjectUtil.checkPositiveOrZero(length, "length");
        if (this.length + length > capacity()) {
            if (this.length != 0) {
                overflow = true;
                return -1;
            }
            grow(length);
        }
        int offset = this.length;
        this.length += length;
        return offset;
    }

    /**
     * Ensure that at least the given number of bytes can be reserved. This must only be called if nothing is
     * reserved at the moment.
     */
    public void ensureCapacity(int capacity) {
        if (length != 0) {
            throw new IllegalStateException("Space is reserved");
        }
        if (capacity > capacity()) {
            grow(capacity);
        }
    }

    /**
     * Release all the space that was reserved.
     */
    public void clear() {
        length = 0;
        if (overflow) {
            overflow = false;
            int capacity = capacity();
            if (capacity < MAX_GROW_CAPACITY) {
                // We did run out of space before, let's grow so this hopefully not happens again.
                grow(Math.min(capacity << 1, MAX_GROW_CAPACITY));
            }
        }
    }

    /**
     * Returns the {@link ByteBuffer} which should be used to read and write the control messages. The
     * {@link ByteBuffer} uses the native byte order and must only be accessed via the absolute methods.
     */
    public ByteBuffer buffer() {
        return buffer;
    }

    /**
     * Returns the memory address for the given offset.
     */
    public long memoryAddress(int offset) {
        return memoryAddress + offset;
    }

    /**
     * Release the native memory.
     */
    public void release() {
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
            buffer = null;
            memoryAddress = 0;
        }
        length = 0;
        overflow = false;
    }

    private int capacity() {
        return buffer == null ? 0 : buffer.capacity();
    }

    private void grow(int minCapacity) {
        assert length == 0;
        int capacity = Math.max(MIN_CAPACITY, minCapacity);
        if (capacity <= capacity()) {
            return;
        }
        release();
        cleanable = Buffer.allocateDirectBufferWithNativeOrder(capacity);
        buffer = cleanable.buffer();
        memoryAddress = Buffer.memoryAddress(buffer);
    }
}
