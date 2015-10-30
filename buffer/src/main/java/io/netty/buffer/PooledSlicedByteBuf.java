/*
 * Copyright 2015 The Netty Project
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.Recycler;
import io.netty.util.internal.PlatformDependent;

final class PooledSlicedByteBuf extends SlicedAbstractByteBuf {

    private static final AtomicIntegerFieldUpdater<PooledSlicedByteBuf> refCntUpdater;

    static {
        AtomicIntegerFieldUpdater<PooledSlicedByteBuf> updater = PlatformDependent
                .newAtomicIntegerFieldUpdater(PooledSlicedByteBuf.class, "refCnt");
        if (updater == null) {
            updater = AtomicIntegerFieldUpdater.newUpdater(PooledSlicedByteBuf.class, "refCnt");
        }
        refCntUpdater = updater;
    }

    private volatile int refCnt = 1;

    private final Recycler.Handle recyclerHandle;

    private static final Recycler<PooledSlicedByteBuf> RECYCLER = new Recycler<PooledSlicedByteBuf>() {
        @Override
        protected PooledSlicedByteBuf newObject(Handle handle) {
            return new PooledSlicedByteBuf(handle);
        }
    };

    static PooledSlicedByteBuf newInstance(ByteBuf buffer, int index, int length) {
        PooledSlicedByteBuf buf = RECYCLER.get();
        buf.init(buffer, index, length);
        buf.setRefCnt(1);
        buffer.retain();
        return buf;
    }

    private PooledSlicedByteBuf(Recycler.Handle recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

    @Override
    public ByteBuf duplicate(boolean retain) {
        return retain ? PooledDuplicatedByteBuf.newInstance(this) : duplicate();
    }

    @Override
    public ByteBuf duplicate() {
        return new DuplicatedAbstractByteBuf(this);
    }

    @Override
    public ByteBuf slice(int index, int length, boolean retain) {
        return retain ? newInstance(this, index, length) : slice(index, length);
    }

    @Override
    public ByteBuf slice(boolean retain) {
        return slice(readerIndex(), readableBytes(), retain);
    }

    @Override
    public ByteBuf slice(int index, int length) {
        checkIndex0(index, length);
        return new SlicedAbstractByteBuf(this, index, length);
    }

    @Override
    public ByteBuf readSlice(int length, boolean retain) {
        ByteBuf slice = slice(readerIndex(), length, retain);
        skipBytes(length);
        return slice;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    private void deallocate() {
        unwrap().release();
        RECYCLER.recycle(this, recyclerHandle);
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected void setRefCnt(int refCnt) {
        this.refCnt = refCnt;
    }

    @Override
    public ByteBuf retain() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, 1);
            }
            if (refCnt == Integer.MAX_VALUE) {
                throw new IllegalReferenceCountException(Integer.MAX_VALUE, 1);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + 1)) {
                break;
            }
        }
        return this;
    }

    @Override
    public ByteBuf retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, increment);
            }
            if (refCnt > Integer.MAX_VALUE - increment) {
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + increment)) {
                break;
            }
        }
        return this;
    }

    @Override
    public boolean release() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, -1);
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - 1)) {
                if (refCnt == 1) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("decrement: " + decrement + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt < decrement) {
                throw new IllegalReferenceCountException(refCnt, -decrement);
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - decrement)) {
                if (refCnt == decrement) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }
}
