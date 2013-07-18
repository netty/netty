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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class ChannelOutboundBuffer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private static final int MIN_INITIAL_CAPACITY = 8;

    private final AbstractChannel channel;

    // Flushed messages are stored in a circulas queue.
    private Object[] flushed;
    private ChannelPromise[] flushedPromises;
    private long[] flushedProgresses;
    private long[] flushedTotals;
    private int head;
    private int tail;

    // Unflushed messages are stored in an array list.
    private Object[] unflushed;
    private ChannelPromise[] unflushedPromises;
    private long[] unflushedTotals;
    private int unflushedCount;

    private boolean inFail;
    private long pendingOutboundBytes;

    private static final AtomicIntegerFieldUpdater<ChannelOutboundBuffer> WRITABLE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "writable");

    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
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

        flushed = new Object[initialCapacity];
        flushedPromises = new ChannelPromise[initialCapacity];
        flushedProgresses = new long[initialCapacity];
        flushedTotals = new long[initialCapacity];

        unflushed = new Object[initialCapacity];
        unflushedPromises = new ChannelPromise[initialCapacity];
        unflushedTotals = new long[initialCapacity];
    }

    void addMessage(Object msg, ChannelPromise promise) {
        Object[] unflushed = this.unflushed;
        int unflushedCount = this.unflushedCount;
        if (unflushedCount == unflushed.length - 1) {
            doubleUnflushedCapacity();
            unflushed = this.unflushed;
        }

        final int size = channel.calculateMessageSize(msg);
        incrementPendingOutboundBytes(size);

        unflushed[unflushedCount] = msg;
        unflushedPromises[unflushedCount] = promise;
        unflushedTotals[unflushedCount] = size;
        this.unflushedCount = unflushedCount + 1;
    }

    private void doubleUnflushedCapacity() {
        int newCapacity = unflushed.length << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException();
        }

        int unflushedCount = this.unflushedCount;

        Object[] a1 = new Object[newCapacity];
        System.arraycopy(unflushed, 0, a1, 0, unflushedCount);
        unflushed = a1;

        ChannelPromise[] a2 = new ChannelPromise[newCapacity];
        System.arraycopy(unflushedPromises, 0, a2, 0, unflushedCount);
        unflushedPromises = a2;

        long[] a3 = new long[newCapacity];
        System.arraycopy(unflushedTotals, 0, a3, 0, unflushedCount);
        unflushedTotals = a3;
    }

    void addFlush() {
        final int unflushedCount = this.unflushedCount;
        if (unflushedCount == 0) {
            return;
        }

        Object[] unflushed = this.unflushed;
        ChannelPromise[] unflushedPromises = this.unflushedPromises;
        long[] unflushedTotals = this.unflushedTotals;

        Object[] flushed = this.flushed;
        ChannelPromise[] flushedPromises = this.flushedPromises;
        long[] flushedProgresses = this.flushedProgresses;
        long[] flushedTotals = this.flushedTotals;
        int head = this.head;
        int tail = this.tail;

        for (int i = 0; i < unflushedCount; i ++) {
            flushed[tail] = unflushed[i];
            flushedPromises[tail] = unflushedPromises[i];
            flushedProgresses[tail] = 0;
            flushedTotals[tail] = unflushedTotals[i];
            if ((tail = (tail + 1) & (flushed.length - 1)) == head) {
                this.tail = tail;
                doubleFlushedCapacity();
                head = this.head;
                tail = this.tail;
                flushed = this.flushed;
                flushedPromises = this.flushedPromises;
                flushedProgresses = this.flushedProgresses;
                flushedTotals = this.flushedTotals;
            }
        }

        Arrays.fill(unflushed, 0, unflushedCount, null);
        Arrays.fill(unflushedPromises, 0, unflushedCount, null);
        this.unflushedCount = 0;

        this.tail = tail;
    }

    private void doubleFlushedCapacity() {
        int p = head;
        int n = flushed.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException();
        }

        Object[] a1 = new Object[newCapacity];
        System.arraycopy(flushed, p, a1, 0, r);
        System.arraycopy(flushed, 0, a1, r, p);
        flushed = a1;

        ChannelPromise[] a2 = new ChannelPromise[newCapacity];
        System.arraycopy(flushedPromises, p, a2, 0, r);
        System.arraycopy(flushedPromises, 0, a2, r, p);
        flushedPromises = a2;

        long[] a3 = new long[newCapacity];
        System.arraycopy(flushedProgresses, p, a3, 0, r);
        System.arraycopy(flushedProgresses, 0, a3, r, p);
        flushedProgresses = a3;

        long[] a4 = new long[newCapacity];
        System.arraycopy(flushedTotals, p, a4, 0, r);
        System.arraycopy(flushedTotals, 0, a4, r, p);
        flushedTotals = a4;

        head = 0;
        tail = n;
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

    public Object current() {
        return flushed[head];
    }

    public void progress(long amount) {
        int head = this.head;
        ChannelPromise p = flushedPromises[head];
        if (p instanceof ChannelProgressivePromise) {
            long progress = flushedProgresses[head] + amount;
            flushedProgresses[head] = progress;
            ((ChannelProgressivePromise) p).tryProgress(progress, flushedTotals[head]);
        }
    }

    public boolean remove() {
        int head = this.head;

        Object msg = flushed[head];
        if (msg == null) {
            return false;
        }

        safeRelease(msg);
        flushed[head] = null;

        ChannelPromise promise = flushedPromises[head];
        promise.trySuccess();
        flushedPromises[head] = null;

        decrementPendingOutboundBytes(flushedTotals[head]);

        this.head = head + 1 & flushed.length - 1;
        return true;
    }

    public boolean remove(Throwable cause) {
        int head = this.head;

        Object msg = flushed[head];
        if (msg == null) {
            return false;
        }

        safeRelease(msg);
        flushed[head] = null;

        safeFail(flushedPromises[head], cause);
        flushedPromises[head] = null;

        decrementPendingOutboundBytes(flushedTotals[head]);

        this.head = head + 1 & flushed.length - 1;
        return true;
    }

    boolean getWritable() {
        return WRITABLE_UPDATER.get(this) == 1;
    }

    public int size() {
        return tail - head & flushed.length - 1;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    void failUnflushed(Throwable cause) {
        if (inFail) {
            return;
        }

        inFail = true;

        // Release all unflushed messages.
        Object[] unflushed = this.unflushed;
        ChannelPromise[] unflushedPromises = this.unflushedPromises;
        long[] unflushedTotals = this.unflushedTotals;
        final int unflushedCount = this.unflushedCount;
        try {
            for (int i = 0; i < unflushedCount; i++) {
                safeRelease(unflushed[i]);
                safeFail(unflushedPromises[i], cause);
                decrementPendingOutboundBytes(unflushedTotals[i]);
            }
        } finally {
            inFail = false;
        }
    }

    void failFlushed(Throwable cause) {
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
            for (;;) {
                if (!remove(cause)) {
                    break;
                }
            }
        } finally {
            inFail = false;
        }
    }

    private static void safeRelease(Object message) {
        try {
            ReferenceCountUtil.release(message);
        } catch (Throwable t) {
            logger.warn("Failed to release a message.", t);
        }
    }

    private static void safeFail(ChannelPromise promise, Throwable cause) {
        if (!(promise instanceof VoidChannelPromise) && !promise.tryFailure(cause)) {
            logger.warn("Promise done already: {} - new exception is:", promise, cause);
        }
    }
}
