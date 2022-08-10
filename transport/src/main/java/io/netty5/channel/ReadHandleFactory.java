/*
 * Copyright 2012 The Netty Project
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

/**
 * Implementations allow to influence how much data / messages are received per read loop invocation.
 */
public interface ReadHandleFactory {
    /**
     * Creates a new handle. The handle provides the actual operations and keeps the internal information which is
     * required for predicting an optimal buffer capacity.
     */
    ReadHandle newHandle();

    /**
     * Handle which allows to customize how data / messages are read.
     */
    interface ReadHandle {

        /**
         * Guess the capacity for the next receive buffer that is probably large enough to read all inbound data and
         * small enough not to waste its space.
         */
        int estimatedBufferCapacity();

        /**
         * Notify the {@link ReadHandle} of the last read operation and its result.
         *
         * @param attemptedBytesRead    The number of  bytes the read operation did attempt to read.
         * @param actualBytesRead       The number of bytes from the previous read operation. This may be negative if a
         *                              read error occurs.
         * @param numMessagesRead       The number of messages read.
         */
        void lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead);

        /**
         * Determine if the current read loop should continue.
         *
         * @param autoRead {@code true} if {@link ChannelOption#AUTO_READ} is used, {@code false} otherwise.
         * @return {@code true} if the read loop should continue reading. {@code false}
         * if the read loop is complete.
         */
        boolean continueReading(boolean autoRead);

        /**
         * Method that must be called once the read loop was completed.
         */
        void readComplete();
    }
}
