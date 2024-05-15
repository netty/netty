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

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannelReadHandleFactory;
import io.netty5.channel.ServerChannelWriteHandleFactory;
import io.netty5.channel.nio.AbstractNioMessageChannel;
import io.netty5.channel.nio.NioIoHandle;
import io.netty5.channel.nio.NioIoOps;
import io.netty5.util.NetUtil;
import io.netty5.util.internal.SocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import static io.netty5.channel.ChannelOption.SO_BACKLOG;
import static io.netty5.channel.socket.nio.NioChannelUtil.isDomainSocket;
import static io.netty5.channel.socket.nio.NioChannelUtil.toDomainSocketAddress;
import static io.netty5.channel.socket.nio.NioChannelUtil.toJdkFamily;
import static io.netty5.channel.socket.nio.NioChannelUtil.toUnixDomainSocketAddress;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * A {@link io.netty5.channel.socket.ServerSocketChannel} implementation which uses
 * NIO selector based implementation to accept new connections.
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
 * <th>{@code UNIX}</th>
 * </tr><tr>
 * <td>{@link NioChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr>
 * </table>
 */
public class NioServerSocketChannel extends AbstractNioMessageChannel<Channel, SocketAddress, SocketAddress>
                             implements io.netty5.channel.socket.ServerSocketChannel {

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static final Logger logger = LoggerFactory.getLogger(NioServerSocketChannel.class);

    private static final Method OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY =
            NioChannelUtil.findOpenMethod("openServerSocketChannel");

    private static ServerSocketChannel newChannel(SelectorProvider provider, ProtocolFamily family) {
        try {
            ServerSocketChannel channel =
                    NioChannelUtil.newChannel(OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY, provider, family);
            return channel == null ? provider.openServerSocketChannel() : channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final ProtocolFamily family;

    private final EventLoopGroup childEventLoopGroup;

    private volatile int backlog = NetUtil.SOMAXCONN;

    private volatile boolean bound;

    /**
     * Create a new instance
     */
    public NioServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        this(eventLoop, childEventLoopGroup, DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, SelectorProvider provider) {
        this(eventLoop, childEventLoopGroup, provider, null);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and protocol family (supported only since JDK 15).
     */
    public NioServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                  SelectorProvider provider, ProtocolFamily protocolFamily) {
        this(eventLoop, childEventLoopGroup, newChannel(provider, toJdkFamily(protocolFamily)), protocolFamily);
    }

    /**
     * Create a new instance using the given {@link ServerSocketChannel}.
     */
    public NioServerSocketChannel(
            EventLoop eventLoop, EventLoopGroup childEventLoopGroup, ServerSocketChannel channel) {
        this(eventLoop, childEventLoopGroup, channel, null);
    }

    /**
     * Create a new instance using the given {@link ServerSocketChannel}.
     */
    public NioServerSocketChannel(
            EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
            ServerSocketChannel channel, ProtocolFamily family) {
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(),
                new ServerChannelWriteHandleFactory(), channel, NioIoOps.ACCEPT);
        this.family = toJdkFamily(family);
        this.childEventLoopGroup = validateEventLoopGroup(
                childEventLoopGroup, "childEventLoopGroup", NioIoHandle.class);
    }

    @Override
    public EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @Override
    public boolean isActive() {
        // As java.nio.ServerSocketChannel.isBound() will continue to return true even after the channel was closed
        // we will also need to check if it is open.
        return isOpen() && bound;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.getOption(javaChannel(), socketOption);
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else {
            SocketOption<T> socketOption = NioChannelOption.toSocketOption(option);
            if (socketOption != null) {
                NioChannelOption.setOption(javaChannel(), socketOption, value);
            } else {
                super.setExtendedOption(option, value);
            }
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option == SO_BACKLOG) {
            return true;
        }
        SocketOption<?> socketOption = NioChannelOption.toSocketOption(option);
        if (socketOption != null) {
            return NioChannelOption.isOptionSupported(javaChannel(), socketOption);
        }
        return super.isExtendedOptionSupported(option);
    }

    private int getBacklog() {
        return backlog;
    }

    private void setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
    }

    @Override
    protected ServerSocketChannel javaChannel() {
        return (ServerSocketChannel) super.javaChannel();
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            SocketAddress address = javaChannel().getLocalAddress();
            if (isDomainSocket(family)) {
                address = toDomainSocketAddress(address);
            }
            return address;
        } catch (IOException e) {
            // Just return null
            return null;
        }
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        return !isActive();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (isDomainSocket(family)) {
            localAddress = toUnixDomainSocketAddress(localAddress);
        }
        javaChannel().bind(localAddress, getBacklog());
        bound = true;
    }

    @Override
    protected int doReadMessages(ReadSink readSink) throws Exception {
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (ch != null) {
                readSink.processRead(0, 0,
                        new NioSocketChannel(this, childEventLoopGroup().next(), ch, family));
                return 1;
            }
        } catch (Throwable t) {
            logger.warn("Failed to create a new channel from an accepted socket.", t);

            try {
                ch.close();
            } catch (Throwable t2) {
                logger.warn("Failed to close a socket.", t2);
            }
        }
        readSink.processRead(0, 0, null);
        return 0;
    }

    // Unnecessary stuff
    @Override
    protected boolean doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doWriteNow(WriteSink writeHandle) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }
}
