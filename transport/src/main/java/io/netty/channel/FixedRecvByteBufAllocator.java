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
package io.netty.channel;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * The {@link RecvByteBufAllocator} that always yields the same buffer
 * size prediction. This predictor ignores the feed back from the I/O thread.
 *
 * If you plan to use this {@link FixedRecvByteBufAllocator} for a {@link io.netty.channel.socket.DatagramChannel}
 * consider using {@link #newDatagramRecvByteBufAllocator(int)}.
 */
public class FixedRecvByteBufAllocator extends DefaultMaxMessagesRecvByteBufAllocator {

    private final int bufferSize;

    private final class HandleImpl extends MaxMessageHandle {
        private final int bufferSize;

        HandleImpl(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        @Override
        public int guess() {
            return bufferSize;
        }
    }

    /**
     * Creates a new {@link FixedRecvByteBufAllocator} that will continue reading even when the read
     * {@link io.netty.channel.socket.DatagramPacket} was smaller then the given {@code bufferSize}.
     *
     * @param bufferSize the buffer size that is used for {@link io.netty.channel.socket.DatagramPacket}s.
     * @return the allocator.
     */
    public static FixedRecvByteBufAllocator newDatagramRecvByteBufAllocator(int bufferSize) {
        FixedRecvByteBufAllocator recvAlloc = new FixedRecvByteBufAllocator(bufferSize);
        // Let's use false here as the number of bytes may be less then what we tried to read when using datagrams.
        // This is expected and we should tolerate this as otherwise we may need to wait for the selector to wakeup
        // again for a read.
        recvAlloc.respectMaybeMoreData(false);
        return recvAlloc;
    }

    /**
     * Creates a new predictor that always returns the same prediction of
     * the specified buffer size.
     */
    public FixedRecvByteBufAllocator(int bufferSize) {
        checkPositive(bufferSize, "bufferSize");
        this.bufferSize = bufferSize;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Handle newHandle() {
        return new HandleImpl(bufferSize);
    }

    @Override
    public FixedRecvByteBufAllocator respectMaybeMoreData(boolean respectMaybeMoreData) {
        super.respectMaybeMoreData(respectMaybeMoreData);
        return this;
    }
}
