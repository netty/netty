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
package io.netty.channel.socket.sctp.oio;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import io.netty.buffer.BufType;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.sctp.DefaultSctpServerChannelConfig;
import io.netty.channel.socket.sctp.SctpServerChannelConfig;
import io.netty.channel.socket.oio.AbstractOioMessageChannel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link io.netty.channel.socket.sctp.SctpServerChannel} implementation which use blocking mode to accept new
 * connections and create the {@link OioSctpChannel} for them.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class OioSctpServerChannel extends AbstractOioMessageChannel
        implements io.netty.channel.socket.sctp.SctpServerChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpServerChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);

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
        this(null, sch);
    }

    /**
     * Create a new instance from the given {@link SctpServerChannel}
     *
     * @param id        the id which should be used for this instance or {@code null} if a new one should be generated
     * @param sch       the {@link SctpServerChannel} which is used by this instance
     */
    public OioSctpServerChannel(Integer id, SctpServerChannel sch) {
        super(null, id);
        if (sch == null) {
            throw new NullPointerException("sctp server channel");
        }

        this.sch = sch;
        boolean success = false;
        try {
            sch.configureBlocking(false);
            selector = Selector.open();
            sch.register(selector, SelectionKey.OP_ACCEPT);
            config = new DefaultSctpServerChannelConfig(this, sch);
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
    public Set<SocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = sch.getAllLocalAddresses();
            final Set<SocketAddress> addresses = new HashSet<SocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add(socketAddress);
            }
            return addresses;
        } catch (Throwable t) {
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
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        if (!isActive()) {
            return -1;
        }

        SctpChannel s = null;
        try {
            final int selectedKeys = selector.select(SO_TIMEOUT);
            if (selectedKeys > 0) {
                final Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                   if (key.isAcceptable()) {
                       s = sch.accept();
                       if (s != null) {
                           buf.add(new OioSctpChannel(this, null, s));
                       }
                   }
                }
                return selectedKeys;
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

        return 0;
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
    protected void doWriteMessages(MessageBuf<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }
}
