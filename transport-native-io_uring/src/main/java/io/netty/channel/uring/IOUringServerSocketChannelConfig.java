/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.NetUtil;

import java.io.IOException;
import java.util.Map;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

public final class IOUringServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig {
    private volatile int backlog = NetUtil.SOMAXCONN;

    IOUringServerSocketChannelConfig(AbstractIOUringServerChannel channel) {
        super(channel);
        setReuseAddress(true);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), ChannelOption.SO_RCVBUF, ChannelOption.SO_REUSEADDR,
                ChannelOption.SO_BACKLOG, IOUringChannelOption.SO_REUSEPORT, IOUringChannelOption.IP_FREEBIND,
                IOUringChannelOption.IP_TRANSPARENT, IOUringChannelOption.TCP_DEFER_ACCEPT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == ChannelOption.SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == ChannelOption.SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        if (option == IOUringChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == IOUringChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
        }
        if (option == IOUringChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == IOUringChannelOption.TCP_DEFER_ACCEPT) {
            return (T) Integer.valueOf(getTcpDeferAccept());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        if (option == ChannelOption.SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == ChannelOption.SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == ChannelOption.SO_BACKLOG) {
            setBacklog((Integer) value);
        } else if (option == IOUringChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == IOUringChannelOption.IP_FREEBIND) {
            setFreeBind((Boolean) value);
        } else if (option == IOUringChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == IOUringChannelOption.TCP_DEFER_ACCEPT) {
            setTcpDeferAccept((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public IOUringServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return ((AbstractIOUringChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractIOUringChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractIOUringChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public IOUringServerSocketChannelConfig setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public IOUringServerSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    @Deprecated
    public IOUringServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public IOUringServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public IOUringServerSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    public boolean isReusePort() {
        try {
            return ((IOUringServerSocketChannel) channel).socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link io.netty.channel.socket.ServerSocketChannel}s to the same port and so accept connections with multiple
     * threads.
     *
     * Be aware this method needs be called before
     * {@link io.netty.channel.socket.ServerSocketChannel#bind(java.net.SocketAddress)} to have any affect.
     */
    public IOUringServerSocketChannelConfig setReusePort(boolean reusePort) {
        try {
            ((IOUringServerSocketChannel) channel).socket.setReusePort(reusePort);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isFreeBind() {
        try {
            return ((IOUringServerSocketChannel) channel).socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public IOUringServerSocketChannelConfig setFreeBind(boolean freeBind) {
        try {
            ((IOUringServerSocketChannel) channel).socket.setIpFreeBind(freeBind);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isIpTransparent() {
        try {
            return ((IOUringServerSocketChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public IOUringServerSocketChannelConfig setIpTransparent(boolean transparent) {
        try {
            ((IOUringServerSocketChannel) channel).socket.setIpTransparent(transparent);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_DEFER_ACCEPT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IOUringServerSocketChannelConfig setTcpDeferAccept(int deferAccept) {
        try {
            ((IOUringServerSocketChannel) channel).socket.setTcpDeferAccept(deferAccept);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns a positive value if <a href="http://linux.die.net/man/7/tcp">TCP_DEFER_ACCEPT</a> is enabled.
     */
    public int getTcpDeferAccept() {
        try {
            return ((IOUringServerSocketChannel) channel).socket.getTcpDeferAccept();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
