/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jctools.queues.MessagePassingQueue;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.PlatformDependent.newMpscQueue;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
public abstract class Recycler<T> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);
    private static final EnhancedHandle<?> NOOP_HANDLE = new EnhancedHandle<Object>() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }

        @Override
        public void unguardedRecycle(final Object object) {
            // NOOP
        }

        @Override
        public String toString() {
            return "NOOP_HANDLE";
        }
    };
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024; // Use 4k instances as default.
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD;
    private static final int RATIO;
    private static final int DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD;
    private static final boolean BLOCKING_POOL;
    private static final boolean BATCH_FAST_TL_ONLY;

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
        DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD = SystemPropertyUtil.getInt("io.netty.recycler.chunkSize", 32);

        // By default, we allow one push to a Recycler for each 8th try on handles that were never recycled before.
        // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
        // bursts.
        RATIO = max(0, SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8));

        BLOCKING_POOL = SystemPropertyUtil.getBoolean("io.netty.recycler.blocking", false);
        BATCH_FAST_TL_ONLY = SystemPropertyUtil.getBoolean("io.netty.recycler.batchFastThreadLocalOnly", true);

        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
                logger.debug("-Dio.netty.recycler.chunkSize: disabled");
                logger.debug("-Dio.netty.recycler.blocking: disabled");
                logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD);
                logger.debug("-Dio.netty.recycler.ratio: {}", RATIO);
                logger.debug("-Dio.netty.recycler.chunkSize: {}", DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
                logger.debug("-Dio.netty.recycler.blocking: {}", BLOCKING_POOL);
                logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: {}", BATCH_FAST_TL_ONLY);
            }
        }
    }

    private final int maxCapacityPerThread;
    private final int interval;
    private final int chunkSize;
    private final FastThreadLocal<LocalPool<T>> threadLocal = new FastThreadLocal<LocalPool<T>>() {
        @Override
        protected LocalPool<T> initialValue() {
            return new LocalPool<T>(maxCapacityPerThread, interval, chunkSize);
        }

        @Override
        protected void onRemoval(LocalPool<T> value) throws Exception {
            super.onRemoval(value);
            MessagePassingQueue<DefaultHandle<T>> handles = value.pooledHandles;
            value.pooledHandles = null;
            value.owner = null;
            handles.clear();
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread, int delayedQueueRatio) {
        this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread, int ratio, int chunkSize) {
        interval = max(0, ratio);
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.chunkSize = 0;
        } else {
            this.maxCapacityPerThread = max(4, maxCapacityPerThread);
            this.chunkSize = max(2, min(chunkSize, this.maxCapacityPerThread >> 1));
        }
    }

    @SuppressWarnings("unchecked")
    public final T get() {
        if (maxCapacityPerThread == 0 || PlatformDependent.isVirtualThread(Thread.currentThread())) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        LocalPool<T> localPool = threadLocal.get();
        DefaultHandle<T> handle = localPool.claim();
        T obj;
        if (handle == null) {
            handle = localPool.newHandle();
            if (handle != null) {
                obj = newObject(handle);
                handle.set(obj);
            } else {
                obj = newObject((Handle<T>) NOOP_HANDLE);
            }
        } else {
            obj = handle.get();
        }

        return obj;
    }

    /**
     * @deprecated use {@link Handle#recycle(Object)}.
     */
    @Deprecated
    public final boolean recycle(T o, Handle<T> handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        handle.recycle(o);
        return true;
    }

    @VisibleForTesting
    final int threadLocalSize() {
        if (PlatformDependent.isVirtualThread(Thread.currentThread())) {
            return 0;
        }
        LocalPool<T> localPool = threadLocal.getIfExists();
        return localPool == null ? 0 : localPool.pooledHandles.size() + localPool.batch.size();
    }

    /**
     * @param handle can NOT be null.
     */
    protected abstract T newObject(Handle<T> handle);

    @SuppressWarnings("ClassNameSameAsAncestorName") // Can't change this due to compatibility.
    public interface Handle<T> extends ObjectPool.Handle<T>  { }

    @UnstableApi
    public abstract static class EnhancedHandle<T> implements Handle<T> {

        public abstract void unguardedRecycle(Object object);

        private EnhancedHandle() {
        }
    }

    private static final class DefaultHandle<T> extends EnhancedHandle<T> {
        private static final int STATE_CLAIMED = 0;
        private static final int STATE_AVAILABLE = 1;
        private static final AtomicIntegerFieldUpdater<DefaultHandle<?>> STATE_UPDATER;
        static {
            AtomicIntegerFieldUpdater<?> updater = AtomicIntegerFieldUpdater.newUpdater(DefaultHandle.class, "state");
            //noinspection unchecked
            STATE_UPDATER = (AtomicIntegerFieldUpdater<DefaultHandle<?>>) updater;
        }

        private volatile int state; // State is initialised to STATE_CLAIMED (aka. 0) so they can be released.
        private final LocalPool<T> localPool;
        private T value;

        DefaultHandle(LocalPool<T> localPool) {
            this.localPool = localPool;
        }

        @Override
        public void recycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            localPool.release(this, true);
        }

        @Override
        public void unguardedRecycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            localPool.release(this, false);
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }

        void toClaimed() {
            assert state == STATE_AVAILABLE;
            STATE_UPDATER.lazySet(this, STATE_CLAIMED);
        }

        void toAvailable() {
            int prev = STATE_UPDATER.getAndSet(this, STATE_AVAILABLE);
            if (prev == STATE_AVAILABLE) {
                throw new IllegalStateException("Object has been recycled already.");
            }
        }

        void unguardedToAvailable() {
            int prev = state;
            if (prev == STATE_AVAILABLE) {
                throw new IllegalStateException("Object has been recycled already.");
            }
            STATE_UPDATER.lazySet(this, STATE_AVAILABLE);
        }
    }

    private static final class LocalPool<T> implements MessagePassingQueue.Consumer<DefaultHandle<T>> {
        private final int ratioInterval;
        private final int chunkSize;
        private final ArrayDeque<DefaultHandle<T>> batch;
        private volatile Thread owner;
        private volatile MessagePassingQueue<DefaultHandle<T>> pooledHandles;
        private int ratioCounter;

        @SuppressWarnings("unchecked")
        LocalPool(int maxCapacity, int ratioInterval, int chunkSize) {
            this.ratioInterval = ratioInterval;
            this.chunkSize = chunkSize;
            batch = new ArrayDeque<DefaultHandle<T>>(chunkSize);
            Thread currentThread = Thread.currentThread();
            owner = !BATCH_FAST_TL_ONLY || currentThread instanceof FastThreadLocalThread ? currentThread : null;
            if (BLOCKING_POOL) {
                pooledHandles = new BlockingMessageQueue<DefaultHandle<T>>(maxCapacity);
            } else {
                pooledHandles = (MessagePassingQueue<DefaultHandle<T>>) newMpscQueue(chunkSize, maxCapacity);
            }
            ratioCounter = ratioInterval; // Start at interval so the first one will be recycled.
        }

        DefaultHandle<T> claim() {
            MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
            if (handles == null) {
                return null;
            }
            if (batch.isEmpty()) {
                handles.drain(this, chunkSize);
            }
            DefaultHandle<T> handle = batch.pollLast();
            if (null != handle) {
                handle.toClaimed();
            }
            return handle;
        }

        void release(DefaultHandle<T> handle, boolean guarded) {
            if (guarded) {
                handle.toAvailable();
            } else {
                handle.unguardedToAvailable();
            }
            Thread owner = this.owner;
            if (owner != null && Thread.currentThread() == owner && batch.size() < chunkSize) {
                accept(handle);
            } else if (owner != null && isTerminated(owner)) {
                this.owner = null;
                pooledHandles = null;
            } else {
                MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
                if (handles != null) {
                    handles.relaxedOffer(handle);
                }
            }
        }

        private static boolean isTerminated(Thread owner) {
            // Do not use `Thread.getState()` in J9 JVM because it's known to have a performance issue.
            // See: https://github.com/netty/netty/issues/13347#issuecomment-1518537895
            return PlatformDependent.isJ9Jvm() ? !owner.isAlive() : owner.getState() == Thread.State.TERMINATED;
        }

        DefaultHandle<T> newHandle() {
            if (++ratioCounter >= ratioInterval) {
                ratioCounter = 0;
                return new DefaultHandle<T>(this);
            }
            return null;
        }

        @Override
        public void accept(DefaultHandle<T> e) {
            batch.addLast(e);
        }
    }

    /**
     * This is an implementation of {@link MessagePassingQueue}, similar to what might be returned from
     * {@link PlatformDependent#newMpscQueue(int)}, but intended to be used for debugging purpose.
     * The implementation relies on synchronised monitor locks for thread-safety.
     * The {@code fill} bulk operation is not supported by this implementation.
     */
    private static final class BlockingMessageQueue<T> implements MessagePassingQueue<T> {
        private final Queue<T> deque;
        private final int maxCapacity;

        BlockingMessageQueue(int maxCapacity) {
            this.maxCapacity = maxCapacity;
            // This message passing queue is backed by an ArrayDeque instance,
            // made thread-safe by synchronising on `this` BlockingMessageQueue instance.
            // Why ArrayDeque?
            // We use ArrayDeque instead of LinkedList or LinkedBlockingQueue because it's more space efficient.
            // We use ArrayDeque instead of ArrayList because we need the queue APIs.
            // We use ArrayDeque instead of ConcurrentLinkedQueue because CLQ is unbounded and has O(n) size().
            // We use ArrayDeque instead of ArrayBlockingQueue because ABQ allocates its max capacity up-front,
            // and these queues will usually have large capacities, in potentially great numbers (one per thread),
            // but often only have comparatively few items in them.
            deque = new ArrayDeque<T>();
        }

        @Override
        public synchronized boolean offer(T e) {
            if (deque.size() == maxCapacity) {
                return false;
            }
            return deque.offer(e);
        }

        @Override
        public synchronized T poll() {
            return deque.poll();
        }

        @Override
        public synchronized T peek() {
            return deque.peek();
        }

        @Override
        public synchronized int size() {
            return deque.size();
        }

        @Override
        public synchronized void clear() {
            deque.clear();
        }

        @Override
        public synchronized boolean isEmpty() {
            return deque.isEmpty();
        }

        @Override
        public int capacity() {
            return maxCapacity;
        }

        @Override
        public boolean relaxedOffer(T e) {
            return offer(e);
        }

        @Override
        public T relaxedPoll() {
            return poll();
        }

        @Override
        public T relaxedPeek() {
            return peek();
        }

        @Override
        public int drain(Consumer<T> c, int limit) {
            T obj;
            int i = 0;
            for (; i < limit && (obj = poll()) != null; i++) {
                c.accept(obj);
            }
            return i;
        }

        @Override
        public int fill(Supplier<T> s, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int drain(Consumer<T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int fill(Supplier<T> s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit) {
            throw new UnsupportedOperationException();
        }
    }
}
