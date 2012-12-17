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

    private final Queue<T> queue;
    private boolean freed;

    QueueBackedMessageBuf(Queue<T> queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    @Override
    public BufType type() {
        return BufType.MESSAGE;
    }

    @Override
    public boolean add(T e) {
        ensureValid();
        return queue.add(e);
    }

    @Override
    public boolean offer(T e) {
        ensureValid();
        return queue.offer(e);
    }

    @Override
    public T remove() {
        ensureValid();
        return queue.remove();
    }

    @Override
    public T poll() {
        ensureValid();
        return queue.poll();
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
        return queue.iterator();
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
        return queue.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        ensureValid();
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        ensureValid();
        return queue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        ensureValid();
        return queue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        ensureValid();
        return queue.retainAll(c);
    }

    @Override
    public void clear() {
        ensureValid();
        queue.clear();
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
}
