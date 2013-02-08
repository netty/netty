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

import java.util.AbstractQueue;
import java.util.Collection;

public abstract class AbstractMessageBuf<T> extends AbstractQueue<T> implements MessageBuf<T> {

    private final int maxCapacity;
    private boolean freed;

    protected AbstractMessageBuf(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity + " (expected: >= 0)");
        }
        this.maxCapacity = maxCapacity;
    }

    @Override
    public final BufType type() {
        return BufType.MESSAGE;
    }

    @Override
    public final boolean isFreed() {
        return freed;
    }

    @Override
    public final void free() {
        if (freed) {
            return;
        }

        try {
            doFree();
        } finally {
            freed = true;
        }
    }

    protected abstract void doFree();

    @Override
    public final int maxCapacity() {
        return maxCapacity;
    }

    @Override
    public final boolean isReadable() {
        return !isEmpty();
    }

    @Override
    public final boolean isReadable(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size + " (expected: >= 0)");
        }
        return size() >= size;
    }

    @Override
    public final boolean isWritable() {
        return size() < maxCapacity;
    }

    @Override
    public final boolean isWritable(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size: " + size + " (expected: >= 0)");
        }
        return size() <= maxCapacity - size;
    }

    protected final void checkUnfreed() {
        if (isFreed()) {
            throw new IllegalBufferAccessException();
        }
    }

    @Override
    public final boolean add(T t) {
        return super.add(t);
    }

    @Override
    public final T remove() {
        return super.remove();
    }

    @Override
    public final T element() {
        return super.element();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean unfoldAndAdd(Object o) {
        if (o == null) {
            return false;
        }

        if (o instanceof Object[]) {
            Object[] a = (Object[]) o;
            int i;
            for (i = 0; i < a.length; i ++) {
                Object m = a[i];
                if (m == null) {
                    break;
                }
                add((T) m);
            }
            return i != 0;
        }

        return add((T) o);
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        checkUnfreed();
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
        checkUnfreed();
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
    public String toString() {
        if (isFreed()) {
            return getClass().getSimpleName() + "(freed)";
        }

        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append("(size: ");
        buf.append(size());
        if (maxCapacity != Integer.MAX_VALUE) {
            buf.append('/');
            buf.append(maxCapacity);
        }
        buf.append(')');

        return buf.toString();
    }
}
