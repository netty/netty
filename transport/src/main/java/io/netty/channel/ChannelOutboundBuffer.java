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
package io.netty.channel;

import io.netty.buffer.AbstractReferenceCountedByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.Recycler;
import io.netty.util.Recycler.EnhancedHandle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.lang.Math.min;

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
            SystemPropertyUtil.getInt("io.netty.transport.outboundBufferEntrySizeOverhead", 96);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelOutboundBuffer.class);

    private static final FastThreadLocal<ByteBuffer[]> NIO_BUFFERS = new FastThreadLocal<ByteBuffer[]>() {
        @Override
        protected ByteBuffer[] initialValue() throws Exception {
            return new ByteBuffer[1024];
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

    private static final AtomicLongFieldUpdater<ChannelOutboundBuffer> TOTAL_PENDING_SIZE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "totalPendingSize");

    @SuppressWarnings("UnusedDeclaration")
    private volatile long totalPendingSize;

    ChannelOutboundBuffer(EventExecutor executor) {
        this.executor = executor;
    }

    private void incrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }
        // Single-writer only so a lazySet will do.
        TOTAL_PENDING_SIZE_UPDATER.lazySet(this, totalPendingSize + size);
    }

    private void decrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        // Single-writer only so a lazySet will do.
        TOTAL_PENDING_SIZE_UPDATER.lazySet(this, totalPendingSize - size);
    }

    /**
     * Add given message to this {@link ChannelOutboundBuffer}. The given {@link CompletionHandler} will be
     * notified once the message was written.
     */
    public void addMessage(Object msg, int size, CompletionHandler<Void> handler) {
        assert executor.inEventLoop();
        Entry entry = Entry.newInstance(msg, size, total(msg), handler);
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

        // Touch the message to make it easier to debug buffer leaks.

        // this save both checking against the ReferenceCounted interface
        // and makes better use of virtual calls vs interface ones
        if (msg instanceof AbstractReferenceCountedByteBuf) {
            ((AbstractReferenceCountedByteBuf) msg).touch();
        } else {
            ReferenceCountUtil.touch(msg);
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
            do {
                flushed ++;
                entry = entry.next;
            } while (entry != null);

            // All flushed so reset unflushedEntry
            unflushedEntry = null;
        }
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
     * Will remove the current message, mark its {@link CompletionHandler} as success and return {@code true}. If no
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

        CompletionHandler<Void> handler = e.handler;
        int size = e.pendingSize;

        removeEntry(e);

        // this save both checking against the ReferenceCounted interface
        // and makes better use of virtual calls vs interface ones
        if (msg instanceof AbstractReferenceCountedByteBuf) {
            try {
                // release now as it is flushed.
                ((AbstractReferenceCountedByteBuf) msg).release();
            } catch (Throwable t) {
                logger.warn("Failed to release a ByteBuf: {}", msg, t);
            }
        } else {
            ReferenceCountUtil.safeRelease(msg);
        }
        handler.onSuccess(null);
        decrementPendingOutboundBytes(size);

        // recycle the entry
        e.unguardedRecycle();

        return true;
    }

    /**
     * Will remove the current message, mark its {@link CompletionHandler} as failure using the given {@link Throwable}
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

        CompletionHandler<Void> handler = e.handler;
        int size = e.pendingSize;

        removeEntry(e);

        ReferenceCountUtil.safeRelease(msg);

        handler.onFailure(cause);
        decrementPendingOutboundBytes(size);

        // recycle the entry
        e.unguardedRecycle();

        return true;
    }

    private void removeEntry(Entry e) {
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
     * This operation assumes all messages in this buffer is {@link ByteBuf}.
     */
    public void removeBytes(long writtenBytes) {
        assert executor.inEventLoop();
        for (;;) {
            Object msg = current();
            if (!(msg instanceof ByteBuf)) {
                assert writtenBytes == 0;
                break;
            }

            final ByteBuf buf = (ByteBuf) msg;
            final int readerIndex = buf.readerIndex();
            final int readableBytes = buf.writerIndex() - readerIndex;

            if (readableBytes <= writtenBytes) {
                if (writtenBytes != 0) {
                    writtenBytes -= readableBytes;
                }
                remove();
            } else { // readableBytes > writtenBytes
                if (writtenBytes != 0) {
                    buf.readerIndex(readerIndex + (int) writtenBytes);
                }
                break;
            }
        }
        clearNioBuffers();
    }

    // Clear all ByteBuffer from the array so these can be GC'ed.
    // See https://github.com/netty/netty/issues/3837
    private void clearNioBuffers() {
        int count = nioBufferCount;
        if (count > 0) {
            nioBufferCount = 0;
            Arrays.fill(NIO_BUFFERS.get(), 0, count, null);
        }
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link ByteBuf} only.
     * {@link #nioBufferCount()} and {@link #nioBufferSize()} will return the number of NIO buffers in the returned
     * array and the total number of readable bytes of the NIO buffers respectively.
     * <p>
     * Note that the returned array is reused and thus should not escape
     * {@link AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     * </p>
     */
    public ByteBuffer[] nioBuffers() {
        return nioBuffers(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link ByteBuf} only.
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
        final InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        ByteBuffer[] nioBuffers = NIO_BUFFERS.get(threadLocalMap);
        Entry entry = flushedEntry;
        while (isFlushedEntry(entry) && entry.msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) entry.msg;
            final int readerIndex = buf.readerIndex();
            final int readableBytes = buf.writerIndex() - readerIndex;

            if (readableBytes > 0) {
                if (maxBytes - readableBytes < nioBufferSize && nioBufferCount != 0) {
                    // If the nioBufferSize + readableBytes will overflow maxBytes, and there is at least one entry
                    // we stop populate the ByteBuffer array. This is done for 2 reasons:
                    // 1. bsd/osx don't allow to write more bytes then Integer.MAX_VALUE with one writev(...) call
                    // and so will return 'EINVAL', which will raise an IOException. On Linux it may work depending
                    // on the architecture and kernel but to be safe we also enforce the limit here.
                    // 2. There is no sense in putting more data in the array than is likely to be accepted by the
                    // OS.
                    //
                    // See also:
                    // - https://www.freebsd.org/cgi/man.cgi?query=write&sektion=2
                    // - https://linux.die.net//man/2/writev
                    break;
                }
                nioBufferSize += readableBytes;
                int count = entry.count;
                if (count == -1) {
                    //noinspection ConstantValueVariableUse
                    entry.count = count = buf.nioBufferCount();
                }
                int neededSpace = min(maxCount, nioBufferCount + count);
                if (neededSpace > nioBuffers.length) {
                    nioBuffers = expandNioBufferArray(nioBuffers, neededSpace, nioBufferCount);
                    NIO_BUFFERS.set(threadLocalMap, nioBuffers);
                }
                if (count == 1) {
                    ByteBuffer nioBuf = entry.buf;
                    if (nioBuf == null) {
                        // cache ByteBuffer as it may need to create a new ByteBuffer instance if its a
                        // derived buffer
                        entry.buf = nioBuf = buf.internalNioBuffer(readerIndex, readableBytes);
                    }
                    nioBuffers[nioBufferCount++] = nioBuf;
                } else {
                    // The code exists in an extra method to ensure the method is not too big to inline as this
                    // branch is not very likely to get hit very frequently.
                    nioBufferCount = nioBuffers(entry, buf, nioBuffers, nioBufferCount, maxCount);
                }
                if (nioBufferCount >= maxCount) {
                    break;
                }
            }
            entry = entry.next;
        }
        this.nioBufferCount = nioBufferCount;
        this.nioBufferSize = nioBufferSize;

        return nioBuffers;
    }

    private static int nioBuffers(Entry entry, ByteBuf buf, ByteBuffer[] nioBuffers, int nioBufferCount, int maxCount) {
        ByteBuffer[] nioBufs = entry.bufs;
        if (nioBufs == null) {
            // cached ByteBuffers as they may be expensive to create in terms
            // of Object allocation
            entry.bufs = nioBufs = buf.nioBuffers();
        }
        for (int i = 0; i < nioBufs.length && nioBufferCount < maxCount; ++i) {
            ByteBuffer nioBuf = nioBufs[i];
            if (nioBuf == null) {
                break;
            } else if (!nioBuf.hasRemaining()) {
                continue;
            }
            nioBuffers[nioBufferCount++] = nioBuf;
        }
        return nioBufferCount;
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

    void failFlushedAndClose(Throwable failCause, Throwable closeCause) {
        failFlushed(failCause);
        close(closeCause);
    }

    void close(final Throwable cause) {
        assert executor.inEventLoop();
        if (inFail) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    close(cause);
                }
            });
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
                // Just decrease; do not trigger any events via decrementPendingOutboundBytes()
                int size = e.pendingSize;
                decrementPendingOutboundBytes(size);

                ReferenceCountUtil.safeRelease(e.msg);
                e.handler.onFailure(cause);
                e = e.unguardedRecycleAndGetNext();
            }
        } finally {
            inFail = false;
        }
        clearNioBuffers();
    }

    @Deprecated
    public void recycle() {
        // NOOP
    }

    public long totalPendingWriteBytes() {
        return totalPendingSize;
    }

    /**
     * Call {@link MessageProcessor#processMessage(Object)} for each flushed message
     * in this {@link ChannelOutboundBuffer} until {@link MessageProcessor#processMessage(Object)}
     * returns {@code false} or there are no more flushed messages to process.
     */
    public void forEachFlushedMessage(MessageProcessor processor) throws Exception {
        ObjectUtil.checkNotNull(processor, "processor");

        Entry entry = flushedEntry;
        if (entry == null) {
            return;
        }

        do {
            if (!processor.processMessage(entry.msg)) {
                return;
            }
            entry = entry.next;
        } while (isFlushedEntry(entry));
    }

    private boolean isFlushedEntry(Entry e) {
        return e != null && e != unflushedEntry;
    }

    public interface MessageProcessor {
        /**
         * Will be called for each flushed message until it either there are no more flushed messages or this
         * method returns {@code false}.
         */
        boolean processMessage(Object msg) throws Exception;
    }

    static final class Entry {
        private static final Recycler<Entry> RECYCLER = new Recycler<Entry>() {
            @Override
            protected Entry newObject(Handle<Entry> handle) {
                return new Entry(handle);
            }
        };

        private final EnhancedHandle<Entry> handle;
        Entry next;
        Object msg;
        ByteBuffer[] bufs;
        ByteBuffer buf;
        CompletionHandler<Void> handler;
        long progress;
        long total;
        int pendingSize;
        int count = -1;

        private Entry(Handle<Entry> handle) {
            this.handle = (EnhancedHandle<Entry>) handle;
        }

        static Entry newInstance(Object msg, int size, long total, CompletionHandler<Void> handler) {
            Entry entry = RECYCLER.get();
            entry.msg = msg;
            entry.pendingSize = size + CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
            entry.total = total;
            entry.handler = handler;
            return entry;
        }

        void unguardedRecycle() {
            next = null;
            bufs = null;
            buf = null;
            msg = null;
            handler = null;
            progress = 0;
            total = 0;
            pendingSize = 0;
            count = -1;
            handle.unguardedRecycle(this);
        }

        Entry unguardedRecycleAndGetNext() {
            Entry next = this.next;
            unguardedRecycle();
            return next;
        }
    }
}
