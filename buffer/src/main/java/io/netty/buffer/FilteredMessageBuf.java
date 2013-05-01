/*
 * Copyright 2013 The Netty Project
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

public abstract class FilteredMessageBuf implements MessageBuf<Object> {

    protected final MessageBuf<Object> buf;

    @SuppressWarnings("unchecked")
    protected FilteredMessageBuf(MessageBuf<?> buf) {
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        this.buf = (MessageBuf<Object>) buf;
    }

    protected abstract Object filter(Object msg);

    @Override
    public int drainTo(Collection<? super Object> c) {
        return buf.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super Object> c, int maxElements) {
        return buf.drainTo(c, maxElements);
    }

    @Override
    public BufType type() {
        return buf.type();
    }

    @Override
    public int maxCapacity() {
        return buf.maxCapacity();
    }

    @Override
    public boolean isReadable() {
        return buf.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return buf.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return buf.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return buf.isWritable(size);
    }

    @Override
    public boolean add(Object e) {
        if (e == null) {
            throw new NullPointerException("e");
        }

        e = filter(e);
        ensureNonNull(e);

        return buf.add(e);
    }

    @Override
    public boolean offer(Object e) {
        if (e == null) {
            throw new NullPointerException("e");
        }

        e = filter(e);
        ensureNonNull(e);

        return buf.offer(e);
    }

    private void ensureNonNull(Object e) {
        if (e == null) {
            throw new IllegalStateException(getClass().getSimpleName() + ".filter() returned null");
        }
    }

    @Override
    public Object remove() {
        return buf.remove();
    }

    @Override
    public Object poll() {
        return buf.poll();
    }

    @Override
    public Object element() {
        return buf.element();
    }

    @Override
    public Object peek() {
        return buf.peek();
    }

    @Override
    public int size() {
        return buf.size();
    }

    @Override
    public boolean isEmpty() {
        return buf.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return buf.contains(o);
    }

    @Override
    public Iterator<Object> iterator() {
        return buf.iterator();
    }

    @Override
    public Object[] toArray() {
        return buf.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return buf.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return buf.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return buf.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<?> c) {
        int i = 0;
        boolean added = false;
        for (Object e: c) {
            if (e == null) {
                throw new NullPointerException("c[" + i + ']');
            }

            e = filter(e);
            ensureNonNull(e);
            added |= buf.add(e);
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return buf.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return buf.retainAll(c);
    }

    @Override
    public void clear() {
        buf.clear();
    }

    @Override
    public int refCnt() {
        return buf.refCnt();
    }

    @Override
    public MessageBuf<Object> retain() {
        buf.retain();
        return this;
    }

    @Override
    public MessageBuf<Object> retain(int increment) {
        buf.retain(increment);
        return this;
    }

    @Override
    public boolean release() {
        return buf.release();
    }

    @Override
    public boolean release(int decrement) {
        return buf.release(decrement);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + buf + ')';
    }
}

