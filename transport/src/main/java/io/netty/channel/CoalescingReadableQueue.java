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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.ArrayDeque;

/**
 * A FIFO queue of bytes where producers add bytes by repeatedly adding {@link ByteBuf} or {@link FileRegion} and
 * consumers take bytes in arbitrary lengths. This allows producers to add lots of small buffers and the consumer to
 * take all the bytes out in a single buffer. Conversely the producer may add larger buffers and the consumer could take
 * the bytes in many small buffers.
 *
 * <p>
 * Bytes are added and removed with promises. If the last byte of a buffer added with a promise is removed then that
 * promise will complete when the promise passed to {@link #remove} completes.
 *
 * <p>
 * This functionality is useful for aggregating or partitioning writes into fixed size buffers for framing protocols
 * such as HTTP2.
 */
public final class CoalescingReadableQueue {

    private final Channel channel;
    private final ArrayDeque<Object> bufAndListenerPairs = new ArrayDeque<Object>();
    private long readableBytes;

    public CoalescingReadableQueue(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
    }

    /**
     * Add a buffer to the end of the queue.
     */
    public void add(Object obj) {
        add(obj, (ChannelFutureListener) null);
    }

    /**
     * Add a buffer to the end of the queue and associate a promise with it that should be completed when all the
     * buffers bytes have been consumed from the queue and written.
     *
     * @param obj
     *            to add to the tail of the queue
     * @param promise
     *            to complete when all the bytes have been consumed and written, can be void.
     */
    public void add(Object obj, ChannelPromise promise) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        ObjectUtil.checkNotNull(promise, "promise");
        add(obj, promise.isVoid() ? null : new ChannelPromiseNotifier(promise));
    }

    /**
     * Add a buffer to the end of the queue and associate a listener with it that should be completed when all the
     * buffers bytes have been consumed from the queue and written.
     *
     * @param obj
     *            to add to the tail of the queue
     * @param listener
     *            to notify when all the bytes have been consumed and written, can be {@code null}.
     */
    public void add(Object obj, ChannelFutureListener listener) {
        // buffers are added before promises so that we naturally 'consume' the entire buffer during removal
        // before we complete it's promise.
        ObjectUtil.checkNotNull(obj, "buffer");
        long addedBytes;
        if (obj instanceof ByteBuf) {
            addedBytes = ((ByteBuf) obj).readableBytes();
        } else if (obj instanceof FileRegion) {
            addedBytes = ((FileRegion) obj).transferableBytes();
        } else if (obj instanceof ReadableCollection) {
            addedBytes = ((ReadableCollection) obj).readableBytes();
        } else {
            throw new IllegalArgumentException("Unsupported buffer type: " + obj.getClass());
        }
        bufAndListenerPairs.add(obj);
        if (listener != null) {
            bufAndListenerPairs.add(listener);
        }
        readableBytes += addedBytes;
    }

    /**
     * Remove a {@link ReadableCollection} from the queue with the specified number of bytes. Any added buffer who's
     * bytes are fully consumed during removal will have it's promise completed when the passed aggregate
     * {@link ChannelPromise} completes.
     *
     * @param bytes
     *            the maximum number of readable bytes in the returned {@link ByteBuf}, if {@code bytes} is greater than
     *            {@link #readableBytes} then a buffer of length {@link #readableBytes} is returned.
     * @param aggregatePromise
     *            used to aggregate the promises and listeners for the constituent buffers.
     * @return a {@link ReadableCollection} composed of the enqueued buffers.
     */
    public ReadableCollection remove(long bytes, ChannelPromise aggregatePromise) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes (expected >= 0): " + bytes);
        }
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");

        // Use isEmpty rather than readableBytes==0 as we may have a promise associated with an empty buffer.
        if (bufAndListenerPairs.isEmpty()) {
            return ReadableCollection.EMPTY;
        }
        bytes = Math.min(bytes, readableBytes);
        readableBytes -= bytes;
        ReadableCollection.Builder builder = ReadableCollection.create(channel.alloc(), bufAndListenerPairs.size() / 2);
        for (long remaining = bytes;;) {
            Object entry = bufAndListenerPairs.poll();
            if (entry == null) {
                break;
            }
            if (entry instanceof ChannelFutureListener) {
                aggregatePromise.addListener((ChannelFutureListener) entry);
                continue;
            }
            if (entry instanceof ByteBuf) {
                ByteBuf entryBuffer = (ByteBuf) entry;
                if (entryBuffer.readableBytes() > remaining) {
                    // Add the buffer back to the queue as we can't consume all of it.
                    bufAndListenerPairs.addFirst(entryBuffer);
                    if (remaining > 0) {
                        // Take a slice of what we can consume and retain it.
                        builder.add(entryBuffer.readSlice((int) remaining).retain());
                    }
                    break;
                } else {
                    remaining -= entryBuffer.readableBytes();
                    builder.add(entryBuffer);
                }
            } else if (entry instanceof FileRegion) {
                FileRegion entryRegion = (FileRegion) entry;
                if (entryRegion.transferableBytes() > remaining) {
                    // Add the buffer back to the queue as we can't consume all of it.
                    bufAndListenerPairs.addFirst(entryRegion);
                    if (remaining > 0) {
                        // Take a slice of what we can consume and retain it.
                        builder.add(entryRegion.transferSlice(remaining).retain());
                    }
                    break;
                } else {
                    remaining -= entryRegion.transferableBytes();
                    builder.add(entryRegion);
                }
            } else { // ReadableCollection
                ReadableCollection entryRc = (ReadableCollection) entry;
                if (entryRc.readableBytes() > remaining) {
                    // Add the buffer back to the queue as we can't consume all of it.
                    bufAndListenerPairs.addFirst(entryRc);
                    if (remaining > 0) {
                        // Take a slice of what we can consume and retain it.
                        builder.add(entryRc, remaining);
                    }
                    break;
                } else {
                    remaining -= entryRc.readableBytes();
                    builder.add(entryRc, entryRc.readableBytes());
                }
            }
        }
        return builder.build();
    }

    /**
     * The number of readable bytes.
     */
    public long readableBytes() {
        return readableBytes;
    }

    /**
     * Are there pending buffers in the queue.
     */
    public boolean isEmpty() {
        return bufAndListenerPairs.isEmpty();
    }

    /**
     * Release all buffers in the queue and complete all listeners and promises.
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
                if (entry instanceof ChannelFutureListener) {
                    ((ChannelFutureListener) entry).operationComplete(future);
                } else if (entry instanceof ReadableCollection) {
                    ((ReadableCollection) entry).clear();
                } else {
                    ReferenceCountUtil.safeRelease(entry);
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
     *
     * @param dest
     *            to copy pending buffers to.
     */
    public void copyTo(CoalescingReadableQueue dest) {
        dest.bufAndListenerPairs.addAll(bufAndListenerPairs);
        dest.readableBytes += readableBytes;
    }
}
