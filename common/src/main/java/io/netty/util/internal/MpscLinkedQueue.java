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

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class MpscLinkedQueuePad0<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueHeadRef<E> extends MpscLinkedQueuePad0<E> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<MpscLinkedQueueHeadRef, MpscLinkedQueueNode> UPDATER =
        newUpdater(MpscLinkedQueueHeadRef.class, MpscLinkedQueueNode.class, "headRef");
    private volatile MpscLinkedQueueNode<E> headRef;

    protected final MpscLinkedQueueNode<E> headRef() {
        return headRef;
    }
    protected final void headRef(MpscLinkedQueueNode<E> val) {
        headRef = val;
    }
    protected final void lazySetHeadRef(MpscLinkedQueueNode<E> newVal) {
        UPDATER.lazySet(this, newVal);
    }
}

abstract class MpscLinkedQueuePad1<E> extends MpscLinkedQueueHeadRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueTailRef<E> extends MpscLinkedQueuePad1<E> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<MpscLinkedQueueTailRef, MpscLinkedQueueNode> UPDATER =
        newUpdater(MpscLinkedQueueTailRef.class, MpscLinkedQueueNode.class, "tailRef");
    private volatile MpscLinkedQueueNode<E> tailRef;
    protected final MpscLinkedQueueNode<E> tailRef() {
        return tailRef;
    }
    protected final void tailRef(MpscLinkedQueueNode<E> val) {
        tailRef = val;
    }
    @SuppressWarnings("unchecked")
    protected final MpscLinkedQueueNode<E> getAndSetTailRef(MpscLinkedQueueNode<E> newVal) {
        return (MpscLinkedQueueNode<E>) UPDATER.getAndSet(this, newVal);
    }
}

/**
 * A lock-free concurrent single-consumer multi-producer {@link Queue}.
 * It allows multiple producer threads to perform the following operations simultaneously:
 * <ul>
 * <li>{@link #offer(Object)}, {@link #add(Object)}, and {@link #addAll(Collection)}</li>
 * <li>All other read-only operations:
 *     <ul>
 *     <li>{@link #contains(Object)} and {@link #containsAll(Collection)}</li>
 *     <li>{@link #element()}, {@link #peek()}</li>
 *     <li>{@link #size()} and {@link #isEmpty()}</li>
 *     <li>{@link #iterator()} (except {@link Iterator#remove()}</li>
 *     <li>{@link #toArray()} and {@link #toArray(Object[])}</li>
 *     </ul>
 * </li>
 * </ul>
 * .. while only one consumer thread is allowed to perform the following operations exclusively:
 * <ul>
 * <li>{@link #poll()} and {@link #remove()}</li>
 * <li>{@link #remove(Object)}, {@link #removeAll(Collection)}, and {@link #retainAll(Collection)}</li>
 * <li>{@link #clear()}</li> {@link #}
 * </ul>
 *
 * <strong>The behavior of this implementation is undefined if you perform the operations for a consumer thread only
 * from multiple threads.</strong>
 *
 * The initial implementation is based on:
 * <ul>
 *   <li><a href="http://goo.gl/sZE3ie">Non-intrusive MPSC node based queue</a> from 1024cores.net</li>
 *   <li><a href="http://goo.gl/O0spmV">AbstractNodeQueue</a> from Akka</li>
 * </ul>
 * and adopted padded head node changes from:
 * <ul>
 * <li><a href="http://goo.gl/bD5ZUV">MpscPaddedQueue</a> from RxJava</li>
 * </ul>
 * data structure modified to avoid false sharing between head and tail Ref as per implementation of MpscLinkedQueue
 * on <a href="https://github.com/JCTools/JCTools">JCTools project</a>.
 */
public final class MpscLinkedQueue<E> extends MpscLinkedQueueTailRef<E> implements Queue<E> {
    // offer() occurs at the tail of the linked list.
    // poll() occurs at the head of the linked list.
    //
    // Resulting layout is:
    //
    //   head --next--> 1st element --next--> 2nd element --next--> ... tail (last element)
    //
    // where the head is a dummy node whose value is null.
    //
    // offer() appends a new node next to the tail using AtomicReference.getAndSet()
    // poll() removes head from the linked list and promotes the 1st element to the head,
    // setting its value to null if possible.
    //
    // Also note that this class extends AtomicReference for the "tail" slot (which is the one that is appended to)
    // since Unsafe does not expose XCHG operation intrinsically.
    MpscLinkedQueue() {
        MpscLinkedQueueNode<E> tombstone = new DefaultNode<E>(null);
        headRef(tombstone);
        tailRef(tombstone);
    }

