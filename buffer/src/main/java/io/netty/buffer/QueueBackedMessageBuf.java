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
package io.netty.buffer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

final class QueueBackedMessageBuf<T> implements MessageBuf<T> {
    private final int maxCapacity;
    private int elements;

    private final Queue<T> queue;
    private boolean freed;

    QueueBackedMessageBuf(Queue<T> queue, int maxCapacity) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
        this.maxCapacity = maxCapacity;
    }

    @Override
    public boolean ensureIsWritable(int num) {
        if (elements + num > maxCapacity()) {
            return false;
        }
        return true;
    }

    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    @Override
    public BufType type() {
        return BufType.MESSAGE;
    }

    @Override
    public boolean add(T e) {
        ensureValid();
        if (!ensureIsWritable(1)) {
            throw new IllegalStateException("maxCapacity of " + maxCapacity() + " reached");
        }
        boolean added = queue.add(e);
        if (added) {
            elements++;
        }
        return added;
    }

    @Override
    public boolean offer(T e) {
        ensureValid();
        if (!ensureIsWritable(1)) {
            return false;
        }
        boolean added =  queue.offer(e);
        if (added) {
            elements++;
        }
        return added;
    }

    @Override
    public T remove() {
        ensureValid();
        T element = queue.remove();
        if (element != null) {
            elements--;
        }
        return element;
    }

    @Override
    public T poll() {
        ensureValid();
        T element = queue.poll();
        if (element != null) {
            elements--;
        }
        return element;
    }

    @Override
    public T element() {
        ensureValid();
        return queue.element();
    }

    @Override
    public T peek() {
        ensureValid();
        return queue.peek();
    }

    @Override
    public int size() {
        ensureValid();
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        ensureValid();
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        ensureValid();
        return queue.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        ensureValid();
        return new CountingIterator(queue.iterator());
    }

    @Override
    public Object[] toArray() {
        ensureValid();
        return queue.toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        ensureValid();
        return queue.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        ensureValid();
        boolean removed = queue.remove(o);
        if (removed) {
            elements--;
        }
        return removed;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        ensureValid();
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        ensureValid();
        if (!ensureIsWritable(c.size())) {
            return false;
        }
        int size = size();
        boolean added = queue.addAll(c);
        if (added) {
            elements += size() - size;
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        ensureValid();
        int size = size();
        boolean removed = queue.removeAll(c);
        if (removed) {
            elements -= size - size();
        }
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        ensureValid();
        boolean retained = queue.retainAll(c);
        if (retained) {
            elements = size();
        }
        return retained;
    }

    @Override
    public void clear() {
        ensureValid();
        queue.clear();
        elements = 0;
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        ensureValid();
        int cnt = 0;
        for (;;) {
            T o = poll();
            if (o == null) {
                break;
            }
            c.add(o);
            cnt ++;
        }
        return cnt;
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        ensureValid();
        int cnt = 0;
        while (cnt < maxElements) {
            T o = poll();
            if (o == null) {
                break;
            }
            c.add(o);
            cnt ++;
        }
        return cnt;
    }

    @Override
    public boolean isFreed() {
        return freed;
    }

    @Override
    public void free() {
        freed = true;
        queue.clear();
        elements = 0;
    }

    @Override
    public String toString() {
        return queue.toString();
    }

    private void ensureValid() {
        if (freed) {
            throw new IllegalBufferAccessException();
        }
    }

    private final class CountingIterator implements Iterator<T> {
        private final Iterator<T> wrapped;
        CountingIterator(Iterator<T> wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public boolean hasNext() {
            return wrapped.hasNext();
        }

        @Override
        public T next() {
            return wrapped.next();
        }

        @Override
        public void remove() {
            wrapped.remove();
            elements--;
        }
    }
}
