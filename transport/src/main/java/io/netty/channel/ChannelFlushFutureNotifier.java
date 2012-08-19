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
import java.util.Deque;

public final class ChannelFlushFutureNotifier {

    private long writeCounter;
    private final Deque<FlushCheckpoint> flushCheckpoints = new ArrayDeque<FlushCheckpoint>();

    public void addFlushFuture(ChannelFuture future, int size) {
        long checkpoint = writeCounter + size;
        if (future instanceof FlushCheckpoint) {
            FlushCheckpoint cp = (FlushCheckpoint) future;
            cp.flushCheckpoint(checkpoint);
            flushCheckpoints.add(cp);
        } else {
            flushCheckpoints.add(new DefaultFlushCheckpoint(checkpoint, future));
        }
    }

    public long writeCounter() {
        return writeCounter;
    }

    public void increaseWriteCounter(long delta) {
        writeCounter += delta;
    }

    public void notifyFlushFutures() {
        if (flushCheckpoints.isEmpty()) {
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
            cp.future().setSuccess();
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
