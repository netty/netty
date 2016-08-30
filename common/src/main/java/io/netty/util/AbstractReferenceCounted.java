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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * Abstract base class for classes wants to implement {@link ReferenceCounted}.
 */
public abstract class AbstractReferenceCounted implements ReferenceCounted {

    private static final AtomicIntegerFieldUpdater<AbstractReferenceCounted> refCntUpdater;

    static {
        AtomicIntegerFieldUpdater<AbstractReferenceCounted> updater =
                PlatformDependent.newAtomicIntegerFieldUpdater(AbstractReferenceCounted.class, "refCnt");
        if (updater == null) {
            updater = AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCounted.class, "refCnt");
        }
        refCntUpdater = updater;
    }

    private volatile int refCnt = 1;

    @Override
    public final int refCnt() {
        return refCnt;
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        this.refCnt = refCnt;
    }

    @Override
    public ReferenceCounted retain() {
        return retain0(1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    private ReferenceCounted retain0(int increment) {
        for (;;) {
            int refCnt = this.refCnt;
            final int nextCnt = refCnt + increment;

            // Ensure we not resurrect (which means the refCnt was 0) and also that we encountered an overflow.
            if (nextCnt <= increment) {
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, nextCnt)) {
                break;
            }
        }
        return this;
    }

    @Override
    public boolean release() {
        return release0(1);
    }

    @Override
    public boolean release(int decrement) {
        return release0(checkPositive(decrement, "decrement"));
    }

    private boolean release0(int decrement) {
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

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
