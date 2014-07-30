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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
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

    private static final int threadLocalDirectBufferSize =
            SystemPropertyUtil.getInt("io.netty.threadLocalDirectBufferSize", 64 * 1024);

    static {
        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.threadLocalDirectBufferSize: {}", threadLocalDirectBufferSize);
        }
    }

    private static final FastThreadLocal<ByteBuffer[]> NIO_BUFFERS = new FastThreadLocal<ByteBuffer[]>() {
        @Override
        protected ByteBuffer[] initialValue() throws Exception {
            return new ByteBuffer[1024];
        }
    };

    private final AbstractChannel channel;

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

    private static final AtomicLongFieldUpdater<ChannelOutboundBuffer> TOTAL_PENDING_SIZE_UPDATER;

    @SuppressWarnings("unused")
    private volatile long totalPendingSize;

    private static final AtomicIntegerFieldUpdater<ChannelOutboundBuffer> WRITABLE_UPDATER;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile int writable = 1;

    static {
        AtomicIntegerFieldUpdater<ChannelOutboundBuffer> writableUpdater =
                PlatformDependent.newAtomicIntegerFieldUpdater(ChannelOutboundBuffer.class, "writable");
        if (writableUpdater == null) {
            writableUpdater = AtomicIntegerFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "writable");
        }
        WRITABLE_UPDATER = writableUpdater;

        AtomicLongFieldUpdater<ChannelOutboundBuffer> pendingSizeUpdater =
                PlatformDependent.newAtomicLongFieldUpdater(ChannelOutboundBuffer.class, "totalPendingSize");
        if (pendingSizeUpdater == null) {
            pendingSizeUpdater = AtomicLongFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "totalPendingSize");
        }
        TOTAL_PENDING_SIZE_UPDATER = pendingSizeUpdater;
    }

    ChannelOutboundBuffer(AbstractChannel channel) {
        this.channel = channel;
    }

    void addMessage(Object msg, ChannelPromise promise) {
        int size = channel.estimatorHandle().size(msg);
        if (size < 0) {
            size = 0;
        }

        Entry entry = Entry.newInstance(msg, size, total(msg), promise);
        if (tailEntry == null) {
            flushedEntry = null;
            tailEntry = entry;
        } else {
            Entry tail = tailEntry;
            tail.next = entry;
            tailEntry = entry;
        }
        if (unflushedEntry == null) {
            unflushedEntry = entry;
        }

        // increment pending bytes after adding message to the unflushed arrays.
        // See https://github.com/netty/netty/issues/1619
        incrementPendingOutboundBytes(size);
    }

    void addFlush() {
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
                if (!entry.promise.setUncancellable()) {
                    // Was cancelled so make sure we free up memory and notify about the freed bytes
                    int pending = entry.cancel();
                    decrementPendingOutboundBytes(pending);
                }
                entry = entry.next;
            } while (entry != null);

            // All flushed so reset unflushedEntry
            unflushedEntry = null;
        }
    }

    /**
     * Increment the pending bytes which will be written at some point.
     * This method is thread-safe!
     */
    void incrementPendingOutboundBytes(int size) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, size);
        if (newWriteBufferSize > channel.config().getWriteBufferHighWaterMark()) {
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
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);
        if (newWriteBufferSize == 0 || newWriteBufferSize < channel.config().getWriteBufferLowWaterMark()) {
            if (WRITABLE_UPDATER.compareAndSet(this, 0, 1)) {
                channel.pipeline().fireChannelWritabilityChanged();
            }
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

    public Object current() {
        return current(true);
    }

    public Object current(boolean preferDirect) {
        // TODO: Think of a smart way to handle ByteBufHolder messages
        Entry entry = flushedEntry;
        if (entry == null) {
            return null;
        }
        Object msg = entry.msg;
        if (threadLocalDirectBufferSize <= 0 || !preferDirect) {
            return msg;
        }
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.isDirect()) {
                return buf;
            } else {
                int readableBytes = buf.readableBytes();
                if (readableBytes == 0) {
                    return buf;
                }

                // Non-direct buffers are copied into JDK's own internal direct buffer on every I/O.
                // We can do a better job by using our pooled allocator. If the current allocator does not
                // pool a direct buffer, we use a ThreadLocal based pool.
                ByteBufAllocator alloc = channel.alloc();
                ByteBuf directBuf;
                if (alloc.isDirectBufferPooled()) {
                    directBuf = alloc.directBuffer(readableBytes);
                } else {
                    directBuf = ThreadLocalPooledByteBuf.newInstance();
                }
                directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
                current(directBuf);
                return directBuf;
            }
        }
        return msg;
    }

    /**
     * Replace the current msg with the given one.
     * The replaced msg will automatically be released
     */
    public void current(Object msg) {
        Entry entry = flushedEntry;
        assert entry != null;
        safeRelease(entry.msg);
        entry.msg = msg;
    }

    public void progress(long amount) {
        Entry e = flushedEntry;
        assert e != null;
        ChannelPromise p = e.promise;
        if (p instanceof ChannelProgressivePromise) {
            long progress = e.progress + amount;
            e.progress = progress;
            ((ChannelProgressivePromise) p).tryProgress(progress, e.total);
        }
    }

    public boolean remove() {
        Entry e = flushedEntry;
        if (e == null) {
            return false;
        }
        Object msg = e.msg;

        ChannelPromise promise = e.promise;
        int size = e.pendingSize;

        removeEntry(e);

        if (!e.cancelled) {
            // only release message, notify and decrement if it was not canceled before.
            safeRelease(msg);
            safeSuccess(promise);
            decrementPendingOutboundBytes(size);
        }

        // recycle the entry
        e.recycle();

        return true;
    }

    public boolean remove(Throwable cause) {
        Entry e = flushedEntry;
        if (e == null) {
            return false;
        }
        Object msg = e.msg;

        ChannelPromise promise = e.promise;
        int size = e.pendingSize;

        removeEntry(e);

        if (!e.cancelled) {
            // only release message, fail and decrement if it was not canceled before.
            safeRelease(msg);

            safeFail(promise, cause);
            decrementPendingOutboundBytes(size);
        }

        // recycle the entry
        e.recycle();

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
        long nioBufferSize = 0;
        int nioBufferCount = 0;
        final ByteBufAllocator alloc = channel.alloc();
        final InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        ByteBuffer[] nioBuffers = NIO_BUFFERS.get(threadLocalMap);
        Entry entry = flushedEntry;
        while (entry != null && entry.msg instanceof ByteBuf) {
            if (!entry.cancelled) {
                ByteBuf buf = (ByteBuf) entry.msg;
                final int readerIndex = buf.readerIndex();
                final int readableBytes = buf.writerIndex() - readerIndex;

                if (readableBytes > 0) {
                    nioBufferSize += readableBytes;
                    int count = entry.count;
                    if (count == -1) {
                        //noinspection ConstantValueVariableUse
                        entry.count = count =  buf.nioBufferCount();
                    }
                    int neededSpace = nioBufferCount + count;
                    if (neededSpace > nioBuffers.length) {
                        nioBuffers = expandNioBufferArray(nioBuffers, neededSpace, nioBufferCount);
                        NIO_BUFFERS.set(threadLocalMap, nioBuffers);
                    }
                    if (buf.isDirect() || threadLocalDirectBufferSize <= 0) {
                        if (count == 1) {
                            ByteBuffer nioBuf = entry.buf;
                            if (nioBuf == null) {
                                // cache ByteBuffer as it may need to create a new ByteBuffer instance if its a
                                // derived buffer
                                entry.buf = nioBuf = buf.internalNioBuffer(readerIndex, readableBytes);
                            }
                            nioBuffers[nioBufferCount ++] = nioBuf;
                        } else {
                            ByteBuffer[] nioBufs = entry.bufs;
                            if (nioBufs == null) {
                                // cached ByteBuffers as they may be expensive to create in terms
                                // of Object allocation
                                entry.bufs = nioBufs = buf.nioBuffers();
                            }
                            nioBufferCount = fillBufferArray(nioBufs, nioBuffers, nioBufferCount);
                        }
                    } else {
                        nioBufferCount = fillBufferArrayNonDirect(entry, buf, readerIndex,
                                readableBytes, alloc, nioBuffers, nioBufferCount);
                    }
                }
            }
            entry = entry.next;
        }
        this.nioBufferCount = nioBufferCount;
        this.nioBufferSize = nioBufferSize;

        return nioBuffers;
    }

    private static int fillBufferArray(ByteBuffer[] nioBufs, ByteBuffer[] nioBuffers, int nioBufferCount) {
        for (ByteBuffer nioBuf: nioBufs) {
            if (nioBuf == null) {
                break;
            }
            nioBuffers[nioBufferCount ++] = nioBuf;
        }
        return nioBufferCount;
    }

    private static int fillBufferArrayNonDirect(Entry entry, ByteBuf buf, int readerIndex, int readableBytes,
                                                ByteBufAllocator alloc, ByteBuffer[] nioBuffers, int nioBufferCount) {
        ByteBuf directBuf;
        if (alloc.isDirectBufferPooled()) {
            directBuf = alloc.directBuffer(readableBytes);
        } else {
            directBuf = ThreadLocalPooledByteBuf.newInstance();
        }
        directBuf.writeBytes(buf, readerIndex, readableBytes);
        buf.release();
        entry.msg = directBuf;
        // cache ByteBuffer
        ByteBuffer nioBuf = entry.buf = directBuf.internalNioBuffer(0, readableBytes);
        entry.count = 1;
        nioBuffers[nioBufferCount ++] = nioBuf;
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
        return flushed;
    }

    public boolean isEmpty() {
        return flushed == 0;
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

        if (!isEmpty()) {
            throw new IllegalStateException("close() must be invoked after all flushed writes are handled.");
        }

        // Release all unflushed messages.
        try {
            Entry e = unflushedEntry;
            while (e != null) {
                // Just decrease; do not trigger any events via decrementPendingOutboundBytes()
                int size = e.pendingSize;
                TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);

                if (!e.cancelled) {
                    safeRelease(e.msg);
                    safeFail(e.promise, cause);
                }
                e = e.recycleAndGetNext();
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

    private static void safeSuccess(ChannelPromise promise) {
        if (!(promise instanceof VoidChannelPromise) && !promise.trySuccess()) {
            logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
        }
    }

    private static void safeFail(ChannelPromise promise, Throwable cause) {
        if (!(promise instanceof VoidChannelPromise) && !promise.tryFailure(cause)) {
            logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
        }
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
        if (processor == null) {
            throw new NullPointerException("processor");
        }
        Entry entry = flushedEntry;
        while (entry != null) {
            if (!entry.cancelled) {
                if (!processor.processMessage(entry.msg)) {
                    return;
                }
            }
            entry = entry.next;
        }
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
            protected Entry newObject(Handle handle) {
                return new Entry(handle);
            }
        };

        private final Handle handle;
        Entry next;
        Object msg;
        ByteBuffer[] bufs;
        ByteBuffer buf;
        ChannelPromise promise;
        long progress;
        long total;
        int pendingSize;
        int count = -1;
        boolean cancelled;

        private Entry(Handle handle) {
            this.handle = handle;
        }

        static Entry newInstance(Object msg, int size, long total, ChannelPromise promise) {
            Entry entry = RECYCLER.get();
            entry.msg = msg;
            entry.pendingSize = size;
            entry.total = total;
            entry.promise = promise;
            return entry;
        }

        int cancel() {
            if (!cancelled) {
                cancelled = true;
                int pSize = pendingSize;

                // release message and replace with an empty buffer
                safeRelease(msg);
                msg = Unpooled.EMPTY_BUFFER;

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
            RECYCLER.recycle(this, handle);
        }

        Entry recycleAndGetNext() {
            Entry next = this.next;
            recycle();
            return next;
        }
    }

    static final class ThreadLocalPooledByteBuf extends UnpooledDirectByteBuf {
        private final Recycler.Handle handle;

        private static final Recycler<ThreadLocalPooledByteBuf> RECYCLER = new Recycler<ThreadLocalPooledByteBuf>() {
            @Override
            protected ThreadLocalPooledByteBuf newObject(Handle handle) {
                return new ThreadLocalPooledByteBuf(handle);
            }
        };

        private ThreadLocalPooledByteBuf(Recycler.Handle handle) {
            super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
            this.handle = handle;
        }

        static ThreadLocalPooledByteBuf newInstance() {
            ThreadLocalPooledByteBuf buf = RECYCLER.get();
            buf.setRefCnt(1);
            return buf;
        }

        @Override
        protected void deallocate() {
            if (capacity() > threadLocalDirectBufferSize) {
                super.deallocate();
            } else {
                clear();
                RECYCLER.recycle(this, handle);
            }
        }
    }
}
