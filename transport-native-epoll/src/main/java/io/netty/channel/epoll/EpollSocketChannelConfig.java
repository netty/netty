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
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.PlatformDependent;

import java.net.InetAddress;
import java.util.Map;

import static io.netty.channel.ChannelOption.*;

public final class EpollSocketChannelConfig extends EpollChannelConfig implements SocketChannelConfig {
    private static final long MAX_UINT32_T = 0xFFFFFFFFL;
    private final EpollSocketChannel channel;
    private volatile boolean allowHalfClosure;

    /**
     * Creates a new instance.
     */
    EpollSocketChannelConfig(EpollSocketChannel channel) {
        super(channel);

        this.channel = channel;
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                ALLOW_HALF_CLOSURE, EpollChannelOption.TCP_CORK, EpollChannelOption.TCP_NOTSENT_LOWAT,
                EpollChannelOption.TCP_KEEPCNT, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPINTVL,
                EpollChannelOption.TCP_MD5SIG);
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
            setTcpKeepCntl((Integer) value);
        } else if (option == EpollChannelOption.TCP_KEEPINTVL) {
            setTcpKeepIntvl((Integer) value);
        } else if (option == EpollChannelOption.TCP_USER_TIMEOUT) {
            setTcpUserTimeout((Integer) value);
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
    public int getReceiveBufferSize() {
        return channel.fd().getReceiveBufferSize();
    }

    @Override
    public int getSendBufferSize() {
        return channel.fd().getSendBufferSize();
    }

    @Override
    public int getSoLinger() {
        return channel.fd().getSoLinger();
    }

    @Override
    public int getTrafficClass() {
        return Native.getTrafficClass(channel.fd().intValue());
    }

    @Override
    public boolean isKeepAlive() {
        return channel.fd().isKeepAlive();
    }

    @Override
    public boolean isReuseAddress() {
        return Native.isReuseAddress(channel.fd().intValue()) == 1;
    }

    @Override
    public boolean isTcpNoDelay() {
        return channel.fd().isTcpNoDelay();
    }

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public boolean isTcpCork() {
        return channel.fd().isTcpCork();
    }

    /**
     * Get the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @return value is a uint32_t
     */
    public long getTcpNotSentLowAt() {
        return Native.getTcpNotSentLowAt(channel.fd().intValue()) & MAX_UINT32_T;
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIdle() {
        return Native.getTcpKeepIdle(channel.fd().intValue());
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepIntvl() {
        return Native.getTcpKeepIntvl(channel.fd().intValue());
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpKeepCnt() {
        return Native.getTcpKeepCnt(channel.fd().intValue());
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public int getTcpUserTimeout() {
        return Native.getTcpUserTimeout(channel.fd().intValue());
    }

    @Override
    public EpollSocketChannelConfig setKeepAlive(boolean keepAlive) {
        channel.fd().setKeepAlive(keepAlive);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        return this;
    }

    @Override
    public EpollSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        channel.fd().setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setReuseAddress(boolean reuseAddress) {
        Native.setReuseAddress(channel.fd().intValue(), reuseAddress ? 1 : 0);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        channel.fd().setSendBufferSize(sendBufferSize);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setSoLinger(int soLinger) {
        channel.fd().setSoLinger(soLinger);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        channel.fd().setTcpNoDelay(tcpNoDelay);
        return this;
    }

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpCork(boolean tcpCork) {
        channel.fd().setTcpCork(tcpCork);
        return this;
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @param tcpNotSentLowAt is a uint32_t
     */
    public EpollSocketChannelConfig setTcpNotSentLowAt(long tcpNotSentLowAt) {
        if (tcpNotSentLowAt < 0 || tcpNotSentLowAt > MAX_UINT32_T) {
            throw new IllegalArgumentException("tcpNotSentLowAt must be a uint32_t");
        }
        Native.setTcpNotSentLowAt(channel.fd().intValue(), (int) tcpNotSentLowAt);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setTrafficClass(int trafficClass) {
        Native.setTrafficClass(channel.fd().intValue(), trafficClass);
        return this;
    }

    /**
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepIdle(int seconds) {
        Native.setTcpKeepIdle(channel.fd().intValue(), seconds);
        return this;
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepIntvl(int seconds) {
        Native.setTcpKeepIntvl(channel.fd().intValue(), seconds);
        return this;
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpKeepCntl(int probes) {
        Native.setTcpKeepCnt(channel.fd().intValue(), probes);
        return this;
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    public EpollSocketChannelConfig setTcpUserTimeout(int milliseconds) {
        Native.setTcpUserTimeout(channel.fd().intValue(), milliseconds);
        return this;
    }

    /*
     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details.
     * Keys can only be set on, not read to prevent a potential leak, as they are confidential.
     * Allowing them being read would mean anyone with access to the channel could get them.
     */
    public EpollSocketChannelConfig setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        channel.setTcpMd5Sig(keys);
        return this;
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
    public EpollSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public EpollSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
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
}
