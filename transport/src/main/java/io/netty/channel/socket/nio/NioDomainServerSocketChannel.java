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

package io.netty.channel.socket.nio;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.socket.DefaultDomainServerSocketChannelConfig;
import io.netty.channel.socket.UnixDomainProtocolFamily;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;

import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class NioDomainServerSocketChannel extends AbstractNioMessageChannel implements io.netty.channel.ServerChannel {
    private static final Method OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY =
            SelectorProviderUtil.findOpenMethod("openServerSocketChannel");
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDomainServerSocketChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
    private final ChannelConfig config;
    private AtomicBoolean bound;
    private static ServerSocketChannel newChannel(SelectorProvider provider, UnixDomainProtocolFamily family) {
        try {
//            ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            ServerSocketChannel channel =
                    SelectorProviderUtil.newChannel(OPEN_SERVER_SOCKET_CHANNEL_WITH_FAMILY, provider, family);
            return channel == null ? provider.openServerSocketChannel(StandardProtocolFamily.UNIX) : channel;
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
    public NioDomainServerSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioDomainServerSocketChannel(SelectorProvider provider) {
        this(provider, UnixDomainProtocolFamily.Unix);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and protocol family (supported only since JDK 15).
     */
    public NioDomainServerSocketChannel(SelectorProvider provider, UnixDomainProtocolFamily family) {
        this(newChannel(provider, family));
    }

    /**
     * Create a new instance using the given {@link ServerSocketChannel}.
     */
    public NioDomainServerSocketChannel(ServerSocketChannel channel) {
        super(null, channel, SelectionKey.OP_ACCEPT);
        config = new NioDomainServerSocketChannelConfig(this);
        bound = new AtomicBoolean(false);
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
        return isOpen() && bound.get();
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (PlatformDependent.javaVersion() >= 7) {
            System.out.println("binding with backlog=" + config.getOption(ChannelOption.SO_BACKLOG));
            javaChannel().bind(localAddress, config.getOption(ChannelOption.SO_BACKLOG));
            bound.set(true);
        } else {
            throw new UnsupportedOperationException("only NIO is supported");
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        System.out.println("domain server socket doAccept()");
        SocketChannel ch = javaChannel().accept();
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
        javaChannel().close();
    }

    @Override
    protected SocketAddress localAddress0() {
        // do not use unsafe which uses native transport (epoll or kqueue)
        // do not use javaChannel().socket() which is not nio
        try {
            return javaChannel().getLocalAddress();
        } catch (IOException ex) {
            logger.warn(ex);
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    private final class NioDomainServerSocketChannelConfig extends DefaultDomainServerSocketChannelConfig {

        private NioDomainServerSocketChannelConfig(NioDomainServerSocketChannel channel) {
            super(channel);
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            System.out.println("NioDomainServerSocketChannel: setOption= " + option);
            if (PlatformDependent.javaVersion() >= 7 && option instanceof NioChannelOption) {
                //JDK16 defaultUnixDomainOptions only include StandardSocketOptions.SO_RCVBUF
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            }
            // channelOptions but not SO_* options
            super.setOption(option, value);
            return true;
        }

        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (PlatformDependent.javaVersion() >= 7 && option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }

            return super.getOption(option);
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            if (PlatformDependent.javaVersion() >= 7) {
                return getOptions(NioChannelOption.getOptions(jdkChannel()));
            }
            logger.warn("jdk < 7 or non-NioChannelOption are not supported");
            return new HashMap();
        }

        private Map<ChannelOption<?>, Object> getOptions(ChannelOption<?>... options) {

            Map<ChannelOption<?>, Object> result = new IdentityHashMap<ChannelOption<?>, Object>();

            for (ChannelOption<?> o: options) {
                result.put(o, getOption(o));
            }

            return result;
        }

        private ServerSocketChannel jdkChannel() {
            return ((NioDomainServerSocketChannel) channel).javaChannel();
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
