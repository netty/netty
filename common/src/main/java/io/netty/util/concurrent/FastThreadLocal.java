/*
 * Copyright 2012 The Netty Project
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
        Recycler_DelayedRecyle,
        DefaultPromise_ListenerStackDepth,
        DefaultPooledByteBufAllocator_ThreadCache
    }

    private static final EnumMap<Type, Boolean> SET = new EnumMap<Type, Boolean>(Type.class);
    private static final Type[] TYPES = Type.values();
    private static final Object EMPTY = new Object();

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

    public FastThreadLocal(Type type) {
        synchronized (SET) {
            if (SET.put(type, Boolean.TRUE) != null) {
                throw new IllegalStateException(type + " has been assigned multiple times");
            }
        }
        this.type = type;
    }

    protected V initialValue() {
        return null;
    }

    public void set(V value) {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof FastThreadLocalThread)) {
            fallback.set(value);
            return;
        }
        EnumMap<Type, Object> lookup = ((FastThreadLocalThread) thread).lookup;
        lookup.put(type, value);
    }

    public final V get() {
        return get(Thread.currentThread());
    }

    public final V get(Thread thread) {
        if (!(thread instanceof FastThreadLocalThread)) {
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
