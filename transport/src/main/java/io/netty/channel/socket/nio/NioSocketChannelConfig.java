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
package io.netty.channel.socket.nio;


import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.SocketChannelConfig;

public interface NioSocketChannelConfig extends SocketChannelConfig {
    @Override
    NioSocketChannelConfig setSoLinger(int soLinger);

    @Override
    NioSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay);

    @Override
    NioSocketChannelConfig setSendBufferSize(int sendBufferSize);

    @Override
    NioSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    @Override
    NioSocketChannelConfig setKeepAlive(boolean keepAlive);

    @Override
    NioSocketChannelConfig setTrafficClass(int trafficClass);

    @Override
    NioSocketChannelConfig setReuseAddress(boolean reuseAddress);

    @Override
    NioSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    @Override
    NioSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    NioSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    NioSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    NioSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    NioSocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    NioSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    NioSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    NioSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    @Override
    NioSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    NioSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    /**
     * Returns {@code true} if the channel should try to automatically merge buffers and so optimize throughput.
     */
    boolean isWriteBufferAutoMerge();

    /**
     * Set if the channel should try to automatically merge buffers and so optimize throughput.
     */
    NioSocketChannelConfig setWriteBufferAutoMerge(boolean autoMerge);
}
