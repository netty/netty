/*
 * Copyright 2015 The Netty Project
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

import java.util.Map.Entry;

/**
 * {@link RecvByteBufAllocator} that limits a read operation based upon a maximum value per individual read
 * and a maximum amount when a read operation is attempted by the event loop.
 */
public interface MaxBytesRecvByteBufAllocator extends RecvByteBufAllocator {
    /**
     * Returns the maximum number of bytes to read per read loop.
     * a {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object) channelRead()} event.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     */
    int maxBytesPerRead();

    /**
     * Sets the maximum number of bytes to read per read loop.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     */
    MaxBytesRecvByteBufAllocator maxBytesPerRead(int maxBytesPerRead);

    /**
     * Returns the maximum number of bytes to read per individual read operation.
     * a {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object) channelRead()} event.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     */
    int maxBytesPerIndividualRead();

    /**
     * Sets the maximum number of bytes to read per individual read operation.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     */
    MaxBytesRecvByteBufAllocator maxBytesPerIndividualRead(int maxBytesPerIndividualRead);

    /**
     * Atomic way to get the maximum number of bytes to read for a read loop and per individual read operation.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     * @return The Key is from {@link #maxBytesPerRead()}. The Value is from {@link #maxBytesPerIndividualRead()}
     */
    Entry<Integer, Integer> maxBytesPerReadPair();

    /**
     * Sets the maximum number of bytes to read for a read loop and per individual read operation.
     * If this value is greater than 1, an event loop might attempt to read multiple times to procure bytes.
     * @param maxBytesPerRead see {@link #maxBytesPerRead(int)}
     * @param maxBytesPerIndividualRead see {@link #maxBytesPerIndividualRead(int)}
     */
    MaxBytesRecvByteBufAllocator maxBytesPerReadPair(int maxBytesPerRead, int maxBytesPerIndividualRead);
}
