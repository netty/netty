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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.lang.Math.max;

/**
 * Abstract base class for classes wants to implement {@link ReferenceCounted}.
 */
public abstract class AbstractReferenceCounted implements ReferenceCounted {

    private static final AtomicIntegerFieldUpdater<AbstractReferenceCounted> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCounted.class, "refCnt");

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int refCnt = 1;

    @Override
    public final int refCnt() {
        // Never return anything smaller then 0 to give the user a consistent few all the time.
        return max(0, refCntUpdater.get(this));
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        refCntUpdater.set(this, refCnt);
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
        int oldRef = refCntUpdater.get(this);
        if (oldRef <= 0 || oldRef + increment < 0) {
            // Either already released or we overflow.
            throw newReferenceCountException(oldRef, increment);
        }
        int ref = refCntUpdater.addAndGet(this, increment);
        if (ref > increment) {
            // Most likely code-path to hit.
            return this;
        }
        if (ref <= 0) {
            // Overflow happened, trying to recover
            refCntUpdater.addAndGet(this, -increment);
            throw newReferenceCountException(ref, increment);
        }
        // Set the reference count back to 0 which signals this reference was deallocated already.
        refCntUpdater.set(this, 0);
        throw newReferenceCountException(ref, increment);
    }

    @Override
    public ReferenceCounted touch() {
        return touch(null);
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
        int oldRef = refCntUpdater.getAndAdd(this, -decrement);
        if (oldRef == decrement) {
            // Most likely code-path to hit.
            deallocate();
            return true;
        }

        if (oldRef < decrement || oldRef - decrement > oldRef) {
            // Ensure we don't over-release, and avoid underflow.
            // Also set the reference count back to 0 as this signals we already completely released it.
            refCntUpdater.set(this, 0);
            throw newReferenceCountException(oldRef, -decrement);
        }
        return false;
    }

    private static IllegalReferenceCountException newReferenceCountException(int oldCnt, int cnt) {
        return new IllegalReferenceCountException(max(0, oldCnt), cnt);
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
