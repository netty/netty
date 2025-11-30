/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.NetUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.TCP_FASTOPEN;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

final class EpollServerSocketChannelConfig extends EpollChannelConfig
        implements ServerSocketChannelConfig {

    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile int pendingFastOpenRequestsThreshold;

    EpollServerSocketChannelConfig(EpollServerSocketChannel channel) {
        super(channel);

        // Use SO_REUSEADDR by default as java.nio does the same.
        //
        // See https://github.com/netty/netty/issues/2605
        setReuseAddress(true);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG, EpollChannelOption.TCP_FASTOPEN,
                EpollChannelOption.SO_REUSEPORT, EpollChannelOption.IP_FREEBIND,
            EpollChannelOption.IP_TRANSPARENT, EpollChannelOption.TCP_DEFER_ACCEPT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        if (option == TCP_FASTOPEN) {
            return (T) Integer.valueOf(getTcpFastopen());
        }
        if (option == EpollChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == EpollChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
        }
        if (option == EpollChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == EpollChannelOption.TCP_DEFER_ACCEPT) {
            return (T) Integer.valueOf(getTcpDeferAccept());
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
        } else if (option == EpollChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == EpollChannelOption.TCP_MD5SIG) {
            @SuppressWarnings("unchecked")
            final Map<InetAddress, byte[]> m = (Map<InetAddress, byte[]>) value;
            setTcpMd5Sig(m);
        } else if (option == EpollChannelOption.TCP_DEFER_ACCEPT) {
            setTcpDeferAccept((Integer) value);
        } else if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else if (option == TCP_FASTOPEN) {
            setTcpFastopen((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return ((AbstractEpollChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public EpollServerSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractEpollChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public int getReceiveBufferSize() {
        try {
            return ((AbstractEpollChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractEpollChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public ServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public EpollServerSocketChannelConfig setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
        return this;
    }

    /**
     * Returns threshold value of number of pending for fast open connect.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    public int getTcpFastopen() {
        return pendingFastOpenRequestsThreshold;
    }

    /**
     * Enables tcpFastOpen on the server channel. If the underlying os doesn't support TCP_FASTOPEN setting this has no
     * effect. This has to be set before doing listen on the socket otherwise this takes no effect.
     *
     * @param pendingFastOpenRequestsThreshold number of requests to be pending for fastopen at a given point in time
     * for security.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    public EpollServerSocketChannelConfig setTcpFastopen(int pendingFastOpenRequestsThreshold) {
        this.pendingFastOpenRequestsThreshold = checkPositiveOrZero(pendingFastOpenRequestsThreshold,
                "pendingFastOpenRequestsThreshold");
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
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
    @Deprecated
    public EpollServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public EpollServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollServerSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
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
        try {
            ((EpollServerSocketChannel) channel).setTcpMd5Sig(keys);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    public boolean isReusePort() {
        try {
            return ((EpollServerSocketChannel) channel).socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link EpollSocketChannel}s to the same port and so accept connections with multiple threads.
     *
     * Be aware this method needs be called before {@link EpollSocketChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    public EpollServerSocketChannelConfig setReusePort(boolean reusePort) {
        try {
            ((EpollServerSocketChannel) channel).socket.setReusePort(reusePort);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isFreeBind() {
        try {
            return ((EpollServerSocketChannel) channel).socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollServerSocketChannelConfig setFreeBind(boolean freeBind) {
        try {
            ((EpollServerSocketChannel) channel).socket.setIpFreeBind(freeBind);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isIpTransparent() {
        try {
            return ((EpollServerSocketChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollServerSocketChannelConfig setIpTransparent(boolean transparent) {
        try {
            ((EpollServerSocketChannel) channel).socket.setIpTransparent(transparent);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_DEFER_ACCEPT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollServerSocketChannelConfig setTcpDeferAccept(int deferAccept) {
        try {
            ((EpollServerSocketChannel) channel).socket.setTcpDeferAccept(deferAccept);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns a positive value if <a href="https://linux.die.net//man/7/tcp">TCP_DEFER_ACCEPT</a> is enabled.
     */
    public int getTcpDeferAccept() {
        try {
            return ((EpollServerSocketChannel) channel).socket.getTcpDeferAccept();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
