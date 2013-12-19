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
package io.netty.channel.socket.nio;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFlushPromiseNotifier.FlushCheckpoint;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Recycler;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;

final class NioSocketChannelOutboundBuffer extends ChannelOutboundBuffer {

    private static final Recycler<NioSocketChannelOutboundBuffer> RECYCLER =
            new Recycler<NioSocketChannelOutboundBuffer>() {
        @Override
        protected NioSocketChannelOutboundBuffer newObject(Handle<NioSocketChannelOutboundBuffer> handle) {
            return new NioSocketChannelOutboundBuffer(handle);
        }
    };

    static NioSocketChannelOutboundBuffer newBuffer(NioSocketChannel channel) {
        NioSocketChannelOutboundBuffer entry = RECYCLER.get();
        entry.channel = channel;
        return entry;
    }

    private static final int INITIAL_CAPACITY = 32;
    private final ArrayDeque<FlushCheckpoint> promises =
            new ArrayDeque<FlushCheckpoint>();
    private final Recycler.Handle<NioSocketChannelOutboundBuffer> handle;
    private ByteBuffer[] nioBuffers = new ByteBuffer[INITIAL_CAPACITY];
    private int nioBufferCount;
    private long nioBufferSize;
    private long totalPending;
    private long writeCounter;
    private boolean inNotify;

    private Entry first;
    private Entry last;
    private int flushed;
    private int messages;

    private NioSocketChannelOutboundBuffer(Recycler.Handle<NioSocketChannelOutboundBuffer> handle) {
        this.handle = handle;
    }

    private boolean isAllFlushed() {
        return messages == flushed;
    }

    @Override
    protected void addMessage(Object msg, int size, ChannelPromise promise) {
        long total = total(msg);
        assert total >= 0;
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;

            // Check if we are inNotify or if the last entry is not the same as the first
            // This is needed because someone may write to the channel in a ChannelFutureListener which then
            // could lead to have the buffer merged into the current buffer. In this case the current buffer may be
            // removed as it was completely written before.
            if (!isAllFlushed() && ((NioSocketChannel) channel).config().isWriteBufferAutoMerge()
                    && (!inNotify || last != first)) {
                Entry entry = last;
                if (entry.merge(buf)) {
                    totalPending += total;
                    addPromise(promise);
                    return;
                }
                if (!buf.isDirect()) {
                    msg = ((NioSocketChannel) channel).toDirect(buf);
                }
            }
        }
        Entry e = Entry.newInstance(this);
        if (last == null) {
            first = e;
            last = e;
        } else {
            last.next = e;
            last = e;
        }
        e.msg = msg;
        e.pendingSize = size;
        e.promise = promise;
        e.total = total;
        e.pendingTotal = total;
        totalPending += total;
        addPromise(promise);

