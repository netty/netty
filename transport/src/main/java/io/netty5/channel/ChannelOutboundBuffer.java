/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.ObjectPool.Handle;
import io.netty5.util.internal.PromiseNotificationUtil;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * (Transport implementors only) an internal data structure used by {@link AbstractChannel} to store its pending
 * outbound write requests.
 * <p>
 * All methods must be called by a transport implementation from an I/O thread, except the following ones:
 * <ul>
 * <li>{@link #totalPendingWriteBytes()}</li>
 * </ul>
 * </p>
 */
final class ChannelOutboundBuffer {
    // Assuming a 64-bit JVM:
    //  - 16 bytes object header
    //  - 6 reference fields
    //  - 2 long fields
    //  - 2 int fields
    //  - 1 boolean field
    //  - padding
    static final int CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD =
            SystemPropertyUtil.getInt("io.netty5.transport.outboundBufferEntrySizeOverhead", 96);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private final EventExecutor executor;

    // Entry(flushedEntry) --> ... Entry(unflushedEntry) --> ... Entry(tailEntry)
    //
    // The Entry that is the first in the linked-list structure that was flushed
    private Entry flushedEntry;
    // The Entry which is the first unflushed in the linked-list structure
    private Entry unflushedEntry;
    // The Entry which represents the tail of the buffer
    private Entry tailEntry;
    // The number of flushed entries that are not written yet
    private int flushed;

    private boolean inFail;

    private boolean closed;

    // We use a volatile only as its single-writer, multiple reader
    private volatile long totalPendingSize;

    @SuppressWarnings("UnusedDeclaration")
    ChannelOutboundBuffer(EventExecutor executor) {
        this.executor = executor;
    }

    private void incrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        // Single-writer only so no atomic operation needed.
        totalPendingSize += size;
    }

    private void decrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        // Single-writer only so no atomic operation needed.
        totalPendingSize -= size;
    }

    /**
     * Add given message to this {@link ChannelOutboundBuffer}. The given {@link Promise} will be notified once
     * the message was written.
     */
    void addMessage(Object msg, int size, Promise<Void> promise) {
        if (closed) {
            throw new IllegalStateException();
        }
        assert executor.inEventLoop();
        Entry entry = Entry.newInstance(msg, size, total(msg), promise);
        if (tailEntry == null) {
            flushedEntry = null;
        } else {
            Entry tail = tailEntry;
            tail.next = entry;
        }
        tailEntry = entry;
        if (unflushedEntry == null) {
            unflushedEntry = entry;
        }

        // increment pending bytes after adding message to the unflushed arrays.
        // See https://github.com/netty/netty/issues/1619
        incrementPendingOutboundBytes(entry.pendingSize);
    }

    /**
     * Add a flush to this {@link ChannelOutboundBuffer}. This means all previous added messages are marked as flushed
     * and so you will be able to handle them.
     */
    void addFlush() {
        assert executor.inEventLoop();

        // There is no need to process all entries if there was already a flush before and no new messages
        // where added in the meantime.
        //
        // See https://github.com/netty/netty/issues/2577
        Entry entry = unflushedEntry;
        if (entry != null) {
            if (flushedEntry == null) {
                // there is no flushedEntry yet, so start with the entry
                flushedEntry = entry;
            }

            Entry prev = null;
            do {
                if (!entry.promise.setUncancellable()) {
                    // Was cancelled so make sure we free up memory, unlink and notify about the freed bytes
                    int pending = entry.cancel();
                    if (prev == null) {
                        // It's the first entry, drop it
                        flushedEntry = entry.next;
                    } else {
                        // Remove te entry from the linked list.
                        prev.next = entry.next;
                    }
                    Entry next = entry.next;
                    entry.recycle();
                    entry = next;

                    decrementPendingOutboundBytes(pending);
                } else {
                    flushed ++;
                    prev = entry;
                    entry = entry.next;
                }
            } while (entry != null);

            // All flushed so reset unflushedEntry
            unflushedEntry = null;
        }
    }

    private static long total(Object msg) {
        if (msg instanceof Buffer) {
            return ((Buffer) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).count();
        }
        return -1;
    }

    /**
     * Return the current message to write or {@code null} if nothing was flushed before and so is ready to be written.
     */
    Object current() {
        assert executor.inEventLoop();

        Entry entry = flushedEntry;
        if (entry == null) {
            return null;
        }

        return entry.msg;
    }

    /**
     * Will remove the current message, mark its {@link Promise} as success and return {@code true}. If no
     * flushed message exists at the time this method is called it will return {@code false} to signal that no more
     * messages are ready to be handled.
     */
    int remove() {
        assert executor.inEventLoop();

        Entry e = flushedEntry;
        if (e == null) {
            return -1;
        }
        Object msg = e.msg;

        Promise<Void> promise = e.promise;
        int size = e.pendingSize;

        removeEntry(e);

        if (!e.cancelled) {
            // only release message, notify and decrement if it was not canceled before.
            SilentDispose.trySilentDispose(msg, logger);
            safeSuccess(promise);
            decrementPendingOutboundBytes(size);
        }

        // recycle the entry
        e.recycle();

        return size - CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
    }

    /**
     * Will remove the current message, mark its {@link Promise} as failure using the given {@link Throwable}
     * and return {@code true}. If no   flushed message exists at the time this method is called it will return
     * {@code false} to signal that no more messages are ready to be handled.
     */
    int remove(Throwable cause) {
        assert executor.inEventLoop();

        Entry e = flushedEntry;
        if (e == null) {
            return -1;
        }
        Object msg = e.msg;

        Promise<Void> promise = e.promise;
        int size = e.pendingSize;

        removeEntry(e);

        if (!e.cancelled) {
            // only release message, fail and decrement if it was not canceled before.
            SilentDispose.trySilentDispose(msg, logger);

            safeFail(promise, cause);
            decrementPendingOutboundBytes(size);
        }

        // recycle the entry
        e.recycle();

        return size - CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
    }

    private void removeEntry(Entry e) {
        assert executor.inEventLoop();

        if (-- flushed == 0) {
            // processed everything
            flushedEntry = null;
            if (e == tailEntry) {
                tailEntry = null;
                unflushedEntry = null;
            }
        } else {
            flushedEntry = e.next;
        }
    }

    /**
     * Removes the fully written entries and update the reader index of the partially written entry.
     * This operation assumes all messages in this buffer are either {@link Buffer}s or {@link Buffer}s.
     */
    int removeBytes(long writtenBytes) {
        assert executor.inEventLoop();

        Object msg = current();
        int messages = 0;
        while (writtenBytes > 0 || hasZeroReadable(msg)) {
            if (msg instanceof BufferAddressedEnvelope) {
                msg = ((BufferAddressedEnvelope<?, ?>) msg).content();
            }
            if (msg instanceof Buffer) {
                Buffer buf = (Buffer) msg;
                final int readableBytes = buf.readableBytes();
                if (readableBytes <= writtenBytes) {
                    writtenBytes -= readableBytes;
                    remove();
                    messages++;
                } else { // readableBytes > writtenBytes
                    buf.readSplit(Math.toIntExact(writtenBytes)).close();
                    break;
                }
            } else {
                break; // Don't know how to process this message. Might be null.
            }
            msg = current();
        }
        return messages;
    }

    private static boolean hasZeroReadable(Object msg) {
        if (msg instanceof Buffer) {
            return ((Buffer) msg).readableBytes() == 0;
        }
        return false;
    }

    /**
     * Returns the number of flushed messages in this {@link ChannelOutboundBuffer}.
     */
    int size() {
        assert executor.inEventLoop();

        return flushed;
    }

    /**
     * Returns {@code true} if there are flushed messages in this {@link ChannelOutboundBuffer} or {@code false}
     * otherwise.
     */
    boolean isEmpty() {
        assert executor.inEventLoop();

        return flushed == 0;
    }

    void failFlushedAndClose(Throwable failCause, Throwable closeCause) {
        assert executor.inEventLoop();

        failFlushed(failCause);
        close(closeCause);
    }

    void failFlushed(Throwable cause) {
        assert executor.inEventLoop();

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
            while (!isEmpty()) {
                remove(cause);
            }
        } finally {
            inFail = false;
        }
    }

    boolean isClosed() {
        return closed;
    }

    private void close(final Throwable cause) {
        assert executor.inEventLoop();

        if (inFail) {
            executor.execute(() -> close(cause));
            return;
        }
        inFail = true;

        if (!isEmpty()) {
            throw new IllegalStateException("close() must be invoked after all flushed writes are handled.");
        }
        // Release all unflushed messages.
        try {
            Entry e = unflushedEntry;
            while (e != null) {
                int size = e.pendingSize;

                decrementPendingOutboundBytes(size);

                if (!e.cancelled) {
                    SilentDispose.dispose(e.msg, logger);
                    safeFail(e.promise, cause);
                }
                e = e.recycleAndGetNext();
            }
        } finally {
            closed = true;
            inFail = false;
        }
    }

    private static void safeSuccess(Promise<Void> promise) {
        PromiseNotificationUtil.trySuccess(promise, null, logger);
    }

    private static void safeFail(Promise<Void> promise, Throwable cause) {
        PromiseNotificationUtil.tryFailure(promise, cause, logger);
    }

    long totalPendingWriteBytes() {
        return totalPendingSize;
    }
    /**
     * Call {@link Function#apply(Object)} for each flushed message
     * in this {@link ChannelOutboundBuffer} until {@link Function#apply(Object)}
     * returns {@link Boolean#FALSE} or there are no more flushed messages to process.
     */
    void forEachFlushedMessage(Predicate<Object> processor) {
        assert executor.inEventLoop();

        requireNonNull(processor, "processor");

        Entry entry = flushedEntry;
        if (entry == null) {
            return;
        }

        do {
            if (!entry.cancelled) {
                if (!processor.test(entry.msg)) {
                    return;
                }
            }
            entry = entry.next;
        } while (isFlushedEntry(entry));
    }

    private boolean isFlushedEntry(Entry e) {
        return e != null && e != unflushedEntry;
    }

    private static final class Entry {
        private static final ObjectPool<Entry> RECYCLER = ObjectPool.newPool(Entry::new);

        private final Handle<Entry> handle;
        Entry next;
        Object msg;
        Promise<Void> promise;
        long progress;
        long total;
        int pendingSize;
        int count = -1;
        boolean cancelled;

        private Entry(Handle<Entry> handle) {
            this.handle = handle;
        }

        static Entry newInstance(Object msg, int size, long total, Promise<Void> promise) {
            Entry entry = RECYCLER.get();
            entry.msg = msg;
            entry.pendingSize = size + CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
            entry.total = total;
            entry.promise = promise;
            return entry;
        }

        int cancel() {
            if (!cancelled) {
                cancelled = true;
                int pSize = pendingSize;

                // release message and replace with null
                SilentDispose.dispose(msg, logger);
                msg = null;

                pendingSize = 0;
                total = 0;
                progress = 0;
                return pSize;
            }
            return 0;
        }

        void recycle() {
            next = null;
            msg = null;
            promise = null;
            progress = 0;
            total = 0;
            pendingSize = 0;
            count = -1;
            cancelled = false;
            handle.recycle(this);
        }

        Entry recycleAndGetNext() {
            Entry next = this.next;
            recycle();
            return next;
        }
    }
}
