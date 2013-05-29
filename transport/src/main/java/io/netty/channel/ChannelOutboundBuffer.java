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
/*
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */
package io.netty.channel;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

final class ChannelOutboundBuffer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private static final int MIN_INITIAL_CAPACITY = 8;

    ChannelPromise currentPromise;
    Object[] currentMessages;
    int currentMessageStart;
    int currentMessageEnd;

    private ChannelPromise[] promises;
    private Object[][] messages;
    private int[] messageIndexes;
    private int[] messageLengths;

    private int head;
    private int tail;

    ChannelOutboundBuffer() {
        this(MIN_INITIAL_CAPACITY << 1);
    }

    ChannelOutboundBuffer(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 0)");
        }

        // Find the best power of two to hold elements.
        // Tests "<=" because arrays aren't kept full.
        if (initialCapacity >= MIN_INITIAL_CAPACITY) {
            initialCapacity |= initialCapacity >>>  1;
            initialCapacity |= initialCapacity >>>  2;
            initialCapacity |= initialCapacity >>>  4;
            initialCapacity |= initialCapacity >>>  8;
            initialCapacity |= initialCapacity >>> 16;
            initialCapacity ++;

            if (initialCapacity < 0) {  // Too many elements, must back off
                initialCapacity >>>= 1; // Good luck allocating 2 ^ 30 elements
            }
        } else {
            initialCapacity = MIN_INITIAL_CAPACITY;
        }

        promises = new ChannelPromise[initialCapacity];
        messages = new Object[initialCapacity][];
        messageIndexes = new int[initialCapacity];
        messageLengths = new int[initialCapacity];
    }

    void add(Object[] msgs, int index, int length, ChannelPromise promise) {
        int tail = this.tail;
        promises[tail] = promise;
        messages[tail] = msgs;
        messageIndexes[tail] = index;
        messageLengths[tail] = length;

        if ((this.tail = tail + 1 & promises.length - 1) == head) {
            doubleCapacity();
        }
    }

    private void doubleCapacity() {
        assert head == tail;

        int p = head;
        int n = promises.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException("Sorry, deque too big");
        }

        ChannelPromise[] a1 = new ChannelPromise[newCapacity];
        System.arraycopy(promises, p, a1, 0, r);
        System.arraycopy(promises, 0, a1, r, p);
        promises = a1;

        Object[][] a2 = new Object[newCapacity][];
        System.arraycopy(messages, p, a2, 0, r);
        System.arraycopy(messages, 0, a2, r, p);
        messages = a2;

        int[] a3 = new int[newCapacity];
        System.arraycopy(messageIndexes, p, a3, 0, r);
        System.arraycopy(messageIndexes, 0, a3, r, p);
        messageIndexes = a3;

        int[] a4 = new int[newCapacity];
        System.arraycopy(messageLengths, p, a4, 0, r);
        System.arraycopy(messageLengths, 0, a4, r, p);
        messageLengths = a4;

        head = 0;
        tail = n;
    }

    boolean next() {
        int h = head;
        ChannelPromise e = promises[h]; // Element is null if deque empty
        if (e == null) {
            currentPromise = null;
            currentMessages = null;
            return false;
        }

        currentPromise = e;
        currentMessages = messages[h];
        currentMessageStart = messageIndexes[h];
        currentMessageEnd = currentMessageStart + messageLengths[h];

        promises[h] = null;
        messages[h] = null;

        head = h + 1 & promises.length - 1;
        return true;
    }

    int size() {
        return tail - head & promises.length - 1;
    }

    boolean isEmpty() {
        return head == tail;
    }

    void clear() {
        int head = this.head;
        int tail = this.tail;
        if (head != tail) {
            this.head = this.tail = 0;
            final int mask = promises.length - 1;
            int i = head;
            do {
                promises[i] = null;
                messages[i] = null;
                i = i + 1 & mask;
            } while (i != tail);
        }
    }

    void fail(Throwable cause) {
        do {
            if (currentPromise != null) {
                if (!currentPromise.tryFailure(cause)) {
                    logger.warn("Promise done already:", cause);
                }
            }
            // release all failed messages
            for (int i = currentMessageStart; i < currentMessageEnd; i++) {
                ByteBufUtil.release(currentMessages[i]);
            }
        } while(next());
    }
}
