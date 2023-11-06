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

import static io.netty5.util.internal.ObjectUtil.checkPositive;

/**
 * Base implementation of {@link ReadHandleFactory} which allows to limit the number of messages read per read loop.
 */
public abstract class MaxMessagesReadHandleFactory implements ReadHandleFactory {
    private final int maxMessagesPerRead;

    protected MaxMessagesReadHandleFactory() {
        this(16);
    }

    protected MaxMessagesReadHandleFactory(int maxMessagesPerRead) {
        this.maxMessagesPerRead = checkPositive(maxMessagesPerRead, "maxMessagesPerRead");
    }

    @Override
    public final ReadHandle newHandle(Channel channel) {
        return newMaxMessageHandle(maxMessagesPerRead);
    }

    /**
     * Creates a new {@link MaxMessageReadHandle} to use.
     *
     * @param maxMessagesPerRead    the maximum number of messages to read per read loop.
     * @return                      the handle.
     */
    protected abstract MaxMessageReadHandle newMaxMessageHandle(int maxMessagesPerRead);

    /**
     * Focuses on enforcing the maximum messages per read condition for {@link #lastRead(int, int, int)}.
     */
    protected abstract static class MaxMessageReadHandle implements ReadHandle {
        private final int maxMessagesPerRead;
        private int messagesPrepared;
        private int messagesCompleted;

        protected MaxMessageReadHandle(int maxMessagesPerRead) {
            this.maxMessagesPerRead = checkPositive(maxMessagesPerRead, "maxMessagesPerRead");
        }

        /**
         * @return 1 if more messages can be prepared,
         * or 0 if the maximum number of messages have already been prepared.
         */
        @Override
        public int prepareRead() {
            return messagesPrepared++ < maxMessagesPerRead? 1 : 0;
        }

        @Override
        public boolean lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead) {
            if (numMessagesRead > 0) {
                messagesCompleted += numMessagesRead;
            }
            return messagesCompleted < maxMessagesPerRead;
        }

        @Override
        public void readComplete() {
            messagesPrepared = 0;
            messagesCompleted = 0;
        }
    }
}
