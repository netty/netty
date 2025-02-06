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

package io.netty.channel.socket.nio;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.ServerChannelRecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;

import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;


/**
 * A {@link io.netty.channel.ServerChannel} implementation which uses
 * NIO selector based implementation to support UNIX Domain Sockets. This is only supported when using Java 16+.
 */
public final class NioServerDomainSocketChannel extends AbstractNioMessageChannel
        implements io.netty.channel.ServerChannel {
    private static final Method OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY =
            SelectorProviderUtil.findOpenMethod("openServerSocketChannel");
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioServerDomainSocketChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private final NioDomainServerSocketChannelConfig config;
    private volatile boolean bound;

    // Package-private for testing.
    static ServerSocketChannel newChannel(SelectorProvider provider) {
        if (PlatformDependent.javaVersion() < 16) {
            throw new UnsupportedOperationException("Only supported with Java 16+");
        }
        try {
            ServerSocketChannel channel =
                    SelectorProviderUtil.newDomainSocketChannel(OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY, provider);
            if (channel == null) {
                throw new ChannelException("Failed to open a socket.");
            }
            return channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    @Override
    protected ServerSocketChannel javaChannel() {
        return (ServerSocketChannel) super.javaChannel();
    }

    /**
     * Create a new instance
     */
    public NioServerDomainSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioServerDomainSocketChannel(SelectorProvider provider) {
        this(newChannel(provider));
    }

    /**
     * Create a new instance using the given {@link ServerSocketChannel}.
     */
    public NioServerDomainSocketChannel(ServerSocketChannel channel) {
        super(null, channel, SelectionKey.OP_ACCEPT);
        if (PlatformDependent.javaVersion() < 16) {
            throw new UnsupportedOperationException("Only supported with Java 16+");
        }
        config = new NioDomainServerSocketChannelConfig(this);
        try {
            // Check if we already have a local address bound.
            bound = channel.getLocalAddress() != null;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public boolean isActive() {
        // As java.nio.ServerSocketChannel.isBound() will continue to return true even after the channel was closed
        // we will also need to check if it is open.
        return isOpen() && bound;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress, config.getBacklog());
        bound = true;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (ch != null) {
                buf.add(new NioDomainSocketChannel(this, ch));
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

        return 0;
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doClose() throws Exception {
        // Obtain the localAddress before we close the channel so it will not return null if we did not retrieve
        // it before.
        SocketAddress local = localAddress();
        try {
            super.doClose();
        } finally {
            javaChannel().close();
            if (local != null) {
                NioDomainSocketUtil.deleteSocketFile(local);
            }
        }
    }

    @Override
    protected SocketAddress localAddress0() {
        // do not use unsafe which uses native transport (epoll or kqueue)
        // do not use javaChannel().socket() which is not nio
        try {
            return javaChannel().getLocalAddress();
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    private final class NioDomainServerSocketChannelConfig extends DefaultChannelConfig {

        private volatile int backlog = NetUtil.SOMAXCONN;

        private NioDomainServerSocketChannelConfig(NioServerDomainSocketChannel channel) {
            super(channel, new ServerChannelRecvByteBufAllocator());
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            List<ChannelOption<?>> options = new ArrayList<ChannelOption<?>>();
            options.add(SO_BACKLOG);
            for (ChannelOption<?> opt : NioChannelOption.getOptions(jdkChannel())) {
                options.add(opt);
            }
            return getOptions(super.getOptions(), options.toArray(new ChannelOption[0]));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option == SO_BACKLOG) {
                return (T) Integer.valueOf(getBacklog());
            }
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }

            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            if (option == SO_BACKLOG) {
                validate(option, value);
                setBacklog((Integer) value);
            } else if (option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            } else {
                return super.setOption(option, value);
            }

            return true;
        }

        private int getBacklog() {
            return backlog;
        }

        private NioDomainServerSocketChannelConfig setBacklog(int backlog) {
            checkPositiveOrZero(backlog, "backlog");
            this.backlog = backlog;
            return this;
        }

        private ServerSocketChannel jdkChannel() {
            return ((NioServerDomainSocketChannel) channel).javaChannel();
        }
    }

    // Override just to to be able to call directly via unit tests.
    @Override
    protected boolean closeOnReadError(Throwable cause) {
        return super.closeOnReadError(cause);
    }

    @Override
    protected boolean doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException();
    }
}
