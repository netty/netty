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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A special {@link ThreadLocal} which is operating over a predefined array, so it always operate in O(1) when called
 * from a {@link FastThreadLocalThread}. This permits less indirection and offers a slight performance improvement,
 * so is useful when invoked frequently.
 *
 * The fast path is only possible on threads that extend FastThreadLocalThread, as this class
 * stores the necessary state. Access by any other kind of thread falls back to a regular ThreadLocal
 *
 * @param <V>
 */
public class FastThreadLocal<V> extends ThreadLocal<V> {
    static final Object EMPTY = new Object();

    private static final AtomicInteger NEXT_INDEX = new AtomicInteger(0);
    private final ThreadLocal<V> fallback = new ThreadLocal<V>() {
        @Override
        protected V initialValue() {
            return FastThreadLocal.this.initialValue();
        }
    };
    private final int index;

    public FastThreadLocal() {
        index = NEXT_INDEX.getAndIncrement();
        if (index < 0) {
            NEXT_INDEX.decrementAndGet();
            throw new IllegalStateException("Maximal number (" + Integer.MAX_VALUE + ") of FastThreadLocal exceeded");
        }
    }

    /**
     * Set the value for the current thread
     */
    @Override
    public void set(V value) {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof FastThreadLocalThread)) {
            fallback.set(value);
            return;
        }
        FastThreadLocalThread fastThread = (FastThreadLocalThread) thread;
        Object[] lookup = fastThread.lookup;
        if (index >= lookup.length) {
            lookup = fastThread.expandArray(index);
        }
        lookup[index] = value;
    }

    /**
     * Sets the value to uninitialized; a proceeding call to get() will trigger a call to initialValue()
     */
    @Override
    public void remove() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof FastThreadLocalThread)) {
            fallback.remove();
            return;
        }
        Object[] lookup = ((FastThreadLocalThread) thread).lookup;
        if (index >= lookup.length) {
            return;
        }
        lookup[index] = EMPTY;
    }

    /**
     * @return the current value for the current thread
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof FastThreadLocalThread)) {
            return fallback.get();
        }
        FastThreadLocalThread fastThread = (FastThreadLocalThread) thread;

        Object[] lookup = fastThread.lookup;
        Object v;
        if (index >= lookup.length) {
            v = initialValue();
            lookup = fastThread.expandArray(index);
            lookup[index] = v;
        } else {
            v = lookup[index];
            if (v == EMPTY) {
                v = initialValue();
                lookup[index] = v;
            }
        }
        return (V) v;
    }
}
