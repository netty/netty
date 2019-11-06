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

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ObjectPool.Handle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Abstract base class for derived {@link ByteBuf} implementations.
 */
abstract class AbstractPooledDerivedByteBuf extends AbstractReferenceCountedByteBuf {

    private final Handle<AbstractPooledDerivedByteBuf> recyclerHandle;
    private AbstractByteBuf rootParent;
    /**
     * Deallocations of a pooled derived buffer should always propagate through the entire chain of derived buffers.
     * This is because each pooled derived buffer maintains its own reference count and we should respect each one.
     * If deallocations cause a release of the "root parent" then then we may prematurely release the underlying
     * content before all the derived buffers have been released.
     */
    private ByteBuf parent;

    @SuppressWarnings("unchecked")
    AbstractPooledDerivedByteBuf(Handle<? extends AbstractPooledDerivedByteBuf> recyclerHandle) {
        super(0);
        this.recyclerHandle = (Handle<AbstractPooledDerivedByteBuf>) recyclerHandle;
    }

    // Called from within SimpleLeakAwareByteBuf and AdvancedLeakAwareByteBuf.
    final void parent(ByteBuf newParent) {
        assert newParent instanceof SimpleLeakAwareByteBuf;
        parent = newParent;
    }

    @Override
    public final AbstractByteBuf unwrap() {
        return rootParent;
    }

    final <U extends AbstractPooledDerivedByteBuf> U init(
            AbstractByteBuf unwrapped, ByteBuf wrapped, int readerIndex, int writerIndex, int maxCapacity) {
        wrapped.retain(); // Retain up front to ensure the parent is accessible before doing more work.
        parent = wrapped;
        rootParent = unwrapped;

        try {
            maxCapacity(maxCapacity);
            setIndex0(readerIndex, writerIndex); // It is assumed the bounds checking is done by the caller.
            resetRefCnt();

            @SuppressWarnings("unchecked")
            final U castThis = (U) this;
            wrapped = null;
            return castThis;
        } finally {
            if (wrapped != null) {
                parent = rootParent = null;
                wrapped.release();
            }
        }
    }

    @Override
    protected final void deallocate() {
        // We need to first store a reference to the parent before recycle this instance. This is needed as
        // otherwise it is possible that the same AbstractPooledDerivedByteBuf is again obtained and init(...) is
        // called before we actually have a chance to call release(). This leads to call release() on the wrong parent.
        ByteBuf parent = this.parent;
        recyclerHandle.recycle(this);
        parent.release();
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
    public boolean isContiguous() {
        return unwrap().isContiguous();
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

    @Override
    public ByteBuf slice(int index, int length) {
        ensureAccessible();
        // All reference count methods should be inherited from this object (this is the "parent").
        return new PooledNonRetainedSlicedByteBuf(this, unwrap(), index, length);
    }

    final ByteBuf duplicate0() {
        ensureAccessible();
        // All reference count methods should be inherited from this object (this is the "parent").
        return new PooledNonRetainedDuplicateByteBuf(this, unwrap());
    }

    private static final class PooledNonRetainedDuplicateByteBuf extends UnpooledDuplicatedByteBuf {
        private final ReferenceCounted referenceCountDelegate;

        PooledNonRetainedDuplicateByteBuf(ReferenceCounted referenceCountDelegate, AbstractByteBuf buffer) {
            super(buffer);
            this.referenceCountDelegate = referenceCountDelegate;
        }

        @Override
        int refCnt0() {
            return referenceCountDelegate.refCnt();
        }

        @Override
        ByteBuf retain0() {
            referenceCountDelegate.retain();
            return this;
        }

        @Override
        ByteBuf retain0(int increment) {
            referenceCountDelegate.retain(increment);
            return this;
        }

        @Override
        ByteBuf touch0() {
            referenceCountDelegate.touch();
            return this;
        }

        @Override
        ByteBuf touch0(Object hint) {
            referenceCountDelegate.touch(hint);
            return this;
        }

        @Override
        boolean release0() {
            return referenceCountDelegate.release();
        }

        @Override
        boolean release0(int decrement) {
            return referenceCountDelegate.release(decrement);
        }

        @Override
        public ByteBuf duplicate() {
            ensureAccessible();
            return new PooledNonRetainedDuplicateByteBuf(referenceCountDelegate, this);
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return PooledDuplicatedByteBuf.newInstance(unwrap(), this, readerIndex(), writerIndex());
        }

        @Override
        public ByteBuf slice(int index, int length) {
            checkIndex(index, length);
            return new PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap(), index, length);
        }

        @Override
        public ByteBuf retainedSlice() {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(readerIndex(), capacity());
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return PooledSlicedByteBuf.newInstance(unwrap(), this, index, length);
        }
    }

    private static final class PooledNonRetainedSlicedByteBuf extends UnpooledSlicedByteBuf {
        private final ReferenceCounted referenceCountDelegate;

        PooledNonRetainedSlicedByteBuf(ReferenceCounted referenceCountDelegate,
                                       AbstractByteBuf buffer, int index, int length) {
            super(buffer, index, length);
            this.referenceCountDelegate = referenceCountDelegate;
        }

        @Override
        int refCnt0() {
            return referenceCountDelegate.refCnt();
        }

        @Override
        ByteBuf retain0() {
            referenceCountDelegate.retain();
            return this;
        }

        @Override
        ByteBuf retain0(int increment) {
            referenceCountDelegate.retain(increment);
            return this;
        }

        @Override
        ByteBuf touch0() {
            referenceCountDelegate.touch();
            return this;
        }

        @Override
        ByteBuf touch0(Object hint) {
            referenceCountDelegate.touch(hint);
            return this;
        }

        @Override
        boolean release0() {
            return referenceCountDelegate.release();
        }

        @Override
        boolean release0(int decrement) {
            return referenceCountDelegate.release(decrement);
        }

        @Override
        public ByteBuf duplicate() {
            ensureAccessible();
            return new PooledNonRetainedDuplicateByteBuf(referenceCountDelegate, unwrap())
                    .setIndex(idx(readerIndex()), idx(writerIndex()));
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return PooledDuplicatedByteBuf.newInstance(unwrap(), this, idx(readerIndex()), idx(writerIndex()));
        }

        @Override
        public ByteBuf slice(int index, int length) {
            checkIndex(index, length);
            return new PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap(), idx(index), length);
        }

        @Override
        public ByteBuf retainedSlice() {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(0, capacity());
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return PooledSlicedByteBuf.newInstance(unwrap(), this, idx(index), length);
        }
    }
}
