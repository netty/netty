/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.internal;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class LeftPaddedReference<T> extends LeftPadding {

    private static final long serialVersionUID = 6513142711280243198L;

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<LeftPaddedReference, Object> referentUpdater;

    static {
        @SuppressWarnings("rawtypes")
        AtomicReferenceFieldUpdater<LeftPaddedReference, Object> u;
        u = PlatformDependent.newAtomicReferenceFieldUpdater(LeftPaddedReference.class, "referent");
        if (u == null) {
            u = AtomicReferenceFieldUpdater.newUpdater(LeftPaddedReference.class, Object.class, "referent");
        }
        referentUpdater = u;
    }

    private volatile T referent; // 8-byte object field (or 4-byte + padding)

    public final T get() {
        return referent;
    }

    public final void set(T referent) {
        this.referent = referent;
    }

    public final void lazySet(T referent) {
        referentUpdater.lazySet(this, referent);
    }

    public final boolean compareAndSet(T expect, T update) {
        return referentUpdater.compareAndSet(this, expect, update);
    }

    public final boolean weakCompareAndSet(T expect, T update) {
        return referentUpdater.weakCompareAndSet(this, expect, update);
    }

    @SuppressWarnings("unchecked")
    public final T getAndSet(T referent) {
        return (T) referentUpdater.getAndSet(this, referent);
    }

    @Override
    public String toString() {
        return String.valueOf(get());
    }
}
