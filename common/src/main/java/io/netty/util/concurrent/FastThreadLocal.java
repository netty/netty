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
package io.netty.util.concurrent;

import java.util.EnumMap;

/**
 * A simple class providing equivalent functionality to java.lang,ThreadLocal, but operating
 * over a predefined hash range, so that we can hash perfectly. This permits less indirection
 * and offers a slight performance improvement, so is useful when invoked frequently.
 *
 * The fast path is only possible on threads that extend FastThreadLocalThread, as this class
 * stores the necessary state. Access by any other kind of thread falls back to a regular ThreadLocal
 *
 * @param <V>
 */
public class FastThreadLocal<V> {

    public static enum Type {
        LocalChannel_ReaderStackDepth,
        PooledDirectByteBuf_Recycler,
        PooledHeapByteBuf_Recycler,
        PooledUnsafeDirectByteBuf_Recycler,
        ChannelOutboundBuffer_Recycler,
        ChannelOutboundBuffer_PooledByteBuf_Recycler,
        DefaultChannelHandlerContext_WriteAndFlushTask_Recycler,
        DefaultChannelHandlerContext_WriteTask_Recycler,
        PendingWrite_Recycler,
        RecyclableArrayList_Recycler,
        DefaultPromise_ListenerStackDepth,
        PooledByteBufAllocator_DefaultAllocator
    }

    // the set of already defined FastThreadLocals
    private static final EnumMap<Type, Boolean> SET = new EnumMap<Type, Boolean>(Type.class);
    // the type values, for cheap iteration
    private static final Type[] TYPES = Type.values();
    // a marker to indicate a value has not yet been initialised
    private static final Object EMPTY = new Object();

    /**
     * To utilise the FastThreadLocal fast-path, all threads accessing a FastThreadLocal must extend this class
     */
    public static class FastThreadLocalThread extends Thread {

        private final EnumMap<Type, Object> lookup = initialMap();

        static EnumMap<Type, Object> initialMap() {
            EnumMap<Type, Object> r = new EnumMap<Type, Object>(Type.class);
            for (Type type : TYPES) {
                r.put(type, EMPTY);
            }
            return r;
        }

        public FastThreadLocalThread() {
            super();
        }

        public FastThreadLocalThread(Runnable target) {
            super(target);
        }

        public FastThreadLocalThread(ThreadGroup group, Runnable target) {
            super(group, target);
        }

        public FastThreadLocalThread(String name) {
            super(name);
        }

        public FastThreadLocalThread(ThreadGroup group, String name) {
            super(group, name);
        }

        public FastThreadLocalThread(Runnable target, String name) {
            super(target, name);
        }

        public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
            super(group, target, name);
        }

        public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
            super(group, target, name, stackSize);
        }
    }

    final Type type;
    final ThreadLocal<V> fallback = new ThreadLocal<V>() {
        protected V initialValue() {
            return FastThreadLocal.this.initialValue();
        }
    };

    /**
     * @param type the predefined type this FastThreadLocal represents; each type may be used only once
     *             globally in a single VM
     */
    public FastThreadLocal(Type type) {
        if (type != null) {
            synchronized (SET) {
                if (SET.put(type, Boolean.TRUE) != null) {
                    throw new IllegalStateException(type + " has been assigned multiple times");
                }
            }
        }
        this.type = type;
    }

    /**
     * Override this method to define the default value to assign
     * when a thread first calls get() without a preceding set()
     *
     * @return the initial value
     */
    protected V initialValue() {
        return null;
    }

    /**
     * Set the value for the current thread
     * @param value
     */
    public void set(V value) {
        Thread thread = Thread.currentThread();
        if (type == null || !(thread instanceof FastThreadLocalThread)) {
            fallback.set(value);
            return;
        }
        EnumMap<Type, Object> lookup = ((FastThreadLocalThread) thread).lookup;
        lookup.put(type, value);
    }

    /**
     * Sets the value to uninitialized; a proceeding call to get() will trigger a call to initialValue()
     */
    public void remove() {
        Thread thread = Thread.currentThread();
        if (type == null || !(thread instanceof FastThreadLocalThread)) {
            fallback.remove();
            return;
        }
        EnumMap<Type, Object> lookup = ((FastThreadLocalThread) thread).lookup;
        lookup.put(type, EMPTY);
    }

    /**
     * @return the current value for the current thread
     */
    public final V get() {
        Thread thread = Thread.currentThread();
        if (type == null || !(thread instanceof FastThreadLocalThread)) {
            return fallback.get();
        }
        EnumMap<Type, Object> lookup = ((FastThreadLocalThread) thread).lookup;
        Object v = lookup.get(type);
        if (v == EMPTY) {
            lookup.put(type, v = initialValue());
        }
        return (V) v;
    }
}
