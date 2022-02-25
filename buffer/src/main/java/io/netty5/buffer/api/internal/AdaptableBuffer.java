/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.AllocatorControl;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.buffer.api.adaptor.BufferIntegratable;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty5.util.IllegalReferenceCountException;
import io.netty5.util.ReferenceCounted;

public abstract class AdaptableBuffer<T extends ResourceSupport<Buffer, T>>
        extends ResourceSupport<Buffer, T> implements BufferIntegratable, Buffer {

    private volatile ByteBufAdaptor adaptor;
    protected final AllocatorControl control;

    protected AdaptableBuffer(Drop<T> drop, AllocatorControl control) {
        super(drop);
        this.control = control;
    }

    public ByteBuf initialise(ByteBufAllocatorAdaptor alloc, int maxCapacity) {
        return new ByteBufAdaptor(alloc, this, maxCapacity);
    }

    @Override
    public ByteBuf asByteBuf() {
        ByteBufAdaptor bba = adaptor;
        if (bba == null) {
            BufferAllocator allocator = control.getAllocator();
            final BufferAllocator onHeap;
            final BufferAllocator offHeap;
            if (allocator.getAllocationType() == StandardAllocationTypes.ON_HEAP) {
                onHeap = allocator;
                offHeap = allocator.isPooling() ? BufferAllocator.offHeapPooled() : BufferAllocator.offHeapUnpooled();
            } else {
                onHeap = allocator.isPooling() ? BufferAllocator.onHeapPooled() : BufferAllocator.onHeapUnpooled();
                offHeap = allocator;
            }
            ByteBufAllocatorAdaptor alloc = new ByteBufAllocatorAdaptor(onHeap, offHeap);
            return adaptor = new ByteBufAdaptor(alloc, this, Integer.MAX_VALUE);
        }
        return bba;
    }

    @Override
    public int refCnt() {
        return isAccessible()? 1 + countBorrows() : 0;
    }

    @Override
    public ReferenceCounted retain() {
        return retain(1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        for (int i = 0; i < increment; i++) {
            acquire();
        }
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        return this;
    }

    @Override
    public AdaptableBuffer<T> touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return release(1);
    }

    @Override
    public boolean release(int decrement) {
        int refCount = 1 + countBorrows();
        if (!isAccessible() || decrement > refCount) {
            throw attachTrace(new IllegalReferenceCountException(refCount, -decrement));
        }
        for (int i = 0; i < decrement; i++) {
            try {
                close();
            } catch (RuntimeException e) {
                throw attachTrace(new IllegalReferenceCountException(e));
            }
        }
        return !isAccessible();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Buffer && Statics.equals(this, (Buffer) o);
    }

    @Override
    public int hashCode() {
        return Statics.hashCode(this);
    }
}
