/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.socket.oio;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannelConfig;

/**
 * A {@link ChannelConfig} for a {@link OioSocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannelConfig},
 * {@link OioSocketChannelConfig} allows the following options in the
 * option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_TIMEOUT}</td><td>{@link #setSoTimeout(int)}</td>
 * </tr>
 * </table>
 */
public interface OioSocketChannelConfig extends SocketChannelConfig {

    /**
     * Sets the maximal time a operation on the underlying socket may block.
     */
    OioSocketChannelConfig setSoTimeout(int timeout);

    /**
     * Returns the maximal time a operation on the underlying socket may block.
     */
    int getSoTimeout();

    @Override
    OioSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay);

    @Override
    OioSocketChannelConfig setSoLinger(int soLinger);

    @Override
    OioSocketChannelConfig setSendBufferSize(int sendBufferSize);

    @Override
    OioSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    @Override
    OioSocketChannelConfig setKeepAlive(boolean keepAlive);

    @Override
    OioSocketChannelConfig setTrafficClass(int trafficClass);

    @Override
    OioSocketChannelConfig setReuseAddress(boolean reuseAddress);

    @Override
    OioSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    @Override
    OioSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    OioSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    @Deprecated
    OioSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    OioSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    OioSocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    OioSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    OioSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    OioSocketChannelConfig setAutoClose(boolean autoClose);

    @Override
    OioSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    OioSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    OioSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    OioSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);
}
