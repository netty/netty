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
package io.netty5.channel.socket;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.MaxMessagesWriteHandleFactory;


import static io.netty5.util.internal.ObjectUtil.checkPositive;

public final class SocketChannelWriteHandleFactory extends MaxMessagesWriteHandleFactory {
    private static final int MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD = 4096;

    private final long maxBytesPerGatheringWrite;

    public SocketChannelWriteHandleFactory(int maxMessagesPerWrite) {
        this(maxMessagesPerWrite, Long.MAX_VALUE);
    }

    public SocketChannelWriteHandleFactory(int maxMessagesPerWrite, long maxBytesPerGatheringWrite) {
        super(maxMessagesPerWrite);
        this.maxBytesPerGatheringWrite = checkPositive(maxBytesPerGatheringWrite, "maxBytesPerGatheringWrite");
    }
    @Override
    protected MaxMessagesWriteHandle newHandle(Channel channel, int maxMessagesPerWrite) {
        return new SndBufferWriteHandle(channel, maxMessagesPerWrite, maxBytesPerGatheringWrite);
    }

    private static final class SndBufferWriteHandle extends MaxMessagesWriteHandle {
        private final long maxBytesPerGatheringWrite;
        private long calculatedMaxBytesPerGatheringWrite;

        SndBufferWriteHandle(Channel channel, int maxMessagesPerWrite, long maxBytesPerGatheringWrite) {
            super(maxMessagesPerWrite);
            this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
            calculatedMaxBytesPerGatheringWrite = calculateMaxBytesPerGatheringWrite(
                    channel, maxBytesPerGatheringWrite);
        }

        @Override
        public boolean lastWrite(long attemptedBytesWrite, long actualBytesWrite, int numMessagesWrite) {
            boolean continueWriting = super.lastWrite(attemptedBytesWrite, actualBytesWrite, numMessagesWrite);
            adjustMaxBytesPerGatheringWrite(attemptedBytesWrite, actualBytesWrite, maxBytesPerGatheringWrite);
            return continueWriting;
        }

        @Override
        public long estimatedMaxBytesPerGatheringWrite() {
            return calculatedMaxBytesPerGatheringWrite;
        }

        private static long calculateMaxBytesPerGatheringWrite(Channel channel, long maxBytesPerGatheringWrite) {
            // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
            int newSendBufferSize = channel.getOption(ChannelOption.SO_SNDBUF) << 1;
            if (newSendBufferSize > 0) {
                return Math.min(newSendBufferSize, maxBytesPerGatheringWrite);
            }
            return maxBytesPerGatheringWrite;
        }

        private void adjustMaxBytesPerGatheringWrite(long attempted, long written, long oldMaxBytesPerGatheringWrite) {
            // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically
            // change SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we
            // should try make a best effort to adjust as OS behavior changes.
            if (attempted == written) {
                if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                    calculatedMaxBytesPerGatheringWrite = Math.min(maxBytesPerGatheringWrite, attempted << 1);
                }
            } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
                calculatedMaxBytesPerGatheringWrite = Math.min(maxBytesPerGatheringWrite, attempted >>> 1);
            }
        }
    }
}
