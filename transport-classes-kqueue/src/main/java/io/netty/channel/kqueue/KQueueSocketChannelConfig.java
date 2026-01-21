/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_LINGER;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.channel.kqueue.KQueueChannelOption.SO_SNDLOWAT;
import static io.netty.channel.kqueue.KQueueChannelOption.TCP_NOPUSH;
import static io.netty.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;

final class KQueueSocketChannelConfig extends KQueueChannelConfig {
    private volatile boolean allowHalfClosure;
    private volatile boolean tcpFastopen;
    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    KQueueSocketChannelConfig(AbstractKQueueChannel channel) {
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
                ALLOW_HALF_CLOSURE, SO_SNDLOWAT, TCP_NOPUSH);
        if (((AbstractKQueueChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return getOptions(options, DOMAIN_SOCKET_READ_MODE);
        }
        return options;
    }

    private void setReadMode(DomainSocketReadMode mode) {
        this.mode = ObjectUtil.checkNotNull(mode, "mode");
    }

    DomainSocketReadMode getReadMode() {
        return mode;
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
        if (option == SO_SNDLOWAT) {
            return (T) Integer.valueOf(getSndLowAt());
        }
        if (option == TCP_NOPUSH) {
            return (T) Boolean.valueOf(isTcpNoPush());
        }
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            return (T) Boolean.valueOf(isTcpFastOpenConnect());
        }
        if (option == DOMAIN_SOCKET_READ_MODE &&
                ((AbstractKQueueChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
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
        } else if (option == SO_SNDLOWAT) {
            setSndLowAt((Integer) value);
        } else if (option == TCP_NOPUSH) {
            setTcpNoPush((Boolean) value);
        } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            setTcpFastOpenConnect((Boolean) value);
        } else if (option == DOMAIN_SOCKET_READ_MODE &&
                ((AbstractKQueueChannel) channel).socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            setReadMode((DomainSocketReadMode) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    private int getReceiveBufferSize() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSendBufferSize() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    int getSoLinger() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isKeepAlive() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoDelay() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSndLowAt() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getSndLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSndLowAt(int sndLowAt)  {
        try {
            ((AbstractKQueueChannel) channel).socket.setSndLowAt(sndLowAt);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoPush() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isTcpNoPush();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoPush(boolean tcpNoPush)  {
        try {
            ((AbstractKQueueChannel) channel).socket.setTcpNoPush(tcpNoPush);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            ((AbstractKQueueChannel) channel).socket.setKeepAlive(keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractKQueueChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractKQueueChannel) channel).socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            ((AbstractKQueueChannel) channel).socket.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            ((AbstractKQueueChannel) channel).socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            ((AbstractKQueueChannel) channel).socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            ((AbstractKQueueChannel) channel).socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    /**
     * Enables client TCP fast open, if available.
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

    private void setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(newSendBufferSize);
        }
    }
}
