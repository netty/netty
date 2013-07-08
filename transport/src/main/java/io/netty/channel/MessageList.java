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
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A simple array-backed list that holds one or more messages.
 *
 * <h3>Recycling a {@link MessageList}</h3>
 * <p>
 * A {@link MessageList} is internally managed by a thread-local object pool to keep the GC pressure minimal.
 * To return the {@link MessageList} to the pool, you must call one of the following methods: {@link #recycle()},
 * {@link #releaseAllAndRecycle()}, or {@link #releaseAllAndRecycle(int)}.  If the list is returned to the pool (i.e.
 * recycled), it will be reused when you attempts to get a {@link MessageList} via {@link #newInstance()}.
 * </p><p>
 * If you don't think recycling a {@link MessageList} isn't worth, it is also fine not to recycle it.  Because of this
 * relaxed contract, you can also decide not to wrap your code with a {@code try-finally} block only to recycle a
 * list.  However, if you decided to recycle it, you must keep in mind that:
 * <ul>
 * <li>you must make sure you do not access the list once it has been recycled.</li>
 * <li>If you are given with a {@link MessageList} as a parameter of your handler, it means it is your handler's
 *     responsibility to release the messages in it and to recycle it.</li>
 * </ul>
 * </p>
 *
 * <h3>Consuming the messages from a {@link MessageList} efficiently yet safely</h3>
 * <p>
 * The following example shows how to iterate over the list which contains {@code ReferenceCounted} messages safely
 * (i.e. without leaking them) while consuming the messages.
 * </p>
 * <pre>
 * public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageList}&lt;Object&gt; msgs) {
 *     final int size = msgs.size();
 *     final Object[] in = msgs.array();
 *     boolean success = false;
 *     try {
 *         for (int i = 0; i < size; i ++) {
 *             Object m = in[i];
 *
 *             // Handle the message.
 *             doSomethingWithMessage(m);
 *
 *             // Release the handled message.
 *             {@link ReferenceCountUtil#release(Object) ReferenceCountUtil.release(m)};
 *
 *             // To prevent {@link #releaseAllAndRecycle()} from releasing the message again,
 *             // replace the message with a dummy object.
 *             in[i] = MessageList.NONE;
 *         }
 *
 *         success = true;
 *     } finally {
 *         if (success) {
 *             {@link #recycle() msgs.recycle()};
 *         } else {
 *             {@link #releaseAllAndRecycle() msgs.releaseAllAndRecycle()};
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> the type of the contained messages
 */
final class MessageList<T> implements Iterable<T> {

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
        for (int i = 0; i < size; i ++) {
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
        for (int i = 0; i < size; i ++) {
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
        checkIndex(index);
        return elements[index];
    }

    /**
     * Returns the first message in this list.
     *
     * @throws NoSuchElementException if this list is empty
     */
    public T first() {
        if (size != 0) {
            return elements[0];
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Returns the last message in this list.
     *
     * @throws NoSuchElementException if this list is empty
     */
    public T last() {
        if (size != 0) {
            return elements[size - 1];
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * Sets the message on the given index.
     */
    public MessageList<T> set(int index, T value) {
        checkIndex(index);
        if (value == null) {
            throw new NullPointerException("value");
        }

        elements[index] = value;

        if (byteBufsOnly && !(value instanceof ByteBuf)) {
            byteBufsOnly = false;
        }

        return this;
    }

    /**
     * Add the message to this {@link MessageList} and return itself.
     */
    public MessageList<T> add(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        modifications ++;
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
        if (src == null) {
            throw new NullPointerException("src");
        }

        modifications ++;

        int dstIdx = size;
        final int newSize = dstIdx + srcLen;
        ensureCapacity(newSize);

        final int srcEndIdx = srcIdx + srcLen;
        int i = srcIdx;
        try {
            if (byteBufsOnly) {
                while (i < srcEndIdx) {
                    T m = src[i];
                    if (m == null) {
                        throw new NullPointerException("src[" + srcIdx + ']');
                    }

                    elements[dstIdx ++] = m;
                    i ++;

                    if (!(m instanceof ByteBuf)) {
                        byteBufsOnly = false;
                        break;
                    }
                }
            }

            for (; i < srcEndIdx; i ++) {
                T m = src[i];
                if (m == null) {
                    throw new NullPointerException("src[" + srcIdx + ']');
                }

                elements[dstIdx ++] = m;
            }
        } finally {
            if (dstIdx != newSize) {
                // Could not finish iteration.
                Arrays.fill(elements, size, dstIdx, null);
            }
        }

        assert dstIdx == newSize : String.format("dstIdx(%d) != newSize(%d)", dstIdx, newSize);
        size = newSize;

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
    public MessageList<T> add(MessageList<T> src, int srcIdx, int srcLen) {
        if (src == null) {
            throw new NullPointerException("src");
        }

        if (srcIdx > src.size - srcLen) {
            throw new IndexOutOfBoundsException(String.format(
                    "srcIdx(%d) + srcLen(%d) > src.size(%d)", srcIdx, srcLen, src.size));
        }

        modifications ++;

        final int dstIdx = size;
        final int newSize = dstIdx + srcLen;
        ensureCapacity(newSize);

        byteBufsOnly &= src.byteBufsOnly;
        System.arraycopy(src.elements, srcIdx, elements, dstIdx, srcLen);

        size = newSize;
        return this;
    }

    /**
     * Clear all messages and return itself.
     */
    public MessageList<T> clear() {
        modifications ++;
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

    /**
     * Casts the type parameter of this list to a different type parameter.  This method is often useful when you have
     * to deal with multiple messages and do not want to down-cast the messages every time you access the list.
     *
     * <pre>
     * public void messageReceived(ChannelHandlerContext ctx, MessageList&lt;Object&gt; msgs) {
     *     MessageList&lt;MyMessage&gt; cast = msgs.cast();
     *     for (MyMessage m: cast) { // or: for (MyMessage m: msgs.&lt;MyMessage&gt;cast())
     *         ...
     *     }
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public <U> MessageList<U> cast() {
        return (MessageList<U>) this;
    }

    /**
     * Iterates over the messages in this list with the specified {@code processor}.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link MessageListProcessor#process(Object)} returned {@code false}.
     */
    public int forEach(MessageListProcessor<? super T> proc) {
        if (proc == null) {
            throw new NullPointerException("proc");
        }

        final int size = this.size;
        if (size == 0) {
            return -1;
        }

        @SuppressWarnings("unchecked")
        MessageListProcessor<T> p = (MessageListProcessor<T>) proc;

        int i = 0;
        try {
            do {
                if (p.process(elements[i])) {
                    i ++;
                } else {
                    return i;
                }
            } while (i < size);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return -1;
    }

    /**
     * Iterates over the messages in this list with the specified {@code processor}.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link MessageListProcessor#process(Object)} returned {@code false}.
     */
    public int forEach(int index, int length, MessageListProcessor<? super T> proc) {
        checkRange(index, length);
        if (proc == null) {
            throw new NullPointerException("proc");
        }

        if (size == 0) {
            return -1;
        }

        @SuppressWarnings("unchecked")
        MessageListProcessor<T> p = (MessageListProcessor<T>) proc;

        final int end = index + length;

        int i = index;
        try {
            do {
                if (p.process(elements[i])) {
                    i ++;
                } else {
                    return i;
                }
            } while (i < end);
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        return -1;
    }

    @Override
    public Iterator<T> iterator() {
        return new MessageListIterator();
    }

    /**
     * Returns the backing array of this list.  Use this array when you want to iterate over the list fast:
     * <pre>
     * {@link MessageList} list = ...;
     * for (Object m: list.array()) {
     *     if (m == null) {
     *         break;
     *     }
     *
     *     handleMessage(m);
     * }
     * </pre>
     */
    public T[] array() {
        return elements;
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

    private void checkIndex(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
    }

    private void checkRange(int index, int length) {
        if (index + length > size) {
            throw new IndexOutOfBoundsException("index: " + index + ", length: " + length + ", size: " + size);
        }
    }

    private final class MessageListIterator implements Iterator<T> {

        private final int expectedModifications = modifications;
        private int index;

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
                return elements[index ++];
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
           throw new UnsupportedOperationException();
        }
    }
}
