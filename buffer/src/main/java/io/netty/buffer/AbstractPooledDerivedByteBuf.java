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
abstract class AbstractPooledDerivedByteBuf<T> extends AbstractByteBuf {

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

        this.buffer = buffer;
        maxCapacity(maxCapacity);

        setIndex(readerIndex, writerIndex);

        @SuppressWarnings("unchecked")
        final U castThis = (U) this;

        return castThis;
    }

    @Override
    public final int refCnt() {
        final AbstractByteBuf unwrapped = unwrap();
        return unwrapped != null ? unwrapped.refCnt() : 0;
    }

    @Override
    public final ByteBuf retain() {
        unwrap().retain();
        return this;
    }

    @Override
    public final ByteBuf retain(int increment) {
        unwrap().retain(increment);
        return this;
    }

    @Override
    public final ByteBuf touch() {
        return this;
    }

    @Override
    public final ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public final boolean release() {
        final boolean deallocated = unwrap().release();
        if (deallocated) {
            recycle();
        }
        return deallocated;
    }

    @Override
    public final boolean release(int decrement) {
        final boolean deallocated = unwrap().release(decrement);
        if (deallocated) {
            recycle();
        }
        return deallocated;
    }

    private void recycle() {
        recyclerHandle.recycle(this);
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
}
