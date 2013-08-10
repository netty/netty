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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * (Transport implementors only) an internal data structure used by {@link AbstractChannel} to store its pending
 * outbound write requests.
 */
public final class ChannelOutboundBuffer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private static final int MIN_INITIAL_CAPACITY = 8;

    private static final Recycler<ChannelOutboundBuffer> RECYCLER = new Recycler<ChannelOutboundBuffer>() {
        @Override
        protected ChannelOutboundBuffer newObject(Handle handle) {
            return new ChannelOutboundBuffer(handle);
        }
    };

    static ChannelOutboundBuffer newInstance(AbstractChannel channel) {
        ChannelOutboundBuffer buffer = RECYCLER.get();
        buffer.channel = channel;
        buffer.totalPendingSize = 0;
        buffer.writable = 1;
        return buffer;
    }

    private final Handle handle;
    private AbstractChannel channel;

    // Flushed messages are stored in a circular buffer.
    private Object[] flushed;
    private ChannelPromise[] flushedPromises;
    private int[] flushedPendingSizes;
    private long[] flushedProgresses;
    private long[] flushedTotals;
    private int head;
    private int tail;

    private ByteBuffer[] nioBuffers;
    private int nioBufferCount;
    private long nioBufferSize;

    // Unflushed messages are stored in an array list.
    private Object[] unflushed;
    private ChannelPromise[] unflushedPromises;
    private int[] unflushedPendingSizes;
    private long[] unflushedTotals;
    private int unflushedCount;

    private boolean inFail;

    private static final AtomicLongFieldUpdater<ChannelOutboundBuffer> TOTAL_PENDING_SIZE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "totalPendingSize");

    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
    private volatile long totalPendingSize;

    private static final AtomicIntegerFieldUpdater<ChannelOutboundBuffer> WRITABLE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "writable");

    @SuppressWarnings({ "unused", "FieldMayBeFinal" })
    private volatile int writable = 1;

    private ChannelOutboundBuffer(Handle handle) {
        this(handle, MIN_INITIAL_CAPACITY << 1);
    }

    private ChannelOutboundBuffer(Handle handle, int initialCapacity) {
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

        this.handle = handle;

        flushed = new Object[initialCapacity];
        flushedPromises = new ChannelPromise[initialCapacity];
        flushedPendingSizes = new int[initialCapacity];
        flushedProgresses = new long[initialCapacity];
        flushedTotals = new long[initialCapacity];

        nioBuffers = new ByteBuffer[initialCapacity];

        unflushed = new Object[initialCapacity];
        unflushedPromises = new ChannelPromise[initialCapacity];
        unflushedPendingSizes = new int[initialCapacity];
        unflushedTotals = new long[initialCapacity];
    }

    void addMessage(Object msg, ChannelPromise promise) {
        Object[] unflushed = this.unflushed;
        int unflushedCount = this.unflushedCount;
        if (unflushedCount == unflushed.length - 1) {
            doubleUnflushedCapacity();
            unflushed = this.unflushed;
        }

        int size = channel.estimatorHandle().size(msg);
        if (size < 0) {
            size = 0;
        }
        unflushed[unflushedCount] = msg;
        unflushedPendingSizes[unflushedCount] = size;
        unflushedPromises[unflushedCount] = promise;
        unflushedTotals[unflushedCount] = total(msg);
        this.unflushedCount = unflushedCount + 1;

        // increment pending bytes after adding message to the unflushed arrays.
        // See https://github.com/netty/netty/issues/1619
        incrementPendingOutboundBytes(size);
    }

    private static long total(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).count();
        }
        if (msg instanceof ByteBufHolder) {
            return ((ByteBufHolder) msg).content().readableBytes();
        }
        return -1;
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

        int[] a3 = new int[newCapacity];
        System.arraycopy(unflushedPendingSizes, 0, a3, 0, unflushedCount);
        unflushedPendingSizes = a3;

        long[] a4 = new long[newCapacity];
        System.arraycopy(unflushedTotals, 0, a4, 0, unflushedCount);
        unflushedTotals = a4;
    }

    void addFlush() {
        final int unflushedCount = this.unflushedCount;
        if (unflushedCount == 0) {
            return;
        }

        Object[] unflushed = this.unflushed;
        ChannelPromise[] unflushedPromises = this.unflushedPromises;
        int[] unflushedPendingSizes = this.unflushedPendingSizes;
        long[] unflushedTotals = this.unflushedTotals;

        Object[] flushed = this.flushed;
        ChannelPromise[] flushedPromises = this.flushedPromises;
        int[] flushedPendingSizes = this.flushedPendingSizes;
        long[] flushedProgresses = this.flushedProgresses;
        long[] flushedTotals = this.flushedTotals;
        int head = this.head;
        int tail = this.tail;

        for (int i = 0; i < unflushedCount; i ++) {
            flushed[tail] = unflushed[i];
            unflushed[i] = null;
            flushedPromises[tail] = unflushedPromises[i];
            unflushedPromises[i] = null;
            flushedPendingSizes[tail] = unflushedPendingSizes[i];
            flushedProgresses[tail] = 0;
            flushedTotals[tail] = unflushedTotals[i];
            if ((tail = (tail + 1) & (flushed.length - 1)) == head) {
                this.tail = tail;
                doubleFlushedCapacity();
                head = this.head;
                tail = this.tail;
                flushed = this.flushed;
                flushedPromises = this.flushedPromises;
                flushedPendingSizes = this.flushedPendingSizes;
                flushedProgresses = this.flushedProgresses;
                flushedTotals = this.flushedTotals;
            }
        }

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

        int[] a3 = new int[newCapacity];
        System.arraycopy(flushedPendingSizes, p, a3, 0, r);
        System.arraycopy(flushedPendingSizes, 0, a3, r, p);
        flushedPendingSizes = a3;

        long[] a4 = new long[newCapacity];
        System.arraycopy(flushedProgresses, p, a4, 0, r);
        System.arraycopy(flushedProgresses, 0, a4, r, p);
        flushedProgresses = a4;

        long[] a5 = new long[newCapacity];
        System.arraycopy(flushedTotals, p, a5, 0, r);
        System.arraycopy(flushedTotals, 0, a5, r, p);
        flushedTotals = a5;

        head = 0;
        tail = n;
    }

    /**
     * Increment the pending bytes which will be written at some point.
     * This method is thread-safe!
     */
    void incrementPendingOutboundBytes(int size) {
        // Cache the channel and check for null to make sure we not produce a NPE in case of the Channel gets
        // recycled while process this method.
        Channel channel = this.channel;
        if (size == 0 || channel == null) {
            return;
        }

        long oldValue = totalPendingSize;
        long newWriteBufferSize = oldValue + size;
        while (!TOTAL_PENDING_SIZE_UPDATER.compareAndSet(this, oldValue, newWriteBufferSize)) {
            oldValue = totalPendingSize;
            newWriteBufferSize = oldValue + size;
        }

        int highWaterMark = channel.config().getWriteBufferHighWaterMark();

        if (newWriteBufferSize > highWaterMark) {
            if (WRITABLE_UPDATER.compareAndSet(this, 1, 0)) {
                channel.pipeline().fireChannelWritabilityChanged();
            }
        }
    }

    /**
     * Decrement the pending bytes which will be written at some point.
     * This method is thread-safe!
     */
    void decrementPendingOutboundBytes(int size) {
        // Cache the channel and check for null to make sure we not produce a NPE in case of the Channel gets
        // recycled while process this method.
        Channel channel = this.channel;
        if (size == 0 || channel == null) {
            return;
        }

        long oldValue = totalPendingSize;
        long newWriteBufferSize = oldValue - size;
        while (!TOTAL_PENDING_SIZE_UPDATER.compareAndSet(this, oldValue, newWriteBufferSize)) {
            oldValue = totalPendingSize;
            newWriteBufferSize = oldValue - size;
        }

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
        flushedPromises[head] = null;

        int size = flushedPendingSizes[head];
        flushedPendingSizes[head] = 0;

        this.head = head + 1 & flushed.length - 1;

        promise.trySuccess();
        decrementPendingOutboundBytes(size);

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

        ChannelPromise promise = flushedPromises[head];
        flushedPromises[head] = null;

        int size = flushedPendingSizes[head];
        flushedPendingSizes[head] = 0;

        this.head = head + 1 & flushed.length - 1;

        safeFail(promise, cause);
        decrementPendingOutboundBytes(size);

        return true;
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link ByteBuf} only.
     * {@code null} is returned otherwise.  If this method returns a non-null array, {@link #nioBufferCount()} and
     * {@link #nioBufferSize()} will return the number of NIO buffers in the returned array and the total number
     * of readable bytes of the NIO buffers respectively.
     * <p>
     * Note that the returned array is reused and thus should not escape
     * {@link AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     * Refer to {@link NioSocketChannel#doWrite(ChannelOutboundBuffer)} for an example.
     * </p>
     */
    public ByteBuffer[] nioBuffers() {
        ByteBuffer[] nioBuffers = this.nioBuffers;
        long nioBufferSize = 0;
        int nioBufferCount = 0;

        final int mask = flushed.length - 1;

        Object m;
        int i = head;
        while ((m = flushed[i]) != null) {
            if (!(m instanceof ByteBuf)) {
                this.nioBufferCount = 0;
                this.nioBufferSize = 0;
                return null;
            }

            ByteBuf buf = (ByteBuf) m;

            final int readerIndex = buf.readerIndex();
            final int readableBytes = buf.writerIndex() - readerIndex;

            if (readableBytes > 0) {
                nioBufferSize += readableBytes;

                if (buf.isDirect()) {
                    int count = buf.nioBufferCount();
                    if (count == 1) {
                        if (nioBufferCount == nioBuffers.length) {
                            this.nioBuffers = nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCount);
                        }
                        nioBuffers[nioBufferCount ++] = buf.internalNioBuffer(readerIndex, readableBytes);
                    } else {
                        ByteBuffer[] nioBufs = buf.nioBuffers();
                        if (nioBufferCount + nioBufs.length == nioBuffers.length + 1) {
                            this.nioBuffers = nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCount);
                        }
                        for (ByteBuffer nioBuf: nioBufs) {
                            if (nioBuf == null) {
                                break;
                            }
                            nioBuffers[nioBufferCount ++] = nioBuf;
                        }
                    }
                } else {
                    ByteBuf directBuf = channel.alloc().directBuffer(readableBytes);
                    directBuf.writeBytes(buf, readerIndex, readableBytes);
                    buf.release();
                    flushed[i] = directBuf;
                    if (nioBufferCount == nioBuffers.length) {
                        nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCount);
                    }
                    nioBuffers[nioBufferCount ++] = directBuf.internalNioBuffer(0, readableBytes);
                }
            }

            i = i + 1 & mask;
        }

        this.nioBufferCount = nioBufferCount;
        this.nioBufferSize = nioBufferSize;

        return nioBuffers;
    }

    private static ByteBuffer[] doubleNioBufferArray(ByteBuffer[] array, int size) {
        int newCapacity = array.length << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException();
        }

        ByteBuffer[] newArray = new ByteBuffer[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        return newArray;
    }

    public int nioBufferCount() {
        return nioBufferCount;
    }

    public long nioBufferSize() {
        return nioBufferSize;
    }

    boolean getWritable() {
        return writable != 0;
    }

    public int size() {
        return tail - head & flushed.length - 1;
    }

    public boolean isEmpty() {
        return head == tail;
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

    void close(final ClosedChannelException cause) {
        if (inFail) {
            channel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    close(cause);
                }
            });
            return;
        }

        inFail = true;

        if (channel.isOpen()) {
            throw new IllegalStateException("close() must be invoked after the channel is closed.");
        }

        if (head != tail) {
            throw new IllegalStateException("close() must be invoked after all flushed writes are handled.");
        }

        // Release all unflushed messages.
        Object[] unflushed = this.unflushed;
        ChannelPromise[] unflushedPromises = this.unflushedPromises;
        int[] unflushedPendingSizes = this.unflushedPendingSizes;
        final int unflushedCount = this.unflushedCount;
        try {
            for (int i = 0; i < unflushedCount; i++) {
                safeRelease(unflushed[i]);
                unflushed[i] = null;
                safeFail(unflushedPromises[i], cause);
                unflushedPromises[i] = null;

                // Just decrease; do not trigger any events via decrementPendingOutboundBytes()
                int size = unflushedPendingSizes[i];
                long oldValue = totalPendingSize;
                long newWriteBufferSize = oldValue - size;
                while (!TOTAL_PENDING_SIZE_UPDATER.compareAndSet(this, oldValue, newWriteBufferSize)) {
                    oldValue = totalPendingSize;
                    newWriteBufferSize = oldValue - size;
                }

                unflushedPendingSizes[i] = 0;
            }
        } finally {
            this.unflushedCount = 0;
            inFail = false;
        }
        RECYCLER.recycle(this, handle);

        // Set the channel to null so it can be GC'ed ASAP
        channel = null;
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
