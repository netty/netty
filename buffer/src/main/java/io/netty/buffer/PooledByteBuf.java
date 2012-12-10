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

import io.netty.buffer.ByteBuf.Unsafe;
import io.netty.buffer.PooledByteBufAllocator.Arena;
import io.netty.buffer.PooledByteBufAllocator.Chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Queue;

abstract class PooledByteBuf<T> extends AbstractByteBuf implements Unsafe {

    protected final Arena<T> arena;
    protected Chunk<T> chunk;
    protected long handle;
    protected T memory;
    protected int offset;
    protected int length;

    private ByteBuffer tmpNioBuf;
    private Queue<Allocation<T>> suspendedDeallocations;

    protected PooledByteBuf(Arena<T> arena, int maxCapacity) {
        super(maxCapacity);
        this.arena = arena;
    }

    void init(Chunk<T> chunk, long handle, T memory, int offset, int length) {
        this.chunk = chunk;
        this.handle = handle;
        this.memory = memory;
        this.offset = offset;
        this.length = length;
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
            arena.reallocate(this, newCapacity, true);
        } else {
            Allocation<T> old = new Allocation<T>(chunk, handle);
            arena.reallocate(this, newCapacity, false);
            suspendedDeallocations.add(old);
        }
        return this;
    }

    @Override
    public ByteBufAllocator alloc() {
        return arena.parent;
    }

    @Override
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public ByteBuf unwrap() {
        return null;
    }

    @Override
    public Unsafe unsafe() {
        return this;
    }

    @Override
    public ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        }
        return tmpNioBuf;
    }

    @Override
    public ByteBuffer[] internalNioBuffers() {
        return new ByteBuffer[] { internalNioBuffer() };
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    public void discardSomeReadBytes() {
        final int readerIndex = readerIndex();
        if (readerIndex == writerIndex()) {
            discardReadBytes();
            return;
        }

        if (readerIndex > 0 && readerIndex >= capacity() >>> 1) {
            discardReadBytes();
        }
    }

    @Override
    public void suspendIntermediaryDeallocations() {
        if (suspendedDeallocations == null) {
            suspendedDeallocations = new ArrayDeque<Allocation<T>>(2);
        }
    }

    @Override
    public void resumeIntermediaryDeallocations() {
        if (suspendedDeallocations == null) {
            return;
        }

        Queue<Allocation<T>> suspendedDeallocations = this.suspendedDeallocations;
        this.suspendedDeallocations = null;

        if (suspendedDeallocations.isEmpty()) {
            return;
        }

        for (Allocation<T> a: suspendedDeallocations) {
            arena.free(a.chunk, a.handle);
        }
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
            arena.free(chunk, handle);
        }
    }

    private static final class Allocation<T> {
        final Chunk<T> chunk;
        final long handle;

        Allocation(Chunk<T> chunk, long handle) {
            this.chunk = chunk;
            this.handle = handle;
        }
    }
}
