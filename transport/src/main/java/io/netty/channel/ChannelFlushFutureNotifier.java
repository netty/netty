/*
 * Copyright 2012 The Netty Project
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

import java.util.ArrayDeque;
import java.util.Queue;

public final class ChannelFlushFutureNotifier {

    private long writeCounter;
    private final Queue<FlushCheckpoint> flushCheckpoints = new ArrayDeque<FlushCheckpoint>();


    /**
     * Add a {@link ChannelFuture} to this {@link ChannelFlushFutureNotifier} which will be notified after the given
     * pendingDataSize was reached.
     */
    public void addFlushFuture(ChannelFuture future, int pendingDataSize) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (pendingDataSize < 0) {
            throw new IllegalArgumentException("pendingDataSize must be >= 0 but was" + pendingDataSize);
        }
        long checkpoint = writeCounter + pendingDataSize;
        if (future instanceof FlushCheckpoint) {
            FlushCheckpoint cp = (FlushCheckpoint) future;
            cp.flushCheckpoint(checkpoint);
            flushCheckpoints.add(cp);
        } else {
            flushCheckpoints.add(new DefaultFlushCheckpoint(checkpoint, future));
        }
    }

    /**
     * Increase the current write counter by the given delta
     */
    public void increaseWriteCounter(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("delta must be >= 0 but was" + delta);
        }
        writeCounter += delta;
    }

    /**
     * Return the current write counter of this {@link ChannelFlushFutureNotifier}
     */
    public long writeCounter() {
        return writeCounter;
    }

    /**
     * Notify all {@link ChannelFuture}s that were registered with {@link #addFlushFuture(ChannelFuture, int)} and
     * their pendingDatasize is smaller after the the current writeCounter returned by {@link #writeCounter()}.
     *
     * After a {@link ChannelFuture} was notified it will be removed from this {@link ChannelFlushFutureNotifier} and
     * so not receive anymore notificiation.
     */
    public void notifyFlushFutures() {
        notifyFlushFutures0(null);
    }

    /**
     * Notify all {@link ChannelFuture}s that were registered with {@link #addFlushFuture(ChannelFuture, int)} and
     * their pendingDatasize isis smaller then the current writeCounter returned by {@link #writeCounter()}.
     *
     * After a {@link ChannelFuture} was notified it will be removed from this {@link ChannelFlushFutureNotifier} and
     * so not receive anymore notificiation.
     *
     * The rest of the remaining {@link ChannelFuture}s will be failed with the given {@link Throwable}.
     *
     * So after this operation this {@link ChannelFutureListener} is empty.
     */
    public void notifyFlushFutures(Throwable cause) {
        notifyFlushFutures();
        for (;;) {
            FlushCheckpoint cp = flushCheckpoints.poll();
            if (cp == null) {
                break;
            }
            cp.future().setFailure(cause);
        }
    }

    /**
     * Notify all {@link ChannelFuture}s that were registered with {@link #addFlushFuture(ChannelFuture, int)} and
     * their pendingDatasize is smaller then the current writeCounter returned by {@link #writeCounter()} using
     * the given cause1.
     *
     * After a {@link ChannelFuture} was notified it will be removed from this {@link ChannelFlushFutureNotifier} and
     * so not receive anymore notificiation.
     *
     * The rest of the remaining {@link ChannelFuture}s will be failed with the given {@link Throwable}.
     *
     * So after this operation this {@link ChannelFutureListener} is empty.
     *
     * @param cause1    the {@link Throwable} which will be used to fail all of the {@link ChannelFuture}s whichs
     *                  pendingDataSize is smaller then the current writeCounter returned by {@link #writeCounter()}
     * @param cause2    the {@link Throwable} which will be used to fail the remaining {@link ChannelFuture}s
     */
    public void notifyFlushFutures(Throwable cause1, Throwable cause2) {
        notifyFlushFutures0(cause1);
        for (;;) {
            FlushCheckpoint cp = flushCheckpoints.poll();
            if (cp == null) {
                break;
            }
            cp.future().setFailure(cause2);
        }
    }

    private void notifyFlushFutures0(Throwable cause) {
        if (flushCheckpoints.isEmpty()) {
            writeCounter = 0;
            return;
        }

        final long writeCounter = this.writeCounter;
        for (;;) {
            FlushCheckpoint cp = flushCheckpoints.peek();
            if (cp == null) {
                // Reset the counter if there's nothing in the notification list.
                this.writeCounter = 0;
                break;
            }

            if (cp.flushCheckpoint() > writeCounter) {
                if (writeCounter > 0 && flushCheckpoints.size() == 1) {
                    this.writeCounter = 0;
                    cp.flushCheckpoint(cp.flushCheckpoint() - writeCounter);
                }
                break;
            }

            flushCheckpoints.remove();
            if (cause == null) {
                cp.future().setSuccess();
            } else {
                cp.future().setFailure(cause);
            }
        }

        // Avoid overflow
        final long newWriteCounter = this.writeCounter;
        if (newWriteCounter >= 0x1000000000000000L) {
            // Reset the counter only when the counter grew pretty large
            // so that we can reduce the cost of updating all entries in the notification list.
            this.writeCounter = 0;
            for (FlushCheckpoint cp: flushCheckpoints) {
                cp.flushCheckpoint(cp.flushCheckpoint() - newWriteCounter);
            }
        }
    }

    abstract static class FlushCheckpoint {
        abstract long flushCheckpoint();
        abstract void flushCheckpoint(long checkpoint);
        abstract ChannelFuture future();
    }

    private static class DefaultFlushCheckpoint extends FlushCheckpoint {
        private long checkpoint;
        private final ChannelFuture future;

        DefaultFlushCheckpoint(long checkpoint, ChannelFuture future) {
            this.checkpoint = checkpoint;
            this.future = future;
        }

        @Override
        long flushCheckpoint() {
            return checkpoint;
        }

        @Override
        void flushCheckpoint(long checkpoint) {
            this.checkpoint = checkpoint;
        }

        @Override
        ChannelFuture future() {
            return future;
        }
    }
}
