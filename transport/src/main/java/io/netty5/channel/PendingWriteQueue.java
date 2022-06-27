/*
 * Copyright 2014 The Netty Project
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

import io.netty5.util.Resource;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.PromiseCombiner;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.Objects;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A queue of write operations which are pending for later execution.
 */
public final class PendingWriteQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PendingWriteQueue.class);
    // Assuming a 64-bit JVM:
    //  - 16 bytes object header
    //  - 4 reference fields
    //  - 1 long fields
    private static final int PENDING_WRITE_OVERHEAD =
            SystemPropertyUtil.getInt("io.netty5.transport.pendingWriteSizeOverhead", 64);

    private final EventExecutor executor;
    private final MessageSizeEstimator.Handle sizeEstimatorHandle;

    // head and tail pointers for the linked-list structure. If empty head and tail are null.
    private PendingWrite head;
    private PendingWrite tail;
    private int size;
    private long bytes;

    public PendingWriteQueue(EventExecutor executor, MessageSizeEstimator.Handle handle) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.sizeEstimatorHandle = Objects.requireNonNull(handle, "handle");
    }

    /**
     * Returns {@code true} if there are no pending write operations left in this queue.
     */
    public boolean isEmpty() {
        assert executor.inEventLoop();
        return head == null;
    }

    /**
     * Returns the number of pending write operations.
     */
    public int size() {
        assert executor.inEventLoop();
        return size;
    }

    /**
     * Returns the total number of bytes that are pending because of pending messages. This is only an estimate so
     * it should only be treated as a hint.
     */
    public long bytes() {
        assert executor.inEventLoop();
        return bytes;
    }

    private int size(Object msg) {
        // It is possible for writes to be triggered from removeAndFailAll(). To preserve ordering,
        // we should add them to the queue and let removeAndFailAll() fail them later.
        int messageSize = sizeEstimatorHandle.size(msg);
        if (messageSize < 0) {
            // Size may be unknown so just use 0
            messageSize = 0;
        }
        return messageSize + PENDING_WRITE_OVERHEAD;
    }

    /**
     * Add the given {@code msg} and {@link Promise}.
     */
    public void add(Object msg, Promise<Void> promise) {
        assert executor.inEventLoop();
        requireNonNull(msg, "msg");
        requireNonNull(promise, "promise");
        // It is possible for writes to be triggered from removeAndFailAll(). To preserve ordering,
        // we should add them to the queue and let removeAndFailAll() fail them later.
        int messageSize = size(msg);

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
    }

    /**
     * Remove all pending write operation and performs them via
     * {@link Function#apply(Object)}.
     *
     * @return  {@link Future} if something was transferred and {@code null}
     *          if the {@link PendingWriteQueue} is empty.
     */
    public Future<Void> removeAndTransferAll(Function<Object, Future<Void>> transferFunc) {
        assert executor.inEventLoop();

        if (isEmpty()) {
            return null;
        }

        Promise<Void> p = executor.newPromise();
        PromiseCombiner combiner = new PromiseCombiner(executor);
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
                    Promise<Void> promise = write.promise;
                    recycle(write, false);
                    transferFunc.apply(msg).cascadeTo(promise);
                    write = next;
                }
            }
            combiner.finish(p);
        } catch (Throwable cause) {
            p.setFailure(cause);
        }
        assertEmpty();
        return p.asFuture();
    }

    /**
     * Remove all pending write operation and fail them with the given {@link Throwable}. The message will be released
     * via {@link Resource#dispose(Object)}.
     */
    public void removeAndFailAll(Throwable cause) {
        assert executor.inEventLoop();
        requireNonNull(cause, "cause");
        // It is possible for some of the failed promises to trigger more writes. The new writes
        // will "revive" the queue, so we need to clean them up until the queue is empty.
        for (PendingWrite write = head; write != null; write = head) {
            head = tail = null;
            size = 0;
            bytes = 0;
            while (write != null) {
                PendingWrite next = write.next;
                SilentDispose.dispose(write.msg, logger);
                Promise<Void> promise = write.promise;
                recycle(write, false);
                safeFail(promise, cause);
                write = next;
            }
        }
        assertEmpty();
    }

    /**
     * Remove a pending write operation and fail it with the given {@link Throwable}. The message will be released via
     * {@link Resource#dispose(Object)}.
     */
    public void removeAndFail(Throwable cause) {
        assert executor.inEventLoop();
        requireNonNull(cause, "cause");
        PendingWrite write = head;

        if (write == null) {
            return;
        }
        SilentDispose.dispose(write.msg, logger);
        Promise<Void> promise = write.promise;
        safeFail(promise, cause);
        recycle(write, true);
    }

    private void assertEmpty() {
        assert tail == null && head == null && size == 0;
    }

    /**
     * Removes a pending write operation and performs it via
     * {@link Function#apply(Object)}.
     *
     * @return  {@link Future} if something was transfered and {@code null}
     *          if the {@link PendingWriteQueue} is empty.
     */
    public Future<Void> removeAndTransfer(Function<Object, Future<Void>> transferFunc) {
        assert executor.inEventLoop();
        PendingWrite write = head;
        if (write == null) {
            return null;
        }
        Object msg = write.msg;
        Promise<Void> promise = write.promise;
        recycle(write, true);

        Future<Void> future = transferFunc.apply(msg);
        future.cascadeTo(promise);
        return future;
    }

    /**
     * Removes a pending write operation and release its message via {@link Resource#dispose(Object)}.
     *
     * @return  {@link Promise} of the pending write or {@code null} if the queue is empty.
     *
     */
    public Promise<Void> remove() {
        assert executor.inEventLoop();
        PendingWrite write = head;
        if (write == null) {
            return null;
        }
        Promise<Void> promise = write.promise;
        SilentDispose.dispose(write.msg, logger);
        recycle(write, true);
        return promise;
    }

    /**
     * Return the current message or {@code null} if empty.
     */
    public Object current() {
        assert executor.inEventLoop();
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
    }

    private static void safeFail(Promise<Void> promise, Throwable cause) {
        if (!promise.tryFailure(cause)) {
            logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
        }
    }

    /**
     * Holds all meta-data and construct the linked-list structure.
     */
    static final class PendingWrite {
        private static final ObjectPool<PendingWrite> RECYCLER = ObjectPool.newPool(PendingWrite::new);

        private final ObjectPool.Handle<PendingWrite> handle;
        private PendingWrite next;
        private long size;
        private Promise<Void> promise;
        private Object msg;

        private PendingWrite(ObjectPool.Handle<PendingWrite> handle) {
            this.handle = handle;
        }

        static PendingWrite newInstance(Object msg, int size, Promise<Void> promise) {
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
            handle.recycle(this);
        }
    }
}
