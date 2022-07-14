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
package io.netty5.channel.socket.nio;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.nio.AbstractNioByteChannel;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SocketUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.netty5.channel.ChannelOption.IP_TOS;
import static io.netty5.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty5.channel.ChannelOption.SO_LINGER;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.ChannelOption.TCP_NODELAY;
import static io.netty5.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;
import static io.netty5.channel.socket.nio.NioChannelUtil.isDomainSocket;
import static io.netty5.channel.socket.nio.NioChannelUtil.toDomainSocketAddress;
import static io.netty5.channel.socket.nio.NioChannelUtil.toUnixDomainSocketAddress;

/**
 * {@link io.netty5.channel.socket.SocketChannel} which uses NIO selector based implementation.
 */
public class NioSocketChannel
        extends AbstractNioByteChannel<NioServerSocketChannel, SocketAddress, SocketAddress>
        implements io.netty5.channel.socket.SocketChannel {
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static final Method OPEN_SOCKET_CHANNEL_WITH_FAMILY =
            NioChannelUtil.findOpenMethod("openSocketChannel");

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();

    private static SocketChannel newChannel(SelectorProvider provider, ProtocolFamily family) {
        try {
            SocketChannel channel = NioChannelUtil.newChannel(OPEN_SOCKET_CHANNEL_WITH_FAMILY, provider, family);
            return channel == null ? provider.openSocketChannel() : channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final ProtocolFamily family;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    /**
     * Create a new instance
     */
    public NioSocketChannel(EventLoop eventLoop) {
        this(eventLoop, DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, provider, null);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and protocol family (supported only since JDK 15).
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider, ProtocolFamily family) {
        this(null, eventLoop, newChannel(provider, family), family);
    }

    /**
     * Create a new instance using the given {@link SocketChannel}.
     */
    public NioSocketChannel(EventLoop eventLoop, SocketChannel socket) {
        this(null, eventLoop, socket, null);
    }

    /**
     * Create a new instance
     *
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param eventLoop the {@link EventLoop} to use for IO.
     * @param socket    the {@link SocketChannel} which will be used
     */
    public NioSocketChannel(NioServerSocketChannel parent, EventLoop eventLoop, SocketChannel socket) {
        this(parent, eventLoop, socket, null);
    }

    /**
     * Create a new instance
     *
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param eventLoop the {@link EventLoop} to use for IO.
     * @param socket    the {@link SocketChannel} which will be used
     * @param family    the {@link ProtocolFamily} that was used to create th {@link SocketChannel}
     */
    public NioSocketChannel(NioServerSocketChannel parent, EventLoop eventLoop, SocketChannel socket,
                            ProtocolFamily family) {
        super(parent, eventLoop, socket);
        this.family = family;
        // Enable TCP_NODELAY by default if possible.
        if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
            try {
                setTcpNoDelay(true);
            } catch (Exception e) {
                // Ignore.
            }
        }
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Outbound:
                return outputShutdown;
            case Inbound:
                return inputShutdown;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) throws Exception {
        switch (direction) {
            case Inbound:
                javaChannel().shutdownInput();
                inputShutdown = true;
                break;
            case Outbound:
                javaChannel().shutdownOutput();
                outputShutdown = true;
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            SocketAddress address = javaChannel().getLocalAddress();
            if (isDomainSocket(family)) {
                return toDomainSocketAddress(address);
            }
            return address;
        } catch (IOException e) {
            // Just return null
            return null;
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            SocketAddress address = javaChannel().getRemoteAddress();
            if (isDomainSocket(family)) {
                return toDomainSocketAddress(address);
            }
            return address;
        } catch (IOException e) {
            // Just return null
            return null;
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        if (isDomainSocket(family)) {
            localAddress = toUnixDomainSocketAddress(localAddress);
        }
        SocketUtils.bind(javaChannel(), localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            if (isDomainSocket(family)) {
                remoteAddress = toUnixDomainSocketAddress(remoteAddress);
            }
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) throws Exception {
        return javaChannel().finishConnect();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected int doReadBytes(Buffer buffer) throws Exception {
        final RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
        allocHandle.attemptedBytesRead(buffer.writableBytes());
        return buffer.transferFrom(javaChannel(), allocHandle.attemptedBytesRead());
    }

    @Override
    protected int doWriteBytes(Buffer buf) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        return buf.transferTo(javaChannel(), expectedWrittenBytes);
    }

    @Override
    protected long doWriteFileRegion(FileRegion region) throws Exception {
        final long position = region.transferred();
        return region.transferTo(javaChannel(), position);
    }

    private void adjustMaxBytesPerGatheringWrite(int attempted, int written, int oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.
        if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            setMaxBytesPerGatheringWrite(attempted >>> 1);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        SocketChannel ch = javaChannel();
        int writeSpinCount = getWriteSpinCount();
        do {
            if (in.isEmpty()) {
                // All written so clear OP_WRITE
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }

            // Ensure the pending writes are made of ByteBufs only.
            int maxBytesPerGatheringWrite = getMaxBytesPerGatheringWrite();
            ByteBuffer[] nioBuffers = in.nioBuffers(1024, maxBytesPerGatheringWrite);
            int nioBufferCnt = in.nioBufferCount();

            // Always use nioBuffers() to workaround data-corruption.
            // See https://github.com/netty/netty/issues/2761
            switch (nioBufferCnt) {
                case 0:
                    // We have something else beside ByteBuffers to write so fallback to normal writes.
                    writeSpinCount -= doWrite0(in);
                    break;
                case 1: {
                    // Only one ByteBuf so use non-gathering write
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    ByteBuffer buffer = nioBuffers[0];
                    int attemptedBytes = buffer.remaining();
                    final int localWrittenBytes = ch.write(buffer);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    adjustMaxBytesPerGatheringWrite(attemptedBytes, localWrittenBytes, maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
                default: {
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    // We limit the max amount to int above so cast is safe
                    long attemptedBytes = in.nioBufferSize();
                    final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    // Casting to int is safe because we limit the total amount of data in the nioBuffers to int above.
                    adjustMaxBytesPerGatheringWrite((int) attemptedBytes, (int) localWrittenBytes,
                            maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
            }
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected Future<Executor> prepareToClose() {
        try {
            if (javaChannel().isOpen() && getOption(ChannelOption.SO_LINGER) > 0) {
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
        if (option instanceof NioChannelOption) {
            return NioChannelOption.getOption(javaChannel(), (NioChannelOption<T>) option);
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
        } else if (option instanceof NioChannelOption) {
            NioChannelOption.setOption(javaChannel(), (NioChannelOption<T>) option, value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option instanceof NioChannelOption) {
            return NioChannelOption.isSupported(javaChannel(), (NioChannelOption<?>) option);
        }
        if (SUPPORTED_OPTIONS.contains(option)) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(
                ChannelOption.SO_RCVBUF, ChannelOption.SO_SNDBUF, ChannelOption.TCP_NODELAY,
                ChannelOption.SO_KEEPALIVE, ChannelOption.SO_REUSEADDR, ChannelOption.SO_LINGER,
                ChannelOption.IP_TOS);
    }

    private int getReceiveBufferSize() {
        try {
            return javaChannel().getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSendBufferSize() {
        try {
            return javaChannel().getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSoLinger() {
        try {
            return javaChannel().getOption(StandardSocketOptions.SO_LINGER);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return javaChannel().getOption(StandardSocketOptions.IP_TOS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isKeepAlive() {
        try {
            return javaChannel().getOption(StandardSocketOptions.SO_KEEPALIVE);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return javaChannel().getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoDelay() {
        try {
            return javaChannel().getOption(StandardSocketOptions.TCP_NODELAY);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            javaChannel().setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            javaChannel().setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            javaChannel().setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            javaChannel().setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public void setSoLinger(int soLinger) {
        try {
            javaChannel().setOption(StandardSocketOptions.SO_LINGER, soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            javaChannel().setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public void setTrafficClass(int trafficClass) {
        try {
            javaChannel().setOption(StandardSocketOptions.IP_TOS, trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected void autoReadCleared() {
        clearReadPending();
    }

    private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;

    void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
        this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
    }

    int getMaxBytesPerGatheringWrite() {
        return maxBytesPerGatheringWrite;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(newSendBufferSize);
        }
    }
}
