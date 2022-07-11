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
package io.netty5.channel.kqueue;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.netty5.channel.ChannelOption.IP_TOS;
import static io.netty5.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty5.channel.ChannelOption.SO_LINGER;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.ChannelOption.TCP_NODELAY;
import static io.netty5.channel.kqueue.KQueueChannelOption.SO_SNDLOWAT;
import static io.netty5.channel.kqueue.KQueueChannelOption.TCP_NOPUSH;

/**
 * {@link SocketChannel} implementation that uses KQueue.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannel} and {@link UnixChannel},
 * {@link KQueueSocketChannel} allows the following options in the option map:
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#SO_SNDLOWAT}</td>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#TCP_NOPUSH}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_FASTOPEN_CONNECT}</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class KQueueSocketChannel
        extends AbstractKQueueStreamChannel<KQueueServerSocketChannel, SocketAddress, SocketAddress>
        implements SocketChannel {
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private volatile boolean tcpFastopen;

    public KQueueSocketChannel(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    public KQueueSocketChannel(EventLoop eventLoop, ProtocolFamily protocol) {
        super(null, eventLoop, BsdSocket.newSocketStream(protocol), false);
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
        calculateMaxBytesPerGatheringWrite();
    }

    public KQueueSocketChannel(EventLoop eventLoop, int fd) {
        super(eventLoop, new BsdSocket(fd));
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
        calculateMaxBytesPerGatheringWrite();
    }

    KQueueSocketChannel(KQueueServerSocketChannel parent, EventLoop eventLoop,
                        BsdSocket fd, InetSocketAddress remoteAddress) {
        super(parent, eventLoop, fd, remoteAddress);
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
        calculateMaxBytesPerGatheringWrite();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
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
        if (option == SO_SNDLOWAT) {
            return (T) Integer.valueOf(getSndLowAt());
        }
        if (option == TCP_NOPUSH) {
            return (T) Boolean.valueOf(isTcpNoPush());
        }
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            return (T) Boolean.valueOf(isTcpFastOpenConnect());
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
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
        } else if (option == SO_SNDLOWAT) {
            setSndLowAt((Integer) value);
        } else if (option == TCP_NOPUSH) {
            setTcpNoPush((Boolean) value);
        } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            setTcpFastOpenConnect((Boolean) value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (SUPPORTED_OPTIONS.contains(option)) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_SNDBUF, TCP_NODELAY,
                SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS, SO_SNDLOWAT, TCP_NOPUSH,
                ChannelOption.TCP_FASTOPEN_CONNECT);
    }

    private int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSoLinger() {
        try {
            return socket.getSoLinger();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isKeepAlive() {
        try {
            return socket.isKeepAlive();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoDelay() {
        try {
            return socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSndLowAt() {
        try {
            return socket.getSndLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSndLowAt(int sndLowAt)  {
        try {
            socket.setSndLowAt(sndLowAt);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoPush() {
        try {
            return socket.isTcpNoPush();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoPush(boolean tcpNoPush)  {
        try {
            socket.setTcpNoPush(tcpNoPush);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            socket.setKeepAlive(keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
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
    private boolean isTcpFastOpenConnect() {
        return tcpFastopen;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(getSendBufferSize() << 1);
        }
    }
    @Override
    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (isTcpFastOpenConnect()) {
            ChannelOutboundBuffer outbound = outboundBuffer();
            outbound.addFlush();
            Object curr;
            if ((curr = outbound.current()) instanceof Buffer) {
                Buffer initialData = (Buffer) curr;
                // Don't bother with TCP FastOpen if we don't have any initial data to send anyway.
                if (initialData.readableBytes() > 0) {
                    IovArray iov = new IovArray();
                    try {
                        initialData.forEachReadable(0, iov);
                        int bytesSent = socket.connectx(
                                (InetSocketAddress) localAddress, (InetSocketAddress) remoteAddress, iov, true);
                        writeFilter(true);
                        outbound.removeBytes(Math.abs(bytesSent));
                        // The `connectx` method returns a negative number if connection is in-progress.
                        // So we should return `true` to indicate that connection was established, if it's positive.
                        return bytesSent > 0;
                    } finally {
                        iov.release();
                    }
                }
            }
        }
        return super.doConnect0(remoteAddress, localAddress);
    }

    @Override
    protected Future<Executor> prepareToClose() {
        try {
            // Check isOpen() first as otherwise it will throw a RuntimeException
            // when call getSoLinger() as the fd is not valid anymore.
            if (isOpen() && getSoLinger() > 0) {
                // We need to cancel this key of the channel so we may not end up in a eventloop spin
                // because we try to read or write until the actual close happens which may be later due
                // SO_LINGER handling.
                // See https://github.com/netty/netty/issues/4449
                return executor().deregisterForIo(this).map(v -> GlobalEventExecutor.INSTANCE);
            }
        } catch (Throwable ignore) {
            // Ignore the error as the underlying channel may be closed in the meantime and so
            // getSoLinger() may produce an exception. In this case we just return null.
            // See https://github.com/netty/netty/issues/4449
        }
        return null;
    }
}
