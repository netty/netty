/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp.nio;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.ServerChannelRecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.nio.NioIoHandle;
import io.netty.channel.nio.NioIoOps;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * {@link io.netty.channel.sctp.SctpServerChannel} implementation which use non-blocking mode to accept new
 * connections and create the {@link NioSctpChannel} for them.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class NioSctpServerChannel extends AbstractNioMessageChannel
        implements io.netty.channel.sctp.SctpServerChannel {

    private static SctpServerChannel newSocket() {
        try {
            return SctpServerChannel.open();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }
    }

    private final NioSctpServerChannelConfig config;
    private final EventLoopGroup childEventLoopGroup;
    /**
     * Create a new instance
     */
    public NioSctpServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, null, newSocket(), NioIoOps.ACCEPT, false);
        this.childEventLoopGroup =
                validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", NioIoHandle.class);
        config = new NioSctpServerChannelConfig(this, javaChannel());
    }

    @Override
    public ServerChannel read() {
        super.read();
        return this;
    }

    @Override
    public ServerChannel flush() {
        super.flush();
        return this;
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public EventLoopGroup childEventExecutorGroup() {
        return childEventLoopGroup;
    }

    @Override
    public Set<InetSocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
            final Set<InetSocketAddress> addresses = new LinkedHashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return isOpen() && !allLocalAddresses().isEmpty();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return null;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    protected SctpServerChannel javaChannel() {
        return (SctpServerChannel) super.javaChannel();
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            Iterator<SocketAddress> i = javaChannel().getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
        try {
            javaChannel().bind(localAddress, config.getBacklog());
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    protected void doClose(ChannelPromise promise) {
        try {
            javaChannel().close();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SctpChannel ch = javaChannel().accept();
        if (ch == null) {
            return 0;
        }
        buf.add(new NioSctpChannel(childEventLoopGroup.next(), this, ch));
        return 1;
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (executor().inEventLoop()) {
            try {
                javaChannel().bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            executor().execute(new Runnable() {
                @Override
                public void run() {
                    bindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        return unbindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture unbindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (executor().inEventLoop()) {
            try {
                javaChannel().unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            executor().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    // Unnecessary stuff
    @Override
    protected boolean doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect(ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    private final class NioSctpServerChannelConfig extends DefaultChannelConfig {

        private final SctpServerChannel javaChannel;
        private volatile int backlog = NetUtil.SOMAXCONN;

        private NioSctpServerChannelConfig(NioSctpServerChannel channel, SctpServerChannel javaChannel) {
            super(channel, new ServerChannelRecvByteBufAllocator());
            this.javaChannel = ObjectUtil.checkNotNull(javaChannel, "javaChannel");
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(
                    super.getOptions(),
                    ChannelOption.SO_RCVBUF, ChannelOption.SO_SNDBUF, ChannelOption.SO_BACKLOG,
                    SctpChannelOption.SCTP_INIT_MAXSTREAMS);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option == ChannelOption.SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == ChannelOption.SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == ChannelOption.SO_BACKLOG) {
                return (T) Integer.valueOf(getBacklog());
            }
            if (option == SctpChannelOption.SCTP_INIT_MAXSTREAMS) {
                return (T) getInitMaxStreams();
            }
            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);

            if (option == ChannelOption.SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == ChannelOption.SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == SctpChannelOption.SO_BACKLOG) {
                setBacklog((Integer) value);
            } else if (option == SctpChannelOption.SCTP_INIT_MAXSTREAMS) {
                setInitMaxStreams((SctpStandardSocketOptions.InitMaxStreams) value);
            } else {
                return super.setOption(option, value);
            }

            return true;
        }

        private int getSendBufferSize() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SO_SNDBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        private void setSendBufferSize(int sendBufferSize) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SO_SNDBUF, sendBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        private int getReceiveBufferSize() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SO_RCVBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        private void setReceiveBufferSize(int receiveBufferSize) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        SctpStandardSocketOptions.InitMaxStreams getInitMaxStreams() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        private void setInitMaxStreams(SctpStandardSocketOptions.InitMaxStreams initMaxStreams) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS, initMaxStreams);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        int getBacklog() {
            return backlog;
        }

        private void setBacklog(int backlog) {
            checkPositiveOrZero(backlog, "backlog");
            this.backlog = backlog;
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }
    }
}
