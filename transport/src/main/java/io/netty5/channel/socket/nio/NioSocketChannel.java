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
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.nio.AbstractNioByteChannel;
import io.netty5.channel.socket.SocketChannelWriteHandleFactory;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SocketUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;

import static io.netty5.channel.socket.nio.NioChannelUtil.isDomainSocket;
import static io.netty5.channel.socket.nio.NioChannelUtil.toDomainSocketAddress;
import static io.netty5.channel.socket.nio.NioChannelUtil.toJdkFamily;
import static io.netty5.channel.socket.nio.NioChannelUtil.toUnixDomainSocketAddress;

/**
 * {@link io.netty5.channel.socket.SocketChannel} which uses NIO selector based implementation.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link io.netty5.channel.socket.SocketChannel},
 * {@link NioSocketChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>{@link ChannelOption}</th>
 * <th>{@code INET}</th>
 * <th>{@code INET6}</th>
 * <th>{@code UNIX</th>
 * </tr><tr>
 * <td>{@link NioChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr>
 * </table>
 */
public class NioSocketChannel
        extends AbstractNioByteChannel<NioServerSocketChannel, SocketAddress, SocketAddress>
        implements io.netty5.channel.socket.SocketChannel {
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static final Method OPEN_SOCKET_CHANNEL_WITH_FAMILY =
            NioChannelUtil.findOpenMethod("openSocketChannel");

    private static SocketChannel newChannel(SelectorProvider provider, ProtocolFamily family) {
        try {
            SocketChannel channel = NioChannelUtil.newChannel(OPEN_SOCKET_CHANNEL_WITH_FAMILY, provider, family);
            return channel == null ? provider.openSocketChannel() : channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final ProtocolFamily family;
    private final ByteBufferCollector collector = new ByteBufferCollector();
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
        this(null, eventLoop, newChannel(provider, toJdkFamily(family)), family);
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
        super(parent, eventLoop, new SocketChannelWriteHandleFactory(Integer.MAX_VALUE), socket);
        this.family = toJdkFamily(family);
        // Enable TCP_NODELAY by default if possible.
        if (!isDomainSocket(family) && PlatformDependent.canEnableTcpNoDelayByDefault()) {
            try {
                javaChannel().setOption(StandardSocketOptions.TCP_NODELAY, true);
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
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer data) throws Exception {
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
        return buffer.transferFrom(javaChannel(), buffer.writableBytes());
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

    @Override
    protected void doWriteNow(WriteSink writeSink)
            throws Exception {
        SocketChannel ch = javaChannel();
        // Ensure the pending writes are made of Buffers only.
        collector.prepare(1024, writeSink.estimatedMaxBytesPerGatheringWrite());
        writeSink.forEach(collector);
        ByteBuffer[] nioBuffers = collector.nioBuffers();
        int nioBufferCnt = collector.nioBufferCount();

        // Always use nioBuffers() to workaround data-corruption.
        // See https://github.com/netty/netty/issues/2761
        switch (nioBufferCnt) {
            case 0:
                // We have something else beside ByteBuffers to write so fallback to normal writes.
                super.doWriteNow(writeSink);
                return;
            case 1: {
                // Only one ByteBuf so use non-gathering write
                // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                // to check if the total size of all the buffers is non-zero.
                ByteBuffer buffer = nioBuffers[0];
                int attemptedBytes = buffer.remaining();
                final int localWrittenBytes = ch.write(buffer);
                writeSink.complete(attemptedBytes, localWrittenBytes, -1, localWrittenBytes > 0);
                return;
            }
            default: {
                // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                // to check if the total size of all the buffers is non-zero.
                // We limit the max amount to int above so cast is safe
                long attemptedBytes = collector.nioBufferSize();
                final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                writeSink.complete(attemptedBytes, localWrittenBytes, -1, localWrittenBytes > 0);
            }
        }
    }

    @Override
    protected Future<Executor> prepareToClose() {
        if (!isDomainSocket(family)) {
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
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.getOption(javaChannel(), socketOption);
        } else {
            return super.getExtendedOption(option);
        }
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            NioChannelOption.setOption(javaChannel(), socketOption, value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        SocketOption<?> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.isOptionSupported(javaChannel(), socketOption);
        }
        return super.isExtendedOptionSupported(option);
    }
}
