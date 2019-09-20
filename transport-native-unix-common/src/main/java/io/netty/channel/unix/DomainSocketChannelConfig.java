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
package io.netty.channel.unix;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

/**
 * Special {@link ChannelConfig} for {@link DomainSocketChannel}s.
 */
public interface DomainSocketChannelConfig extends ChannelConfig {

    @Override
    @Deprecated
    DomainSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    DomainSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    DomainSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    DomainSocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    DomainSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    DomainSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    DomainSocketChannelConfig setAutoClose(boolean autoClose);

    @Override
    @Deprecated
    DomainSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    @Deprecated
    DomainSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    DomainSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    DomainSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    /**
     * Change the {@link DomainSocketReadMode} for the channel. The default is
     * {@link DomainSocketReadMode#BYTES} which means bytes will be read from the
     * {@link io.netty.channel.Channel} and passed through the pipeline. If
     * {@link DomainSocketReadMode#FILE_DESCRIPTORS} is used
     * {@link FileDescriptor}s will be passed through the {@link io.netty.channel.ChannelPipeline}.
     *
     * This setting can be modified on the fly if needed.
     */
    DomainSocketChannelConfig setReadMode(DomainSocketReadMode mode);

    /**
     * Return the {@link DomainSocketReadMode} for the channel.
     */
    DomainSocketReadMode getReadMode();
}
