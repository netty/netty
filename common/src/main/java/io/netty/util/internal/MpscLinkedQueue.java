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
public final class MpscLinkedQueue<T> extends AtomicReference<MpscLinkedQueue.Node<T>> implements Queue<T> {
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
    private volatile Node<T> tail;

    MpscLinkedQueue() {
        final Node<T> task = new DefaultNode<T>(null);
        tail = task;
        set(task);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean add(T value) {
        if (value instanceof Node) {
            Node<T> node = (Node<T>) value;
            node.setNext(null);
            getAndSet(node).setNext(node);
        } else {
            final Node<T> n = new DefaultNode<T>(value);
            getAndSet(n).setNext(n);
        }
        return true;
    }

    @Override
    public boolean offer(T value) {
        return add(value);
    }

    @Override
    public T remove() {
        T v = poll();
        if (v == null) {
            throw new NoSuchElementException();
        }
        return v;
    }

    @Override
    public T poll() {
        final Node<T> next = peekNode();
        if (next == null) {
            return null;
        }
        final Node<T> ret = next;
        PlatformDependent.putOrderedObject(this, tailOffset, next);
        return ret.value();
    }

    @Override
    public T element() {
        final Node<T> next = peekNode();
        if (next == null) {
            throw new NoSuchElementException();
        }
        return next.value();
    }

    @Override
    public T peek() {
        final Node<T> next = peekNode();
        if (next == null) {
            return null;
        }
        return next.value();
    }

    @Override
    public int size() {
        int count = 0;
        Node<T> n = peekNode();
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
    private Node<T> peekNode() {
        for (;;) {
            final Node<T> tail = (Node<T>) PlatformDependent.getObjectVolatile(this, tailOffset);
            final Node<T> next = tail.next();
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
        Node<T> n = peekNode();
        for (;;) {
            if (n == null) {
                break;
            }
            if (n.value() == o) {
                return true;
            }
            n = n.next();
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
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
    public boolean addAll(Collection<? extends T> c) {
        for (T r: c) {
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

    private static final class DefaultNode<T> extends Node<T> {
        private final T value;

        DefaultNode(T value) {
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }
    }

    public abstract static class Node<T> {

        private static final long nextOffset;

        static {
            if (PlatformDependent0.hasUnsafe()) {
                try {
                    nextOffset = PlatformDependent.objectFieldOffset(
                            Node.class.getDeclaredField("tail"));
                } catch (Throwable t) {
                    throw new ExceptionInInitializerError(t);
                }
            } else {
                nextOffset = -1;
            }
        }

        @SuppressWarnings("unused")
        private volatile Node<T> tail;

        // Only use from MpscLinkedQueue and so we are sure Unsafe is present
        @SuppressWarnings("unchecked")
        final Node<T> next() {
            return (Node<T>) PlatformDependent.getObjectVolatile(this, nextOffset);
        }

        // Only use from MpscLinkedQueue and so we are sure Unsafe is present
        final void setNext(final Node<T> newNext) {
            PlatformDependent.putOrderedObject(this, nextOffset, newNext);
        }

        public abstract T value();
    }
}
