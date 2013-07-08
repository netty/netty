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

import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

final class ChannelOutboundBuffer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private static final int MIN_INITIAL_CAPACITY = 8;

    ChannelPromise currentPromise;
    MessageList<Object> currentMessages;
    int currentMessageIndex;
    private long currentMessageListSize;

    private ChannelPromise[] promises;
    private MessageList<Object>[] messages;
    private long[] messageListSizes;

    private int head;
    private int tail;
    private boolean inFail;
    private final AbstractChannel channel;

    private long pendingOutboundBytes;

    private static final AtomicIntegerFieldUpdater<ChannelOutboundBuffer> WRITABLE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "writable");

    @SuppressWarnings("unused")
    private volatile int writable = 1;

    ChannelOutboundBuffer(AbstractChannel channel) {
        this(channel, MIN_INITIAL_CAPACITY << 1);
    }

    @SuppressWarnings("unchecked")
    ChannelOutboundBuffer(AbstractChannel channel, int initialCapacity) {
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
        messages = new MessageList[initialCapacity];
        messageListSizes = new long[initialCapacity];
        this.channel = channel;
    }

    void addMessage(Object msg) {
        int tail = this.tail;
        MessageList<Object> msgs = messages[tail];
        if (msgs == null) {
            messages[tail] = msgs = MessageList.newInstance();
        }
        msgs.add(msg);

        int size = channel.calculateMessageSize(msg);
        messageListSizes[tail] += size;
        incrementPendingOutboundBytes(size);
    }

    void addPromise(ChannelPromise promise) {
        int tail = this.tail;
        promises[tail] = promise;
        if ((this.tail = tail + 1 & promises.length - 1) == head) {
            doubleCapacity();
        }
    }

    private void incrementPendingOutboundBytes(int size) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = pendingOutboundBytes += size;
        int highWaterMark = channel.config().getWriteBufferHighWaterMark();

        if (newWriteBufferSize > highWaterMark) {
            if (WRITABLE_UPDATER.compareAndSet(this, 1, 0)) {
                channel.pipeline().fireChannelWritabilityChanged();
            }
        }
    }

    private void decrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = pendingOutboundBytes -= size;
        int lowWaterMark = channel.config().getWriteBufferLowWaterMark();

        if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {

            if (WRITABLE_UPDATER.compareAndSet(this, 0, 1)) {
                channel.pipeline().fireChannelWritabilityChanged();
            }
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

        @SuppressWarnings("unchecked")
        MessageList<Object>[] a2 = new MessageList[newCapacity];
        System.arraycopy(messages, p, a2, 0, r);
        System.arraycopy(messages, 0, a2, r, p);
        messages = a2;

        long[] a3 = new long[newCapacity];
        System.arraycopy(messageListSizes, p, a3, 0, r);
        System.arraycopy(messageListSizes, 0, a3, r, p);
        messageListSizes = a3;

        head = 0;
        tail = n;
    }

    boolean next() {
        // FIXME: pendingOutboundBytes should be decreased when the messages are flushed.

        decrementPendingOutboundBytes(currentMessageListSize);

        int h = head;

        ChannelPromise e = promises[h]; // Element is null if deque empty
        if (e == null) {
            currentMessageListSize = 0;
            currentPromise = null;
            currentMessages = null;
            return false;
        }

        currentPromise = e;
        currentMessages = messages[h];
        currentMessageIndex = 0;
        currentMessageListSize = messageListSizes[h];

        promises[h] = null;
        messages[h] = null;
        messageListSizes[h] = 0;

        head = h + 1 & promises.length - 1;
        return true;
    }

    boolean getWritable() {
        return WRITABLE_UPDATER.get(this) == 1;
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
                messageListSizes[i] = 0;
                i = i + 1 & mask;
            } while (i != tail);
        }
    }

    void fail(Throwable cause) {
        // Make sure that this method does not reenter.  A listener added to the current promise can be notified by the
        // current thread in the tryFailure() call of the loop below, and the listener can trigger another fail() call
        // indirectly (usually by closing the channel.)
        //
        // See https://github.com/netty/netty/issues/1501
        if (inFail) {
            return;
        }

        try {
            inFail = true;
            if (currentPromise == null) {
                if (!next()) {
                    return;
                }
            }

            do {
                if (!(currentPromise instanceof VoidChannelPromise) && !currentPromise.tryFailure(cause)) {
                    logger.warn("Promise done already: {} - new exception is:", currentPromise, cause);
                }

                if (currentMessages != null) {
                    // Release all failed messages.
                    try {
                        for (int i = currentMessageIndex; i < currentMessages.size(); i++) {
                            Object msg = currentMessages.get(i);
                            ReferenceCountUtil.release(msg);
                        }
                    } finally {
                        currentMessages.recycle();
                    }
                }
            } while(next());
        } finally {
            inFail = false;
        }
    }
}
