/*
 * Copyright 2012 The Netty Project
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

package io.netty.buffer;

import io.netty.util.internal.ObjectPool.Handle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {

    private final Handle<PooledByteBuf<T>> recyclerHandle;

    protected PoolChunk<T> chunk;
    protected long handle;
    protected T memory;
    protected int offset;
    protected int length;
    int maxLength;
    PoolThreadCache cache;
    ByteBuffer tmpNioBuf;
    private ByteBufAllocator allocator;

    @SuppressWarnings("unchecked")
    protected PooledByteBuf(Handle<? extends PooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (Handle<PooledByteBuf<T>>) recyclerHandle;
    }

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, null, 0, 0, length, length, null);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;
        assert !PoolChunk.isSubpage(handle) || chunk.arena.size2SizeIdx(maxLength) <= chunk.arena.smallMaxSizeIdx:
                "Allocated small sub-page handle for a buffer size that isn't \"small.\"";

        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        memory = chunk.memory;
        tmpNioBuf = nioBuffer;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    /**
     * Method must be called before reuse this {@link PooledByteBufAllocator}
     */
    final void reuse(int maxCapacity) {
        maxCapacity(maxCapacity);
        resetRefCnt();
        setIndex0(0, 0);
        discardMarks();
    }

    @Override
    public final int capacity() {
        return length;
    }

    @Override
    public int maxFastWritableBytes() {
        return Math.min(maxLength, maxCapacity()) - writerIndex;
    }

    @Override
    public final ByteBuf capacity(int newCapacity) {
        if (newCapacity == length) {
            ensureAccessible();
            return this;
        }
        checkNewCapacity(newCapacity);
        if (!chunk.unpooled) {
            // If the request capacity does not require reallocation, just update the length of the memory.
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity > maxLength >>> 1 &&
                    (maxLength > 512 || newCapacity > maxLength - 16)) {
                // here newCapacity < length
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }
        }

        // Reallocation required.
        chunk.decrementPinnedMemory(maxLength);
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public final ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public final ByteBuf unwrap() {
        return null;
    }

    @Override
    public final ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(this, this, readerIndex(), writerIndex());
    }

    @Override
    public final ByteBuf retainedSlice() {
        final int index = readerIndex();
        return retainedSlice(index, writerIndex() - index);
    }

    @Override
    public final ByteBuf retainedSlice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(this, this, index, length);
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        } else {
            tmpNioBuf.clear();
        }
        return tmpNioBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.decrementPinnedMemory(maxLength);
            chunk.arena.free(chunk, tmpNioBuf, handle, maxLength, cache);
            tmpNioBuf = null;
            chunk = null;
            cache = null;
            recycle();
        }
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    protected final int idx(int index) {
        return offset + index;
    }

    final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        index = idx(index);
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        buffer.limit(index + length).position(index);
        return buffer;
    }

    ByteBuffer duplicateInternalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length, true);
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length, false);
    }

    @Override
    public final int nioBufferCount() {
        return 1;
    }

    @Override
    public final ByteBuffer nioBuffer(int index, int length) {
        return duplicateInternalNioBuffer(index, length).slice();
    }

    @Override
    public final ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public final boolean isContiguous() {
        return true;
    }

    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length));
    }

    @Override
    public final int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false));
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length), position);
    }

    @Override
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false), position);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public final int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length), position);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }
}
