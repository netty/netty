/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;
// Tony: 这是netty自己的一种buffer释放机制。概念上和GC类似
/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {// Tony: 这个要看，实现了引用计数
    private static final long REFCNT_FIELD_OFFSET;
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    // even => "real" refcount is (refCnt >>> 1); odd => "real" refcount is 0
    @SuppressWarnings("unused")
    private volatile int refCnt = 2;

    static {
        long refCntFieldOffset = -1;
        try {
            if (PlatformDependent.hasUnsafe()) {
                refCntFieldOffset = PlatformDependent.objectFieldOffset(
                        AbstractReferenceCountedByteBuf.class.getDeclaredField("refCnt"));
            }
        } catch (Throwable ignore) {
            refCntFieldOffset = -1;
        }

        REFCNT_FIELD_OFFSET = refCntFieldOffset;
    }

    private static int realRefCnt(int rawCnt) {
        return (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
    }

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    private int nonVolatileRawCnt() {
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        return REFCNT_FIELD_OFFSET != -1 ? PlatformDependent.getInt(this, REFCNT_FIELD_OFFSET)
                : refCntUpdater.get(this);
    }

    @Override
    int internalRefCnt() {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return realRefCnt(nonVolatileRawCnt());
    }
    /** Tony: 返回该对象的引用计数。如果为0，则表示该对象已解除分配，是时候释放了。 */
    @Override
    public int refCnt() {
        return realRefCnt(refCntUpdater.get(this));
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int newRefCnt) {
        refCntUpdater.set(this, newRefCnt << 1); // overflow OK here
    }
    /** Tony: 引用计数+1 */
    @Override
    public ByteBuf retain() {
        return retain0(1);
    }

    @Override
    public ByteBuf retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    private ByteBuf retain0(final int increment) {
        // all changes to the raw count are 2x the "real" change
        int adjustedIncrement = increment << 1; // overflow OK here
        int oldRef = refCntUpdater.getAndAdd(this, adjustedIncrement);
        if ((oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + adjustedIncrement >= 0)
                || (oldRef >= 0 && oldRef + adjustedIncrement < oldRef)) {
            // overflow case
            refCntUpdater.getAndAdd(this, -adjustedIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }
    /** Tony: 将引用计数减少1，并在引用计数达到0时释放该对象。 */
    @Override
    public boolean release() {
        return release0(1);
    }

    @Override
    public boolean release(int decrement) {
        return release0(checkPositive(decrement, "decrement"));
    }

    private boolean release0(int decrement) {
        int rawCnt = nonVolatileRawCnt(), realCnt = toLiveRealCnt(rawCnt, decrement);
        if (decrement == realCnt) {// Tony: 如果要减的数量和目前的引用数量相等，代表要释放了
            if (refCntUpdater.compareAndSet(this, rawCnt, 1)) {// Tony: cas无锁机制，保证线程安全
                deallocate();// Tony: 执行释放
                return true;
            }
            return retryRelease0(decrement);// Tony: 自旋锁重试
        }
        return releaseNonFinal0(decrement, rawCnt, realCnt);
    }

    private boolean releaseNonFinal0(int decrement, int rawCnt, int realCnt) {
        if (decrement < realCnt
                // all changes to the raw count are 2x the "real" change
                && refCntUpdater.compareAndSet(this, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        return retryRelease0(decrement);
    }

    private boolean retryRelease0(int decrement) {
        for (;;) {// Tony: 自旋锁
            int rawCnt = refCntUpdater.get(this), realCnt = toLiveRealCnt(rawCnt, decrement);
            if (decrement == realCnt) {
                if (refCntUpdater.compareAndSet(this, rawCnt, 1)) {
                    deallocate();
                    return true;
                }
            } else if (decrement < realCnt) {
                // all changes to the raw count are 2x the "real" change
                if (refCntUpdater.compareAndSet(this, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }
            Thread.yield(); // this benefits throughput under high contention
        }
    }

    /**
     * Like {@link #realRefCnt(int)} but throws if refCnt == 0
     */
    private static int toLiveRealCnt(int rawCnt, int decrement) {
        if ((rawCnt & 1) == 0) {
            return rawCnt >>> 1;
        }
        // odd rawCnt => already deallocated
        throw new IllegalReferenceCountException(0, -decrement);
    }

    /**
     * Called once {@link #refCnt()} is equals 0.// Tony: 执行释放，不同的ByteBuf类型，回收的方式不同
     */
    protected abstract void deallocate();
}
