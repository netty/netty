/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.channel;

import io.netty5.buffer.ByteBufConvertible;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.CompositeByteBuf;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.UnstableApi;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;

import static io.netty5.util.ReferenceCountUtil.safeRelease;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("unchecked")
@UnstableApi
public abstract class AbstractCoalescingBufferQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractCoalescingBufferQueue.class);
    private final ArrayDeque<Object> bufAndListenerPairs;
    private final PendingBytesTracker tracker;
    private int readableBytes;

    /**
     * Create a new instance.
     *
     * @param channel the {@link Channel} which will have the {@link Channel#isWritable()} reflect the amount of queued
     *                buffers or {@code null} if there is no writability state updated.
     * @param initSize the initial size of the underlying queue.
     */
    protected AbstractCoalescingBufferQueue(Channel channel, int initSize) {
        bufAndListenerPairs = new ArrayDeque<>(initSize);
        tracker = channel == null ? null : PendingBytesTracker.newTracker(channel);
    }

    /**
     * Add a buffer to the front of the queue and associate a promise with it that should be completed when
     * all the buffer's bytes have been consumed from the queue and written.
     * @param buf to add to the head of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public final void addFirst(ByteBuf buf, Promise<Void> promise) {
        addFirst(buf, f -> f.cascadeTo(promise));
    }

    private void addFirst(ByteBuf buf, FutureListener<Void> listener) {
        if (listener != null) {
            bufAndListenerPairs.addFirst(listener);
        }
        bufAndListenerPairs.addFirst(buf);
        incrementReadableBytes(buf.readableBytes());
    }

    /**
     * Add a buffer to the end of the queue.
     */
    public final void add(ByteBuf buf) {
        add(buf, (FutureListener<Void>) null);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed when
     * all the buffer's bytes have been consumed from the queue and written.
     * @param buf to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public final void add(ByteBuf buf, Promise<Void> promise) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        add(buf, f -> f.cascadeTo(promise));
    }

    /**
     * Add a buffer to the end of the queue and associate a listener with it that should be completed when
     * all the buffers  bytes have been consumed from the queue and written.
     * @param buf to add to the tail of the queue
     * @param listener to notify when all the bytes have been consumed and written, can be {@code null}.
     */
    public final void add(ByteBuf buf, FutureListener<Void> listener) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        bufAndListenerPairs.add(buf);
        if (listener != null) {
            bufAndListenerPairs.add(listener);
        }
        incrementReadableBytes(buf.readableBytes());
    }

    /**
     * Remove the first {@link ByteBuf} from the queue.
     * @param aggregatePromise used to aggregate the promises and listeners for the returned buffer.
     * @return the first {@link ByteBuf} from the queue.
     */
    public final ByteBuf removeFirst(Promise<Void> aggregatePromise) {
        Object entry = bufAndListenerPairs.poll();
        if (entry == null) {
            return null;
        }
        assert entry instanceof ByteBufConvertible;
        ByteBuf result = ((ByteBufConvertible) entry).asByteBuf();

        decrementReadableBytes(result.readableBytes());

        entry = bufAndListenerPairs.peek();
        if (entry instanceof FutureListener) {
            aggregatePromise.asFuture().addListener((FutureListener<Void>) entry);
            bufAndListenerPairs.poll();
        }
        return result;
    }

    /**
     * Remove a {@link ByteBuf} from the queue with the specified number of bytes. Any added buffer who's bytes are
     * fully consumed during removal will have it's promise completed when the passed aggregate {@link Promise}
     * completes.
     *
     * @param alloc The allocator used if a new {@link ByteBuf} is generated during the aggregation process.
     * @param bytes the maximum number of readable bytes in the returned {@link ByteBuf}, if {@code bytes} is greater
     *              than {@link #readableBytes} then a buffer of length {@link #readableBytes} is returned.
     * @param aggregatePromise used to aggregate the promises and listeners for the constituent buffers.
     * @return a {@link ByteBuf} composed of the enqueued buffers.
     */
    public final ByteBuf remove(ByteBufAllocator alloc, int bytes, Promise<Void> aggregatePromise) {
        checkPositiveOrZero(bytes, "bytes");
        requireNonNull(aggregatePromise, "aggregatePromise");

        // Use isEmpty rather than readableBytes==0 as we may have a promise associated with an empty buffer.
        if (bufAndListenerPairs.isEmpty()) {
            assert readableBytes == 0;
            return removeEmptyValue();
        }
        bytes = Math.min(bytes, readableBytes);

        ByteBuf toReturn = null;
        ByteBuf entryBuffer = null;
        int originalBytes = bytes;
        try {
            for (;;) {
                Object entry = bufAndListenerPairs.poll();
                if (entry == null) {
                    break;
                }
                if (entry instanceof FutureListener) {
                    aggregatePromise.asFuture().addListener((FutureListener<Void>) entry);
                    continue;
                }
                entryBuffer = (ByteBuf) entry;
                if (entryBuffer.readableBytes() > bytes) {
                    // Add the buffer back to the queue as we can't consume all of it.
                    bufAndListenerPairs.addFirst(entryBuffer);
                    if (bytes > 0) {
                        // Take a slice of what we can consume and retain it.
                        entryBuffer = entryBuffer.readRetainedSlice(bytes);
                        toReturn = toReturn == null ? composeFirst(alloc, entryBuffer)
                                                    : compose(alloc, toReturn, entryBuffer);
                        bytes = 0;
                    }
                    break;
                } else {
                    bytes -= entryBuffer.readableBytes();
                    toReturn = toReturn == null ? composeFirst(alloc, entryBuffer)
                                                : compose(alloc, toReturn, entryBuffer);
                }
                entryBuffer = null;
            }
        } catch (Throwable cause) {
            safeRelease(entryBuffer);
            safeRelease(toReturn);
            aggregatePromise.setFailure(cause);
            throw cause;
        }
        decrementReadableBytes(originalBytes - bytes);
        return toReturn;
    }

    /**
     * The number of readable bytes.
     */
    public final int readableBytes() {
        return readableBytes;
    }

    /**
     * Are there pending buffers in the queue.
     */
    public final boolean isEmpty() {
        return bufAndListenerPairs.isEmpty();
    }

    /**
     *  Release all buffers in the queue and complete all listeners and promises.
     */
    public final void releaseAndFailAll(ChannelOutboundInvoker invoker, Throwable cause) {
        releaseAndCompleteAll(invoker.newFailedFuture(cause));
    }

    /**
     * Copy all pending entries in this queue into the destination queue.
     * @param dest to copy pending buffers to.
     */
    public final void copyTo(AbstractCoalescingBufferQueue dest) {
        dest.bufAndListenerPairs.addAll(bufAndListenerPairs);
        dest.incrementReadableBytes(readableBytes);
    }

    /**
     * Writes all remaining elements in this queue.
     * @param ctx The context to write all elements to.
     */
    public final void writeAndRemoveAll(ChannelHandlerContext ctx) {
        Throwable pending = null;
        ByteBuf previousBuf = null;
        for (;;) {
            Object entry = bufAndListenerPairs.poll();
            try {
                if (entry == null) {
                    if (previousBuf != null) {
                        decrementReadableBytes(previousBuf.readableBytes());
                        // If the write fails we want to at least propagate the exception through the ChannelPipeline
                        // as otherwise the user will not be made aware of the failure at all.
                        ctx.write(previousBuf)
                           .addListener(ctx.channel(), ChannelFutureListeners.FIRE_EXCEPTION_ON_FAILURE);
                    }
                    break;
                }

                if (entry instanceof ByteBufConvertible) {
                    if (previousBuf != null) {
                        decrementReadableBytes(previousBuf.readableBytes());
                        // If the write fails we want to at least propagate the exception through the ChannelPipeline
                        // as otherwise the user will not be made aware of the failure at all.
                        ctx.write(previousBuf)
                           .addListener(ctx.channel(), ChannelFutureListeners.FIRE_EXCEPTION_ON_FAILURE);
                    }
                    previousBuf = ((ByteBufConvertible) entry).asByteBuf();
                } else if (entry instanceof Promise) {
                    decrementReadableBytes(previousBuf.readableBytes());
                    ctx.write(previousBuf).cascadeTo((Promise<? super Void>) entry);
                    previousBuf = null;
                } else {
                    decrementReadableBytes(previousBuf.readableBytes());
                    ctx.write(previousBuf).addListener((FutureListener<Void>) entry);
                    previousBuf = null;
                }
            } catch (Throwable t) {
                if (pending == null) {
                    pending = t;
                } else {
                    logger.info("Throwable being suppressed because Throwable {} is already pending", pending, t);
                }
            }
        }
        if (pending != null) {
            throw new IllegalStateException(pending);
        }
    }

    @Override
    public String toString() {
        return "bytes: " + readableBytes + " buffers: " + (size() >> 1);
    }

    /**
     * Calculate the result of {@code current + next}.
     */
    protected abstract ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next);

    /**
     * Compose {@code cumulation} and {@code next} into a new {@link CompositeByteBuf}.
     */
    protected final ByteBuf composeIntoComposite(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
        // Create a composite buffer to accumulate this pair and potentially all the buffers
        // in the queue. Using +2 as we have already dequeued current and next.
        CompositeByteBuf composite = alloc.compositeBuffer(size() + 2);
        try {
            composite.addComponent(true, cumulation);
            composite.addComponent(true, next);
        } catch (Throwable cause) {
            composite.release();
            safeRelease(next);
            throw cause;
        }
        return composite;
    }

    /**
     * Compose {@code cumulation} and {@code next} into a new {@link ByteBufAllocator#ioBuffer()}.
     * @param alloc The allocator to use to allocate the new buffer.
     * @param cumulation The current cumulation.
     * @param next The next buffer.
     * @return The result of {@code cumulation + next}.
     */
    protected final ByteBuf copyAndCompose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
        ByteBuf newCumulation = alloc.ioBuffer(cumulation.readableBytes() + next.readableBytes());
        try {
            newCumulation.writeBytes(cumulation).writeBytes(next);
        } catch (Throwable cause) {
            newCumulation.release();
            safeRelease(next);
            throw cause;
        }
        cumulation.release();
        next.release();
        return newCumulation;
    }

    /**
     * Calculate the first {@link ByteBuf} which will be used in subsequent calls to
     * {@link #compose(ByteBufAllocator, ByteBuf, ByteBuf)}.
     */
    protected ByteBuf composeFirst(ByteBufAllocator allocator, ByteBuf first) {
        return first;
    }

    /**
     * The value to return when {@link #remove(ByteBufAllocator, int, Promise)} is called but the queue is empty.
     * @return the {@link ByteBuf} which represents an empty queue.
     */
    protected abstract ByteBuf removeEmptyValue();

    /**
     * Get the number of elements in this queue added via one of the {@link #add(ByteBuf)} methods.
     * @return the number of elements in this queue.
     */
    protected final int size() {
        return bufAndListenerPairs.size();
    }

    private void releaseAndCompleteAll(Future<Void> future) {
        Throwable pending = null;
        for (;;) {
            Object entry = bufAndListenerPairs.poll();
            if (entry == null) {
                break;
            }
            try {
                if (entry instanceof ByteBufConvertible) {
                    ByteBuf buffer = ((ByteBufConvertible) entry).asByteBuf();
                    decrementReadableBytes(buffer.readableBytes());
                    safeRelease(buffer);
                } else {
                    ((FutureListener<Void>) entry).operationComplete(future);
                }
            } catch (Throwable t) {
                if (pending == null) {
                    pending = t;
                } else {
                    logger.info("Throwable being suppressed because Throwable {} is already pending", pending, t);
                }
            }
        }
        if (pending != null) {
            throw new IllegalStateException(pending);
        }
    }

    private void incrementReadableBytes(int increment) {
        int nextReadableBytes = readableBytes + increment;
        if (nextReadableBytes < readableBytes) {
            throw new IllegalStateException("buffer queue length overflow: " + readableBytes + " + " + increment);
        }
        readableBytes = nextReadableBytes;
        if (tracker != null) {
            tracker.incrementPendingOutboundBytes(increment);
        }
    }

    private void decrementReadableBytes(int decrement) {
        readableBytes -= decrement;
        assert readableBytes >= 0;
        if (tracker != null) {
            tracker.decrementPendingOutboundBytes(decrement);
        }
    }
}
