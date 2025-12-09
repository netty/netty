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

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.channel.ChannelOption.*;
import static io.netty.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;


final class IoUringSocketChannelConfig extends IoUringChannelConfig {
    private volatile boolean allowHalfClosure;
    private volatile boolean tcpFastopen;
    private static final AtomicReferenceFieldUpdater<IoUringSocketChannelConfig, DomainSocketReadMode> MODE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    IoUringSocketChannelConfig.class, DomainSocketReadMode.class, "mode");

    static final int DISABLE_WRITE_ZERO_COPY = -1;
    private volatile int writeZeroCopyThreshold = DISABLE_WRITE_ZERO_COPY;
    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;
    private volatile short bufferGroupId = -1;

    IoUringSocketChannelConfig(AbstractIoUringChannel channel) {
        super(channel);
        if (channel.socket.protocolFamily() != SocketProtocolFamily.UNIX &&
                PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        Map<ChannelOption<?>, Object> options = getOptions(
                super.getOptions(), IoUringChannelOption.IO_URING_BUFFER_GROUP_ID,
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                ALLOW_HALF_CLOSURE, IoUringChannelOption.TCP_CORK, IoUringChannelOption.TCP_NOTSENT_LOWAT,
                IoUringChannelOption.TCP_KEEPCNT, IoUringChannelOption.TCP_KEEPIDLE, IoUringChannelOption.TCP_KEEPINTVL,
                IoUringChannelOption.TCP_QUICKACK, IoUringChannelOption.IP_TRANSPARENT,
                ChannelOption.TCP_FASTOPEN_CONNECT, IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD);
        if (((AbstractIoUringChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return getOptions(options, DOMAIN_SOCKET_READ_MODE);
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            return (T) Short.valueOf(getBufferGroupId());
        }
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
        if (option == IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD) {
            return (T) Integer.valueOf(getWriteZeroCopyThreshold());
        }
        if (option == DOMAIN_SOCKET_READ_MODE &&
                ((AbstractIoUringChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return (T) getReadMode();
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            setBufferGroupId((Short) value);
        } else if (option == SO_RCVBUF) {
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
        } else if (option == IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD) {
            setWriteZeroCopyThreshold((Integer) value);
        } else if (option == DOMAIN_SOCKET_READ_MODE &&
                ((AbstractIoUringChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            setReadMode((DomainSocketReadMode) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    private int getSendBufferSize() {
        try {
            return ((IoUringSocketChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSoLinger() {
        try {
            return ((IoUringSocketChannel) channel).socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isKeepAlive() {
        try {
            return ((IoUringSocketChannel) channel).socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return ((IoUringSocketChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoDelay() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    private boolean isTcpCork() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpCork();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getSoBusyPoll() {
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
    private long getTcpNotSentLowAt() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpNotSentLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepIdle() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepIdle();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepIntvl() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepIntvl();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepCnt() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpKeepCnt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpUserTimeout() {
        try {
            return ((IoUringSocketChannel) channel).socket.getTcpUserTimeout();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            ((IoUringSocketChannel) channel).socket.setKeepAlive(keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((IoUringSocketChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            ((IoUringSocketChannel) channel).socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            ((IoUringSocketChannel) channel).socket.setSendBufferSize(sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return ((IoUringSocketChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            ((IoUringSocketChannel) channel).socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpCork(boolean tcpCork) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpCork(tcpCork);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setSoBusyPoll(int loopMicros) {
        try {
            ((IoUringSocketChannel) channel).socket.setSoBusyPoll(loopMicros);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     *
     * @param tcpNotSentLowAt is a uint32_t
     */
    private void setTcpNotSentLowAt(long tcpNotSentLowAt) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpNotSentLowAt(tcpNotSentLowAt);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            ((IoUringSocketChannel) channel).socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIdle(int seconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepIdle(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIntvl(int seconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepIntvl(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepCnt(int probes) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpKeepCnt(probes);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpUserTimeout(int milliseconds) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpUserTimeout(milliseconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} otherwise.
     */
    boolean isIpTransparent() {
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
    private void setIpTransparent(boolean transparent) {
        try {
            ((IoUringSocketChannel) channel).socket.setIpTransparent(transparent);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_QUICKACK} option on the socket. See <a href="https://linux.die.net/man/7/tcp">TCP_QUICKACK</a>
     * for more details.
     */
    private void setTcpQuickAck(boolean quickAck) {
        try {
            ((IoUringSocketChannel) channel).socket.setTcpQuickAck(quickAck);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://linux.die.net/man/7/tcp">TCP_QUICKACK</a> is enabled, {@code false}
     * otherwise.
     */
    private boolean isTcpQuickAck() {
        try {
            return ((IoUringSocketChannel) channel).socket.isTcpQuickAck();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Enables client TCP fast open. See this <a href="https://lwn.net/Articles/508865/">LWN article</a> for more info.
     */
    private void setTcpFastOpenConnect(boolean fastOpenConnect) {
        this.tcpFastopen = fastOpenConnect;
    }

    /**
     * Returns {@code true} if {@code TCP_FASTOPEN_CONNECT} is enabled, {@code false} otherwise.
     */
    private boolean isTcpFastOpenConnect() {
        return tcpFastopen;
    }

    boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    private void setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
    }

    private int getWriteZeroCopyThreshold() {
        return writeZeroCopyThreshold;
    }

    private void setWriteZeroCopyThreshold(int setWriteZeroCopyThreshold) {
        if (setWriteZeroCopyThreshold == DISABLE_WRITE_ZERO_COPY) {
            this.writeZeroCopyThreshold = DISABLE_WRITE_ZERO_COPY;
        } else {
            this.writeZeroCopyThreshold =
                    ObjectUtil.checkPositiveOrZero(setWriteZeroCopyThreshold, "setWriteZeroCopyThreshold");
        }
    }

    boolean shouldWriteZeroCopy(int amount) {
        // This can reduce one read operation on a volatile field.
        int threshold = this.getWriteZeroCopyThreshold();
        return threshold != DISABLE_WRITE_ZERO_COPY && amount >= threshold;
    }

    private void setReadMode(DomainSocketReadMode mode) {
        ObjectUtil.checkNotNull(mode, "mode");
        DomainSocketReadMode expectedMode = mode == DomainSocketReadMode.BYTES ?
                DomainSocketReadMode.FILE_DESCRIPTORS : DomainSocketReadMode.BYTES;
        boolean change = MODE_UPDATER.compareAndSet(this, expectedMode, mode);
        if (change) {
            if (channel.isRegistered()) {
                // cancel current Read
                ((AbstractIoUringChannel) channel).autoReadCleared();
            }
        }
    }

    DomainSocketReadMode getReadMode() {
        return mode;
    }

    short getBufferGroupId() {
        return bufferGroupId;
    }

    IoUringSocketChannelConfig setBufferGroupId(short bufferGroupId) {
        this.bufferGroupId = (short) ObjectUtil.checkPositiveOrZero(bufferGroupId, "bufferGroupId");
        return this;
    }
}
