/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.unix;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

public interface VSockChannelConfig extends ChannelConfig {
    @Override
    @Deprecated
    VSockChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    VSockChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    VSockChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    VSockChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    VSockChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    VSockChannelConfig setAutoRead(boolean autoRead);

    @Override
    VSockChannelConfig setAutoClose(boolean autoClose);

    @Override
    @Deprecated
    VSockChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    @Deprecated
    VSockChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    VSockChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    VSockChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);
}
