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
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_LINGER;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import static io.netty.channel.ChannelOption.TCP_NODELAY;

public final class EpollSocketChannelConfig extends EpollChannelConfig implements SocketChannelConfig {
    private volatile boolean allowHalfClosure;

    /**
     * Creates a new instance.
     */
    EpollSocketChannelConfig(EpollSocketChannel channel) {
        super(channel);

        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
        calculateMaxBytesPerGatheringWrite();
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                ALLOW_HALF_CLOSURE, EpollChannelOption.TCP_CORK, EpollChannelOption.TCP_NOTSENT_LOWAT,
                EpollChannelOption.TCP_KEEPCNT, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPINTVL,
                EpollChannelOption.TCP_MD5SIG, EpollChannelOption.TCP_QUICKACK, EpollChannelOption.IP_TRANSPARENT,
                EpollChannelOption.TCP_FASTOPEN_CONNECT, EpollChannelOption.SO_BUSY_POLL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == TCP_NODELAY) {
            return (T) Boolean.valueOf(isTcpNoDelay());
        }
        if (option == SO_KEEPALIVE) {
            return (T) Boolean.valueOf(isKeepAlive());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_LINGER) {
            return (T) Integer.valueOf(getSoLinger());
        }
        if (option == IP_TOS) {
            return (T) Integer.valueOf(getTrafficClass());
        }
        if (option == ALLOW_HALF_CLOSURE) {
            return (T) Boolean.valueOf(isAllowHalfClosure());
        }
        if (option == EpollChannelOption.TCP_CORK) {
            return (T) Boolean.valueOf(isTcpCork());
        }
        if (option == EpollChannelOption.TCP_NOTSENT_LOWAT) {
            return (T) Long.valueOf(getTcpNotSentLowAt());
        }
        if (option == EpollChannelOption.TCP_KEEPIDLE) {
            return (T) Integer.valueOf(getTcpKeepIdle());
        }
        if (option == EpollChannelOption.TCP_KEEPINTVL) {
            return (T) Integer.valueOf(getTcpKeepIntvl());
        }
        if (option == EpollChannelOption.TCP_KEEPCNT) {
            return (T) Integer.valueOf(getTcpKeepCnt());
        }
        if (option == EpollChannelOption.TCP_USER_TIMEOUT) {
            return (T) Integer.valueOf(getTcpUserTimeout());
        }
        if (option == EpollChannelOption.TCP_QUICKACK) {
            return (T) Boolean.valueOf(isTcpQuickAck());
        }
        if (option == EpollChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == EpollChannelOption.TCP_FASTOPEN_CONNECT) {
            return (T) Boolean.valueOf(isTcpFastOpenConnect());
        }
        if (option == EpollChannelOption.SO_BUSY_POLL) {
            return (T) Integer.valueOf(getSoBusyPoll());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == TCP_NODELAY) {
            setTcpNoDelay((Boolean) value);
        } else if (option == SO_KEEPALIVE) {
            setKeepAlive((Boolean) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_LINGER) {
            setSoLinger((Integer) value);
        } else if (option == IP_TOS) {
            setTrafficClass((Integer) value);
        } else if (option == ALLOW_HALF_CLOSURE) {
            setAllowHalfClosure((Boolean) value);
        } else if (option == EpollChannelOption.TCP_CORK) {
            setTcpCork((Boolean) value);
        } else if (option == EpollChannelOption.TCP_NOTSENT_LOWAT) {
            setTcpNotSentLowAt((Long) value);
        } else if (option == EpollChannelOption.TCP_KEEPIDLE) {
            setTcpKeepIdle((Integer) value);
        } else if (option == EpollChannelOption.TCP_KEEPCNT) {
            setTcpKeepCnt((Integer) value);
        } else if (option == EpollChannelOption.TCP_KEEPINTVL) {
            setTcpKeepIntvl((Integer) value);
        } else if (option == EpollChannelOption.TCP_USER_TIMEOUT) {
            setTcpUserTimeout((Integer) value);
        } else if (option == EpollChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == EpollChannelOption.TCP_MD5SIG) {
            @SuppressWarnings("unchecked")
            final Map<InetAddress, byte[]> m = (Map<InetAddress, byte[]>) value;
            setTcpMd5Sig(m);
        } else if (option == EpollChannelOption.TCP_QUICKACK) {
            setTcpQuickAck((Boolean) value);
        } else if (option == EpollChannelOption.TCP_FASTOPEN_CONNECT) {
            setTcpFastOpenConnect((Boolean) value);
        } else if (option == EpollChannelOption.SO_BUSY_POLL) {
            setSoBusyPoll((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return ((EpollSocketChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((EpollSocketChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSoLinger() {
        try {
            return ((EpollSocketChannel) channel).socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return ((EpollSocketChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isKeepAlive() {
        try {
            return ((EpollSocketChannel) channel).socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return ((EpollSocketChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isTcpNoDelay() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public boolean isTcpCork() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpCork();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getSoBusyPoll() {
        try {
            return ((EpollSocketChannel) channel).socket.getSoBusyPoll();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @return value is a uint32_t
     */
    public long getTcpNotSentLowAt() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpNotSentLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIdle() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepIdle();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIntvl() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepIntvl();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepCnt() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepCnt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpUserTimeout() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpUserTimeout();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setKeepAlive(boolean keepAlive) {
        try {
            ((EpollSocketChannel) channel).socket.setKeepAlive(keepAlive);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public EpollSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((EpollSocketChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((EpollSocketChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((EpollSocketChannel) channel).socket.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setSoLinger(int soLinger) {
        try {
            ((EpollSocketChannel) channel).socket.setSoLinger(soLinger);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpNoDelay(tcpNoDelay);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpCork(boolean tcpCork) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpCork(tcpCork);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setSoBusyPoll(int loopMicros) {
        try {
            ((EpollSocketChannel) channel).socket.setSoBusyPoll(loopMicros);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @param tcpNotSentLowAt is a uint32_t
     */
    public EpollSocketChannelConfig setTcpNotSentLowAt(long tcpNotSentLowAt) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpNotSentLowAt(tcpNotSentLowAt);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollSocketChannelConfig setTrafficClass(int trafficClass) {
        try {
            ((EpollSocketChannel) channel).socket.setTrafficClass(trafficClass);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepIdle(int seconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepIdle(seconds);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepIntvl(int seconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepIntvl(seconds);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * @deprecated use {@link #setTcpKeepCnt(int)}
     */
    @Deprecated
    public EpollSocketChannelConfig setTcpKeepCntl(int probes) {
        return setTcpKeepCnt(probes);
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepCnt(int probes) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepCnt(probes);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpUserTimeout(int milliseconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpUserTimeout(milliseconds);
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
            return ((EpollSocketChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollSocketChannelConfig setIpTransparent(boolean transparent) {
        try {
            ((EpollSocketChannel) channel).socket.setIpTransparent(transparent);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details.
     * Keys can only be set on, not read to prevent a potential leak, as they are confidential.
     * Allowing them being read would mean anyone with access to the channel could get them.
     */
    public EpollSocketChannelConfig setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        try {
            ((EpollSocketChannel) channel).setTcpMd5Sig(keys);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_QUICKACK} option on the socket. See <a href="http://linux.die.net/man/7/tcp">TCP_QUICKACK</a>
     * for more details.
     */
    public EpollSocketChannelConfig setTcpQuickAck(boolean quickAck) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpQuickAck(quickAck);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="http://linux.die.net/man/7/tcp">TCP_QUICKACK</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isTcpQuickAck() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpQuickAck();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_FASTOPEN_CONNECT} option on the socket. Requires Linux kernel 4.11 or later.
     * See
     * <a href="https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/commit/?id=19f6d3f3">this commit</a>
     * for more details.
     */
    public EpollSocketChannelConfig setTcpFastOpenConnect(boolean fastOpenConnect) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpFastOpenConnect(fastOpenConnect);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if {@code TCP_FASTOPEN_CONNECT} is enabled, {@code false} otherwise.
     */
    public boolean isTcpFastOpenConnect() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpFastOpenConnect();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    @Override
    public EpollSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    @Override
    public EpollSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public EpollSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    @Deprecated
    public EpollSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public EpollSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setEpollMode(EpollMode mode) {
        super.setEpollMode(mode);
        return this;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(getSendBufferSize() << 1);
        }
    }
}
