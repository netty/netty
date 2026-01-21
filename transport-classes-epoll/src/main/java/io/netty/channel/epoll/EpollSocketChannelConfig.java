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

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.util.internal.ObjectUtil;
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
import static io.netty.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;

final class EpollSocketChannelConfig extends EpollChannelConfig {
    private volatile boolean allowHalfClosure;
    private volatile boolean tcpFastopen;
    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    /**
     * Creates a new instance.
     */
    EpollSocketChannelConfig(AbstractEpollChannel channel) {
        super(channel);

        if (channel.socket.protocolFamily() != SocketProtocolFamily.UNIX &&
                PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
        calculateMaxBytesPerGatheringWrite();
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        Map<ChannelOption<?>, Object> options = getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                ALLOW_HALF_CLOSURE, EpollChannelOption.TCP_CORK, EpollChannelOption.TCP_NOTSENT_LOWAT,
                EpollChannelOption.TCP_KEEPCNT, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPINTVL,
                EpollChannelOption.TCP_MD5SIG, EpollChannelOption.TCP_QUICKACK,
                EpollChannelOption.IP_BIND_ADDRESS_NO_PORT, EpollChannelOption.IP_TRANSPARENT,
                ChannelOption.TCP_FASTOPEN_CONNECT, EpollChannelOption.SO_BUSY_POLL);
        if (((AbstractEpollChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return getOptions(options, DOMAIN_SOCKET_READ_MODE);
        }
        return options;
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
        if (option == EpollChannelOption.IP_BIND_ADDRESS_NO_PORT) {
            return (T) Boolean.valueOf(isIpBindAddressNoPort());
        }
        if (option == EpollChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            return (T) Boolean.valueOf(isTcpFastOpenConnect());
        }
        if (option == EpollChannelOption.SO_BUSY_POLL) {
            return (T) Integer.valueOf(getSoBusyPoll());
        }
        if (option == DOMAIN_SOCKET_READ_MODE &&
                ((EpollSocketChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return (T) getReadMode();
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
        } else if (option == EpollChannelOption.IP_BIND_ADDRESS_NO_PORT) {
            setIpBindAddressNoPort((Boolean) value);
        } else if (option == EpollChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == EpollChannelOption.TCP_MD5SIG) {
            @SuppressWarnings("unchecked")
            final Map<InetAddress, byte[]> m = (Map<InetAddress, byte[]>) value;
            setTcpMd5Sig(m);
        } else if (option == EpollChannelOption.TCP_QUICKACK) {
            setTcpQuickAck((Boolean) value);
        } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            setTcpFastOpenConnect((Boolean) value);
        } else if (option == EpollChannelOption.SO_BUSY_POLL) {
            setSoBusyPoll((Integer) value);
        } else if (option == DOMAIN_SOCKET_READ_MODE &&
                ((EpollSocketChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            setReadMode((DomainSocketReadMode) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    int getReceiveBufferSize() {
        try {
            return ((EpollSocketChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    int getSendBufferSize() {
        try {
            return ((EpollSocketChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    int getSoLinger() {
        try {
            return ((EpollSocketChannel) channel).socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    int getTrafficClass() {
        try {
            return ((EpollSocketChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    boolean isKeepAlive() {
        try {
            return ((EpollSocketChannel) channel).socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    boolean isReuseAddress() {
        try {
            return ((EpollSocketChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    boolean isTcpNoDelay() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    boolean isTcpCork() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpCork();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    int getSoBusyPoll() {
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
    long getTcpNotSentLowAt() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpNotSentLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    int getTcpKeepIdle() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepIdle();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    int getTcpKeepIntvl() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepIntvl();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    int getTcpKeepCnt() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpKeepCnt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    int getTcpUserTimeout() {
        try {
            return ((EpollSocketChannel) channel).socket.getTcpUserTimeout();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            ((EpollSocketChannel) channel).socket.setKeepAlive(keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((EpollSocketChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            ((EpollSocketChannel) channel).socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            ((EpollSocketChannel) channel).socket.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            ((EpollSocketChannel) channel).socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpCork(boolean tcpCork) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpCork(tcpCork);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setSoBusyPoll(int loopMicros) {
        try {
            ((EpollSocketChannel) channel).socket.setSoBusyPoll(loopMicros);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @param tcpNotSentLowAt is a uint32_t
     */
    private void setTcpNotSentLowAt(long tcpNotSentLowAt) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpNotSentLowAt(tcpNotSentLowAt);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            ((EpollSocketChannel) channel).socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIdle(int seconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepIdle(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIntvl(int seconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepIntvl(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepCnt(int probes) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpKeepCnt(probes);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpUserTimeout(int milliseconds) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpUserTimeout(milliseconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if the IP_BIND_ADDRESS_NO_PORT option is set.
     */
    private boolean isIpBindAddressNoPort() {
        try {
            return ((EpollSocketChannel) channel).socket.isIpBindAddressNoPort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the IP_BIND_ADDRESS_NO_PORT option on the underlying Channel.
     *
     * Be aware this method needs be called before {@link EpollSocketChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    private void setIpBindAddressNoPort(boolean ipBindAddressNoPort) {
        try {
            ((EpollSocketChannel) channel).socket.setIpBindAddressNoPort(ipBindAddressNoPort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

     /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} otherwise.
     */
     private boolean isIpTransparent() {
        try {
            return ((EpollSocketChannel) channel).socket.isIpTransparent();
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
            ((EpollSocketChannel) channel).socket.setIpTransparent(transparent);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details.
     * Keys can only be set on, not read to prevent a potential leak, as they are confidential.
     * Allowing them being read would mean anyone with access to the channel could get them.
     */
    private void setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        try {
            ((EpollSocketChannel) channel).setTcpMd5Sig(keys);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_QUICKACK} option on the socket.
     * See <a href="https://linux.die.net//man/7/tcp">TCP_QUICKACK</a>
     * for more details.
     */
    private void setTcpQuickAck(boolean quickAck) {
        try {
            ((EpollSocketChannel) channel).socket.setTcpQuickAck(quickAck);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://linux.die.net//man/7/tcp">TCP_QUICKACK</a> is enabled,
     * {@code false} otherwise.
     */
    private boolean isTcpQuickAck() {
        try {
            return ((EpollSocketChannel) channel).socket.isTcpQuickAck();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Enables client TCP fast open. {@code TCP_FASTOPEN_CONNECT} normally
     * requires Linux kernel 4.11 or later, so instead we use the traditional fast open
     * client socket mechanics that work with kernel 3.6 and later. See this
     * <a href="https://lwn.net/Articles/508865/">LWN article</a> for more info.
     */
    private void setTcpFastOpenConnect(boolean fastOpenConnect) {
        tcpFastopen = fastOpenConnect;
    }

    /**
     * Returns {@code true} if TCP fast open is enabled, {@code false} otherwise.
     */
    boolean isTcpFastOpenConnect() {
        return tcpFastopen;
    }

    boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    private EpollSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(newSendBufferSize);
        }
    }

    private void setReadMode(DomainSocketReadMode mode) {
        this.mode = ObjectUtil.checkNotNull(mode, "mode");
    }

    DomainSocketReadMode getReadMode() {
        return mode;
    }
}
