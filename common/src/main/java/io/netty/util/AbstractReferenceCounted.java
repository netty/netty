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
    public ReferenceCounted retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, 1);
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
    public final boolean release() {
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
    public final boolean release(int decrement) {
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

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
