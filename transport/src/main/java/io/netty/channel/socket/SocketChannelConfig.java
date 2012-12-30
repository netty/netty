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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.net.Socket;
import java.net.StandardSocketOptions;

/**
 * A {@link ChannelConfig} for a {@link SocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link SocketChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "keepAlive"}</td><td>{@link #setKeepAlive(boolean)}</td>
 * </tr><tr>
 * <td>{@code "reuseAddress"}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@code "soLinger"}</td><td>{@link #setSoLinger(int)}</td>
 * </tr><tr>
 * <td>{@code "tcpNoDelay"}</td><td>{@link #setTcpNoDelay(boolean)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "trafficClass"}</td><td>{@link #setTrafficClass(int)}</td>
 * </tr>
 * </table>
 */
public interface SocketChannelConfig extends ChannelConfig {

    /**
     * Gets the {@link StandardSocketOptions#TCP_NODELAY} option.
     */
    boolean isTcpNoDelay();

    /**
     * Sets the {@link StandardSocketOptions#TCP_NODELAY} option.
     */
    SocketChannelConfig setTcpNoDelay(boolean tcpNoDelay);

    /**
     * Gets the {@link StandardSocketOptions#SO_LINGER} option.
     */
    int getSoLinger();

    /**
     * Sets the {@link StandardSocketOptions#SO_LINGER} option.
     */
    SocketChannelConfig setSoLinger(int soLinger);

    /**
     * Gets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    int getSendBufferSize();

    /**
     * Sets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    SocketChannelConfig setSendBufferSize(int sendBufferSize);

    /**
     * Gets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    int getReceiveBufferSize();

    /**
     * Sets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    SocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the {@link StandardSocketOptions#SO_KEEPALIVE} option.
     */
    boolean isKeepAlive();

    /**
     * Sets the {@link StandardSocketOptions#SO_KEEPALIVE} option.
     */
    SocketChannelConfig setKeepAlive(boolean keepAlive);

    /**
     * Gets the {@link StandardSocketOptions#IP_TOS} option.
     */
    int getTrafficClass();

    /**
     * Sets the {@link StandardSocketOptions#IP_TOS} option.
     */
    SocketChannelConfig setTrafficClass(int trafficClass);

    /**
     * Gets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    boolean isReuseAddress();

    /**
     * Sets the {@link StandardSocketOptions#SO_REUSEADDR} option.
     */
    SocketChannelConfig setReuseAddress(boolean reuseAddress);

    /**
     * Sets the performance preferences as specified in
     * {@link Socket#setPerformancePreferences(int, int, int)}.
     */
    SocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    /**
     * Returns {@code true} if and only if the channel should not close itself when its remote
     * peer shuts down output to make the connection half-closed.  If {@code false}, the connection
     * is closed automatically when the remote peer shuts down output.
     */
    boolean isAllowHalfClosure();

    /**
     * Sets whether the channel should not close itself when its remote peer shuts down output to
     * make the connection half-closed.  If {@code true} the connection is not closed when the
     * remote peer shuts down output. Instead, {@link ChannelHandler#userEventTriggered(ChannelHandlerContext, Object)}
     * is invoked with a {@link ChannelInputShutdownEvent} object. If {@code false}, the connection
     * is closed automatically.
     */
    SocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    SocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    SocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    SocketChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    SocketChannelConfig setAutoRead(boolean autoRead);
}
