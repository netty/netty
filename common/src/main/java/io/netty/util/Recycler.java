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

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.FastThreadLocal;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

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
    private final ThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
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


    public final T get() {
        Stack<T> stack = threadLocal.get();
        Handle handle = stack.pop();
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle);
        }
        return (T) handle.value;
    }

    protected abstract T newObject(Handle handle);

    public static final class Handle {
        private final int id;
        private Stack stack;
        private Object value;
        Handle(int id, Stack stack) {
            this.id = id;
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
                delayedRecycled.put(stack, queue = new WeakOrderQueue(stack));
            }
            queue.add(this);
        }
    }

    static final ThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED = new FastThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
        @Override
        protected Map<Stack<?>, WeakOrderQueue> initialValue() {
            return new WeakHashMap<Stack<?>, WeakOrderQueue>();
        }
    };

    // a queue that makes only moderate guarantees about visibility: items are seen in the correct order,
    // but we aren't absolutely guaranteed to ever see anything at all, thereby keeping the queue cheap to maintain
    private static final class WeakOrderQueue {
        private static final int LINK_CAPACITY = 16;
        private static final class Link {
            private int readIndex;
            private volatile int writeIndex;
            private final Handle[] elements = new Handle[LINK_CAPACITY];
            private Link next;

            private static final AtomicIntegerFieldUpdater<Link> writeIndexUpdater
                    = AtomicIntegerFieldUpdater.newUpdater(Link.class, "writeIndex");
        }

        // chain of data items
        private Link head, tail;
        // pointer to another queue of delayed items for the same stack
        private WeakOrderQueue next;
        private final WeakReference<Thread> owner;

        public WeakOrderQueue(Stack<?> stack) {
            head = tail = new Link();
            owner = new WeakReference<Thread>(Thread.currentThread());
            synchronized (stack) {
                next = stack.head;
                stack.head = this;
            }
        }

        void add(Handle handle) {
            Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.writeIndex) == LINK_CAPACITY) {
                this.tail = tail = tail.next = new Link();
                writeIndex = tail.writeIndex;
            }
            tail.elements[writeIndex] = handle;
            handle.stack = null;
            // we lazy set to ensure that setting stack to null appears before we unnull it in the owning thread;
            // this also means we guarantee visibility of an element in the queue if we see the index updated
            Link.writeIndexUpdater.lazySet(tail, writeIndex + 1);
        }

        boolean hasFinalData() {
            return tail.readIndex != tail.writeIndex;
        }

        // transfer as many items as we can from this queue to the stack, returning true if any were transferred
        boolean transfer(Stack<?> to) {

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

            int start = head.readIndex;
            int end = head.writeIndex;
            if (start == end) {
                return false;
            }

            int count = end - start;
            if (to.size + count > to.elements.length) {
                to.elements = Arrays.copyOf(to.elements, (to.size + count) * 2);
            }

            BitSet present = to.present;
            Handle[] src = head.elements;
            Handle[] trg = to.elements;
            int size = to.size;
            while (start < end) {
                Handle element = src[start];
                if (!present.set(element.id)) {
                    throw new IllegalStateException("recycled already");
                }
                element.stack = to;
                trg[size++] = element;
                src[start++] = null;
            }
            to.size = size;
            to.scavenged = true;

            if (end == LINK_CAPACITY & head.next != null) {
                this.head = head.next;
            }

            head.readIndex = end;
            return true;
        }
    }

    static final class Stack<T> {

        // we keep a queue of per-thread queues, which is appended to once only, each time a new thread other
        // than the stack owner recycles: when we run out of items in our stack we iterate this collection
        // to scavenge those that can be reused. this permits us to incur minimal thread synchronisation whilst
        // still recycling all items; more importantly it permits us to use a BitSet to track ids, as we can
        // be certain that, under correct usage, the id domain does not grow unbounded
        private int maxId;
        final Recycler<T> parent;
        final Thread thread;
        private Handle[] elements;
        private final BitSet present = new BitSet(256);
        private int size;

        private volatile WeakOrderQueue head;
        private WeakOrderQueue cursor, prev;
        private boolean scavenged;

        @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
        Stack(Recycler<T> parent, Thread thread, int maxCapacity) {
            this.parent = parent;
            this.thread = thread;
            elements = new Handle[INITIAL_CAPACITY];
        }

        Handle pop() {
            int size = this.size;
            if (size == 0) {
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            size --;
            Handle ret = elements[size];
            present.clear(ret.id);
            this.size = size;
            return ret;
        }

        boolean scavenge() {
            // continue an existing scavenge, if any
            if (scavengeSome()) {
                return true;
            }

            if (scavenged) {
                // if we scavenged anything last loop, try once more before increasing our pool size
                scavenged = false;
                prev = null;
                cursor = head;
                if (scavengeSome()) {
                    return true;
                }
            }

            // maybe expand our id domain / pool size
            if (maxId == present.size()) {
                present.ensureCapacity(maxId * 2);
            }

            // reset our scavenge cursor
            prev = null;
            cursor = head;
            return false;
        }

        boolean scavengeSome() {
            boolean success = false;
            WeakOrderQueue cursor = this.cursor, prev = this.prev;
            while (cursor != null) {
                if (cursor.transfer(this)) {
                    success = true;
                    break;
                }
                WeakOrderQueue next = cursor.next;
                if (cursor.owner.get() == null) {
                    // if the thread associated with the queue is gone, unlink it, after
                    // performing a volatile read to confirm there is no data left to collect
                    // we never unlink the first queue, as we don't want to synchronize on updating the head
                    if (cursor.hasFinalData()) {
                        while (true) {
                            if (!cursor.transfer(this)) {
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
            }
            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        void push(Handle item) {
            if (!present.set(item.id)) {
                throw new IllegalStateException("recycled already");
            }

            int size = this.size;
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, size << 1);
            }

            elements[size] = item;
            this.size = size + 1;
        }

        Handle newHandle() {
            return new Handle(maxId++, this);
        }
    }

    // a simpler bitset than the java.util.BitSet, which performs unnecessary work
    private static final class BitSet {
        private long[] bits;

        BitSet(int initialCapacity) {
            assert (initialCapacity & 63) == 0;
            bits = new long[initialCapacity >>> 6];
        }

        boolean set(int index) {
            int bucket = index >>> 6;
            long bit = 1L << (index & 63);
            long prev = bits[bucket];
            bits[bucket] = prev | bit;
            return (prev & bit) == 0;
        }

        void clear(int index) {
            bits[index >>> 6] &= ~(1L << (index & 63));
        }

        int size() {
            return bits.length << 6;
        }

        void ensureCapacity(int newCapacity) {
            assert (newCapacity & 63) == 0;
            bits = Arrays.copyOf(bits, newCapacity);
        }
    }
}
