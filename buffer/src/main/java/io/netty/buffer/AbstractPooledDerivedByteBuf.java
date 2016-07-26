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
abstract class AbstractPooledDerivedByteBuf extends AbstractReferenceCountedByteBuf {

    private final Handle<AbstractPooledDerivedByteBuf> recyclerHandle;
    private AbstractByteBuf buffer;

    @SuppressWarnings("unchecked")
    AbstractPooledDerivedByteBuf(Handle<? extends AbstractPooledDerivedByteBuf> recyclerHandle) {
        super(0);
        this.recyclerHandle = (Handle<AbstractPooledDerivedByteBuf>) recyclerHandle;
    }

    @Override
    public final AbstractByteBuf unwrap() {
        return buffer;
    }

    final <U extends AbstractPooledDerivedByteBuf> U init(
            AbstractByteBuf unwrapped, ByteBuf wrapped, int readerIndex, int writerIndex, int maxCapacity) {

        wrapped.retain(); // Retain up front to ensure the wrapped buffer is accessible before doing more work.
        this.buffer = unwrapped;

        try {
            maxCapacity(maxCapacity);
            setIndex0(readerIndex, writerIndex); // It is assumed the bounds checking is done by the caller.
            setRefCnt(1);

            @SuppressWarnings("unchecked")
            final U castThis = (U) this;
            wrapped = null;
            return castThis;
        } finally {
            if (wrapped != null) {
                this.buffer = null;
                wrapped.release();
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
    public final ByteBuf retainedSlice() {
        final int index = readerIndex();
        return retainedSlice(index, writerIndex() - index);
    }
}
