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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.MathUtil.safeFindNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
public abstract class Recycler<T> {
    // By its very nature this class uses heuristics, and depending on the
    // workload may be overeager or too conservative. However, to prevent
    // unnecessary burden on users, it has a core design principal of "do no
    // harm." Being conservative and providing less benefit is better than
    // being disabled due to overeagerness and providing no benefit.
    //
    // Because this class causes objects to have longer lifetimes than
    // previously, it can negatively impact memory usage. Thus it follows the
    // rule: "do not be the _direct_ cause of an object to outlive its
    // generation." It does this by using weak references when objects are not
    // actively being used. Worst-case, it will retain one WeakReference per
    // thread of additional memory. This class does _indirectly_ cause objects
    // to outlive their generation, however. To help mitigate that, it attempts
    // to follow: "prefer using objects in older generations over objects in
    // newer generations." This keeps newer objects out of the application,
    // giving them more opportunity to be garbage collected. This rule works
    // well for CMS, but is imprecise for G1.
    //
    // Because this class needs to maintain state, it can negatively impact GC
    // and memory usage. Thus it follows the rule: "avoid overhead for objects
    // that won't be reused." While more sophisticated methods of following the
    // rule are possible, the class uses a simple approach of making ~1% of the
    // new objects be recyclable. If recycling is helping, the recyclable
    // objects will accumulate.

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    private static final Handle<?> NOOP_HANDLE = new Handle<Object>() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }
    };

    // These constants aren't actually used, but they are needed to maintain the previous API.
    // TODO: Remove in next major Netty release.
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024;
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD;
    private static final int MAX_SHARED_CAPACITY_FACTOR;
    private static final int MAX_DELAYED_QUEUES_PER_THREAD;
    private static final int LINK_CAPACITY;
    private static final int RATIO;

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer
        int maxCapacityPerThread = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD));
        if (maxCapacityPerThread < 0) {
            maxCapacityPerThread = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD;
        }

        DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread;

        MAX_SHARED_CAPACITY_FACTOR = max(2,
                SystemPropertyUtil.getInt("io.netty.recycler.maxSharedCapacityFactor",
                        2));

        MAX_DELAYED_QUEUES_PER_THREAD = max(0,
                SystemPropertyUtil.getInt("io.netty.recycler.maxDelayedQueuesPerThread",
                        // We use the same value as default EventLoop number
                        NettyRuntime.availableProcessors() * 2));

        LINK_CAPACITY = safeFindNextPositivePowerOfTwo(
                max(SystemPropertyUtil.getInt("io.netty.recycler.linkCapacity", 16), 16));

        // By default we allow one push to a Recycler for each 8th try on handles that were never recycled before.
        // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
        // bursts.
        RATIO = safeFindNextPositivePowerOfTwo(SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8));

        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: disabled");
                logger.debug("-Dio.netty.recycler.linkCapacity: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD);
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: {}", MAX_SHARED_CAPACITY_FACTOR);
                logger.debug("-Dio.netty.recycler.linkCapacity: {}", LINK_CAPACITY);
                logger.debug("-Dio.netty.recycler.ratio: {}", RATIO);
            }
        }
    }

    private final int maxCapacityPerThread;

    final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this);
        }
    };
    final AtomicInteger currentGenId = new AtomicInteger();

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, MAX_SHARED_CAPACITY_FACTOR);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, RATIO, MAX_DELAYED_QUEUES_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
        } else {
            this.maxCapacityPerThread = maxCapacityPerThread;
        }
    }

    @SuppressWarnings("unchecked")
    public final T get() {
        if (maxCapacityPerThread == 0) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        Stack<T> stack = threadLocal.get();
        return stack.popOrCreate();
    }

    /**
     * @deprecated use {@link Handle#recycle(Object)}.
     */
    @Deprecated
    public final boolean recycle(T o, Handle<T> handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        DefaultHandle<T> h = (DefaultHandle<T>) handle;
        if (h.parent != this) {
            return false;
        }

        h.recycle(o);
        return true;
    }

    protected abstract T newObject(Handle<T> handle);

    public interface Handle<T> {
        void recycle(T object);
    }

    static final class DefaultHandle<T> implements Handle<T> {
        private final WeakReference<DefaultHandle<T>> selfRef = new WeakReference<DefaultHandle<T>>(this);
        private final Recycler<T> parent;
        private T value;
        private WeakReference<DefaultHandle<T>> next;
        final int genId;

        DefaultHandle(Recycler<T> parent) {
            this.parent = parent;
            this.genId = parent.currentGenId.get();
        }

        @Override
        public void recycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            parent.threadLocal.get().push(this);
        }
    }

    static final class Stack<T> {
        private static final WeakReference<?> NULL_REF = new WeakReference<DefaultHandle<?>>(null);

        final Recycler<T> parent;
        /** The maxId of every handle within the generation. The last generation accepts the remaining ids. */
        private final int[] genMaxId = new int[] {-1, -1};
        private final WeakReference[] genHead = new WeakReference[] {
            NULL_REF, NULL_REF, NULL_REF
        };
        private int allocationCount;

        Stack(Recycler<T> parent) {
            this.parent = parent;
        }

        DefaultHandle<T> pop() {
            for (int i = 0; i < genHead.length; i++) {
                WeakReference<DefaultHandle<T>> head = genHead[i];
                if (head == NULL_REF) {
                    continue;
                }
                DefaultHandle<T> node = head.get();
                if (node == null) {
                    gcDetected(i);
                    genHead[i] = NULL_REF;
                    continue;
                }
                genHead[i] = node.next;
                node.next = null;
                return node;
            }
            return null;
        }

        void gcDetected(int genIndex) {
            int i = genIndex;
            if (i > 0) {
                // Promote this generation

                int maxId;
                if (i < genMaxId.length) {
                    maxId = genMaxId[i];
                } else {
                    // Eden generation

                    maxId = parent.currentGenId.get();
                    if (maxId == genMaxId[i - 1]) {
                        // This is the first thread to detect the new generation
                        parent.currentGenId.incrementAndGet();
                    }
                }
                genMaxId[i - 1] = maxId;
            }
        }

        void push(DefaultHandle<T> node) {
            if (node.next != null) {
                throw new IllegalStateException("recycled already");
            }
            int i = 0;
            for (; i < genMaxId.length && genMaxId[i] < node.genId; i++) {
                // find generation index
            }
            node.next = genHead[i];
            genHead[i] = node.selfRef;
        }

        T popOrCreate() {
            DefaultHandle<T> handle = pop();
            if (handle != null) {
                return handle.value;
            }
            if ((allocationCount++ & 63) == 0) {
                // Create recyclable objects at a low rate. If they are helpful, they will accumulate
                handle = new DefaultHandle<T>(parent);
                handle.value = parent.newObject(handle);
                return handle.value;
            }
            return parent.newObject((Handle<T>) NOOP_HANDLE);
        }
    }
}
