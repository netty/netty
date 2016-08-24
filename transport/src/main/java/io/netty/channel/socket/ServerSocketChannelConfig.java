/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

import java.net.ServerSocket;
import java.net.StandardSocketOptions;

/**
 * A {@link ChannelConfig} for a {@link ServerSocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link ServerSocketChannelConfig} allows the following options in the
 * option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "backlog"}</td><td>{@link #setBacklog(int)}</td>
 * </tr><tr>
 * <td>{@code "reuseAddress"}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr>
 * </table>
 */
public interface ServerSocketChannelConfig extends ChannelConfig {

    /**
     * Gets the backlog value to specify when the channel binds to a local
     * address.
     */
    int getBacklog();

    /**
     * Sets the backlog value to specify when the channel binds to a local
     * address.
     */
    ServerSocketChannelConfig setBacklog(int backlog);

    /**
     * Gets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    boolean isReuseAddress();

    /**
     * Sets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    ServerSocketChannelConfig setReuseAddress(boolean reuseAddress);

    /**
     * Gets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    ServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    /**
     * Sets the performance preferences as specified in
     * {@link ServerSocket#setPerformancePreferences(int, int, int)}.
     */
    ServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    @Override
    ServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    @Deprecated
    ServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    ServerSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    ServerSocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    ServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    ServerSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    ServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    @Override
    ServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    ServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    ServerSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

}
