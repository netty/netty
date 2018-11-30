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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.PlatformDependent;

/**
 * Common logic for {@link ReferenceCounted} implementations
 */
public final class ReferenceCountUpdater<T extends ReferenceCounted> {

    // For updated int field:
    //   Even => "real" refcount is (refCnt >>> 1)
    //   Odd  => "real" refcount is 0

    private final long refCntFieldOffset;
    private final AtomicIntegerFieldUpdater<T> refCntUpdater;

    // Unfortunately the owning class must create the AtomicIntegerFieldUpdater
    private ReferenceCountUpdater(Class<T> clz, String fieldName, AtomicIntegerFieldUpdater<T> fieldUpdater) {
        long fieldOffset = -1;
        try {
            if (PlatformDependent.hasUnsafe()) {
                fieldOffset = PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        refCntFieldOffset = fieldOffset;
        refCntUpdater = fieldUpdater;
    }

    public static <T extends ReferenceCounted> ReferenceCountUpdater<T> newUpdater(Class<T> clz,
            String fieldName, AtomicIntegerFieldUpdater<T> fieldUpdater) {
        return new ReferenceCountUpdater<T>(clz, fieldName, fieldUpdater);
    }

    public int initialValue() {
        return 2;
    }

    private static int realRefCnt(int rawCnt) {
        return (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
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

    private int nonVolatileRawCnt(T instance) {
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        return refCntFieldOffset != -1 ? PlatformDependent.getInt(instance, refCntFieldOffset)
                : refCntUpdater.get(instance);
    }

    public int refCnt(T instance) {
        return realRefCnt(refCntUpdater.get(instance));
    }

    public int nonVolatileRefCnt(T instance) {
        return realRefCnt(nonVolatileRawCnt(instance));
    }

    /**
     * An unsafe operation that sets the reference count directly
     */
    public void setRefCnt(T instance, int refCnt) {
        refCntUpdater.set(instance, refCnt > 0 ? refCnt << 1 : 1); // overflow OK here
    }

    /**
     * Resets the reference count to 1
     */
    public void resetRefCnt(T instance) {
        refCntUpdater.set(instance, initialValue());
    }

    public T retain(T instance) {
        return retain0(instance, 1, 2);
    }

    public T retain(T instance, int increment) {
        // all changes to the raw count are 2x the "real" change - overflow is OK
        int rawIncrement = checkPositive(increment, "increment") << 1;
        return retain0(instance, increment, rawIncrement);
    }

    // rawIncrement == increment << 1
    private T retain0(T instance, final int increment, final int rawIncrement) {
        int oldRef = refCntUpdater.getAndAdd(instance, rawIncrement);
        if ((oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + rawIncrement >= 0)
                || (oldRef >= 0 && oldRef + rawIncrement < oldRef)) {
            // overflow case
            refCntUpdater.getAndAdd(instance, -rawIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return instance;
    }

    public boolean release(T instance) {
        int rawCnt = nonVolatileRawCnt(instance);
        return rawCnt == 2 ? tryFinalRelease0(instance, 2) || retryRelease0(instance, 1)
                : nonFinalRelease0(instance, 1, rawCnt, toLiveRealCnt(rawCnt, 1));
    }

    public boolean release(T instance, int decrement) {
        int rawCnt = nonVolatileRawCnt(instance);
        int realCnt = toLiveRealCnt(rawCnt, checkPositive(decrement, "decrement"));
        return decrement == realCnt ? tryFinalRelease0(instance, rawCnt) || retryRelease0(instance, decrement)
                : nonFinalRelease0(instance, decrement, rawCnt, realCnt);
    }

    private boolean tryFinalRelease0(T instance, int expectRawCnt) {
        return refCntUpdater.compareAndSet(instance, expectRawCnt, 1); // any odd number will work
    }

    private boolean nonFinalRelease0(T instance, int decrement, int rawCnt, int realCnt) {
        if (decrement < realCnt
                // all changes to the raw count are 2x the "real" change - overflow is OK
                && refCntUpdater.compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        return retryRelease0(instance, decrement);
    }

    private boolean retryRelease0(T instance, int decrement) {
        for (;;) {
            int rawCnt = refCntUpdater.get(instance), realCnt = toLiveRealCnt(rawCnt, decrement);
            if (decrement == realCnt) {
                if (tryFinalRelease0(instance, rawCnt)) {
                    return true;
                }
            } else if (decrement < realCnt) {
                // all changes to the raw count are 2x the "real" change
                if (refCntUpdater.compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }
            Thread.yield(); // this benefits throughput under high contention
        }
    }
}
