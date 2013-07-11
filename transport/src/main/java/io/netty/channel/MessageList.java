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

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

import java.util.Arrays;

/**
 * A simple array-backed list that holds one or more messages.
 */
final class MessageList {

    private static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MIN_INITIAL_CAPACITY = 4;

    private static final Recycler<MessageList> RECYCLER = new Recycler<MessageList>() {
        @Override
        protected MessageList newObject(Handle handle) {
            return new MessageList(handle);
        }
    };

    /**
     * Create a new empty {@link MessageList} instance.
     */
    static MessageList newInstance() {
        MessageList ret = RECYCLER.get();
        return ret;
    }

    private final Handle handle;
    private Object[] messages;
    private ChannelPromise[] promises;
    private int size;

    MessageList(Handle handle) {
        this(handle, DEFAULT_INITIAL_CAPACITY);
    }

    MessageList(Handle handle, int initialCapacity) {
        this.handle = handle;
        initialCapacity = normalizeCapacity(initialCapacity);
        messages = new Object[initialCapacity];
        promises = new ChannelPromise[initialCapacity];
    }

    /**
     * Return the current size of this {@link MessageList} and so how many messages it holds.
     */
    int size() {
        return size;
    }

    /**
     * Return {@code true} if this {@link MessageList} is empty and so contains no messages.
     */
    boolean isEmpty() {
        return size == 0;
    }

    /**
     * Add the message to this {@link MessageList} and return itself.
     */
    MessageList add(Object message, ChannelPromise promise) {
        int oldSize = size;
        int newSize = oldSize + 1;
        ensureCapacity(newSize);
        messages[oldSize] = message;
        promises[oldSize] = promise;
        size = newSize;
        return this;
    }

    /**
     * Returns the backing array of this list.
     */
    Object[] messages() {
        return messages;
    }

    ChannelPromise[] promises() {
        return promises;
    }

    /**
     * Clear and recycle this instance.
     */
    boolean recycle() {
        Arrays.fill(messages, 0, size, null);
        Arrays.fill(promises, 0, size, null);
        size = 0;
        return RECYCLER.recycle(this, handle);
    }

    private void ensureCapacity(int capacity) {
        if (messages.length >= capacity) {
            return;
        }

        final int size = this.size;
        capacity = normalizeCapacity(capacity);

        Object[] newMessages = new Object[capacity];
        System.arraycopy(messages, 0, newMessages, 0, size);
        messages = newMessages;

        ChannelPromise[] newPromises = new ChannelPromise[capacity];
        System.arraycopy(promises, 0, newPromises, 0, size);
        promises = newPromises;
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
}
