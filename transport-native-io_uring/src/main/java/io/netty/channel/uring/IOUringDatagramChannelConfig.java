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
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.DatagramChannelConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;

public final class IOUringDatagramChannelConfig extends DefaultChannelConfig implements DatagramChannelConfig {
    private static final RecvByteBufAllocator DEFAULT_RCVBUF_ALLOCATOR = new FixedRecvByteBufAllocator(2048);
    private boolean activeOnOpen;

    IOUringDatagramChannelConfig(AbstractIOUringChannel channel) {
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
                IOUringChannelOption.SO_REUSEPORT, IOUringChannelOption.IP_FREEBIND,
                IOUringChannelOption.IP_TRANSPARENT);
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
        if (option == IOUringChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == IOUringChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == IOUringChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
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
        } else if (option == IOUringChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == IOUringChannelOption.IP_FREEBIND) {
            setFreeBind((Boolean) value);
        } else if (option == IOUringChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
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
    public IOUringDatagramChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    @Deprecated
    public IOUringDatagramChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public IOUringDatagramChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public IOUringDatagramChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public IOUringDatagramChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public int getSendBufferSize() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            ((AbstractIOUringChannel) channel).socket.setSendBufferSize(sendBufferSize);
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
    public IOUringDatagramChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractIOUringChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setTrafficClass(int trafficClass) {
        try {
            ((AbstractIOUringChannel) channel).socket.setTrafficClass(trafficClass);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
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
    public IOUringDatagramChannelConfig setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractIOUringChannel) channel).socket.setReuseAddress(reuseAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isBroadcast() {
        try {
            return ((AbstractIOUringChannel) channel).socket.isBroadcast();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setBroadcast(boolean broadcast) {
        try {
            ((AbstractIOUringChannel) channel).socket.setBroadcast(broadcast);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        try {
            return ((AbstractIOUringChannel) channel).socket.isLoopbackModeDisabled();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        try {
            ((AbstractIOUringChannel) channel).socket.setLoopbackModeDisabled(loopbackModeDisabled);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTimeToLive() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getTimeToLive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setTimeToLive(int ttl) {
        try {
            ((AbstractIOUringChannel) channel).socket.setTimeToLive(ttl);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InetAddress getInterface() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setInterface(InetAddress interfaceAddress) {
        try {
            ((AbstractIOUringChannel) channel).socket.setInterface(interfaceAddress);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        try {
            return ((AbstractIOUringChannel) channel).socket.getNetworkInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public IOUringDatagramChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
        try {
            ((AbstractIOUringChannel) channel).socket.setNetworkInterface(networkInterface);
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
            return ((AbstractIOUringChannel) channel).socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link io.netty.channel.socket.DatagramChannel}s to the same port and so receive datagrams with multiple threads.
     *
     * Be aware this method needs be called before
     * {@link io.netty.channel.socket.DatagramChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    public IOUringDatagramChannelConfig setReusePort(boolean reusePort) {
        try {
            ((AbstractIOUringChannel) channel).socket.setReusePort(reusePort);
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
            return ((AbstractIOUringChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public IOUringDatagramChannelConfig setIpTransparent(boolean ipTransparent) {
        try {
            ((AbstractIOUringChannel) channel).socket.setIpTransparent(ipTransparent);
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
            return ((AbstractIOUringChannel) channel).socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="http://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    public IOUringDatagramChannelConfig setFreeBind(boolean freeBind) {
        try {
            ((AbstractIOUringChannel) channel).socket.setIpFreeBind(freeBind);
            return this;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
