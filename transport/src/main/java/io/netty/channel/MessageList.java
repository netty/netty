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

import io.netty.buffer.ByteBuf;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Signal;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Datastructure which holds messages.
 *
 * You should call {@link #recycle()} once you are done with using it.
 * @param <T>
 */
public final class MessageList<T> implements Iterable<T> {

    private static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MIN_INITIAL_CAPACITY = 4;

    private static final Recycler<MessageList<?>> RECYCLER = new Recycler<MessageList<?>>() {
        @Override
        protected MessageList<?> newObject(Handle handle) {
            return new MessageList<Object>(handle);
        }
    };

    /**
     * Create a new empty {@link MessageList} instance
     */
    public static <T> MessageList<T> newInstance() {
        return newInstance(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new empty {@link MessageList} instance with the given capacity.
     */
    @SuppressWarnings("unchecked")
    public static <T> MessageList<T> newInstance(int minCapacity) {
        MessageList<T> ret = (MessageList<T>) RECYCLER.get();
        ret.ensureCapacity(minCapacity);
        ret.modifications = 0;
        return ret;
    }

    /**
     * Create a new {@link MessageList} instance which holds the given value
     */
    public static <T> MessageList<T> newInstance(T value) {
        MessageList<T> ret = newInstance(MIN_INITIAL_CAPACITY);
        ret.add(value);
        return ret;
    }

    /**
     * Call {@link ReferenceCountUtil#retain(Object)} on all messages in this {@link MessageList} and return itself.
     */
    public MessageList<T> retainAll() {
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            ReferenceCountUtil.retain(elements[i]);
        }
        return this;
    }

    /**
     * Call {@link ReferenceCountUtil#retain(Object), int} on all messages in this {@link MessageList} and return
     * itself.
     */
    public MessageList<T> retainAll(int increment) {
        int size = this.size;
        for (int i = 0; i < size; i ++) {
            ReferenceCountUtil.retain(elements[i], increment);
        }
        return this;
    }

    /**
     * Call {@link ReferenceCountUtil#release(Object)} on all messages in this {@link MessageList} and return
     * {@code true} if all messages were released.
     */
    public boolean releaseAll() {
        boolean releasedAll = true;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            releasedAll &= ReferenceCountUtil.release(elements[i]);
        }
        return releasedAll;
    }

    /**
     * Call {@link ReferenceCountUtil#release(Object, int)} on all messages in this {@link MessageList} and return
     * {@code true} if all messages were released.
     */
    public boolean releaseAll(int decrement) {
        boolean releasedAll = true;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            releasedAll &= ReferenceCountUtil.release(elements[i], decrement);
        }
        return releasedAll;
    }

    /**
     * Short-cut for calling {@link #releaseAll()} and {@link #recycle()}. Returns {@code true} if both return
     * {@code true}.
     */
    public boolean releaseAllAndRecycle() {
        return releaseAll() && recycle();
    }

    /**
     * Short-cut for calling {@link #releaseAll(int)} and {@link #recycle()}. Returns {@code true} if both return
     * {@code true}.
     */
    public boolean releaseAllAndRecycle(int decrement) {
        return releaseAll(decrement) && recycle();
    }

    /**
     * Clear and recycle this instance.
     */
    public boolean recycle() {
        clear();
        return RECYCLER.recycle(this, handle);
    }

    private final Handle handle;
    private T[] elements;
    private int size;
    private int modifications;
    private boolean byteBufsOnly = true;

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

    /**
     * Return the current size of this {@link MessageList} and so how many messages it holds.
     */
    public int size() {
        return size;
    }

    /**
     * Return {@code true} if this {@link MessageList} is empty and so contains no messages.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Return the message on the given index. This method will throw an {@link IndexOutOfBoundsException} if there is
     * no message stored in the given index.
     */
    public T get(int index) {
        checkExclusive(index);
        return elements[index];
    }

    /**
     * Sets the message on the given index.
     */
    public MessageList<T> set(int index, T value) {
        checkExclusive(index);
        if (value == null) {
            throw new NullPointerException("value");
        }
        elements[index] = value;
        return this;
    }

    /**
     * Add the message to this {@link MessageList} and return itself.
     */
    public MessageList<T> add(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        modifications++;
        int oldSize = size;
        int newSize = oldSize + 1;
        ensureCapacity(newSize);
        elements[oldSize] = value;
        size = newSize;
        if (byteBufsOnly && !(value instanceof ByteBuf)) {
            byteBufsOnly = false;
        }
        return this;
    }

    /**
     * Add the messages contained in the array to this {@link MessageList} and return itself.
     */
    public MessageList<T> add(T[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        return add(src, 0, src.length);
    }

    /**
     * Add the messages contained in the array, using the given src index and src length, to this {@link MessageList}
     * and return itself.
     */
    public MessageList<T> add(T[] src, int srcIdx, int srcLen) {
        checkElements(src, srcIdx, srcLen);

        modifications++;
        int oldSize = size;
        int newSize = oldSize + srcLen;
        ensureCapacity(newSize);
        System.arraycopy(src, srcIdx, elements, oldSize, srcLen);
        size = newSize;
        if (byteBufsOnly) {
            for (int i = srcIdx; i < srcIdx; i++) {
                if (!(src[i] instanceof ByteBuf)) {
                    byteBufsOnly = false;
                    break;
                }
            }
        }

        return this;
    }

    /**
     * Add the messages contained in the given {@link MessageList} to this {@link MessageList} and return itself.
     */
    public MessageList<T> add(MessageList<T> src) {
        return add(src, 0, src.size());
    }

    /**
     * Add the messages contained in the given {@link MessageList}, using the given src index and src length, to this
     * {@link MessageList} and return itself.
     */
    public MessageList<T> add(MessageList<T>  src, int srcIdx, int srcLen) {
        return add(src.elements, srcIdx, srcLen);
    }

    /**
     * Clear all messages and return itself.
     */
    public MessageList<T> clear() {
        modifications++;
        Arrays.fill(elements, 0, size, null);
        byteBufsOnly = true;
        size = 0;
        return this;
    }

    /**
     * Create a new copy all messages of this {@link MessageList} and return it.
     */
    public MessageList<T> copy() {
        return new MessageList<T>(handle, elements.clone(), size);
    }

    /**
     * Create a new copy all messages of this {@link MessageList}, starting at the given index and using the given len,
     * and return it.
     */
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

    @Override
    public Iterator<T> iterator() {
        return new MessageListIterator();
    }

    /**
     * Returns {@code true} if all messages contained in this {@link MessageList} are assignment-compatible with the
     * object represented by this {@link Class}.
     */
    public boolean containsOnly(Class<?> clazz) {
        if (clazz == ByteBuf.class) {
            return byteBufsOnly;
        }
        for (int i = 0; i < size; i++) {
            if (!clazz.isInstance(elements[i])) {
                return false;
            }
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

    private final class MessageListIterator implements Iterator<T> {
        private int index;
        private int expectedModifications = modifications;

        private void checkConcurrentModifications() {
            if (expectedModifications != modifications) {
                throw new ConcurrentModificationException();
            }
            if (index > size) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public T next() {
            checkConcurrentModifications();
            if (hasNext()) {
                return elements[index++];
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
           throw new UnsupportedOperationException("Read-Only");
        }
    }
}
