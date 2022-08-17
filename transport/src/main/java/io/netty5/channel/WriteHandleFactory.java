/*
 * Copyright 2022 The Netty Project
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
 * Implementations allow to influence how much data / messages are written per write loop invocation.
 */
public interface WriteHandleFactory {

    /**
     * Creates a new handle for the given {@link Channel}.
     *
     * @param channel   the {@link Channel} for which the {@link WriteHandle} is used.
     */
    WriteHandle newHandle(Channel channel);

    /**
     * Handle which allows to customize how data / messages are read.
     */
    interface WriteHandle {

        /**
         * Estimate the maximum number of bytes that can be written with one gathering write operation.
         */
        default long estimatedMaxBytesPerGatheringWrite() {
            return Integer.MAX_VALUE;
        }

        /**
         * Notify the {@link WriteHandle} of the last write operation and its result.
         *
         * @param attemptedBytesWrite   The number of  bytes the write operation did attempt to write.
         * @param actualBytesWrite      The number of bytes from the previous write operation. This may be negative if a
         *                              write error occurs.
         * @param numMessagesWrite      The number of messages written.
         * @return                      {@code true} if the write loop should continue writing, {@code false} otherwise.
         */
        boolean lastWrite(long attemptedBytesWrite, long actualBytesWrite, int numMessagesWrite);

        /**
         * Method that must be called once the write loop was completed.
         */
        void writeComplete();
    }
}
