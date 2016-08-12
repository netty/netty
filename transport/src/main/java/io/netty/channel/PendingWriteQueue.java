/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel;

import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A queue of write operations which are pending for later execution. It also updates the
 * {@linkplain Channel#isWritable() writability} of the associated {@link Channel}, so that
 * the pending write operations are also considered to determine the writability.
 */
public final class PendingWriteQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PendingWriteQueue.class);

    private final ChannelHandlerContext ctx;
    private final ChannelOutboundBuffer buffer;
    private final MessageSizeEstimator.Handle estimatorHandle;

    // head and tail pointers for the linked-list structure. If empty head and tail are null.
    private PendingWrite head;
    private PendingWrite tail;
    private int size;
    private long bytes;

    public PendingWriteQueue(ChannelHandlerContext ctx) {
        if (ctx == null) {
            throw new NullPointerException("ctx");
        }
        this.ctx = ctx;
        buffer = ctx.channel().unsafe().outboundBuffer();
        estimatorHandle = ctx.channel().config().getMessageSizeEstimator().newHandle();
    }

    /**
     * Returns {@code true} if there are no pending write operations left in this queue.
     */
    public boolean isEmpty() {
        assert ctx.executor().inEventLoop();
        return head == null;
    }

    /**
     * Returns the number of pending write operations.
     */
    public int size() {
        assert ctx.executor().inEventLoop();
        return size;
    }

    /**
     * Returns the total number of bytes that are pending because of pending messages. This is only an estimate so
     * it should only be treated as a hint.
     */
    public long bytes() {
        assert ctx.executor().inEventLoop();
        return bytes;
    }

    /**
     * Add the given {@code msg} and {@link ChannelPromise}.
     */
    public void add(Object msg, ChannelPromise promise) {
        assert ctx.executor().inEventLoop();
        if (msg == null) {
            throw new NullPointerException("msg");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }
        // It is possible for writes to be triggered from removeAndFailAll(). To preserve ordering,
        // we should add them to the queue and let removeAndFailAll() fail them later.
        int messageSize = estimatorHandle.size(msg);
        if (messageSize < 0) {
            // Size may be unknow so just use 0
            messageSize = 0;
        }
        PendingWrite write = PendingWrite.newInstance(msg, messageSize, promise);
        PendingWrite currentTail = tail;
        if (currentTail == null) {
            tail = head = write;
        } else {
            currentTail.next = write;
            tail = write;
        }
        size ++;
        bytes += messageSize;
        // We need to guard against null as channel.unsafe().outboundBuffer() may returned null
        // if the channel was already closed when constructing the PendingWriteQueue.
        // See https://github.com/netty/netty/issues/3967
        if (buffer != null) {
            buffer.incrementPendingOutboundBytes(write.size);
        }
    }

    /**
     * Remove all pending write operation and performs them via
     * {@link ChannelHandlerContext#write(Object, ChannelPromise)}.
     *
     * @return  {@link ChannelFuture} if something was written and {@code null}
     *          if the {@link PendingWriteQueue} is empty.
     */
    public ChannelFuture removeAndWriteAll() {
        assert ctx.executor().inEventLoop();

        if (isEmpty()) {
            return null;
        }

        ChannelPromise p = ctx.newPromise();
        PromiseCombiner combiner = new PromiseCombiner();
        try {
            // It is possible for some of the written promises to trigger more writes. The new writes
            // will "revive" the queue, so we need to write them up until the queue is empty.
            for (PendingWrite write = head; write != null; write = head) {
                head = tail = null;
                size = 0;
                bytes = 0;

                while (write != null) {
                    PendingWrite next = write.next;
                    Object msg = write.msg;
                    ChannelPromise promise = write.promise;
                    recycle(write, false);
                    combiner.add(promise);
                    ctx.write(msg, promise);
                    write = next;
                }
            }
            combiner.finish(p);
        } catch (Throwable cause) {
            p.setFailure(cause);
        }
        assertEmpty();
        return p;
    }

    /**
     * Remove all pending write operation and fail them with the given {@link Throwable}. The message will be released
     * via {@link ReferenceCountUtil#safeRelease(Object)}.
     */
    public void removeAndFailAll(Throwable cause) {
        assert ctx.executor().inEventLoop();
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        // It is possible for some of the failed promises to trigger more writes. The new writes
        // will "revive" the queue, so we need to clean them up until the queue is empty.
        for (PendingWrite write = head; write != null; write = head) {
            head = tail = null;
            size = 0;
            bytes = 0;
            while (write != null) {
                PendingWrite next = write.next;
                ReferenceCountUtil.safeRelease(write.msg);
                ChannelPromise promise = write.promise;
                recycle(write, false);
                safeFail(promise, cause);
                write = next;
            }
        }
        assertEmpty();
    }

    /**
     * Remove a pending write operation and fail it with the given {@link Throwable}. The message will be released via
     * {@link ReferenceCountUtil#safeRelease(Object)}.
     */
    public void removeAndFail(Throwable cause) {
        assert ctx.executor().inEventLoop();
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        PendingWrite write = head;

        if (write == null) {
            return;
        }
        ReferenceCountUtil.safeRelease(write.msg);
        ChannelPromise promise = write.promise;
        safeFail(promise, cause);
        recycle(write, true);
    }

    private void assertEmpty() {
        assert tail == null && head == null && size == 0;
    }

    /**
     * Removes a pending write operation and performs it via
     * {@link ChannelHandlerContext#write(Object, ChannelPromise)}.
     *
     * @return  {@link ChannelFuture} if something was written and {@code null}
     *          if the {@link PendingWriteQueue} is empty.
     */
    public ChannelFuture removeAndWrite() {
        assert ctx.executor().inEventLoop();
        PendingWrite write = head;
        if (write == null) {
            return null;
        }
        Object msg = write.msg;
        ChannelPromise promise = write.promise;
        recycle(write, true);
        return ctx.write(msg, promise);
    }

    /**
     * Removes a pending write operation and release it's message via {@link ReferenceCountUtil#safeRelease(Object)}.
     *
     * @return  {@link ChannelPromise} of the pending write or {@code null} if the queue is empty.
     *
     */
    public ChannelPromise remove() {
        assert ctx.executor().inEventLoop();
        PendingWrite write = head;
        if (write == null) {
            return null;
        }
        ChannelPromise promise = write.promise;
        ReferenceCountUtil.safeRelease(write.msg);
        recycle(write, true);
        return promise;
    }

    /**
     * Return the current message or {@code null} if empty.
     */
    public Object current() {
        assert ctx.executor().inEventLoop();
        PendingWrite write = head;
        if (write == null) {
            return null;
        }
        return write.msg;
    }

    private void recycle(PendingWrite write, boolean update) {
        final PendingWrite next = write.next;
        final long writeSize = write.size;

        if (update) {
            if (next == null) {
                // Handled last PendingWrite so rest head and tail
                // Guard against re-entrance by directly reset
                head = tail = null;
                size = 0;
                bytes = 0;
            } else {
                head = next;
                size --;
                bytes -= writeSize;
                assert size > 0 && bytes >= 0;
            }
        }

        write.recycle();
        // We need to guard against null as channel.unsafe().outboundBuffer() may returned null
        // if the channel was already closed when constructing the PendingWriteQueue.
        // See https://github.com/netty/netty/issues/3967
        if (buffer != null) {
            buffer.decrementPendingOutboundBytes(writeSize);
        }
    }

    private static void safeFail(ChannelPromise promise, Throwable cause) {
        if (!(promise instanceof VoidChannelPromise) && !promise.tryFailure(cause)) {
            logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
        }
    }

    /**
     * Holds all meta-data and construct the linked-list structure.
     */
    static final class PendingWrite {
        private static final Recycler<PendingWrite> RECYCLER = new Recycler<PendingWrite>() {
            @Override
            protected PendingWrite newObject(Handle handle) {
                return new PendingWrite(handle);
            }
        };

        private final Recycler.Handle handle;
        private PendingWrite next;
        private long size;
        private ChannelPromise promise;
        private Object msg;

        private PendingWrite(Recycler.Handle handle) {
            this.handle = handle;
        }

        static PendingWrite newInstance(Object msg, int size, ChannelPromise promise) {
            PendingWrite write = RECYCLER.get();
            write.size = size;
            write.msg = msg;
            write.promise = promise;
            return write;
        }

        private void recycle() {
            size = 0;
            next = null;
            msg = null;
            promise = null;
            RECYCLER.recycle(this, handle);
        }
    }
}
