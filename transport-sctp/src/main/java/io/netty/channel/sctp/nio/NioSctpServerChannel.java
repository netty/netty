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
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.sctp.DefaultSctpServerChannelConfig;
import io.netty.channel.sctp.SctpServerChannelConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link io.netty.channel.sctp.SctpServerChannel} implementation which use non-blocking mode to accept new
 * connections and create the {@link NioSctpChannel} for them.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class NioSctpServerChannel extends AbstractNioMessageChannel
        implements io.netty.channel.sctp.SctpServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private static SctpServerChannel newSocket() {
        try {
            return SctpServerChannel.open();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }
    }

    private final SctpServerChannelConfig config;

    /**
     * Create a new instance
     */
    public NioSctpServerChannel() {
        super(null, newSocket(), SelectionKey.OP_ACCEPT);
        config = new NioSctpServerChannelConfig(this, javaChannel());
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
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
    public SctpServerChannelConfig config() {
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
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress, config.getBacklog());
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SctpChannel ch = javaChannel().accept();
        if (ch == null) {
            return 0;
        }
        buf.add(new NioSctpChannel(this, ch));
        return 1;
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
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
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
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
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    private final class NioSctpServerChannelConfig extends DefaultSctpServerChannelConfig {
        private NioSctpServerChannelConfig(NioSctpServerChannel channel, SctpServerChannel javaChannel) {
            super(channel, javaChannel);
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }
    }
}
