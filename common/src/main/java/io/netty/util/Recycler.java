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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
public abstract class Recycler<T> {

    private final ThreadLocal<Stack<T>> threadLocal = new ThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this, Thread.currentThread());
        }
    };

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
        final Recycler<T> parent;
        final Thread thread;
        private final Deque<T> deque = new ArrayDeque<T>();
        private final Map<T, Boolean> map = new IdentityHashMap<T, Boolean>();

        Stack(Recycler<T> parent, Thread thread) {
            this.parent = parent;
            this.thread = thread;
        }

        T pop() {
            T ret = deque.pollLast();
            map.remove(ret);
            return ret;
        }

        void push(T o) {
            if (map.put(o, Boolean.TRUE) != null) {
                throw new IllegalStateException("recycled already");
            }
            deque.addLast(o);
        }
    }
}
