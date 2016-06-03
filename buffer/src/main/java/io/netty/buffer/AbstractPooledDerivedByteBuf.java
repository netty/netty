/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.Recycler.Handle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Abstract base class for derived {@link ByteBuf} implementations.
 */
abstract class AbstractPooledDerivedByteBuf<T> extends AbstractReferenceCountedByteBuf {

    private final Handle<AbstractPooledDerivedByteBuf<T>> recyclerHandle;
    private AbstractByteBuf buffer;

    @SuppressWarnings("unchecked")
    AbstractPooledDerivedByteBuf(Handle<? extends AbstractPooledDerivedByteBuf<T>> recyclerHandle) {
        super(0);
        this.recyclerHandle = (Handle<AbstractPooledDerivedByteBuf<T>>) recyclerHandle;
    }

    @Override
    public final AbstractByteBuf unwrap() {
        return buffer;
    }

    final <U extends AbstractPooledDerivedByteBuf<T>> U init(
            AbstractByteBuf buffer, int readerIndex, int writerIndex, int maxCapacity) {

        buffer.retain();
        this.buffer = buffer;

        boolean success = false;
        try {
            maxCapacity(maxCapacity);
            setIndex(readerIndex, writerIndex);
            setRefCnt(1);

            @SuppressWarnings("unchecked")
            final U castThis = (U) this;
            success = true;
            return castThis;
        } finally {
            if (!success) {
                this.buffer = null;
                buffer.release();
            }
        }
    }

    @Override
    protected final void deallocate() {
        // We need to first store a reference to the wrapped buffer before recycle this instance. This is needed as
        // otherwise it is possible that the same AbstractPooledDerivedByteBuf is again obtained and init(...) is
        // called before we actually have a chance to call release(). This leads to call release() on the wrong buffer.
        ByteBuf wrapped = unwrap();
        recyclerHandle.recycle(this);
        wrapped.release();
    }

    @Override
    public final ByteBufAllocator alloc() {
        return unwrap().alloc();
    }

    @Override
    @Deprecated
    public final ByteOrder order() {
        return unwrap().order();
    }

    @Override
    public boolean isReadOnly() {
        return unwrap().isReadOnly();
    }

    @Override
    public final boolean isDirect() {
        return unwrap().isDirect();
    }

    @Override
    public boolean hasArray() {
        return unwrap().hasArray();
    }

    @Override
    public byte[] array() {
        return unwrap().array();
    }

    @Override
    public boolean hasMemoryAddress() {
        return unwrap().hasMemoryAddress();
    }

    @Override
    public final int nioBufferCount() {
        return unwrap().nioBufferCount();
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        return nioBuffer(index, length);
    }

    @Override
    public final ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(this, readerIndex(), writerIndex());
    }

    @Override
    public final ByteBuf retainedSlice() {
        final int index = readerIndex();
        return retainedSlice(index, writerIndex() - index);
    }

    @Override
    public final ByteBuf retainedSlice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(this, index, length, index);
    }
}
