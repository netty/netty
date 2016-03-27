/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.sctp.oio;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.oio.AbstractOioMessageChannel;
import io.netty.channel.sctp.DefaultSctpServerChannelConfig;
import io.netty.channel.sctp.SctpServerChannelConfig;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link io.netty.channel.sctp.SctpServerChannel} implementation which use blocking mode to accept new
 * connections and create the {@link OioSctpChannel} for them.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class OioSctpServerChannel extends AbstractOioMessageChannel
        implements io.netty.channel.sctp.SctpServerChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpServerChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static SctpServerChannel newServerSocket() {
        try {
            return SctpServerChannel.open();
        } catch (IOException e) {
            throw new ChannelException("failed to create a sctp server channel", e);
        }
    }

    private final SctpServerChannel sch;
    private final SctpServerChannelConfig config;
    private final Selector selector;

    /**
     * Create a new instance with an new {@link SctpServerChannel}
     */
    public OioSctpServerChannel() {
        this(newServerSocket());
    }

    /**
     * Create a new instance from the given {@link SctpServerChannel}
     *
     * @param sch    the {@link SctpServerChannel} which is used by this instance
     */
    public OioSctpServerChannel(SctpServerChannel sch) {
        super(null);
        if (sch == null) {
            throw new NullPointerException("sctp server channel");
        }

        this.sch = sch;
        boolean success = false;
        try {
            sch.configureBlocking(false);
            selector = Selector.open();
            sch.register(selector, SelectionKey.OP_ACCEPT);
            config = new OioSctpServerChannelConfig(this, sch);
            success = true;
        } catch (Exception e) {
            throw new ChannelException("failed to initialize a sctp server channel", e);
        } finally {
            if (!success) {
                try {
                    sch.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a sctp server channel.", e);
                }
            }
        }
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SctpServerChannelConfig config() {
        return config;
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
    public boolean isOpen() {
        return sch.isOpen();
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            Iterator<SocketAddress> i = sch.getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<InetSocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = sch.getAllLocalAddresses();
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
    public boolean isActive() {
        return isOpen() && localAddress0() != null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        sch.bind(localAddress, config.getBacklog());
    }

    @Override
    protected void doClose() throws Exception {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn("Failed to close a selector.", e);
        }
        sch.close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        if (!isActive()) {
            return -1;
        }

        SctpChannel s = null;
        int acceptedChannels = 0;
        try {
            final int selectedKeys = selector.select(SO_TIMEOUT);
            if (selectedKeys > 0) {
                final Iterator<SelectionKey> selectionKeys = selector.selectedKeys().iterator();
                for (;;) {
                    SelectionKey key = selectionKeys.next();
                    selectionKeys.remove();
                    if (key.isAcceptable()) {
                        s = sch.accept();
                        if (s != null) {
                            buf.add(new OioSctpChannel(this, s));
                            acceptedChannels ++;
                        }
                    }
                    if (!selectionKeys.hasNext()) {
                        return acceptedChannels;
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("Failed to create a new channel from an accepted sctp channel.", t);
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a sctp channel.", t2);
                }
            }
        }

        return acceptedChannels;
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            try {
                sch.bindAddress(localAddress);
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
                sch.unbindAddress(localAddress);
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

    @Override
    protected void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
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
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    private final class OioSctpServerChannelConfig extends DefaultSctpServerChannelConfig {
        private OioSctpServerChannelConfig(OioSctpServerChannel channel, SctpServerChannel javaChannel) {
            super(channel, javaChannel);
        }

        @Override
        protected void autoReadCleared() {
            setReadPending(false);
        }
    }
}
