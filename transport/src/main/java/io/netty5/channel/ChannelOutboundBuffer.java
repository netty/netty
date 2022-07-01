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
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.ObjectPool.Handle;
import io.netty5.util.internal.PromiseNotificationUtil;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
public final class ChannelOutboundBuffer {
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

    private static final FastThreadLocal<BufferCache> NIO_BUFFERS = new FastThreadLocal<>() {
        @Override
        protected BufferCache initialValue() {
            BufferCache cache = new BufferCache();
            cache.buffers = new ByteBuffer[1024];
            return cache;
        }
    };

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

    private int nioBufferCount;
    private long nioBufferSize;

    private boolean inFail;

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
    public void addMessage(Object msg, int size, Promise<Void> promise) {
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
    public void addFlush() {
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
    public Object current() {
        assert executor.inEventLoop();

        Entry entry = flushedEntry;
        if (entry == null) {
            return null;
        }

        return entry.msg;
    }

    /**
     * Return the current message flush progress.
     * @return {@code 0} if nothing was flushed before for the current message or there is no current message
     */
    public long currentProgress() {
        assert executor.inEventLoop();

        Entry entry = flushedEntry;
        if (entry == null) {
            return 0;
        }
        return entry.progress;
    }

    /**
     * Notify the {@link Promise} of the current message about writing progress.
     */
    public void progress(long amount) {
        assert executor.inEventLoop();

        Entry e = flushedEntry;
        assert e != null;
        e.progress += amount;
    }

    /**
     * Will remove the current message, mark its {@link Promise} as success and return {@code true}. If no
     * flushed message exists at the time this method is called it will return {@code false} to signal that no more
     * messages are ready to be handled.
     */
    public boolean remove() {
        assert executor.inEventLoop();

        Entry e = flushedEntry;
        if (e == null) {
            clearNioBuffers();
            return false;
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

        return true;
    }

    /**
     * Will remove the current message, mark its {@link Promise} as failure using the given {@link Throwable}
     * and return {@code true}. If no   flushed message exists at the time this method is called it will return
     * {@code false} to signal that no more messages are ready to be handled.
     */
    public boolean remove(Throwable cause) {
        assert executor.inEventLoop();

        Entry e = flushedEntry;
        if (e == null) {
            clearNioBuffers();
            return false;
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

        return true;
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
    public void removeBytes(long writtenBytes) {
        assert executor.inEventLoop();

        Object msg = current();
        while (writtenBytes > 0 || hasZeroReadable(msg)) {
            if (msg instanceof Buffer) {
                Buffer buf = (Buffer) msg;
                final int readableBytes = buf.readableBytes();
                if (readableBytes <= writtenBytes) {
                    progress(readableBytes);
                    writtenBytes -= readableBytes;
                    remove();
                } else { // readableBytes > writtenBytes
                    buf.readSplit(Math.toIntExact(writtenBytes)).close();
                    progress(writtenBytes);
                    break;
                }
            } else {
                break; // Don't know how to process this message. Might be null.
            }
            msg = current();
        }
        clearNioBuffers();
    }

    private static boolean hasZeroReadable(Object msg) {
        if (msg instanceof Buffer) {
            return ((Buffer) msg).readableBytes() == 0;
        }
        return false;
    }

    // Clear all ByteBuffer from the array so these can be GC'ed.
    // See https://github.com/netty/netty/issues/3837
    private void clearNioBuffers() {
        int count = nioBufferCount;
        if (count > 0) {
            nioBufferCount = 0;
            Arrays.fill(NIO_BUFFERS.get().buffers, 0, count, null);
        }
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link Buffer} only.
     * {@link #nioBufferCount()} and {@link #nioBufferSize()} will return the number of NIO buffers in the returned
     * array and the total number of readable bytes of the NIO buffers respectively.
     * <p>
     * Note that the returned array is reused and thus should not escape
     * {@link AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     * </p>
     */
    public ByteBuffer[] nioBuffers() {
        assert executor.inEventLoop();

        return nioBuffers(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link Buffer} only.
     * {@link #nioBufferCount()} and {@link #nioBufferSize()} will return the number of NIO buffers in the returned
     * array and the total number of readable bytes of the NIO buffers respectively.
     * <p>
     * Note that the returned array is reused and thus should not escape
     * {@link AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     * </p>
     * @param maxCount The maximum amount of buffers that will be added to the return value.
     * @param maxBytes A hint toward the maximum number of bytes to include as part of the return value. Note that this
     *                 value maybe exceeded because we make a best effort to include at least 1 {@link ByteBuffer}
     *                 in the return value to ensure write progress is made.
     */
    public ByteBuffer[] nioBuffers(int maxCount, long maxBytes) {
        assert executor.inEventLoop();

        assert maxCount > 0;
        assert maxBytes > 0;
        long nioBufferSize = 0;
        int nioBufferCount = 0;
        BufferCache cache = NIO_BUFFERS.get();
        cache.dataSize = 0;
        cache.bufferCount = 0;
        ByteBuffer[] nioBuffers = cache.buffers;

        Entry entry = flushedEntry;
        while (isFlushedEntry(entry) && entry.msg instanceof Buffer) {
            if (!entry.cancelled) {
                Buffer buf = (Buffer) entry.msg;
                if (buf.readableBytes() > 0) {
                    int count = buf.forEachReadable(0, (index, component) -> {
                        ByteBuffer byteBuffer = component.readableBuffer();
                        if (cache.bufferCount > 0 && cache.dataSize + byteBuffer.remaining() > maxBytes) {
                            // If the nioBufferSize + readableBytes will overflow maxBytes, and there is at least
                            // one entry we stop populate the ByteBuffer array. This is done for 2 reasons:
                            // 1. bsd/osx don't allow to write more bytes then Integer.MAX_VALUE with one
                            // writev(...) call and so will return 'EINVAL', which will raise an IOException.
                            // On Linux it may work depending on the architecture and kernel but to be safe we also
                            // enforce the limit here.
                            // 2. There is no sense in putting more data in the array than is likely to be accepted
                            // by the OS.
                            //
                            // See also:
                            // - https://www.freebsd.org/cgi/man.cgi?query=write&sektion=2
                            // - https://linux.die.net//man/2/writev
                            return false;
                        }
                        cache.dataSize += byteBuffer.remaining();
                        ByteBuffer[] buffers = cache.buffers;
                        int bufferCount = cache.bufferCount;
                        if (buffers.length == bufferCount && bufferCount < maxCount) {
                            buffers = cache.buffers = expandNioBufferArray(buffers, bufferCount + 1, bufferCount);
                        }
                        buffers[cache.bufferCount] = byteBuffer;
                        bufferCount++;
                        cache.bufferCount = bufferCount;
                        return bufferCount < maxCount;
                    });
                    if (count < 0) {
                        break;
                    }
                }
            }
            entry = entry.next;
        }
        this.nioBufferCount = nioBufferCount + cache.bufferCount;
        this.nioBufferSize = nioBufferSize + cache.dataSize;

        return nioBuffers;
    }

    private static ByteBuffer[] expandNioBufferArray(ByteBuffer[] array, int neededSpace, int size) {
        int newCapacity = array.length;
        do {
            // double capacity until it is big enough
            // See https://github.com/netty/netty/issues/1890
            newCapacity <<= 1;

            if (newCapacity < 0) {
                throw new IllegalStateException();
            }

        } while (neededSpace > newCapacity);

        ByteBuffer[] newArray = new ByteBuffer[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        return newArray;
    }

    /**
     * Returns the number of {@link ByteBuffer} that can be written out of the {@link ByteBuffer} array that was
     * obtained via {@link #nioBuffers()}. This method <strong>MUST</strong> be called after {@link #nioBuffers()}
     * was called.
     */
    public int nioBufferCount() {
        assert executor.inEventLoop();

        return nioBufferCount;
    }

    /**
     * Returns the number of bytes that can be written out of the {@link ByteBuffer} array that was
     * obtained via {@link #nioBuffers()}. This method <strong>MUST</strong> be called after {@link #nioBuffers()}
     * was called.
     */
    public long nioBufferSize() {
        assert executor.inEventLoop();

        return nioBufferSize;
    }

    /**
     * Returns the number of flushed messages in this {@link ChannelOutboundBuffer}.
     */
    public int size() {
        assert executor.inEventLoop();

        return flushed;
    }

    /**
     * Returns {@code true} if there are flushed messages in this {@link ChannelOutboundBuffer} or {@code false}
     * otherwise.
     */
    public boolean isEmpty() {
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
            for (;;) {
                if (!remove(cause)) {
                    break;
                }
            }
        } finally {
            inFail = false;
        }
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
            inFail = false;
        }
        clearNioBuffers();
    }

    private static void safeSuccess(Promise<Void> promise) {
        PromiseNotificationUtil.trySuccess(promise, null, logger);
    }

    private static void safeFail(Promise<Void> promise, Throwable cause) {
        PromiseNotificationUtil.tryFailure(promise, cause, logger);
    }

    public long totalPendingWriteBytes() {
        return totalPendingSize;
    }
    /**
     * Call {@link MessageProcessor#processMessage(Object)} for each flushed message
     * in this {@link ChannelOutboundBuffer} until {@link MessageProcessor#processMessage(Object)}
     * returns {@code false} or there are no more flushed messages to process.
     */
    public <T extends Exception> void forEachFlushedMessage(MessageProcessor<T> processor) throws T {
        assert executor.inEventLoop();

        requireNonNull(processor, "processor");

        Entry entry = flushedEntry;
        if (entry == null) {
            return;
        }

        do {
            if (!entry.cancelled) {
                if (!processor.processMessage(entry.msg)) {
                    return;
                }
            }
            entry = entry.next;
        } while (isFlushedEntry(entry));
    }

    private boolean isFlushedEntry(Entry e) {
        return e != null && e != unflushedEntry;
    }

    public interface MessageProcessor<T extends Exception> {
        /**
         * Will be called for each flushed message until it either there are no more flushed messages or this
         * method returns {@code false}.
         */
        boolean processMessage(Object msg) throws T;
    }

    private static final class Entry {
        private static final ObjectPool<Entry> RECYCLER = ObjectPool.newPool(Entry::new);

        private final Handle<Entry> handle;
        Entry next;
        Object msg;
        ByteBuffer[] bufs;
        ByteBuffer buf;
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
                bufs = null;
                buf = null;
                return pSize;
            }
            return 0;
        }

        void recycle() {
            next = null;
            bufs = null;
            buf = null;
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

    /**
     * Thread-local cache of {@link ByteBuffer} array, and processing meta-data.
     */
    private static final class BufferCache {
        ByteBuffer[] buffers;
        long dataSize;
        int bufferCount;
    }
}
