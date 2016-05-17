/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.ArrayDeque;

/**
 * A FIFO queue of bytes where producers add bytes by repeatedly adding {@link ByteBuf} and consumers take bytes in
 * arbitrary lengths. This allows producers to add lots of small buffers and the consumer to take all the bytes
 * out in a single buffer. Conversely the producer may add larger buffers and the consumer could take the bytes in
 * many small buffers.
 *
 * <p>Bytes are added and removed with promises. If the last byte of a buffer added with a promise is removed then
 * that promise will complete when the promise passed to {@link #remove} completes.
 *
 * <p>This functionality is useful for aggregating or partitioning writes into fixed size buffers for framing protocols
 * such as HTTP2.
 */
public final class CoalescingBufferQueue {

    private final Channel channel;
    private final ArrayDeque<Object> bufAndListenerPairs;
    private int readableBytes;

    public CoalescingBufferQueue(Channel channel) {
        this(channel, 4);
    }

    public CoalescingBufferQueue(Channel channel, int initSize) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        bufAndListenerPairs = new ArrayDeque<Object>(initSize);
    }

    /**
     * Add a buffer to the end of the queue.
     */
    public void add(ByteBuf buf) {
        add(buf, (ChannelFutureListener) null);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed when
     * all the buffers bytes have been consumed from the queue and written.
     * @param buf to add to the tail of the queue
     * @param promise to complete when all the bytes have been consumed and written, can be void.
     */
    public void add(ByteBuf buf, ChannelPromise promise) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        ObjectUtil.checkNotNull(promise, "promise");
        add(buf, promise.isVoid() ? null : new ChannelPromiseNotifier(promise));
    }

    /**
     * Add a buffer to the end of the queue and associate a listener with it that should be completed when
     * all the buffers  bytes have been consumed from the queue and written.
     * @param buf to add to the tail of the queue
     * @param listener to notify when all the bytes have been consumed and written, can be {@code null}.
     */
    public void add(ByteBuf buf, ChannelFutureListener listener) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        ObjectUtil.checkNotNull(buf, "buf");
        if (readableBytes > Integer.MAX_VALUE - buf.readableBytes()) {
            throw new IllegalStateException("buffer queue length overflow: " + readableBytes
                    + " + " + buf.readableBytes());
        }
        bufAndListenerPairs.add(buf);
        if (listener != null) {
            bufAndListenerPairs.add(listener);
        }
        readableBytes += buf.readableBytes();
    }

    /**
     * Remove a {@link ByteBuf} from the queue with the specified number of bytes. Any added buffer who's bytes are
     * fully consumed during removal will have it's promise completed when the passed aggregate {@link ChannelPromise}
     * completes.
     *
     * @param bytes the maximum number of readable bytes in the returned {@link ByteBuf}, if {@code bytes} is greater
     *              than {@link #readableBytes} then a buffer of length {@link #readableBytes} is returned.
     * @param aggregatePromise used to aggregate the promises and listeners for the constituent buffers.
     * @return a {@link ByteBuf} composed of the enqueued buffers.
     */
    public ByteBuf remove(int bytes, ChannelPromise aggregatePromise) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes (expected >= 0): " + bytes);
        }
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");

        // Use isEmpty rather than readableBytes==0 as we may have a promise associated with an empty buffer.
        if (bufAndListenerPairs.isEmpty()) {
            return Unpooled.EMPTY_BUFFER;
        }
        bytes = Math.min(bytes, readableBytes);

        ByteBuf toReturn = null;
        int originalBytes = bytes;
        for (;;) {
            Object entry = bufAndListenerPairs.poll();
            if (entry == null) {
                break;
            }
            if (entry instanceof ChannelFutureListener) {
                aggregatePromise.addListener((ChannelFutureListener) entry);
                continue;
            }
            ByteBuf entryBuffer = (ByteBuf) entry;
            if (entryBuffer.readableBytes() > bytes) {
                // Add the buffer back to the queue as we can't consume all of it.
                bufAndListenerPairs.addFirst(entryBuffer);
                if (bytes > 0) {
                    // Take a slice of what we can consume and retain it.
                    toReturn = compose(toReturn, entryBuffer.readRetainedSlice(bytes));
                    bytes = 0;
                }
                break;
            } else {
                toReturn = compose(toReturn, entryBuffer);
                bytes -= entryBuffer.readableBytes();
            }
        }
        readableBytes -= originalBytes - bytes;
        assert readableBytes >= 0;
        return toReturn;
    }

    /**
     * Compose the current buffer with another.
     */
    private ByteBuf compose(ByteBuf current, ByteBuf next) {
        if (current == null) {
            return next;
        }
        if (current instanceof CompositeByteBuf) {
            CompositeByteBuf composite = (CompositeByteBuf) current;
            composite.addComponent(true, next);
            return composite;
        }
        // Create a composite buffer to accumulate this pair and potentially all the buffers
        // in the queue. Using +2 as we have already dequeued current and next.
        CompositeByteBuf composite = channel.alloc().compositeBuffer(bufAndListenerPairs.size() + 2);
        composite.addComponent(true, current);
        composite.addComponent(true, next);
        return composite;
    }

    /**
     * The number of readable bytes.
     */
    public int readableBytes() {
        return readableBytes;
    }

    /**
     * Are there pending buffers in the queue.
     */
    public boolean isEmpty() {
        return bufAndListenerPairs.isEmpty();
    }

    /**
     *  Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll(Throwable cause) {
        releaseAndCompleteAll(channel.newFailedFuture(cause));
    }

    private void releaseAndCompleteAll(ChannelFuture future) {
        readableBytes = 0;
        Throwable pending = null;
        for (;;) {
            Object entry = bufAndListenerPairs.poll();
            if (entry == null) {
                break;
            }
            try {
                if (entry instanceof ByteBuf) {
                    ReferenceCountUtil.safeRelease(entry);
                } else {
                    ((ChannelFutureListener) entry).operationComplete(future);
                }
            } catch (Throwable t) {
                pending = t;
            }
        }
        if (pending != null) {
            throw new IllegalStateException(pending);
        }
    }

    /**
     * Copy all pending entries in this queue into the destination queue.
     * @param dest to copy pending buffers to.
     */
    public void copyTo(CoalescingBufferQueue dest) {
        dest.bufAndListenerPairs.addAll(bufAndListenerPairs);
        dest.readableBytes += readableBytes;
    }
}
