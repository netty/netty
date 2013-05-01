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
/*
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */
package io.netty.buffer;

import java.lang.reflect.Array;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Default {@link MessageBuf} implementation.
 *
 * You should use {@link Unpooled#messageBuffer()} to create an instance
 *
 */
public class DefaultMessageBuf<T> extends AbstractMessageBuf<T> {

    private static final int MIN_INITIAL_CAPACITY = 8;
    private static final Object[] PLACEHOLDER = new Object[2];

    private T[] elements;
    private int head;
    private int tail;

    protected DefaultMessageBuf() {
        this(MIN_INITIAL_CAPACITY << 1);
    }

    protected DefaultMessageBuf(int initialCapacity) {
        this(initialCapacity, Integer.MAX_VALUE);
    }

    protected DefaultMessageBuf(int initialCapacity, int maxCapacity) {
        super(maxCapacity);

        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 0)");
        }
        if (maxCapacity < initialCapacity) {
            throw new IllegalArgumentException(
                    "maxCapacity: " + maxCapacity + " (expected: >= initialCapacity(" + initialCapacity + ')');
        }

        // Find the best power of two to hold elements.
        // Tests "<=" because arrays aren't kept full.
        if (initialCapacity >= MIN_INITIAL_CAPACITY) {
            initialCapacity |= initialCapacity >>>  1;
            initialCapacity |= initialCapacity >>>  2;
            initialCapacity |= initialCapacity >>>  4;
            initialCapacity |= initialCapacity >>>  8;
            initialCapacity |= initialCapacity >>> 16;
            initialCapacity ++;

            if (initialCapacity < 0) {  // Too many elements, must back off
                initialCapacity >>>= 1; // Good luck allocating 2 ^ 30 elements
            }
        } else {
            initialCapacity = MIN_INITIAL_CAPACITY;
        }

        elements = cast(new Object[initialCapacity]);
    }

    @Override
    protected void deallocate() {
        head = 0;
        tail = 0;
        elements = cast(PLACEHOLDER);
    }

    @Override
    public boolean offer(T e) {
        if (e == null) {
            throw new NullPointerException();
        }

        ensureAccessible();
        if (!isWritable()) {
            return false;
        }

        elements[tail] = e;
        if ((tail = tail + 1 & elements.length - 1) == head) {
            doubleCapacity();
        }

        return true;
    }

    private void doubleCapacity() {
        assert head == tail;

        int p = head;
        int n = elements.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException("Sorry, deque too big");
        }
        Object[] a = new Object[newCapacity];
        System.arraycopy(elements, p, a, 0, r);
        System.arraycopy(elements, 0, a, r, p);
        elements = cast(a);
        head = 0;
        tail = n;
    }

    @Override
    public T poll() {
        ensureAccessible();
        int h = head;
        T result = elements[h]; // Element is null if deque empty
        if (result == null) {
            return null;
        }
        elements[h] = null;     // Must null out slot
        head = h + 1 & elements.length - 1;
        return result;
    }

    @Override
    public T peek() {
        ensureAccessible();
        return elements[head]; // elements[head] is null if deque empty
    }

    @Override
    public boolean remove(Object o) {
        if (o == null) {
            return false;
        }

        ensureAccessible();
        int mask = elements.length - 1;
        int i = head;
        T x;
        while ((x = elements[i]) != null) {
            if (o.equals(x)) {
                delete(i);
                return true;
            }
            i = i + 1 & mask;
        }
        return false;
    }

    private boolean delete(int i) {
        assert elements[tail] == null;
        assert head == tail ? elements[head] == null
                            : elements[head] != null && elements[tail - 1 & elements.length - 1] != null;
        assert elements[head - 1 & elements.length - 1] == null;

        final T[] elements = this.elements;
        final int mask = elements.length - 1;
        final int h = head;
        final int t = tail;
        final int front = i - h & mask;
        final int back  = t - i & mask;

        // Invariant: head <= i < tail mod circularity
        if (front >= (t - h & mask)) {
            throw new ConcurrentModificationException();
        }

        // Optimize for least element motion
        if (front < back) {
            if (h <= i) {
                System.arraycopy(elements, h, elements, h + 1, front);
            } else { // Wrap around
                System.arraycopy(elements, 0, elements, 1, i);
                elements[0] = elements[mask];
                System.arraycopy(elements, h, elements, h + 1, mask - h);
            }
            elements[h] = null;
            head = h + 1 & mask;
            return false;
        } else {
            if (i < t) { // Copy the null tail as well
                System.arraycopy(elements, i + 1, elements, i, back);
                tail = t - 1;
            } else { // Wrap around
                System.arraycopy(elements, i + 1, elements, i, mask - i);
                elements[mask] = elements[0];
                System.arraycopy(elements, 1, elements, 0, t);
                tail = t - 1 & mask;
            }
            return true;
        }
    }

    @Override
    public int size() {
        return tail - head & elements.length - 1;
    }

    @Override
    public boolean isEmpty() {
        return head == tail;
    }

    @Override
    public Iterator<T> iterator() {
        ensureAccessible();
        return new Itr();
    }

    @Override
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }

        ensureAccessible();
        final int mask = elements.length - 1;
        int i = head;
        Object e;
        while ((e = elements[i]) != null) {
            if (o.equals(e)) {
                return true;
            }
            i = i + 1 & mask;
        }

        return false;
    }

    @Override
    public void clear() {
        ensureAccessible();
        int head = this.head;
        int tail = this.tail;
        if (head != tail) {
            this.head = this.tail = 0;
            final int mask = elements.length - 1;
            int i = head;
            do {
                elements[i] = null;
                i = i + 1 & mask;
            } while (i != tail);
        }
    }

    @Override
    public Object[] toArray() {
        ensureAccessible();
        return copyElements(new Object[size()]);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        ensureAccessible();
        int size = size();
        if (a.length < size) {
            a = cast(Array.newInstance(a.getClass().getComponentType(), size));
        }
        copyElements(a);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    private <U> U[] copyElements(U[] a) {
        if (head < tail) {
            System.arraycopy(elements, head, cast(a), 0, size());
        } else if (head > tail) {
            int headPortionLen = elements.length - head;
            System.arraycopy(elements, head, cast(a), 0, headPortionLen);
            System.arraycopy(elements, 0, cast(a), headPortionLen, tail);
        }
        return a;
    }

    @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
    private static <T> T[] cast(Object a) {
        return (T[]) a;
    }

    private class Itr implements Iterator<T> {
        private int cursor = head;
        private int fence = tail;
        private int lastRet = -1;

        @Override
        public boolean hasNext() {
            ensureAccessible();
            return cursor != fence;
        }

        @Override
        public T next() {
            ensureAccessible();
            if (cursor == fence) {
                throw new NoSuchElementException();
            }
            T result = elements[cursor];
            // This check doesn't catch all possible comodifications,
            // but does catch the ones that corrupt traversal
            if (tail != fence || result == null) {
                throw new ConcurrentModificationException();
            }
            lastRet = cursor;
            cursor = cursor + 1 & elements.length - 1;
            return result;
        }

        @Override
        public void remove() {
            ensureAccessible();
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            if (delete(lastRet)) { // if left-shifted, undo increment in next()
                cursor = cursor - 1 & elements.length - 1;
                fence = tail;
            }
            lastRet = -1;
        }
    }
}
