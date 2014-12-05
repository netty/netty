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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
        int maxCapacity = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", 0);
        if (maxCapacity <= 0) {
            // TODO: Some arbitrary large number - should adjust as we get more production experience.
            maxCapacity = 262144;
        }

        DEFAULT_MAX_CAPACITY = maxCapacity;
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.recycler.maxCapacity: {}", DEFAULT_MAX_CAPACITY);
        }

        INITIAL_CAPACITY = Math.min(DEFAULT_MAX_CAPACITY, 256);
    }

    private final int maxCapacity;
    private final FastThreadLocal<Stack> threadLocal = new FastThreadLocal<Stack>() {
        @Override
        protected Stack initialValue() {
            return new Stack(Thread.currentThread(), maxCapacity);
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY);
    }

    protected Recycler(int maxCapacity) {
        this.maxCapacity = Math.max(0, maxCapacity);
    }

    /**
     * Gets an instance from recycler's pool or a new instance if the pool is empty.
     *
     * @return An instance from pool or a new instance.
     */
    @SuppressWarnings("unchecked")
    public final T get() {
        // if current thread pool can't provide handlers, a new handle for a new element is constructed.
        Stack stack = threadLocal.get();
        Handle handle = stack.pop();
        if (handle == null) {
            handle = new Handle(stack, this);
        }
        return (T) handle.value;
    }

    final int threadLocalCapacity() {
        return threadLocal.get().syncHandles.length;
    }

    protected abstract T newObject(Handle handle);

    /**
     * Object used to reference a pool and a value. A handle is bound only to a single thread because is bound to a
     * single pool. It can be recycled safely from other threads.
     */
    public static final class Handle {
        // Keeping a handle is not blocking the pool reference.
        private final WeakReference<Stack> stackWeakRef;
        // Value referenced by this handle.
        private final Object value;
        // Flag used to find if the current handle is in recycler's pool.
        private final AtomicBoolean inPool;

        private Handle(Stack stack, Recycler<?> recycler) {
            stackWeakRef = new WeakReference<Stack>(stack);
            value = recycler.newObject(this);
            inPool = new AtomicBoolean(false);
        }

        /**
         * Recycles a handle to allow the value to be used again.
         *
         * @return {@code true} if the handle was recycled; {@code false} if the pool max capacity was reached or the
         * pool is garbage collected.
         */
        public boolean recycle() {
            Stack stack = stackWeakRef.get();
            if (stack != null) {
                return stack.push(this);
            } else {
                return false;
            }
        }

        /**
         * Finds if the current handle can be recycled.
         *
         * @return {@code true} if the handle can be recycled.
         */
        public boolean canBeRecycled() {
            return stackWeakRef.get() != null && inPool.get();
        }
    }

    /**
     * Pool used to keep handles for a single thread.
     * <p/>
     * This pool contains an array bounded by a maximum capacity, an array used in LIFO mode. This array is only
     * accessed from owner thread in order to avoid synchronization.
     * <p/>
     * This pool also contains a non blocking thread-safe queue of handles. In this queue other threads can add handles
     * owned by current pool. To avoid performance loss caused by adding/retrieving of a single handle a buffer class is
     * used.
     */
    protected static final class Stack {
        // Thread for which this pool was built.
        private final Thread owner;
        // Max capacity for the synchronized array.
        private final int syncMaxCapacity;
        // Array that is only used only from the owner thread.
        private Handle[] syncHandles;
        // Number of handles in the array that can be safely used by the owner thread.
        private int syncSize;

        // Queue that contains buffers pushed by other threads, buffers filled with recycled handles from other threads.
        private final ConcurrentLinkedQueue<Buffer> queue;

        // Other threads keep buffers in this pool.
        // A thread != owner can recycle a handle and will firstly introduce it in its buffer; when the buffer is full
        // it is added to the shared queue.
        private final FastThreadLocal<Buffer> threadBuffer;

        /**
         * Constructor that receives the owner thread and the maximum capacity for the array of handles contained in
         * this pool.
         *
         * @param owner The thread for which this pool was built.
         * @param maxCapacity The maximum capacity for the array of handles.
         */
        private Stack(final Thread owner, final int maxCapacity) {
            this.owner = owner;
            syncMaxCapacity = maxCapacity;

            syncHandles = new Handle[INITIAL_CAPACITY];
            syncSize = 0;

            queue = new ConcurrentLinkedQueue<Buffer>();

            threadBuffer = new FastThreadLocal<Buffer>() {
                @Override
                protected Buffer initialValue() throws Exception {
                    return new Buffer();
                }
            };
        }

        /**
         * Extracts a handle from pool.
         *
         * @return A {@link Handle} extracted from current pool.
         */
        private Handle pop() {
            // this method is only called from owner thread. Always Thread.currentThread() == this.owner

            // first, we try to extract the handle from the array which does not require any synchronization because
            // it is only accessed from a single thread, the owner thread.
            // synchronized array = array used only from the owner thread
            if (syncSize <= 0) {

                // we try to extract a buffer full of handles added in the queue by other threads.
                Buffer buffer = queue.poll();
                if (buffer == null) {
                    return null;
                }

                // we try to find out if the array can contain the extra handles from the buffer.
                int freeSpace = syncHandles.length - syncSize;
                if (freeSpace < Buffer.CAPACITY && syncHandles.length != syncMaxCapacity) {
                    int newSize = Math.max(syncHandles.length << 1, syncSize + Buffer.CAPACITY);
                    syncHandles = Arrays.copyOf(syncHandles, Math.min(newSize, syncMaxCapacity));
                    freeSpace = syncHandles.length - syncSize;
                }
                int toBeCopied = Math.min(freeSpace, Buffer.CAPACITY);
                if (toBeCopied > 0) {
                    // we copy the handles from buffer to the safely owned array.
                    System.arraycopy(buffer.handles, 0, syncHandles, syncSize, toBeCopied);
                    syncSize += toBeCopied;
                }
            }

            if (syncSize <= 0) {
                return null;
            }

            // we can provide the handle from thread's array.
            Handle handle = syncHandles[syncSize - 1];
            syncSize--;
            syncHandles[syncSize] = null;
            // we mark the handle as ready to be recycled.
            handle.inPool.lazySet(false);
            return handle;
        }

        private boolean push(Handle handle) {
            // this method is only called from an owned handle. Always handle.stackWeakRef.get() == this

            // we check to see if another thread already recycled this handle.
            if (!handle.inPool.compareAndSet(false, true)) {
                throw new IllegalStateException("recycled already");
            }

            if (Thread.currentThread() == owner) {
                if (syncSize == syncMaxCapacity) {
                    // max capacity reached. we can't recycle.
                    return false;
                }
                if (syncSize == syncHandles.length) {
                    syncHandles = Arrays.copyOf(syncHandles, Math.min(syncSize << 1, syncMaxCapacity));
                }
                // we can add the element safely to the owned array (no synchronization required).
                syncHandles[syncSize] = handle;
                syncSize++;
            } else {
                // we obtain the buffer for the current thread.
                Buffer buffer = threadBuffer.get();
                // we add the handle to the buffer
                if (!buffer.addHandle(handle)) {
                    // max capacity reached; we add the buffer to the synchronized queue.
                    queue.offer(buffer);
                    // we set a new buffer for current thread.
                    buffer = new Buffer();
                    buffer.addHandle(handle);
                    threadBuffer.set(buffer);
                }
            }

            return true;
        }
    }

    /**
     * Light-weight buffer for handles.
     */
    protected static final class Buffer {
        // The number of handles that a thread will collect before adding them the queue shared by another thread.
        private static final int CAPACITY = 8;
        // Array where handles are collected.
        private final Handle[] handles;
        // Current number of handles collected.
        private int size;

        private Buffer() {
            handles = new Handle[CAPACITY];
            size = 0;
        }

        private boolean addHandle(Handle handle) {
            if (size == CAPACITY) {
                return false;
            }

            handles[size] = handle;
            size++;
            return true;
        }
    }
}
