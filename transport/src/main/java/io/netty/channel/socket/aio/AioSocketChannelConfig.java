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
package io.netty.channel.socket.aio;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannelConfig;

import java.nio.channels.InterruptedByTimeoutException;


/**
 * Special {@link SocketChannelConfig} which is used for the {@link AioSocketChannel} to expose extra configuration
 * possiblilites.
 *
 * In addition to the options provided by {@link SocketChannelConfig},
 * {@link AioSocketChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link ChannelOption#AIO_READ_TIMEOUT}</td><td>{@link #setReadTimeout(long)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#AIO_WRITE_TIMEOUT}</td><td>{@link #setWriteTimeout(long)}</td>
 * </tr>
 * </table>
 */
public interface AioSocketChannelConfig extends SocketChannelConfig {

    /**
     * Return the read timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     * Once such an exception was detected it will get propagated to the handlers first. After that the channel
     * will get closed as it may be in an unknown state.
     *
     * To disable it just use {@code 0}.
     */
    AioSocketChannelConfig setReadTimeout(long readTimeoutInMillis);

    /**
     * Return the write timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     * Once such an exception was detected it will get propagated to the handlers first. After that the channel
     * will get closed as it may be in an unknown state.
     *
     * To disable it just use {@code 0}.
     */
    AioSocketChannelConfig setWriteTimeout(long writeTimeoutInMillis);

    /**
     * Return the read timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     *
     * The default is {@code 0}
     */
    long getReadTimeout();

    /**
     * Return the write timeout in milliseconds after which a {@link InterruptedByTimeoutException} will get thrown.
     *
     * The default is {@code 0}
     */
    long getWriteTimeout();

    @Override
    AioSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay);

    @Override
    AioSocketChannelConfig setSoLinger(int soLinger);

    @Override
    AioSocketChannelConfig setSendBufferSize(int sendBufferSize);

    @Override
    AioSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    @Override
    AioSocketChannelConfig setKeepAlive(boolean keepAlive);

    @Override
    AioSocketChannelConfig setTrafficClass(int trafficClass);

    @Override
    AioSocketChannelConfig setReuseAddress(boolean reuseAddress);

    @Override
    AioSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    @Override
    AioSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    AioSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    AioSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    AioSocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    AioSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    AioSocketChannelConfig setDefaultHandlerByteBufType(ChannelHandlerByteBufType type);
}
