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
/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package io.netty.util.internal;


import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A lock-free concurrent {@link java.util.Queue} implementations for single-consumer multiple-producer pattern.
 * <strong>It's important is is only used for this as otherwise it is not thread-safe.</strong>
 *
 * This implementation is based on:
 * <ul>
 *   <li><a href="https://github.com/akka/akka/blob/wip-2.2.3-for-scala-2.11/akka-actor/src/main/java/akka/dispatch/
 *   AbstractNodeQueue.java">AbstractNodeQueue</a></li>
 *   <li><a href="http://www.1024cores.net/home/lock-free-algorithms/
 *   queues/non-intrusive-mpsc-node-based-queue">Non intrusive MPSC node based queue</a></li>
 * </ul>
 *
 */
@SuppressWarnings("serial")
final class MpscLinkedQueue extends AtomicReference<OneTimeTask> implements Queue<Runnable> {
    private static final long tailOffset;

    static {
        try {
            tailOffset = PlatformDependent.objectFieldOffset(
                    MpscLinkedQueue.class.getDeclaredField("tail"));
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    // Extends AtomicReference for the "head" slot (which is the one that is appended to)
    // since Unsafe does not expose XCHG operation intrinsically
    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
    private volatile OneTimeTask tail;

    MpscLinkedQueue() {
        final OneTimeTask task = new OneTimeTaskAdapter(null);
        tail = task;
        set(task);
    }

    @Override
    public boolean add(Runnable runnable) {
        if (runnable instanceof OneTimeTask) {
            OneTimeTask node = (OneTimeTask) runnable;
            node.setNext(null);
            getAndSet(node).setNext(node);
        } else {
            final OneTimeTask n = new OneTimeTaskAdapter(runnable);
            getAndSet(n).setNext(n);
        }
        return true;
    }

    @Override
    public boolean offer(Runnable runnable) {
        return add(runnable);
    }

    @Override
    public Runnable remove() {
        Runnable task = poll();
        if (task == null) {
            throw new NoSuchElementException();
        }
        return task;
    }

    @Override
    public Runnable poll() {
        final OneTimeTask next = peekTask();
        if (next == null) {
            return null;
        }
        final OneTimeTask ret = next;
        PlatformDependent.putOrderedObject(this, tailOffset, next);
        return unwrapIfNeeded(ret);
    }

    @Override
    public Runnable element() {
        final OneTimeTask next = peekTask();
        if (next == null) {
            throw new NoSuchElementException();
        }
        return unwrapIfNeeded(next);
    }

    @Override
    public Runnable peek() {
        final OneTimeTask next = peekTask();
        if (next == null) {
            return null;
        }
        return unwrapIfNeeded(next);
    }

    @Override
    public int size() {
        int count = 0;
        OneTimeTask n = peekTask();
        for (;;) {
            if (n == null) {
                break;
            }
            count++;
            n = n.next();
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private OneTimeTask peekTask() {
        for (;;) {
            final OneTimeTask tail = (OneTimeTask) PlatformDependent.getObjectVolatile(this, tailOffset);
            final OneTimeTask next = tail.next();
            if (next != null || get() == tail) {
                return next;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return peek() == null;
    }

    @Override
    public boolean contains(Object o) {
        OneTimeTask n = peekTask();
        for (;;) {
            if (n == null) {
                break;
            }
            if (unwrapIfNeeded(n) == o) {
                return true;
            }
            n = n.next();
        }
        return false;
    }

    @Override
    public Iterator<Runnable> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o: c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends Runnable> c) {
        for (Runnable r: c) {
            add(r);
        }
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {
        for (;;) {
            if (poll() == null) {
                break;
            }
        }
    }

    /**
     * Unwrap {@link OneTimeTask} if needed and so return the proper queued task.
     */
    private static Runnable unwrapIfNeeded(OneTimeTask task) {
        if (task instanceof OneTimeTaskAdapter) {
            return ((OneTimeTaskAdapter) task).task;
        }
        return task;
    }

    private static final class OneTimeTaskAdapter extends OneTimeTask {
        private final Runnable task;

        OneTimeTaskAdapter(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }
}
