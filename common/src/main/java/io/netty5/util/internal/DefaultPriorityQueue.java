/*
 * Copyright 2015 The Netty Project
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
package io.netty5.util.internal;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.locks.StampedLock;

import static io.netty5.util.internal.PriorityQueueNode.INDEX_NOT_IN_QUEUE;
import static java.util.Objects.requireNonNull;

/**
 * A priority queue which uses natural ordering of elements. Elements are also required to be of type
 * {@link PriorityQueueNode} for the purpose of maintaining the index in the priority queue.
 * <p>
 * This priority queue is thread-safe, and uses locking internally.
 *
 * @param <T> The object that is maintained in the queue.
 */
public final class DefaultPriorityQueue<T extends PriorityQueueNode> extends AbstractQueue<T>
                                                                     implements PriorityQueue<T> {
    private static final PriorityQueueNode[] EMPTY_ARRAY = new PriorityQueueNode[0];
    private final Comparator<T> comparator;
    private final StampedLock lock;
    private T[] queue;
    private int size;

    @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
    public DefaultPriorityQueue(Comparator<T> comparator, int initialSize) {
        this.comparator = requireNonNull(comparator, "comparator");
        lock = new StampedLock();
        queue = (T[]) (initialSize != 0 ? new PriorityQueueNode[initialSize] : EMPTY_ARRAY);
    }

    @Override
    public int size() {
        long stamp = lock.readLock();
        try {
            return size;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean isEmpty() {
        long stamp = lock.readLock();
        try {
            return size == 0;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean contains(Object o) {
        long stamp = lock.readLock();
        try {
            if (!(o instanceof PriorityQueueNode)) {
                return false;
            }
            PriorityQueueNode node = (PriorityQueueNode) o;
            return contains(node, node.priorityQueueIndex(this));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public boolean containsTyped(T node) {
        long stamp = lock.readLock();
        try {
            return contains(node, node.priorityQueueIndex(this));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public void clear() {
        long stamp = lock.writeLock();
        try {
            for (int i = 0; i < size; ++i) {
                T node = queue[i];
                if (node != null) {
                    node.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE);
                    queue[i] = null;
                }
            }
            size = 0;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void clearIgnoringIndexes() {
        long stamp = lock.writeLock();
        try {
            size = 0;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean offer(T e) {
        long stamp = lock.writeLock();
        try {
            if (e.priorityQueueIndex(this) != INDEX_NOT_IN_QUEUE) {
                throw new IllegalArgumentException("e.priorityQueueIndex(): " + e.priorityQueueIndex(this) +
                        " (expected: " + INDEX_NOT_IN_QUEUE + ") + e: " + e);
            }

            // Check that the array capacity is enough to hold values by doubling capacity.
            int length = queue.length;
            if (size >= length) {
                // Use a policy which allows for a 0 initial capacity. Same policy as JDK's priority queue, double when
                // "small", then grow by 50% when "large".
                queue = Arrays.copyOf(queue, length + (length < 64 ? length + 2 : length >>> 1));
            }

            bubbleUp(size++, e);
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public T poll() {
        long stamp = lock.writeLock();
        try {
            if (size == 0) {
                return null;
            }
            T result = queue[0];
            result.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE);

            T last = queue[--size];
            queue[size] = null;
            if (size != 0) { // Make sure we don't add the last element back.
                bubbleDown(0, last);
            }

            return result;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public T peek() {
        long stamp = lock.readLock();
        try {
            return size == 0 ? null : queue[0];
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        long stamp = lock.writeLock();
        try {
            final T node;
            try {
                node = (T) o;
            } catch (ClassCastException e) {
                return false;
            }
            return removeTyped(node);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeTyped(T node) {
        long stamp = lock.writeLock();
        try {
            int i = node.priorityQueueIndex(this);
            if (!contains(node, i)) {
                return false;
            }

            node.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE);
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
            if (comparator.compare(node, moved) < 0) {
                bubbleDown(i, moved);
            } else {
                bubbleUp(i, moved);
            }
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void priorityChanged(T node) {
        long stamp = lock.writeLock();
        try {
            int i = node.priorityQueueIndex(this);
            if (!contains(node, i)) {
                return;
            }

            // Preserve the min-heap property by comparing the new priority with parents/children in the heap.
            if (i == 0) {
                bubbleDown(i, node);
            } else {
                // Get the parent to see if min-heap properties are violated.
                int iParent = i - 1 >>> 1;
                T parent = queue[iParent];
                if (comparator.compare(node, parent) < 0) {
                    bubbleUp(i, node);
                } else {
                    bubbleDown(i, node);
                }
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Object[] toArray() {
        long stamp = lock.readLock();
        try {
            return Arrays.copyOf(queue, size);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @SuppressWarnings({"unchecked", "SuspiciousArrayCast", "SuspiciousSystemArraycopy"})
    @Override
    public <X> X[] toArray(X[] a) {
        long stamp = lock.readLock();
        try {
            if (a.length < size) {
                return (X[]) Arrays.copyOf(queue, size, a.getClass());
            }
            System.arraycopy(queue, 0, a, 0, size);
            if (a.length > size) {
                a[size] = null;
            }
            return a;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * This priority queue does not support iteration.
     *
     * @throws UnsupportedOperationException iteration is not supported.
     */
    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException();
    }

    private boolean contains(PriorityQueueNode node, int i) {
        return i >= 0 && i < size && node.equals(queue[i]);
    }

    private void bubbleDown(int k, T node) {
        final int half = size >>> 1;
        while (k < half) {
            // Compare node to the children of index k.
            int iChild = (k << 1) + 1;
            T child = queue[iChild];

            // Make sure we get the smallest child to compare against.
            int rightChild = iChild + 1;
            if (rightChild < size && comparator.compare(child, queue[rightChild]) > 0) {
                child = queue[iChild = rightChild];
            }
            // If the bubbleDown node is less than or equal to the smallest child then we will preserve the min-heap
            // property by inserting the bubbleDown node here.
            if (comparator.compare(node, child) <= 0) {
                break;
            }

            // Bubble the child up.
            queue[k] = child;
            child.priorityQueueIndex(this, k);

            // Move down k down the tree for the next iteration.
            k = iChild;
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node;
        node.priorityQueueIndex(this, k);
    }

    private void bubbleUp(int k, T node) {
        while (k > 0) {
            int iParent = k - 1 >>> 1;
            T parent = queue[iParent];

            // If the bubbleUp node is less than the parent, then we have found a spot to insert and still maintain
            // min-heap properties.
            if (comparator.compare(node, parent) >= 0) {
                break;
            }

            // Bubble the parent down.
            queue[k] = parent;
            parent.priorityQueueIndex(this, k);

            // Move k up the tree for the next iteration.
            k = iParent;
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node;
        node.priorityQueueIndex(this, k);
    }
}
