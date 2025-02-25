/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.*;


final class IoUringSocketChannelConfig extends IoUringStreamChannelConfig implements SocketChannelConfig {
    private volatile boolean allowHalfClosure;
    private volatile boolean tcpFastopen;

    IoUringSocketChannelConfig(AbstractIoUringChannel channel) {
        super(channel);
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                ALLOW_HALF_CLOSURE, IoUringChannelOption.TCP_CORK, IoUringChannelOption.TCP_NOTSENT_LOWAT,
                IoUringChannelOption.TCP_KEEPCNT, IoUringChannelOption.TCP_KEEPIDLE, IoUringChannelOption.TCP_KEEPINTVL,
                IoUringChannelOption.TCP_QUICKACK, IoUringChannelOption.IP_TRANSPARENT,
                ChannelOption.TCP_FASTOPEN_CONNECT);
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
        if (option == IoUringChannelOption.TCP_CORK) {
            return (T) Boolean.valueOf(isTcpCork());
        }
        if (option == IoUringChannelOption.TCP_NOTSENT_LOWAT) {
            return (T) Long.valueOf(getTcpNotSentLowAt());
        }
        if (option == IoUringChannelOption.TCP_KEEPIDLE) {
            return (T) Integer.valueOf(getTcpKeepIdle());
        }
        if (option == IoUringChannelOption.TCP_KEEPINTVL) {
            return (T) Integer.valueOf(getTcpKeepIntvl());
        }
        if (option == IoUringChannelOption.TCP_KEEPCNT) {
            return (T) Integer.valueOf(getTcpKeepCnt());
        }
        if (option == IoUringChannelOption.TCP_USER_TIMEOUT) {
            return (T) Integer.valueOf(getTcpUserTimeout());
        }
        if (option == IoUringChannelOption.TCP_QUICKACK) {
            return (T) Boolean.valueOf(isTcpQuickAck());
        }
        if (option == IoUringChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            return (T) Boolean.valueOf(isTcpFastOpenConnect());
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
        } else if (option == IoUringChannelOption.TCP_CORK) {
            setTcpCork((Boolean) value);
        } else if (option == IoUringChannelOption.TCP_NOTSENT_LOWAT) {
            setTcpNotSentLowAt((Long) value);
        } else if (option == IoUringChannelOption.TCP_KEEPIDLE) {
            setTcpKeepIdle((Integer) value);
        } else if (option == IoUringChannelOption.TCP_KEEPCNT) {
            setTcpKeepCnt((Integer) value);
        } else if (option == IoUringChannelOption.TCP_KEEPINTVL) {
            setTcpKeepIntvl((Integer) value);
        } else if (option == IoUringChannelOption.TCP_USER_TIMEOUT) {
            setTcpUserTimeout((Integer) value);
        } else if (option == IoUringChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == IoUringChannelOption.TCP_QUICKACK) {
            setTcpQuickAck((Boolean) value);
        } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            setTcpFastOpenConnect((Boolean) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((IoUringSocketChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSoLinger() {
        try {
            return ((IoUringSocketChannel) channel).socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isKeepAlive() {
        try {
            return ((IoUringSocketChannel) channel).socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return ((IoUringSocketChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isTcpNoDelay() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public boolean isTcpCork() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpCork();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getSoBusyPoll() {
        try {
            return ((IoUringSocketChannel) channel).socket.getSoBusyPoll();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     *
     * @return value is a uint32_t
     */
    public long getTcpNotSentLowAt() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpNotSentLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIdle() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepIdle();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIntvl() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepIntvl();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepCnt() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepCnt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpUserTimeout() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpUserTimeout();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setKeepAlive(boolean keepAlive) {
        try {
            ((IoUringSocketChannel) channel).socket.setKeepAlive(keepAlive);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((IoUringSocketChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((IoUringSocketChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((IoUringSocketChannel) channel).socket.setSendBufferSize(sendBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return ((IoUringSocketChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setSoLinger(int soLinger) {
        try {
            ((IoUringSocketChannel) channel).socket.setSoLinger(soLinger);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpNoDelay(tcpNoDelay);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setTcpCork(boolean tcpCork) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpCork(tcpCork);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setSoBusyPoll(int loopMicros) {
        try {
            ((IoUringSocketChannel) channel).socket.setSoBusyPoll(loopMicros);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     *
     * @param tcpNotSentLowAt is a uint32_t
     */
    public IoUringSocketChannelConfig setTcpNotSentLowAt(long tcpNotSentLowAt) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpNotSentLowAt(tcpNotSentLowAt);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IoUringSocketChannelConfig setTrafficClass(int trafficClass) {
        try {
            ((IoUringSocketChannel) channel).socket.setTrafficClass(trafficClass);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setTcpKeepIdle(int seconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepIdle(seconds);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setTcpKeepIntvl(int seconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepIntvl(seconds);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * @deprecated use {@link #setTcpKeepCnt(int)}
     */
    @Deprecated
    public IoUringSocketChannelConfig setTcpKeepCntl(int probes) {
        return setTcpKeepCnt(probes);
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setTcpKeepCnt(int probes) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepCnt(probes);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public IoUringSocketChannelConfig setTcpUserTimeout(int milliseconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpUserTimeout(milliseconds);
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
            return ((IoUringSocketChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public IoUringSocketChannelConfig setIpTransparent(boolean transparent) {
        try {
            ((IoUringSocketChannel) channel).socket.setIpTransparent(transparent);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

//    /**
//     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details. Keys can only be set
//     * on, not read to prevent a potential leak, as they are confidential. Allowing them being read would mean anyone
//     * with access to the channel could get them.
//     */
//    public IOUringSocketChannelConfig setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
//        try {
//            ((IOUringSocketChannel) channel).setTcpMd5Sig(keys);
//            return this;
//        } catch (IOException e) {
//            throw new ChannelException(e);
//        }
//    }

    /**
     * Set the {@code TCP_QUICKACK} option on the socket. See <a href="https://linux.die.net/man/7/tcp">TCP_QUICKACK</a>
     * for more details.
     */
    public IoUringSocketChannelConfig setTcpQuickAck(boolean quickAck) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpQuickAck(quickAck);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://linux.die.net/man/7/tcp">TCP_QUICKACK</a> is enabled, {@code false}
     * otherwise.
     */
    public boolean isTcpQuickAck() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpQuickAck();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Enables client TCP fast open. See this <a href="https://lwn.net/Articles/508865/">LWN article</a> for more info.
     */
    public IoUringSocketChannelConfig setTcpFastOpenConnect(boolean fastOpenConnect) {
        this.tcpFastopen = fastOpenConnect;
        return this;
    }

    /**
     * Returns {@code true} if {@code TCP_FASTOPEN_CONNECT} is enabled, {@code false} otherwise.
     */
    public boolean isTcpFastOpenConnect() {
        return tcpFastopen;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    @Override
    public IoUringSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public IoUringSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    @Deprecated
    public IoUringSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public IoUringSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public IoUringSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

}
