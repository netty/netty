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

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
public abstract class Recycler<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    private static final Handle NOOP_HANDLE = new Handle() { };
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(Integer.MIN_VALUE);
    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();
    // TODO: Some arbitrary large number - should adjust as we get more production experience.
    private static final int DEFAULT_INITIAL_MAX_CAPACITY = 262144;
    private static final int DEFAULT_MAX_CAPACITY;
    private static final int INITIAL_CAPACITY;

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer
        int maxCapacity = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity.default",
                                                    DEFAULT_INITIAL_MAX_CAPACITY);
        if (maxCapacity < 0) {
            maxCapacity = DEFAULT_INITIAL_MAX_CAPACITY;
        }

        DEFAULT_MAX_CAPACITY = maxCapacity;
        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacity.default: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacity.default: {}", DEFAULT_MAX_CAPACITY);
            }
        }

        INITIAL_CAPACITY = Math.min(DEFAULT_MAX_CAPACITY, 256);
    }

    private final int maxCapacity;
    private final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacity);
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY);
    }

    protected Recycler(int maxCapacity) {
        this.maxCapacity = Math.max(0, maxCapacity);
    }

    @SuppressWarnings("unchecked")
    public final T get() {
        if (maxCapacity == 0) {
            return newObject(NOOP_HANDLE);
        }
        Stack<T> stack = threadLocal.get();
        DefaultHandle handle = stack.pop();
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return (T) handle.value;
    }

    public final boolean recycle(T o, Handle handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        DefaultHandle h = (DefaultHandle) handle;
        if (h.stack.parent != this) {
            return false;
        }
        if (o != h.value) {
            throw new IllegalArgumentException("o does not belong to handle");
        }
        h.recycle();
        return true;
    }

    protected abstract T newObject(Handle handle);

    final int threadLocalCapacity() {
        return threadLocal.get().elements.length;
    }

    final int threadLocalSize() {
        return threadLocal.get().size;
    }

    public interface Handle { }

    static final class DefaultHandle implements Handle {
        private int lastRecycledId;
        private int recycleId;

        private Stack<?> stack;
        private Object value;

        DefaultHandle(Stack<?> stack) {
            this.stack = stack;
        }

        public void recycle() {
            Thread thread = Thread.currentThread();
            if (thread == stack.thread) {
                stack.push(this);
                return;
            }
            // we don't want to have a ref to the queue as the value in our weak map
            // so we null it out; to ensure there are no races with restoring it later
            // we impose a memory ordering here (no-op on x86)
            Map<Stack<?>, WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            WeakOrderQueue queue = delayedRecycled.get(stack);
            if (queue == null) {
                delayedRecycled.put(stack, queue = new WeakOrderQueue(stack, thread));
            }
            queue.add(this);
        }
    }

    private static final FastThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED =
            new FastThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
        @Override
        protected Map<Stack<?>, WeakOrderQueue> initialValue() {
            return new WeakHashMap<Stack<?>, WeakOrderQueue>();
        }
    };

    // a queue that makes only moderate guarantees about visibility: items are seen in the correct order,
    // but we aren't absolutely guaranteed to ever see anything at all, thereby keeping the queue cheap to maintain
    private static final class WeakOrderQueue {
        private static final int LINK_CAPACITY = 16;

        // Let Link extend AtomicInteger for intrinsics. The Link itself will be used as writerIndex.
        @SuppressWarnings("serial")
        private static final class Link extends AtomicInteger {
            private final DefaultHandle[] elements = new DefaultHandle[LINK_CAPACITY];

            private int readIndex;
            private Link next;
        }

        // chain of data items
        private Link head, tail;
        // pointer to another queue of delayed items for the same stack
        private WeakOrderQueue next;
        private final WeakReference<Thread> owner;
        private final int id = ID_GENERATOR.getAndIncrement();

        WeakOrderQueue(Stack<?> stack, Thread thread) {
            head = tail = new Link();
            owner = new WeakReference<Thread>(thread);
            synchronized (stack) {
                next = stack.head;
                stack.head = this;
            }
        }

        void add(DefaultHandle handle) {
            handle.lastRecycledId = id;

            Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.get()) == LINK_CAPACITY) {
                this.tail = tail = tail.next = new Link();
                writeIndex = tail.get();
            }
            tail.elements[writeIndex] = handle;
            handle.stack = null;
            // we lazy set to ensure that setting stack to null appears before we unnull it in the owning thread;
            // this also means we guarantee visibility of an element in the queue if we see the index updated
            tail.lazySet(writeIndex + 1);
        }

        boolean hasFinalData() {
            return tail.readIndex != tail.get();
        }

        // transfer as many items as we can from this queue to the stack, returning true if any were transferred
        @SuppressWarnings("rawtypes")
        boolean transfer(Stack<?> dst) {

            Link head = this.head;
            if (head == null) {
                return false;
            }

            if (head.readIndex == LINK_CAPACITY) {
                if (head.next == null) {
                    return false;
                }
                this.head = head = head.next;
            }

            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            final int srcSize = srcEnd - srcStart;
            if (srcSize == 0) {
                return false;
            }

            final int dstSize = dst.size;
            final int expectedCapacity = dstSize + srcSize;

            if (expectedCapacity > dst.elements.length) {
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = Math.min(srcStart + actualCapacity - dstSize, srcEnd);
            }

            if (srcStart != srcEnd) {
                final DefaultHandle[] srcElems = head.elements;
                final DefaultHandle[] dstElems = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    DefaultHandle element = srcElems[i];
                    if (element.recycleId == 0) {
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    element.stack = dst;
                    dstElems[newDstSize ++] = element;
                    srcElems[i] = null;
                }
                dst.size = newDstSize;

                if (srcEnd == LINK_CAPACITY && head.next != null) {
                    this.head = head.next;
                }

                head.readIndex = srcEnd;
                return true;
            } else {
                // The destination stack is full already.
                return false;
            }
        }
    }

    static final class Stack<T> {

        // we keep a queue of per-thread queues, which is appended to once only, each time a new thread other
        // than the stack owner recycles: when we run out of items in our stack we iterate this collection
        // to scavenge those that can be reused. this permits us to incur minimal thread synchronisation whilst
        // still recycling all items.
        final Recycler<T> parent;
        final Thread thread;
        private DefaultHandle[] elements;
        private final int maxCapacity;
        private int size;

        private volatile WeakOrderQueue head;
        private WeakOrderQueue cursor, prev;

        Stack(Recycler<T> parent, Thread thread, int maxCapacity) {
            this.parent = parent;
            this.thread = thread;
            this.maxCapacity = maxCapacity;
            elements = new DefaultHandle[Math.min(INITIAL_CAPACITY, maxCapacity)];
        }

        int increaseCapacity(int expectedCapacity) {
            int newCapacity = elements.length;
            int maxCapacity = this.maxCapacity;
            do {
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);

            newCapacity = Math.min(newCapacity, maxCapacity);
            if (newCapacity != elements.length) {
                elements = Arrays.copyOf(elements, newCapacity);
            }

            return newCapacity;
        }

        DefaultHandle pop() {
            int size = this.size;
            if (size == 0) {
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            size --;
            DefaultHandle ret = elements[size];
            if (ret.lastRecycledId != ret.recycleId) {
                throw new IllegalStateException("recycled multiple times");
            }
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            this.size = size;
            return ret;
        }

        boolean scavenge() {
            // continue an existing scavenge, if any
            if (scavengeSome()) {
                return true;
            }

            // reset our scavenge cursor
            prev = null;
            cursor = head;
            return false;
        }

        boolean scavengeSome() {
            WeakOrderQueue cursor = this.cursor;
            if (cursor == null) {
                cursor = head;
                if (cursor == null) {
                    return false;
                }
            }

            boolean success = false;
            WeakOrderQueue prev = this.prev;
            do {
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }

                WeakOrderQueue next = cursor.next;
                if (cursor.owner.get() == null) {
                    // If the thread associated with the queue is gone, unlink it, after
                    // performing a volatile read to confirm there is no data left to collect.
                    // We never unlink the first queue, as we don't want to synchronize on updating the head.
                    if (cursor.hasFinalData()) {
                        for (;;) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;
                            }
                        }
                    }
                    if (prev != null) {
                        prev.next = next;
                    }
                } else {
                    prev = cursor;
                }

                cursor = next;

            } while (cursor != null && !success);

            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        void push(DefaultHandle item) {
            if ((item.recycleId | item.lastRecycledId) != 0) {
                throw new IllegalStateException("recycled already");
            }
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;

            int size = this.size;
            if (size >= maxCapacity) {
                // Hit the maximum capacity - drop the possibly youngest object.
                return;
            }
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, Math.min(size << 1, maxCapacity));
            }

            elements[size] = item;
            this.size = size + 1;
        }

        DefaultHandle newHandle() {
            return new DefaultHandle(this);
        }
    }
}
