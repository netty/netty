/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;

public final class EpollDatagramChannelConfig extends EpollChannelConfig implements DatagramChannelConfig {
    private static final RecvByteBufAllocator DEFAULT_RCVBUF_ALLOCATOR = new FixedRecvByteBufAllocator(2048);
    private boolean activeOnOpen;
    private volatile int maxDatagramSize;

    EpollDatagramChannelConfig(EpollDatagramChannel channel) {
        super(channel);
        setRecvByteBufAllocator(DEFAULT_RCVBUF_ALLOCATOR);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                ChannelOption.SO_BROADCAST, ChannelOption.SO_RCVBUF, ChannelOption.SO_SNDBUF,
                ChannelOption.SO_REUSEADDR, ChannelOption.IP_MULTICAST_LOOP_DISABLED,
                ChannelOption.IP_MULTICAST_ADDR, ChannelOption.IP_MULTICAST_IF, ChannelOption.IP_MULTICAST_TTL,
                ChannelOption.IP_TOS, ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION,
                EpollChannelOption.SO_REUSEPORT, EpollChannelOption.IP_FREEBIND, EpollChannelOption.IP_TRANSPARENT,
                EpollChannelOption.IP_RECVORIGDSTADDR, EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE);
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_BROADCAST) {
            return (T) Boolean.valueOf(isBroadcast());
        }
        if (option == ChannelOption.SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == ChannelOption.SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == ChannelOption.SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == ChannelOption.IP_MULTICAST_LOOP_DISABLED) {
            return (T) Boolean.valueOf(isLoopbackModeDisabled());
        }
        if (option == ChannelOption.IP_MULTICAST_ADDR) {
            return (T) getInterface();
        }
        if (option == ChannelOption.IP_MULTICAST_IF) {
            return (T) getNetworkInterface();
        }
        if (option == ChannelOption.IP_MULTICAST_TTL) {
            return (T) Integer.valueOf(getTimeToLive());
        }
        if (option == ChannelOption.IP_TOS) {
            return (T) Integer.valueOf(getTrafficClass());
        }
        if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            return (T) Boolean.valueOf(activeOnOpen);
        }
        if (option == EpollChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == EpollChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == EpollChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
        }
        if (option == EpollChannelOption.IP_RECVORIGDSTADDR) {
            return (T) Boolean.valueOf(isIpRecvOrigDestAddr());
        }
        if (option == EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            return (T) Integer.valueOf(getMaxDatagramPayloadSize());
        }
        return super.getOption(option);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == ChannelOption.SO_BROADCAST) {
            setBroadcast((Boolean) value);
        } else if (option == ChannelOption.SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == ChannelOption.SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == ChannelOption.SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == ChannelOption.IP_MULTICAST_LOOP_DISABLED) {
            setLoopbackModeDisabled((Boolean) value);
        } else if (option == ChannelOption.IP_MULTICAST_ADDR) {
            setInterface((InetAddress) value);
        } else if (option == ChannelOption.IP_MULTICAST_IF) {
            setNetworkInterface((NetworkInterface) value);
        } else if (option == ChannelOption.IP_MULTICAST_TTL) {
            setTimeToLive((Integer) value);
        } else if (option == ChannelOption.IP_TOS) {
            setTrafficClass((Integer) value);
        } else if (option == ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
            setActiveOnOpen((Boolean) value);
        } else if (option == EpollChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == EpollChannelOption.IP_FREEBIND) {
            setFreeBind((Boolean) value);
        } else if (option == EpollChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == EpollChannelOption.IP_RECVORIGDSTADDR) {
            setIpRecvOrigDestAddr((Boolean) value);
        } else if (option == EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            setMaxDatagramPayloadSize((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (channel.isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    boolean getActiveOnOpen() {
        return activeOnOpen;
    }

    @Override
    public EpollDatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    @Deprecated
    public EpollDatagramChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public EpollDatagramChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollDatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public EpollDatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((EpollDatagramChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((EpollDatagramChannel) channel).socket.setSendBufferSize(sendBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return ((EpollDatagramChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((EpollDatagramChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return ((EpollDatagramChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setTrafficClass(int trafficClass) {
        try {
            ((EpollDatagramChannel) channel).socket.setTrafficClass(trafficClass);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return ((EpollDatagramChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((EpollDatagramChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isBroadcast() {
        try {
            return ((EpollDatagramChannel) channel).socket.isBroadcast();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setBroadcast(boolean broadcast) {
        try {
            ((EpollDatagramChannel) channel).socket.setBroadcast(broadcast);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        try {
            return ((EpollDatagramChannel) channel).socket.isLoopbackModeDisabled();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public DatagramChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        try {
            ((EpollDatagramChannel) channel).socket.setLoopbackModeDisabled(loopbackModeDisabled);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTimeToLive() {
        try {
            return ((EpollDatagramChannel) channel).socket.getTimeToLive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setTimeToLive(int ttl) {
        try {
            ((EpollDatagramChannel) channel).socket.setTimeToLive(ttl);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InetAddress getInterface() {
        try {
            return ((EpollDatagramChannel) channel).socket.getInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setInterface(InetAddress interfaceAddress) {
        try {
            ((EpollDatagramChannel) channel).socket.setInterface(interfaceAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        try {
            return ((EpollDatagramChannel) channel).socket.getNetworkInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
        try {
            EpollDatagramChannel datagramChannel = (EpollDatagramChannel) channel;
            datagramChannel.socket.setNetworkInterface(networkInterface);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public EpollDatagramChannelConfig setEpollMode(EpollMode mode) {
        super.setEpollMode(mode);
        return this;
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    public boolean isReusePort() {
        try {
            return ((EpollDatagramChannel) channel).socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link EpollSocketChannel}s to the same port and so accept connections with multiple threads.
     *
     * Be aware this method needs be called before {@link EpollDatagramChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    public EpollDatagramChannelConfig setReusePort(boolean reusePort) {
        try {
            ((EpollDatagramChannel) channel).socket.setReusePort(reusePort);
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
            return ((EpollDatagramChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollDatagramChannelConfig setIpTransparent(boolean ipTransparent) {
        try {
            ((EpollDatagramChannel) channel).socket.setIpTransparent(ipTransparent);
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
            return ((EpollDatagramChannel) channel).socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public EpollDatagramChannelConfig setFreeBind(boolean freeBind) {
        try {
            ((EpollDatagramChannel) channel).socket.setIpFreeBind(freeBind);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_RECVORIGDSTADDR</a> is
     * enabled, {@code false} otherwise.
     */
    public boolean isIpRecvOrigDestAddr() {
        try {
            return ((EpollDatagramChannel) channel).socket.isIpRecvOrigDestAddr();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_RECVORIGDSTADDR</a> is
     * enabled, {@code false} for disable it. Default is disabled.
     */
    public EpollDatagramChannelConfig setIpRecvOrigDestAddr(boolean ipTransparent) {
        try {
            ((EpollDatagramChannel) channel).socket.setIpRecvOrigDestAddr(ipTransparent);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the maximum {@link io.netty.channel.socket.DatagramPacket} size. This will be used to determine if
     * {@code recvmmsg} should be used when reading from the underlying socket. When {@code recvmmsg} is used
     * we may be able to read multiple {@link io.netty.channel.socket.DatagramPacket}s with one syscall and so
     * greatly improve the performance. This number will be used to slice {@link ByteBuf}s returned by the used
     * {@link RecvByteBufAllocator}. You can use {@code 0} to disable the usage of recvmmsg, any other bigger value
     * will enable it.
     */
    public EpollDatagramChannelConfig setMaxDatagramPayloadSize(int maxDatagramSize) {
        this.maxDatagramSize = ObjectUtil.checkPositiveOrZero(maxDatagramSize, "maxDatagramSize");
        return this;
    }

    /**
     * Get the maximum {@link io.netty.channel.socket.DatagramPacket} size.
     */
    public int getMaxDatagramPayloadSize() {
        return maxDatagramSize;
    }
}
