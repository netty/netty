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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;

final class IoUringDatagramChannelConfig extends IoUringChannelConfig {
    private static final RecvByteBufAllocator DEFAULT_RCVBUF_ALLOCATOR = new FixedRecvByteBufAllocator(2048);
    private boolean activeOnOpen;
    private volatile int maxDatagramSize;

    IoUringDatagramChannelConfig(AbstractIoUringChannel channel) {
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
                IoUringChannelOption.SO_REUSEPORT, IoUringChannelOption.IP_FREEBIND,
                IoUringChannelOption.IP_TRANSPARENT, IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE,
                IoUringChannelOption.IP_MULTICAST_ALL);
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
        if (option == IoUringChannelOption.SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == IoUringChannelOption.IP_TRANSPARENT) {
            return (T) Boolean.valueOf(isIpTransparent());
        }
        if (option == IoUringChannelOption.IP_FREEBIND) {
            return (T) Boolean.valueOf(isFreeBind());
        }
        if (option == IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            return (T) Integer.valueOf(getMaxDatagramPayloadSize());
        }
        if (option == IoUringChannelOption.IP_MULTICAST_ALL) {
            return (T) Boolean.valueOf(isIpMulticastAll());
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
        } else if (option == IoUringChannelOption.SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == IoUringChannelOption.IP_FREEBIND) {
            setFreeBind((Boolean) value);
        } else if (option == IoUringChannelOption.IP_TRANSPARENT) {
            setIpTransparent((Boolean) value);
        } else if (option == IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE) {
            setMaxDatagramPayloadSize((Integer) value);
        } else if (option == IoUringChannelOption.IP_MULTICAST_ALL) {
            setIpMulticastAll((Boolean) value);
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

    private int getSendBufferSize() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            ((AbstractIoUringChannel) channel).socket.setSendBufferSize(sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractIoUringChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            ((AbstractIoUringChannel) channel).socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return ((AbstractIoUringChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractIoUringChannel) channel).socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isBroadcast() {
        try {
            return ((AbstractIoUringChannel) channel).socket.isBroadcast();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setBroadcast(boolean broadcast) {
        try {
            ((AbstractIoUringChannel) channel).socket.setBroadcast(broadcast);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isLoopbackModeDisabled() {
        try {
            return ((AbstractIoUringChannel) channel).socket.isLoopbackModeDisabled();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        try {
            ((AbstractIoUringChannel) channel).socket.setLoopbackModeDisabled(loopbackModeDisabled);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTimeToLive() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getTimeToLive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTimeToLive(int ttl) {
        try {
            ((AbstractIoUringChannel) channel).socket.setTimeToLive(ttl);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private InetAddress getInterface() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setInterface(InetAddress interfaceAddress) {
        try {
            ((AbstractIoUringChannel) channel).socket.setInterface(interfaceAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private NetworkInterface getNetworkInterface() {
        try {
            return ((AbstractIoUringChannel) channel).socket.getNetworkInterface();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setNetworkInterface(NetworkInterface networkInterface) {
        try {
            ((AbstractIoUringChannel) channel).socket.setNetworkInterface(networkInterface);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    private boolean isReusePort() {
        try {
            return ((AbstractIoUringChannel) channel).socket.isReusePort();
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
    private void setReusePort(boolean reusePort) {
        try {
            ((AbstractIoUringChannel) channel).socket.setReusePort(reusePort);
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
            return ((AbstractIoUringChannel) channel).socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    private void setIpTransparent(boolean ipTransparent) {
        try {
            ((AbstractIoUringChannel) channel).socket.setIpTransparent(ipTransparent);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} otherwise.
     */
    private boolean isFreeBind() {
        try {
            return ((AbstractIoUringChannel) channel).socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_FREEBIND</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    private void setFreeBind(boolean freeBind) {
        try {
            ((AbstractIoUringChannel) channel).socket.setIpFreeBind(freeBind);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the maximum {@link io.netty.channel.socket.DatagramPacket} size. This will be used to determine if
     * a batch of {@code IORING_IO_RECVMSG} should be used when reading from the underlying socket.
     * When batched {@code recvmmsg} is used
     * we may be able to read multiple {@link io.netty.channel.socket.DatagramPacket}s with one syscall and so
     * greatly improve the performance. This number will be used to slice {@link ByteBuf}s returned by the used
     * {@link RecvByteBufAllocator}. You can use {@code 0} to disable the usage of batching, any other bigger value
     * will enable it.
     */
    private void setMaxDatagramPayloadSize(int maxDatagramSize) {
        this.maxDatagramSize = ObjectUtil.checkPositiveOrZero(maxDatagramSize, "maxDatagramSize");
    }

    /**
     * Get the maximum {@link io.netty.channel.socket.DatagramPacket} size.
     */
    int getMaxDatagramPayloadSize() {
        return maxDatagramSize;
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_MULTICAST_ALL</a> is
     * enabled (or IPV6_MULTICAST_ALL for IPV6), {@code false} for disable it. Default is enabled.
     */
    private void setIpMulticastAll(boolean multicastAll) {
        try {
            ((IoUringDatagramChannel) channel).socket.setIpMulticastAll(multicastAll);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_MULTICAST_ALL</a> (or
     * IPV6_MULTICAST_ALL for IPV6) is enabled, {@code false} otherwise.
     */
    private boolean isIpMulticastAll() {
        try {
            return ((IoUringDatagramChannel) channel).socket.isIpMulticastAll();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
