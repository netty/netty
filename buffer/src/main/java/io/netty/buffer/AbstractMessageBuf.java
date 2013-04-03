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

import io.netty.util.internal.PlatformDependent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Abstract base class for {@link MessageBuf} implementations.
 * @param <T>
 */
public abstract class AbstractMessageBuf<T> extends AbstractQueue<T> implements MessageBuf<T> {

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractMessageBuf> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractMessageBuf.class, "refCnt");

    private static final long REFCNT_FIELD_OFFSET;

    static {
        long refCntFieldOffset = -1;
        try {
            if (PlatformDependent.hasUnsafe()) {
                refCntFieldOffset = PlatformDependent.objectFieldOffset(
                        AbstractMessageBuf.class.getDeclaredField("refCnt"));
            }
        } catch (Throwable t) {
            // Ignored
        }

        REFCNT_FIELD_OFFSET = refCntFieldOffset;
    }

    private final int maxCapacity;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int refCnt = 1;

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
    public final int refCnt() {
        if (REFCNT_FIELD_OFFSET >= 0) {
            // Try to do non-volatile read for performance.
            return PlatformDependent.getInt(this, REFCNT_FIELD_OFFSET);
        } else {
            return refCnt;
        }
    }

    @Override
    public MessageBuf<T> retain() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalBufferAccessException();
            }
            if (refCnt == Integer.MAX_VALUE) {
                throw new IllegalBufferAccessException("refCnt overflow");
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + 1)) {
                break;
            }
        }
        return this;
    }

    @Override
    public MessageBuf<T> retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalBufferAccessException();
            }
            if (refCnt > Integer.MAX_VALUE - increment) {
                throw new IllegalBufferAccessException("refCnt overflow");
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + increment)) {
                break;
            }
        }
        return this;
    }

    @Override
    public final boolean release() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalBufferAccessException();
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - 1)) {
                if (refCnt == 1) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public final boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("decrement: " + decrement + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt < decrement) {
                throw new IllegalBufferAccessException();
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - decrement)) {
                if (refCnt == decrement) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }

    protected abstract void deallocate();

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

    protected final void ensureAccessible() {
        if (refCnt <= 0) {
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
    public int drainTo(Collection<? super T> c) {
        ensureAccessible();
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
        ensureAccessible();
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
        if (refCnt <= 0) {
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
