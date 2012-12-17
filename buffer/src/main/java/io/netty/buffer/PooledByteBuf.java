/*
 * Copyright 2012 The Netty Project
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

package io.netty.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Queue;

abstract class PooledByteBuf<T> extends AbstractByteBuf {

    protected PoolChunk<T> chunk;
    protected long handle;
    protected T memory;
    protected int offset;
    protected int length;

    private ByteBuffer tmpNioBuf;
    private Queue<Allocation<T>> suspendedDeallocations;

    protected PooledByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    void init(PoolChunk<T> chunk, long handle, T memory, int offset, int length) {
        assert handle >= 0;
        assert memory != null;

        this.chunk = chunk;
        this.handle = handle;
        this.memory = memory;
        this.offset = offset;
        this.length = length;
        setIndex(0, 0);
        tmpNioBuf = null;
    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        assert !isFreed();

        if (suspendedDeallocations == null) {
            chunk.arena.reallocate(this, newCapacity, true);
        } else {
            Allocation<T> old = new Allocation<T>(chunk, handle);
            chunk.arena.reallocate(this, newCapacity, false);
            suspendedDeallocations.add(old);
        }
        return this;
    }

    @Override
    public ByteBufAllocator alloc() {
        return chunk.arena.parent;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    protected ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        }
        return tmpNioBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    public ByteBuf suspendIntermediaryDeallocations() {
        assert !isFreed();
        if (suspendedDeallocations == null) {
            suspendedDeallocations = new ArrayDeque<Allocation<T>>(2);
        }
        return this;
    }

    @Override
    public ByteBuf resumeIntermediaryDeallocations() {
        if (suspendedDeallocations == null) {
            return this;
        }

        Queue<Allocation<T>> suspendedDeallocations = this.suspendedDeallocations;
        this.suspendedDeallocations = null;

        if (suspendedDeallocations.isEmpty()) {
            return this;
        }

        for (Allocation<T> a: suspendedDeallocations) {
            chunk.arena.free(a.chunk, a.handle);
        }
        return this;
    }

    @Override
    public boolean isFreed() {
        return handle < 0;
    }

    @Override
    public void free() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            resumeIntermediaryDeallocations();
            chunk.arena.free(chunk, handle);
        }
    }

    protected int idx(int index) {
        return offset + index;
    }

    protected void checkIndex(int index) {
        assert !isFreed();
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d (expected: range(0, %d))", index, length));
        }
    }

    protected void checkIndex(int index, int fieldLength) {
        assert !isFreed();
        if (index < 0 || index > length - fieldLength) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: range(0, %d))", index, fieldLength, length));
        }
    }

    private static final class Allocation<T> {
        final PoolChunk<T> chunk;
        final long handle;

        Allocation(PoolChunk<T> chunk, long handle) {
            this.chunk = chunk;
            this.handle = handle;
        }
    }
}
