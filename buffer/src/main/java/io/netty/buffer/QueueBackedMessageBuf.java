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

final class QueueBackedMessageBuf<T> extends AbstractMessageBuf<T> {

    private Queue<T> queue;

    QueueBackedMessageBuf(Queue<T> queue) {
        super(Integer.MAX_VALUE);
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    @Override
    public boolean offer(T e) {
        if (e == null) {
            throw new NullPointerException("e");
        }
        ensureAccessible();
        return isWritable() && queue.offer(e);
    }

    @Override
    public T poll() {
        ensureAccessible();
        return queue.poll();
    }

    @Override
    public T peek() {
        ensureAccessible();
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        ensureAccessible();
        return queue.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        ensureAccessible();
        return queue.iterator();
    }

    @Override
    public Object[] toArray() {
        ensureAccessible();
        return queue.toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        ensureAccessible();
        return queue.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        ensureAccessible();
        return queue.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        ensureAccessible();
        return queue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        ensureAccessible();
        return isWritable(c.size()) && queue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        ensureAccessible();
        return queue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        ensureAccessible();
        return queue.retainAll(c);
    }

    @Override
    public void clear() {
        ensureAccessible();
        queue.clear();
    }

    @Override
    protected void deallocate() {
        for (T e: queue) {
            BufUtil.release(e);
        }
        queue = null;
    }
}
