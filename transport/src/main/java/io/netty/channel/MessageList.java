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

package io.netty.channel;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.Signal;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;

public final class MessageList<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MIN_INITIAL_CAPACITY = 4;

    private static final Recycler<MessageList<?>> RECYCLER = new Recycler<MessageList<?>>() {
        @Override
        protected MessageList<?> newObject(Handle handle) {
            return new MessageList<Object>(handle);
        }
    };

    public static <T> MessageList<T> newInstance() {
        return newInstance(DEFAULT_INITIAL_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public static <T> MessageList<T> newInstance(int minCapacity) {
        MessageList<T> ret = (MessageList<T>) RECYCLER.get();
        ret.ensureCapacity(minCapacity);
        return ret;
    }

    public static <T> MessageList<T> newInstance(T value) {
        MessageList<T> ret = newInstance(MIN_INITIAL_CAPACITY);
        ret.add(value);
        return ret;
    }

    public MessageList<T> retainAll() {
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            ByteBufUtil.retain(elements[i]);
        }
        return this;
    }

    public MessageList<T> retainAll(int increment) {
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            ByteBufUtil.retain(elements[i], increment);
        }
        return this;
    }

    public boolean releaseAll() {
        boolean releasedAll = true;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            releasedAll &= ByteBufUtil.release(elements[i]);
        }
        return releasedAll;
    }

    public boolean releaseAll(int decrement) {
        boolean releasedAll = true;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            releasedAll &= ByteBufUtil.release(elements[i], decrement);
        }
        return releasedAll;
    }

    public boolean releaseAllAndRecycle() {
        return releaseAll() && recycle();
    }

    public boolean releaseAllAndRecycle(int decrement) {
        return releaseAll(decrement) && recycle();
    }

    public boolean recycle() {
        clear();
        return RECYCLER.recycle(this, handle);
    }

    private final Handle handle;
    private T[] elements;
    private int size;

    MessageList(Handle handle) {
        this(handle, DEFAULT_INITIAL_CAPACITY);
    }

    MessageList(Handle handle, int initialCapacity) {
        this.handle = handle;
        initialCapacity = normalizeCapacity(initialCapacity);
        elements = newArray(initialCapacity);
    }

    private MessageList(Handle handle, T[] elements, int size) {
        this.handle = handle;
        this.elements = elements;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return elements.length;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public T get(int index) {
        checkExclusive(index);
        return elements[index];
    }

    public MessageList<T> add(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        int oldSize = size;
        int newSize = oldSize + 1;
        ensureCapacity(newSize);
        elements[oldSize] = value;
        size = newSize;
        return this;
    }

    public MessageList<T> add(T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        return add(src, 0, src.length);
    }

    public MessageList<T> add(T[] src, int srcIdx, int srcLen) {
        checkElements(src, srcIdx, srcLen);

        int oldSize = size;
        int newSize = oldSize + srcLen;
        ensureCapacity(newSize);
        System.arraycopy(src, srcIdx, elements, oldSize, srcLen);
        size = newSize;
        return this;
    }

    public MessageList<T> clear() {
        Arrays.fill(elements, 0, size, null);
        size = 0;
        return this;
    }

    public MessageList<T> copy() {
        return new MessageList<T>(handle, elements.clone(), size);
    }

    public MessageList<T> copy(int index, int length) {
        checkRange(index, length);
        MessageList<T> copy = new MessageList<T>(handle, length);
        System.arraycopy(elements, index, copy.elements, 0, length);
        copy.size = length;
        return copy;
    }

    @SuppressWarnings("unchecked")
    public <U> MessageList<U> cast() {
        return (MessageList<U>) this;
    }

    public T[] array() {
        return elements;
    }

    public boolean forEach(MessageListProcessor<? super T> proc) {
        if (proc == null) {
            throw new NullPointerException("proc");
        }

        @SuppressWarnings("unchecked")
        MessageListProcessor<T> p = (MessageListProcessor<T>) proc;

        int size = this.size;
        try {
            for (int i = 0; i < size; i ++) {
                i += p.process(this, i, elements[i]);
            }
        } catch (Signal abort) {
            abort.expect(MessageListProcessor.ABORT);
            return false;
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return true;
    }

    public boolean forEach(int index, int length, MessageListProcessor<? super T> proc) {
        checkRange(index, length);
        if (proc == null) {
            throw new NullPointerException("proc");
        }

        @SuppressWarnings("unchecked")
        MessageListProcessor<T> p = (MessageListProcessor<T>) proc;

        int end = index + length;
        try {
            for (int i = index; i < end;) {
                i += p.process(this, i, elements[i]);
            }
        } catch (Signal abort) {
            abort.expect(MessageListProcessor.ABORT);
            return false;
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return true;
    }

    private void ensureCapacity(int capacity) {
        if (elements.length >= capacity) {
            return;
        }

        T[] newElements = newArray(normalizeCapacity(capacity));
        System.arraycopy(elements, 0, newElements, 0, size);
        elements = newElements;
    }

    private static int normalizeCapacity(int initialCapacity) {
        if (initialCapacity <= MIN_INITIAL_CAPACITY) {
            initialCapacity = MIN_INITIAL_CAPACITY;
        } else {
            initialCapacity |= initialCapacity >>>  1;
            initialCapacity |= initialCapacity >>>  2;
            initialCapacity |= initialCapacity >>>  4;
            initialCapacity |= initialCapacity >>>  8;
            initialCapacity |= initialCapacity >>> 16;
            initialCapacity ++;

            if (initialCapacity < 0) {
                initialCapacity >>>= 1;
            }
        }
        return initialCapacity;
    }

    @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
    private T[] newArray(int initialCapacity) {
        return (T[]) new Object[initialCapacity];
    }

    private void checkExclusive(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
    }

    private void checkRange(int index, int length) {
        if (index + length > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", length: " + length + ", size: " + size);
        }
    }

    private void checkElements(T[] src, int srcIdx, int srcLen) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        int end = srcIdx + srcLen;
        for (int i = srcIdx; i < end; i ++) {
            if (src[i] == null) {
                throw new NullPointerException("src[" + i + ']');
            }
        }
    }
}