        messages++;
    }

    @Override
    protected void addFlush() {
        flushed = messages;
    }

    @Override
    public Object current() {
        if (isEmpty()) {
            return null;
        } else {
            return first.msg;
        }
    }

    @Override
    public void progress(long amount) {
        Entry e = first;
        e.pendingTotal -= amount;
        assert e.pendingTotal >= 0;
        ChannelPromise p = e.promise;
        if (p instanceof ChannelProgressivePromise) {
            long progress = e.progress + amount;
            e.progress = progress;
            ((ChannelProgressivePromise) p).tryProgress(progress, e.total);
        }
        if (amount > 0) {
            writeCounter += amount;
            notifyPromises(null);
        }
    }

    @Override
    public boolean remove() {
        if (isEmpty()) {
            return false;
        }

        Entry e = first;
        first = e.next;
        if (first == null) {
            last = null;
        }

        messages--;
        flushed--;
        e.success();

        return true;
    }

    @Override
    public boolean remove(Throwable cause) {
        if (isEmpty()) {
            return false;
        }

        Entry e = first;
        first = e.next;
        if (first == null) {
            last = null;
        }

        messages--;
        flushed--;
        e.fail(cause, true);

        return true;
    }

    @Override
    public int size() {
        return flushed;
    }

    @Override
    protected void failUnflushed(Throwable cause) {
        Entry e = first;
        while (e != null) {
            e.fail(cause, false);
            e = e.next;
        }
    }

    private void addPromise(ChannelPromise promise) {
        if (isVoidPromise(promise)) {
            // no need to add the promises to later notify if it is a VoidChannelPromise
            return;
        }
        FlushCheckpoint checkpoint;
        if (promise instanceof FlushCheckpoint) {
            checkpoint = (FlushCheckpoint) promise;
            checkpoint.flushCheckpoint(totalPending);
        } else {
            checkpoint = new DefaultFlushCheckpoint(totalPending, promise);
        }

        promises.offer(checkpoint);
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link ByteBuf} only.
     * {@code null} is returned otherwise.  If this method returns a non-null array, {@link #nioBufferCount()} and
     * {@link #nioBufferSize()} will return the number of NIO buffers in the returned array and the total number
     * of readable bytes of the NIO buffers respectively.
     * <p>
     * Note that the returned array is reused and thus should not escape
     * {@link io.netty.channel.AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     * Refer to {@link io.netty.channel.socket.nio.NioSocketChannel#doWrite(ChannelOutboundBuffer)} for an example.
     * </p>
     */
    ByteBuffer[] nioBuffers() {
        long nioBufferSize = 0;
        int nioBufferCount = 0;

        if (!isEmpty()) {
            ByteBuffer[] nioBuffers = this.nioBuffers;
            Entry entry = first;
            int i = size();

            for (;;) {
                Object m = entry.msg;
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
                    int count = entry.count;
                    if (count == -1) {
                        entry.count = count = buf.nioBufferCount();
                    }
                    int neededSpace = nioBufferCount + count;
                    if (neededSpace > nioBuffers.length) {
                        this.nioBuffers = nioBuffers = expandNioBufferArray(nioBuffers, neededSpace, nioBufferCount);
                    }

                    if (count == 1) {
                        ByteBuffer nioBuf = entry.buf;
                        if (nioBuf == null) {
                            // cache ByteBuffer as it may need to create a new ByteBuffer instance if its a
                            // derived buffer
                            entry.buf = nioBuf = buf.internalNioBuffer(readerIndex, readableBytes);
                        }
                        nioBuffers[nioBufferCount ++] = nioBuf;
                    } else {
                        ByteBuffer[] nioBufs = entry.buffers;
                        if (nioBufs == null) {
                            // cached ByteBuffers as they may be expensive to create in terms of Object allocation
                            entry.buffers = nioBufs = buf.nioBuffers();
                        }
                        nioBufferCount = fillBufferArray(nioBufs, nioBuffers, nioBufferCount);
                    }
                }
                if (--i == 0) {
                    break;
                }
                entry = entry.next;
            }
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

    int nioBufferCount() {
        return nioBufferCount;
    }

    long nioBufferSize() {
        return nioBufferSize;
    }

    @Override
    protected void onRecycle() {
        inNotify = false;
        assert promises.isEmpty();
        nioBufferCount = 0;
        nioBufferSize = 0;
        totalPending = 0;
        writeCounter = 0;
        if (nioBuffers.length > INITIAL_CAPACITY) {
            nioBuffers = new ByteBuffer[INITIAL_CAPACITY];
        } else {
            // null out the nio buffers array so the can be GC'ed
            // https://github.com/netty/netty/issues/1763
            Arrays.fill(nioBuffers, null);
        }
        handle.recycle(this);
    }

    static final class Entry {
        private static final Recycler<Entry> RECYCLER = new Recycler<Entry>() {
            @Override
            protected Entry newObject(Handle<Entry> handle) {
                return new Entry(handle);
            }
        };

        static Entry newInstance(NioSocketChannelOutboundBuffer buffer) {
            Entry entry = RECYCLER.get();
            entry.buffer = buffer;
            return entry;
        }
        ByteBuffer[] buffers;
        ByteBuffer buf;
        int count = -1;
        private final Recycler.Handle<Entry> entryHandle;
        private Object msg;
        private ChannelPromise promise;
        private long progress;
        private long total;
        private long pendingSize;
        private long pendingTotal;

        private Entry next;
        private NioSocketChannelOutboundBuffer buffer;

        @SuppressWarnings("unchecked")
        private Entry(Recycler.Handle<? extends Entry> entryHandle) {
            this.entryHandle = (Recycler.Handle<Entry>) entryHandle;
        }

        private void success() {
            try {
                buffer.writeCounter += pendingTotal;
                safeRelease(msg);
                buffer.notifyPromises(null);
                buffer.decrementPendingOutboundBytes(pendingSize);
            } finally {
                recycle();
            }
        }

        private void fail(Throwable cause, boolean decrementAndNotify) {
            cause.printStackTrace();
            try {
                buffer.writeCounter += pendingTotal;
                safeRelease(msg);
                buffer.notifyPromises(cause);

                if (decrementAndNotify) {
                    buffer.decrementPendingOutboundBytes(pendingSize);
                }
            } finally {
                recycle();
            }
        }

        private boolean merge(ByteBuf buffer) {
            if (!(msg instanceof ByteBuf)) {
                return false;
            }
            ByteBuf last = (ByteBuf) msg;
            int readable = buffer.readableBytes();
            if (last.nioBufferCount() == 1 && last.isWritable(readable)) {
                // reset cached stuff
                buffers = null;
                buf = null;
                count = -1;

                // merge bytes in and release the original buffer
                last.writeBytes(buffer);
                safeRelease(buffer);
                pendingSize += readable;
                pendingTotal += readable;
                total += readable;

                return true;
            }
            return false;
        }

        private void recycle() {
            msg = null;
            promise = null;
            progress = 0;
            total = 0;
            pendingSize = 0;
            pendingTotal = 0;
            next = null;
            buffer = null;
            buffers = null;
            buf = null;
            count = -1;
            entryHandle.recycle(this);
        }
    }

    private void notifyPromises(Throwable cause) {
        try {
            inNotify = true;
            final long writeCounter = this.writeCounter;
            for (;;) {
                FlushCheckpoint cp = promises.peek();
                if (cp == null) {
                    // Reset the counter if there's nothing in the notification list.
                    this.writeCounter = 0;
                    totalPending = 0;
                    break;
                }

                if (cp.flushCheckpoint() > writeCounter) {
                    if (writeCounter > 0 && promises.size() == 1) {
                        this.writeCounter = 0;
                        totalPending -= writeCounter;
                        cp.flushCheckpoint(cp.flushCheckpoint() - writeCounter);
                    }
                    break;
                }

                promises.remove();
                if (cause == null) {
                    cp.promise().trySuccess();
                } else {
                    safeFail(cp.promise(), cause);
                }
            }
            // Avoid overflow
            final long newWriteCounter = this.writeCounter;
            if (newWriteCounter >= 0x8000000000L) {
                // Reset the counter only when the counter grew pretty large
                // so that we can reduce the cost of updating all entries in the notification list.
                this.writeCounter = 0;
                totalPending -= newWriteCounter;
                for (FlushCheckpoint cp: promises) {
                    cp.flushCheckpoint(cp.flushCheckpoint() - newWriteCounter);
                }
            }
        } finally {
            inNotify = false;
        }
    }

    private static class DefaultFlushCheckpoint implements FlushCheckpoint {
        private long checkpoint;
        private final ChannelPromise future;

        DefaultFlushCheckpoint(long checkpoint, ChannelPromise future) {
            this.checkpoint = checkpoint;
            this.future = future;
        }

        @Override
        public long flushCheckpoint() {
            return checkpoint;
        }

        @Override
        public void flushCheckpoint(long checkpoint) {
            this.checkpoint = checkpoint;
        }

        @Override
        public ChannelPromise promise() {
            return future;
        }
    }
}
