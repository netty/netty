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

import io.netty.util.Signal;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;

public final class MessageList<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MIN_INITIAL_CAPACITY = 4;

    private T[] elements;
    private int size;

    public MessageList() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public MessageList(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        elements = newArray(MIN_INITIAL_CAPACITY);
        elements[0] = value;
        size = 1;
    }

    public MessageList(int initialCapacity) {
        initialCapacity = normalizeCapacity(initialCapacity);
        elements = newArray(initialCapacity);
    }

    private MessageList(T[] elements, int size) {
        this.elements = elements;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public T get(int index) {
        checkExclusive(index);
        return elements[index];
    }

    public void set(int index, T value) {
        checkExclusive(index);
        if (value == null) {
            throw new NullPointerException("value");
        }
        elements[index] = value;
    }

    public void set(int index, T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        set(index, src, 0, src.length);
    }

    public void set(int index, T[] src, int srcIdx, int srcLen) {
        checkElements(src, srcIdx, srcLen);

        if (srcLen == 0) {
            remove(index);
            return;
        }

        if (srcLen == 1) {
            set(index, src[srcIdx]);
            return;
        }

        checkExclusive(index);

        int oldSize = size;
        int newSize = oldSize + srcLen - 1;
        ensureCapacity(newSize);
        System.arraycopy(elements, index + 1, elements, index + srcLen, oldSize - (index + 1));
        System.arraycopy(src, srcIdx, elements, index, srcLen);
        size = newSize;
    }

    public void set(int index, int length, T value) {
        if (length == 0) {
            add(index, value);
            return;
        }

        if (length == 1) {
            set(index, value);
            return;
        }

        checkRange(index, length);
        if (value == null) {
            throw new NullPointerException("value");
        }

        elements[index] = value;
        int nextElemIdx = index + length;
        int oldSize = size;
        int newSize = oldSize - length + 1;
        System.arraycopy(elements, nextElemIdx, elements, index + 1, oldSize - nextElemIdx);
        Arrays.fill(elements, newSize, oldSize, null);
        size = newSize;
    }

    public void set(int index, int length, T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        set(index, length, src, 0, src.length);
    }

    public void set(int index, int length, T[] src, int srcIdx, int srcLen) {
        if (length == 0) {
            add(index, src, srcIdx, srcLen);
            return;
        }

        if (length == 1) {
            set(index, src, srcIdx, srcLen);
            return;
        }

        checkRange(index, length);
        checkElements(src, srcIdx, srcLen);

        if (srcLen == length) {
            System.arraycopy(src, srcIdx, elements, index, length);
        } else if (srcLen < length) {
            int remainderIdx = index + length;
            int oldSize = size;
            int newSize = oldSize - (length - srcLen);
            System.arraycopy(src, srcIdx, elements, index, srcLen);
            System.arraycopy(elements, remainderIdx, elements, index + srcLen, oldSize - remainderIdx);
            Arrays.fill(elements, newSize, oldSize, null);
            size = newSize;
        } else {
            int remainderIdx = index + length;
            int oldSize = size;
            int newSize = oldSize + srcLen - length;
            ensureCapacity(newSize);
            // 0 [1 2] 3 4 5 -> 0 [1 2 3] 3 4 5
            System.arraycopy(elements, remainderIdx, elements, index + srcLen, oldSize - remainderIdx);
            System.arraycopy(src, srcIdx, elements, index, srcLen);
            size = newSize;
        }
    }

    public void add(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        int oldSize = size;
        int newSize = oldSize + 1;
        ensureCapacity(newSize);
        elements[oldSize] = value;
        size = newSize;
    }

    public void add(T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        add(src, 0, src.length);
    }

    public void add(T[] src, int srcIdx, int srcLen) {
        checkElements(src, srcIdx, srcLen);

        int oldSize = size;
        int newSize = oldSize + srcLen;
        ensureCapacity(newSize);
        System.arraycopy(src, srcIdx, elements, oldSize, srcLen);
        size = newSize;
    }

    public void add(int index, T value) {
        checkInclusive(index);

        if (value == null) {
            throw new NullPointerException("value");
        }

        int oldSize = size;
        int newSize = oldSize + 1;
        ensureCapacity(newSize);
        System.arraycopy(elements, index, elements, index + 1, oldSize - index);
        elements[index] = value;
        size = newSize;
    }

    public void add(int index, T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        add(index, src, 0, src.length);
    }

    public void add(int index, T[] src, int srcIdx, int srcLen) {
        checkInclusive(index);
        checkElements(src, srcIdx, srcLen);

        if (srcLen == 0) {
            return;
        }

        int oldSize = size;
        int newSize = oldSize + srcLen;
        ensureCapacity(newSize);
        System.arraycopy(elements, index, elements, index + srcLen, oldSize - index);
        System.arraycopy(src, srcIdx, elements, index, srcLen);
        size = newSize;
    }

    public void remove(int index) {
        checkExclusive(index);
        int oldSize = size;
        int newSize = oldSize - 1;
        System.arraycopy(elements, index + 1, elements, index, newSize - index);
        elements[newSize] = null;
        size = newSize;
    }

    public void remove(int index, int length) {
        checkRange(index, length);
        if (length == 0) {
            return;
        }

        int oldSize = size;
        int newSize = oldSize - length;
        System.arraycopy(elements, index + length, elements, index, newSize - index);
        Arrays.fill(elements, newSize, oldSize, null);
        size = newSize;
    }

    public MessageList<T> copy() {
        return new MessageList<T>(elements.clone(), size);
    }

    public MessageList<T> copy(int index, int length) {
        checkRange(index, length);
        MessageList<T> copy = new MessageList<T>(length);
        System.arraycopy(elements, index, copy.elements, 0, length);
        copy.size = length;
        return copy;
    }

    @SuppressWarnings("unchecked")
    public <U> MessageList<U> cast() {
        return (MessageList<U>) this;
    }

    @SuppressWarnings("unchecked")
    public <U> MessageList<U> cast(@SuppressWarnings("UnusedParameters") Class<U> type) {
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

    @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
    private T[] newArray(int initialCapacity) {
        return (T[]) new Object[initialCapacity];
    }

    private void checkExclusive(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
    }

    private void checkInclusive(int index) {
        if (index > size) {
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
