/*
 * Copyright 2015 The Netty Project
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

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.PriorityQueueNode.INDEX_NOT_IN_QUEUE;

/**
 * A priority queue which uses natural ordering of elements. Elements are also required to be of type
 * {@link PriorityQueueNode} for the purpose of maintaining the index in the priority queue.
 * @param <T> The object that is maintained in the queue.
 */
public final class PriorityQueue<T extends PriorityQueueNode<T>> extends AbstractQueue<T> implements Queue<T> {
    @SuppressWarnings("rawtypes")
    private static final PriorityQueueNode[] EMPTY_QUEUE = new PriorityQueueNode[0];
    private T[] queue;
    private int size;

    public PriorityQueue() {
        this(8);
    }

    @SuppressWarnings("unchecked")
    public PriorityQueue(int initialSize) {
        queue = (T[]) (initialSize != 0 ? new PriorityQueueNode[initialSize] : EMPTY_QUEUE);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof PriorityQueueNode)) {
            return false;
        }
        PriorityQueueNode<?> node = (PriorityQueueNode<?>) o;
        int i = node.priorityQueueIndex();
        return i >= 0 && i < size && node.equals(queue[i]);
    }

    @Override
    public void clear() {
        for (int i = 0; i < size; ++i) {
            T node = queue[i];
            if (node != null) {
                node.priorityQueueIndex(INDEX_NOT_IN_QUEUE);
                queue[i] = null;
            }
        }
        size = 0;
    }

    @Override
    public boolean offer(T e) {
        checkNotNull(e, "e");
        if (e.priorityQueueIndex() != INDEX_NOT_IN_QUEUE) {
            throw new IllegalArgumentException("e.priorityQueueIndex(): " + e.priorityQueueIndex() +
                    " (expected: " + INDEX_NOT_IN_QUEUE + ")");
        }

        // Check that the array capacity is enough to hold values by doubling capacity.
        if (size >= queue.length) {
            // Use a policy which allows for a 0 initial capacity. Same policy as JDK's priority queue, double when
            // "small", then grow by 50% when "large".
            queue = Arrays.copyOf(queue, queue.length + ((queue.length < 64) ?
                                                         (queue.length + 2) :
                                                         (queue.length >>> 1)));
        }

        bubbleUp(size++, e);
        return true;
    }

    @Override
    public T poll() {
        if (size == 0) {
            return null;
        }
        T result = queue[0];
        result.priorityQueueIndex(INDEX_NOT_IN_QUEUE);

        T last = queue[--size];
        queue[size] = null;
        if (size != 0) { // Make sure we don't add the last element back.
            bubbleDown(0, last);
        }

        return result;
    }

    @Override
    public T peek() {
        return (size == 0) ? null : queue[0];
    }

    @Override
    public boolean remove(Object o) {
        if (!contains(o)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        T node = (T) o;
        int i = node.priorityQueueIndex();

        node.priorityQueueIndex(INDEX_NOT_IN_QUEUE);
        if (--size == 0 || size == i) {
            // If there are no node left, or this is the last node in the array just remove and return.
            queue[i] = null;
            return true;
        }

        // Move the last element where node currently lives in the array.
        T moved = queue[i] = queue[size];
        queue[size] = null;
        // priorityQueueIndex will be updated below in bubbleUp or bubbleDown

        // Make sure the moved node still preserves the min-heap properties.
        if (node.compareTo(moved) < 0) {
            bubbleDown(i, moved);
        } else {
            bubbleUp(i, moved);
        }
        return true;
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(queue, size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X> X[] toArray(X[] a) {
        if (a.length < size) {
            return (X[]) Arrays.copyOf(queue, size, a.getClass());
        }
        System.arraycopy(queue, 0, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    /**
     * This iterator does not return elements in any particular order.
     */
    @Override
    public Iterator<T> iterator() {
        return new PriorityQueueIterator();
    }

    private final class PriorityQueueIterator implements Iterator<T> {
        private int index;

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public T next() {
            if (index >= size) {
                throw new NoSuchElementException();
            }

            return queue[index++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }

    private void bubbleDown(int k, T node) {
        final int half = size >>> 1;
        while (k < half) {
            // Compare node to the children of index k.
            int iChild = (k << 1) + 1;
            T child = queue[iChild];

            // Make sure we get the smallest child to compare against.
            int rightChild = iChild + 1;
            if (rightChild < size && child.compareTo(queue[rightChild]) > 0) {
                child = queue[iChild = rightChild];
            }
            // If the bubbleDown node is less than or equal to the smallest child then we will preserve the min-heap
            // property by inserting the bubbleDown node here.
            if (node.compareTo(child) <= 0) {
                break;
            }

            // Bubble the child up.
            queue[k] = child;
            child.priorityQueueIndex(k);

            // Move down k down the tree for the next iteration.
            k = iChild;
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node;
        node.priorityQueueIndex(k);
    }

    private void bubbleUp(int k, T node) {
        while (k > 0) {
            int iParent = (k - 1) >>> 1;
            T parent = queue[iParent];

            // If the bubbleUp node is less than the parent, then we have found a spot to insert and still maintain
            // min-heap properties.
            if (node.compareTo(parent) >= 0) {
                break;
            }

            // Bubble the parent down.
            queue[k] = parent;
            parent.priorityQueueIndex(k);

            // Move k up the tree for the next iteration.
            k = iParent;
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node;
        node.priorityQueueIndex(k);
    }
}
