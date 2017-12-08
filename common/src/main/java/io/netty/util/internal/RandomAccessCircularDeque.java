/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link Deque} based on a circular array (ringbuffer) which also allows random access via
 * {@link java.util.List} methods and offers {@link #remove(int, int)} which allows to do bulk-removals
 * on a given index.
 *
 * {@link #get(int)} is {@code O(1)}.
 */
public final class RandomAccessCircularDeque<E> extends AbstractList<E> implements Deque<E>, RandomAccess {
    private Object[] elements;
    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    public RandomAccessCircularDeque(int initialCapacity) {
        elements = new Object[Math.max(MathUtil.safeFindNextPositivePowerOfTwo(initialCapacity), 2)];
    }

    // Visible for testing only
    boolean storageIsFlat() {
        return head <= tail;
    }

    int head() {
        return head;
    }

    int tail() {
        return tail;
    }

    int capacity() {
        return elements.length;
    }

    private int mask() {
        return elements.length - 1;
    }

    // Increase the capacity (by doubling). After this operation head will also be at index 0 which means the array was
    // flatten and so no wrap over is needed anymore.
    private void increaseCapacity() {
        assert head == tail;
        int currentHead = head;
        int currentLen = elements.length;
        int rightSideElements = currentLen - currentHead;

        // if currentLen overflows we will produce an exception when trying to allocate the new array.
        Object[] newElements = new Object[currentLen << 1];
        System.arraycopy(elements, currentHead, newElements, 0, rightSideElements);
        System.arraycopy(elements, 0, newElements, rightSideElements, currentHead);
        elements = newElements;
        head = 0;
        tail = currentLen;
        // This assert statement is super expensive, so only enable for debugging.
        //assert checkArrayIsNulledOut() == -1;
    }

    private int incrementIdx(int value) {
        return (value + 1) & mask();
    }

    private int decrementIdx(int value) {
        return (value - 1) & mask();
    }

    private E mayThrowNoSuchElementException(E element) {
        if (element == null) {
            throw new NoSuchElementException();
        }
        return element;
    }

    private void flattenArray() {
        int currentHead = head;
        int currentLen = elements.length;
        int rightSideElements = currentLen - currentHead;

        // First copy over everything from idx 0 - tail and ensure we have enough space left to put in what
        // was contained from head to the end of the array.
        System.arraycopy(elements, 0, elements, rightSideElements, tail);
        System.arraycopy(elements, head, elements, 0, rightSideElements);

        head = 0;
        tail += rightSideElements;
        Arrays.fill(elements, tail, elements.length, null);
        // This assert statement is super expensive, so only enable for debugging.
        // assert checkArrayIsNulledOut() == -1;
    }

    private void flattenArrayIfNeeded() {
        if (tail < head) {
            flattenArray();
        }
    }

    private int checkArrayIsNulledOut() {
        for (int idx = tail; idx < elements.length; idx++) {
            if (elements[idx] != null) {
                return idx;
            }
        }
        return -1;
    }

    private void increaseCapacityIfNeeded() {
        if (tail == head) {
            // No more space!
            increaseCapacity();
        }
    }

    private int idx(int index) {
        return (head + index) & mask();
    }

    private int lastIdx() {
        return (tail - 1) & mask();
    }

    @SuppressWarnings("unchecked")

    private E elementAt(int index) {
        return (E) elements[index];
    }

    @Override
    public void addFirst(E e) {
        checkNotNull(e, "e");
        head = (head - 1) & mask();

        assert elements[head] == null;
        elements[head] = e;
        increaseCapacityIfNeeded();
    }

    @Override
    public void addLast(E e) {
        checkNotNull(e, "e");
        assert elements[tail] == null;

        elements[tail] = e;
        tail = incrementIdx(tail);
        increaseCapacityIfNeeded();
    }

    @Override
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override
    public E removeFirst() {
        return mayThrowNoSuchElementException(pollFirst());
    }

    @Override
    public E removeLast() {
        return mayThrowNoSuchElementException(pollLast());
    }

    @Override
    public E pollFirst() {
        E result = elementAt(head);
        if (result != null) {
            elements[head] = null;     // Must null out slot
            head = incrementIdx(head);
            if (head == tail) {
                // queue is empty so reset head and tail to 0 as we optimized for the flatten array use-case
                head = tail = 0;
            }
        } else {
            // Element is null if the queue was empty.
            assert tail == head;
        }
        return result;
    }

    @Override
    public E getFirst() {
        return mayThrowNoSuchElementException(peekFirst());
    }

    @Override
    public E getLast() {
        return mayThrowNoSuchElementException(peekLast());
    }

    @Override
    public E peekFirst() {
        E result = elementAt(head);
        // Can only be null if the queue was empty.
        assert result != null || tail == head;
        return result;
    }

    @Override
    public E peekLast() {
        E result = elementAt(lastIdx());
        // Can only be null if the queue was empty.
        assert result != null || tail == head;
        return result;
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        int idx = indexOf(o);
        if (idx != -1) {
            remove(idx);
            return true;
        }
        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        int idx = lastIndexOf(o);
        if (idx != -1) {
            remove(idx);
            return true;
        }
        return false;
    }

    @Override
    public void push(E e) {
        addFirst(e);
    }

    @Override
    public E pop() {
        return removeFirst();
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new DequeIterator();
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        E element = elementAt(idx(index));
        assert element != null;
        return element;
    }

    @Override
    public E set(int index, E e) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        checkNotNull(e, "e");
        int idx = idx(index);
        E element = elementAt(idx);
        assert element != null;
        elements[idx] = e;
        return element;
    }

    @Override
    public E remove(int index) {
        E element = get(index);
        remove(index, 1);
        return element;
    }

    @Override
    public int indexOf(Object o) {
        // null not supported.
        if (o != null) {
            int idx = head;
            int end = tail;
            do {
                E element = elementAt(idx);
                if (o.equals(element)) {
                    // Remove the element now.
                    return idx;
                }
                idx = incrementIdx(idx);
            } while (idx != end);
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        // null not supported.
        if (o != null) {
            int idx = decrementIdx(tail);
            int end = head;
            do {
                E element = elementAt(idx);
                if (o.equals(element)) {
                    return idx;
                }
                idx = decrementIdx(idx);
            } while (idx != end);
        }
        return -1;
    }

    /**
     * Removes {@code numElements} starting on the given {@code index}.
     */
    public void remove(int index, int numElements) {
        if (index < 0 || index + numElements > size()) {
            throw new IndexOutOfBoundsException();
        }
        if (numElements == 0) {
            return;
        }
        if (index == 0) {
            if (numElements == size()) {
                clear();
                return;
            }
            do {
                E element = poll();
                assert element != null;
            } while (--numElements > 0);
            return;
        }

        // To keep it simple we just flatten-out the array for now.
        // TODO: This could be improved to not flatten the array at all.
        flattenArrayIfNeeded();

        removeFromFlatArray(index, numElements);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        remove(fromIndex, toIndex - fromIndex);
    }

    private void removeFromFlatArray(int index, int numElements) {
        assert head < tail;
        // no wrapping as its a flat array!
        int idx = idx(index);

        assert idx < tail;

        int move = size() - index - numElements;
        System.arraycopy(elements, idx + numElements, elements, idx, move);
        Arrays.fill(elements, idx + move, elements.length, null);
        tail -= numElements;

        // This assert statement is super expensive, so only enable for debugging.
        //assert checkArrayIsNulledOut() == -1;
    }

    @Override
    public void add(int index, E e) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException();
        }

        if (index == size()) {
            add(e);
            return;
        }

        // To keep it simple we just flatten-out the array for now.
        // TODO: This could be improved to not flatten the array at all.
        flattenArrayIfNeeded();

        addToFlatArray(index, e);
    }

    private void addToFlatArray(int index, E e) {
        assert head < tail;
        // no wrapping as its a flat array!
        int idx = idx(index);
        assert idx < tail;

        System.arraycopy(elements, idx, elements, idx + 1, tail - idx);
        elements[idx] = e;
        tail = incrementIdx(tail);
        if (tail == head) {
            // No more space!
            increaseCapacity();
        }
    }

    @Override
    public boolean offer(E e) {
        return add(e);
    }

    @Override
    public E remove() {
        return removeFirst();
    }

    @Override
    public E poll() {
        return pollFirst();
    }

    @Override
    public E pollLast() {
        int idx = lastIdx();
        E result = elementAt(idx);
        if (result != null) {
            elements[idx] = null;
            tail = idx;
        } else {
            // Element is null if the queue was empty.
            assert tail == head;
        }

        return result;
    }
    @Override
    public E element() {
       return getFirst();
    }

    @Override
    public E peek() {
       return peekFirst();
    }

    @Override
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new QueueIterator();
    }

    @Override
    public int size() {
        return (tail - head) & mask();
    }

    @Override
    public boolean isEmpty() {
        return head == tail;
    }

    @Override
    public void clear() {
        if (isEmpty()) {
            return;
        }
        int start = head;
        int end = tail;
        head = tail = 0;
        do {
            elements[start] = null;
            start = incrementIdx(start);
        } while (start != end);
    }

    @Override
    public boolean contains(Object o) {
        // null not supported.
        if (o != null) {
            int start = head;
            int end = tail;
            head = tail = 0;
            do {
                E element = elementAt(start);
                if (o.equals(element)) {
                    return true;
                }
                start = incrementIdx(start);
            } while (start != end);
        }

        return false;
    }

    private final class QueueIterator implements Iterator<E> {
        private final int end = tail;

        private int idx = head;

        @Override
        public boolean hasNext() {
            return idx != end;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            @SuppressWarnings("unchecked")
            E result = elementAt(idx);
            if (tail != end) {
                // Seems like we had some concurrent modification...
                throw new ConcurrentModificationException();
            }
            idx = incrementIdx(idx);
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read-only");
        }
    }

    private final class DequeIterator implements Iterator<E> {
        private final int end = head;

        private int idx = tail;

        @Override
        public boolean hasNext() {
            return idx != end;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            idx = decrementIdx(idx);
            @SuppressWarnings("unchecked")
            E result = elementAt(idx);
            if (head != end) {
                // Seems like we had some concurrent modification...
                throw new ConcurrentModificationException();
            }
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read-only");
        }
    }
}
