/*
 * Copyright 2021 The Netty Project
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
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

/**
 * A {@link ChannelConfig} for a {@link DomainDatagramChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link DomainDatagramChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_SNDBUF}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr>
 * </table>
 */
public interface DomainDatagramChannelConfig extends ChannelConfig {

    @Override
    DomainDatagramChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    DomainDatagramChannelConfig setAutoClose(boolean autoClose);

    @Override
    DomainDatagramChannelConfig setAutoRead(boolean autoRead);

    @Override
    DomainDatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    @Deprecated
    DomainDatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    DomainDatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    @Override
    DomainDatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    /**
     * Sets the {@link java.net.StandardSocketOptions#SO_SNDBUF} option.
     */
    DomainDatagramChannelConfig setSendBufferSize(int sendBufferSize);

    /**
     * Gets the {@link java.net.StandardSocketOptions#SO_SNDBUF} option.
     */
    int getSendBufferSize();

    @Override
    DomainDatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    DomainDatagramChannelConfig setWriteSpinCount(int writeSpinCount);
}
