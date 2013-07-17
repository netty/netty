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

    MessageList currentMessageList;
    int currentMessageIndex;
    private long currentMessageListSize;

    private MessageList[] messageLists;
    private long[] messageListSizes;

    private MessageList unflushedMessageList;
    private long unflushedMessageListSize;

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

        this.channel = channel;

        messageLists = new MessageList[initialCapacity];
        messageListSizes = new long[initialCapacity];
    }

    void addMessage(Object msg, ChannelPromise promise) {
        MessageList unflushedMessageList = this.unflushedMessageList;
        if (unflushedMessageList == null) {
            this.unflushedMessageList = unflushedMessageList = MessageList.newInstance();
        }

        unflushedMessageList.add(msg, promise);

        int size = channel.calculateMessageSize(msg);
        unflushedMessageListSize += size;
        incrementPendingOutboundBytes(size);
    }

    void addFlush() {
        MessageList unflushedMessageList = this.unflushedMessageList;
        if (unflushedMessageList == null) {
            return;
        }

        int tail = this.tail;

        messageLists[tail] = unflushedMessageList;
        messageListSizes[tail] = unflushedMessageListSize;
        this.unflushedMessageList = null;
        unflushedMessageListSize = 0;

        if ((this.tail = (tail + 1) & (messageLists.length - 1)) == head) {
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
        int n = messageLists.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException("Sorry, deque too big");
        }

        @SuppressWarnings("unchecked")
        MessageList[] a1 = new MessageList[newCapacity];
        System.arraycopy(messageLists, p, a1, 0, r);
        System.arraycopy(messageLists, 0, a1, r, p);
        messageLists = a1;

        long[] a2 = new long[newCapacity];
        System.arraycopy(messageListSizes, p, a2, 0, r);
        System.arraycopy(messageListSizes, 0, a2, r, p);
        messageListSizes = a2;

        head = 0;
        tail = n;
    }

    boolean next() {
        // FIXME: pendingOutboundBytes should be decreased when the messages are flushed.

        decrementPendingOutboundBytes(currentMessageListSize);

        int h = head;

        MessageList e = messageLists[h]; // Element is null if deque empty
        if (e == null) {
            currentMessageListSize = 0;
            currentMessageList = null;
            return false;
        }

        currentMessageList = messageLists[h];
        currentMessageIndex = 0;
        currentMessageListSize = messageListSizes[h];

        messageLists[h] = null;
        messageListSizes[h] = 0;

        head = h + 1 & messageLists.length - 1;
        return true;
    }

    boolean getWritable() {
        return WRITABLE_UPDATER.get(this) == 1;
    }

    int size() {
        return tail - head & messageLists.length - 1;
    }

    boolean isEmpty() {
        return head == tail;
    }

    void clearUnflushed(Throwable cause) {
        MessageList unflushed = unflushedMessageList;
        if (unflushed == null) {
            return;
        }

        // Release all unflushed messages.
        Object[] messages = unflushed.messages();
        ChannelPromise[] promises = unflushed.promises();
        final int size = unflushed.size();
        try {
            for (int i = 0; i < size; i++) {
                ReferenceCountUtil.release(messages[i]);
                ChannelPromise p = promises[i];
                if (!(p instanceof VoidChannelPromise)) {
                    if (!p.tryFailure(cause)) {
                        logger.warn("Promise done already: {} - new exception is:", p, cause);
                    }
                }
            }
        } finally {
            unflushed.recycle();
            decrementPendingOutboundBytes(unflushedMessageListSize);
            unflushedMessageListSize = 0;
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
            if (currentMessageList == null) {
                if (!next()) {
                    return;
                }
            }

            do {
                if (currentMessageList != null) {
                    // Store a local reference of current messages
                    // This is needed as a promise may have a listener attached that will close the channel
                    // The close will call next() which will set currentMessages to null and so
                    // trigger a NPE in the finally block if no local reference is used.
                    //
                    // See https://github.com/netty/netty/issues/1573
                    MessageList current = currentMessageList;
                    // Release all failed messages.
                    Object[] messages = current.messages();
                    ChannelPromise[] promises = current.promises();
                    final int size = current.size();
                    try {
                        for (int i = currentMessageIndex; i < size; i++) {
                            ReferenceCountUtil.release(messages[i]);
                            ChannelPromise p = promises[i];
                            if (!(p instanceof VoidChannelPromise) && !p.tryFailure(cause)) {
                                logger.warn("Promise done already: {} - new exception is:", p, cause);
                            }
                        }
                    } finally {
                        current.recycle();
                    }
                }
            } while(next());
        } finally {
            inFail = false;
        }
    }
}
