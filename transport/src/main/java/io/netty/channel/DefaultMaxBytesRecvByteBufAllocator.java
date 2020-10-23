/*
 * Copyright 2015 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

import java.util.AbstractMap;
import java.util.Map.Entry;

/**
 * The {@link RecvByteBufAllocator} that yields a buffer size prediction based upon decrementing the value from
 * the max bytes per read.
 */
public class DefaultMaxBytesRecvByteBufAllocator implements MaxBytesRecvByteBufAllocator {
    private volatile int maxBytesPerRead;
    private volatile int maxBytesPerIndividualRead;

    private final class HandleImpl implements ExtendedHandle {
        private int individualReadMax;
        private int bytesToRead;
        private int lastBytesRead;
        private int attemptBytesRead;
        private final UncheckedBooleanSupplier defaultMaybeMoreSupplier = new UncheckedBooleanSupplier() {
            @Override
            public boolean get() {
                return attemptBytesRead == lastBytesRead;
            }
        };

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return alloc.ioBuffer(guess());
        }

        @Override
        public int guess() {
            return Math.min(individualReadMax, bytesToRead);
        }

        @Override
        public void reset(ChannelConfig config) {
            bytesToRead = maxBytesPerRead();
            individualReadMax = maxBytesPerIndividualRead();
        }

        @Override
        public void incMessagesRead(int amt) {
        }

        @Override
        public void lastBytesRead(int bytes) {
            lastBytesRead = bytes;
            // Ignore if bytes is negative, the interface contract states it will be detected externally after call.
            // The value may be "invalid" after this point, but it doesn't matter because reading will be stopped.
            bytesToRead -= bytes;
        }

        @Override
        public int lastBytesRead() {
            return lastBytesRead;
        }

        @Override
        public boolean continueReading() {
            return continueReading(defaultMaybeMoreSupplier);
        }

        @Override
        public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
            // Keep reading if we are allowed to read more bytes, and our last read filled up the buffer we provided.
            return bytesToRead > 0 && maybeMoreDataSupplier.get();
        }

        @Override
        public void readComplete() {
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            attemptBytesRead = bytes;
        }

        @Override
        public int attemptedBytesRead() {
            return attemptBytesRead;
        }
    }

    public DefaultMaxBytesRecvByteBufAllocator() {
        this(64 * 1024, 64 * 1024);
    }

    public DefaultMaxBytesRecvByteBufAllocator(int maxBytesPerRead, int maxBytesPerIndividualRead) {
        checkMaxBytesPerReadPair(maxBytesPerRead, maxBytesPerIndividualRead);
        this.maxBytesPerRead = maxBytesPerRead;
        this.maxBytesPerIndividualRead = maxBytesPerIndividualRead;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new HandleImpl();
    }

    @Override
    public int maxBytesPerRead() {
        return maxBytesPerRead;
    }

    @Override
    public DefaultMaxBytesRecvByteBufAllocator maxBytesPerRead(int maxBytesPerRead) {
        checkPositive(maxBytesPerRead, "maxBytesPerRead");
        // There is a dependency between this.maxBytesPerRead and this.maxBytesPerIndividualRead (a < b).
        // Write operations must be synchronized, but independent read operations can just be volatile.
        synchronized (this) {
            final int maxBytesPerIndividualRead = maxBytesPerIndividualRead();
            if (maxBytesPerRead < maxBytesPerIndividualRead) {
                throw new IllegalArgumentException(
                        "maxBytesPerRead cannot be less than " +
                                "maxBytesPerIndividualRead (" + maxBytesPerIndividualRead + "): " + maxBytesPerRead);
            }

            this.maxBytesPerRead = maxBytesPerRead;
        }
        return this;
    }

    @Override
    public int maxBytesPerIndividualRead() {
        return maxBytesPerIndividualRead;
    }

    @Override
    public DefaultMaxBytesRecvByteBufAllocator maxBytesPerIndividualRead(int maxBytesPerIndividualRead) {
        checkPositive(maxBytesPerIndividualRead, "maxBytesPerIndividualRead");
        // There is a dependency between this.maxBytesPerRead and this.maxBytesPerIndividualRead (a < b).
        // Write operations must be synchronized, but independent read operations can just be volatile.
        synchronized (this) {
            final int maxBytesPerRead = maxBytesPerRead();
            if (maxBytesPerIndividualRead > maxBytesPerRead) {
                throw new IllegalArgumentException(
                        "maxBytesPerIndividualRead cannot be greater than " +
                                "maxBytesPerRead (" + maxBytesPerRead + "): " + maxBytesPerIndividualRead);
            }

            this.maxBytesPerIndividualRead = maxBytesPerIndividualRead;
        }
        return this;
    }

    @Override
    public synchronized Entry<Integer, Integer> maxBytesPerReadPair() {
        return new AbstractMap.SimpleEntry<Integer, Integer>(maxBytesPerRead, maxBytesPerIndividualRead);
    }

    private static void checkMaxBytesPerReadPair(int maxBytesPerRead, int maxBytesPerIndividualRead) {
        checkPositive(maxBytesPerRead, "maxBytesPerRead");
        checkPositive(maxBytesPerIndividualRead, "maxBytesPerIndividualRead");
        if (maxBytesPerRead < maxBytesPerIndividualRead) {
            throw new IllegalArgumentException(
                    "maxBytesPerRead cannot be less than " +
                            "maxBytesPerIndividualRead (" + maxBytesPerIndividualRead + "): " + maxBytesPerRead);
        }
    }

    @Override
    public DefaultMaxBytesRecvByteBufAllocator maxBytesPerReadPair(int maxBytesPerRead,
            int maxBytesPerIndividualRead) {
        checkMaxBytesPerReadPair(maxBytesPerRead, maxBytesPerIndividualRead);
        // There is a dependency between this.maxBytesPerRead and this.maxBytesPerIndividualRead (a < b).
        // Write operations must be synchronized, but independent read operations can just be volatile.
        synchronized (this) {
            this.maxBytesPerRead = maxBytesPerRead;
            this.maxBytesPerIndividualRead = maxBytesPerIndividualRead;
        }
        return this;
    }
}
