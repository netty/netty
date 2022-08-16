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

import static io.netty5.util.internal.ObjectUtil.checkPositive;

public class MaxMessagesWriteHandleFactory implements WriteHandleFactory {
    private final int maxMessagesPerWrite;

    public MaxMessagesWriteHandleFactory(int maxMessagesPerWrite) {
        this.maxMessagesPerWrite = checkPositive(maxMessagesPerWrite, "maxMessagesPerWrite");
    }

    @Override
    public final WriteHandle newHandle(Channel channel) {
        return newHandle(channel, maxMessagesPerWrite);
    }

    protected MaxMessagesWriteHandle newHandle(Channel channel, int maxMessagesPerWrite) {
        return new MaxMessagesWriteHandle(maxMessagesPerWrite);
    }

    protected static class MaxMessagesWriteHandle implements WriteHandle {
        private final int maxMessagesPerWrite;

        private int totalNumMessages;

        protected MaxMessagesWriteHandle(int maxMessagesPerWrite) {
            this.maxMessagesPerWrite = checkPositive(maxMessagesPerWrite, "maxMessagesPerWrite");
        }

        @Override
        public boolean lastWrite(long attemptedBytesWrite, long actualBytesWrite, int numMessagesWrite) {
            if (numMessagesWrite > 0) {
                this.totalNumMessages += numMessagesWrite;
            }
            return totalNumMessages <= maxMessagesPerWrite;
        }

        @Override
        public void writeComplete() {
            this.totalNumMessages = 0;
        }
    }
}
