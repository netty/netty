/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ServerSocketChannelConfig;

import java.net.InetAddress;
import java.util.Map;

public final class EpollServerSocketChannelConfig extends EpollServerChannelConfig
        implements ServerSocketChannelConfig {

    EpollServerSocketChannelConfig(EpollServerSocketChannel channel) {
        super(channel);

        // Use SO_REUSEADDR by default as java.nio does the same.
        //
        // See https://github.com/netty/netty/issues/2605
        setReuseAddress(true);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), EpollChannelOption.SO_REUSEPORT, EpollChannelOption.IP_FREEBIND);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == EpollChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == EpollChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == EpollChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == EpollChannelOption.IP_FREEBIND) {
            setFreeBind((Boolean) value);
        } else if (option == EpollChannelOption.TCP_MD5SIG) {
            @SuppressWarnings("unchecked")
            final Map<InetAddress, byte[]> m = (Map<InetAddress, byte[]>) value;
            setTcpMd5Sig(m);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public EpollServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        super.setReuseAddress(reuseAddress);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setBacklog(int backlog) {
        super.setBacklog(backlog);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    /**
     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details.
     * Keys can only be set on, not read to prevent a potential leak, as they are confidential.
     * Allowing them being read would mean anyone with access to the channel could get them.
     */
    public EpollServerSocketChannelConfig setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        ((EpollServerSocketChannel) channel).setTcpMd5Sig(keys);
        return this;
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    public boolean isReusePort() {
        return Native.isReusePort(channel.fd().intValue()) == 1;
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link EpollSocketChannel}s to the same port and so accept connections with multiple threads.
     *
     * Be aware this method needs be called before {@link EpollSocketChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    public EpollServerSocketChannelConfig setReusePort(boolean reusePort) {
        Native.setReusePort(channel.fd().intValue(), reusePort ? 1 : 0);
        return this;
    }

    /**
     * Returns {@code true} if <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isFreeBind() {
        return Native.isIpFreeBind(channel.fd().intValue()) != 0;
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollServerSocketChannelConfig setFreeBind(boolean freeBind) {
        Native.setIpFreeBind(channel.fd().intValue(), freeBind ? 1: 0);
        return this;
    }
}
