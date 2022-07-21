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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;

import java.util.function.Predicate;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

/**
 * Default implementation of {@link MaxMessagesRecvBufferAllocator} which respects {@link ChannelOption#AUTO_READ}
 * and also prevents overflow.
 */
public abstract class DefaultMaxMessagesRecvBufferAllocator implements MaxMessagesRecvBufferAllocator {
    private final boolean ignoreBytesRead;
    private volatile int maxMessagesPerRead;
    private volatile boolean respectMaybeMoreData = true;

    protected DefaultMaxMessagesRecvBufferAllocator() {
        this(1);
    }

    protected DefaultMaxMessagesRecvBufferAllocator(int maxMessagesPerRead) {
        this(maxMessagesPerRead, false);
    }

    DefaultMaxMessagesRecvBufferAllocator(int maxMessagesPerRead, boolean ignoreBytesRead) {
        this.ignoreBytesRead = ignoreBytesRead;
        maxMessagesPerRead(maxMessagesPerRead);
    }

    @Override
    public int maxMessagesPerRead() {
        return maxMessagesPerRead;
    }

    @Override
    public MaxMessagesRecvBufferAllocator maxMessagesPerRead(int maxMessagesPerRead) {
        checkPositive(maxMessagesPerRead, "maxMessagesPerRead");
        this.maxMessagesPerRead = maxMessagesPerRead;
        return this;
    }

    /**
     * Focuses on enforcing the maximum messages per read condition for {@link #continueReading(boolean)}.
     */
    public abstract class MaxMessageHandle implements Handle {
        private int maxMessagePerRead;
        private int totalMessages;
        private int totalBytesRead;
        private int attemptedBytesRead;
        private int lastBytesRead;

        private final Predicate<Handle> defaultMaybeMoreSupplier = h -> attemptedBytesRead == lastBytesRead;

        /**
         * Only {@link ChannelOption#MAX_MESSAGES_PER_READ} is used.
         */
        @Override
        public void reset() {
            maxMessagePerRead = maxMessagesPerRead();
            totalMessages = totalBytesRead = 0;
        }

        @Override
        public Buffer allocate(BufferAllocator alloc) {
            return alloc.allocate(guess());
        }

        @Override
        public final void incMessagesRead(int amt) {
            totalMessages += amt;
        }

        @Override
        public void lastBytesRead(int bytes) {
            lastBytesRead = bytes;
            if (bytes > 0) {
                totalBytesRead += bytes;
            }
        }

        @Override
        public final int lastBytesRead() {
            return lastBytesRead;
        }

        @Override
        public boolean continueReading(boolean autoRead) {
            return continueReading(autoRead, defaultMaybeMoreSupplier);
        }

        @Override
        public boolean continueReading(boolean autoRead, Predicate<Handle> maybeMoreDataSupplier) {
            return autoRead && maybeMoreDataSupplier.test(this) &&
                   totalMessages < maxMessagePerRead && (ignoreBytesRead || totalBytesRead > 0);
        }

        @Override
        public void readComplete() {
        }

        @Override
        public int attemptedBytesRead() {
            return attemptedBytesRead;
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            attemptedBytesRead = bytes;
        }

        protected final int totalBytesRead() {
            return totalBytesRead < 0 ? Integer.MAX_VALUE : totalBytesRead;
        }
    }
}
