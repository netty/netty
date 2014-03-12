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

import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
public abstract class Recycler<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    private static final int DEFAULT_MAX_CAPACITY;
    private static final int INITIAL_CAPACITY;

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer
        int maxCapacity = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity.default", 0);
        if (maxCapacity <= 0) {
            // TODO: Some arbitrary large number - should adjust as we get more production experience.
            maxCapacity = 262144;
        }

        DEFAULT_MAX_CAPACITY = maxCapacity;
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.recycler.maxCapacity.default: {}", DEFAULT_MAX_CAPACITY);
        }

        INITIAL_CAPACITY = Math.min(DEFAULT_MAX_CAPACITY, 256);
    }

    private final int maxCapacity;

    private final ThreadLocal<Stack<T>> threadLocal = new ThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacity);
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY);
    }

    protected Recycler(int maxCapacity) {
        if (maxCapacity <= 0) {
            maxCapacity = 0;
        }
        this.maxCapacity = maxCapacity;
    }

    public final T get() {
        Stack<T> stack = threadLocal.get();
        T o = stack.pop();
        if (o == null) {
            o = newObject(stack);
        }
        return o;
    }

    public final boolean recycle(T o, Handle handle) {
        @SuppressWarnings("unchecked")
        Stack<T> stack = (Stack<T>) handle;
        if (stack.parent != this) {
            return false;
        }

        if (Thread.currentThread() != stack.thread) {
            return false;
        }

        stack.push(o);
        return true;
    }

    protected abstract T newObject(Handle handle);

    public interface Handle { }

    static final class Stack<T> implements Handle {

        private T[] elements;
        private int size;
        private final int maxCapacity;

        private final Map<T, Boolean> map;

        final Recycler<T> parent;
        final Thread thread;

        @SuppressWarnings("AssertWithSideEffects")
        Stack(Recycler<T> parent, Thread thread, int maxCapacity) {
            this.parent = parent;
            this.thread = thread;
            this.maxCapacity = maxCapacity;
            elements = newArray(INITIAL_CAPACITY);

            // *assigns* true if assertions are on.
            @SuppressWarnings("UnusedAssignment")
            boolean assertionEnabled = false;
            assert assertionEnabled = true;

            if (assertionEnabled) {
                map = new IdentityHashMap<T, Boolean>(INITIAL_CAPACITY);
            } else {
                map = null;
            }
        }

        T pop() {
            int size = this.size;
            if (size == 0) {
                return null;
            }
            size --;
            T ret = elements[size];
            elements[size] = null;
            assert map == null || map.remove(ret) != null;
            this.size = size;
            return ret;
        }

        void push(T o) {
            assert map == null || map.put(o, Boolean.TRUE) == null: "recycled already";

            int size = this.size;
            if (size == elements.length) {
                if (size == maxCapacity) {
                    // Hit the maximum capacity - drop the possibly youngest object.
                    return;
                }

                T[] newElements = newArray(Math.min(maxCapacity, size << 1));
                System.arraycopy(elements, 0, newElements, 0, size);
                elements = newElements;
            }

            elements[size] = o;
            this.size = size + 1;
        }

        @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
        private static <T> T[] newArray(int length) {
            return (T[]) new Object[length];
        }
    }
}