    /**
     * Returns the node right next to the head, which contains the first element of this queue.
     */
    private MpscLinkedQueueNode<E> peekNode() {
        for (;;) {
            final MpscLinkedQueueNode<E> head = headRef();
            final MpscLinkedQueueNode<E> next = head.next();
            if (next != null) {
                return next;
            }
            if (head == tailRef()) {
                return null;
            }

            // If we are here, it means:
            // * offer() is adding the first element, and
            // * it's between replaceTail(newTail) and oldTail.setNext(newTail).
            //   (i.e. next == oldTail and oldTail.next == null and head == oldTail != newTail)
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean offer(E value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        final MpscLinkedQueueNode<E> newTail;
        if (value instanceof MpscLinkedQueueNode) {
            newTail = (MpscLinkedQueueNode<E>) value;
            newTail.setNext(null);
        } else {
            newTail = new DefaultNode<E>(value);
        }

        MpscLinkedQueueNode<E> oldTail = getAndSetTailRef(newTail);
        oldTail.setNext(newTail);
        return true;
    }

    @Override
    public E poll() {
        final MpscLinkedQueueNode<E> next = peekNode();
        if (next == null) {
            return null;
        }

        // next becomes a new head.
        MpscLinkedQueueNode<E> oldHead = headRef();
        // Similar to 'headRef.node = next', but slightly faster (storestore vs loadstore)
        // See: http://robsjava.blogspot.com/2013/06/a-faster-volatile.html
        // See: http://psy-lob-saw.blogspot.com/2012/12/atomiclazyset-is-performance-win-for.html
        lazySetHeadRef(next);

        // Break the linkage between the old head and the new head.
        oldHead.setNext(null);

        return next.clearMaybe();
    }

    @Override
    public E peek() {
        final MpscLinkedQueueNode<E> next = peekNode();
        if (next == null) {
            return null;
        }
        return next.value();
    }

    @Override
    public int size() {
        int count = 0;
        MpscLinkedQueueNode<E> n = peekNode();
        for (;;) {
            if (n == null) {
                break;
            }
            count ++;
            n = n.next();
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return peekNode() == null;
    }

    @Override
    public boolean contains(Object o) {
        MpscLinkedQueueNode<E> n = peekNode();
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
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private MpscLinkedQueueNode<E> node = peekNode();

            @Override
            public boolean hasNext() {
                return node != null;
            }

            @Override
            public E next() {
                MpscLinkedQueueNode<E> node = this.node;
                if (node == null) {
                    throw new NoSuchElementException();
                }
                E value = node.value();
                this.node = node.next();
                return value;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public boolean add(E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("queue full");
    }

    @Override
    public E remove() {
        E e = poll();
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @Override
    public E element() {
        E e = peek();
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @Override
    public Object[] toArray() {
        final Object[] array = new Object[size()];
        final Iterator<E> it = iterator();
        for (int i = 0; i < array.length; i ++) {
            if (it.hasNext()) {
                array[i] = it.next();
            } else {
                return Arrays.copyOf(array, i);
            }
        }
        return array;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final int size = size();
        final T[] array;
        if (a.length >= size) {
            array = a;
        } else {
            array = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        }

        final Iterator<E> it = iterator();
        for (int i = 0; i < array.length; i++) {
            if (it.hasNext()) {
                array[i] = (T) it.next();
            } else {
                if (a == array) {
                    array[i] = null;
                    return array;
                }

                if (a.length < i) {
                    return Arrays.copyOf(array, i);
                }

                System.arraycopy(array, 0, a, 0, i);
                if (a.length > i) {
                    a[i] = null;
                }
                return a;
            }
        }
        return array;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object e: c) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (c == null) {
            throw new NullPointerException("c");
        }
        if (c == this) {
            throw new IllegalArgumentException("c == this");
        }

        boolean modified = false;
        for (E e: c) {
            add(e);
            modified = true;
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        while (poll() != null) {
            continue;
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        for (E e: this) {
            out.writeObject(e);
        }
        out.writeObject(null);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        final MpscLinkedQueueNode<E> tombstone = new DefaultNode<E>(null);
        headRef(tombstone);
        tailRef(tombstone);

        for (;;) {
            @SuppressWarnings("unchecked")
            E e = (E) in.readObject();
            if (e == null) {
                break;
            }
            add(e);
        }
    }

    private static final class DefaultNode<T> extends MpscLinkedQueueNode<T> implements Serializable {

        private static final long serialVersionUID = 1006745279405945948L;

        private T value;

        DefaultNode(T value) {
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        protected T clearMaybe() {
            T value = this.value;
            this.value = null;
            return value;
        }
    }
}
