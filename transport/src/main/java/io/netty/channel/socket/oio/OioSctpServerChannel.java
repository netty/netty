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
package io.netty.channel.socket.oio;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import io.netty.buffer.ChannelBufType;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSctpServerChannelConfig;
import io.netty.channel.socket.SctpServerChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OioSctpServerChannel extends AbstractOioMessageChannel
        implements io.netty.channel.socket.SctpServerChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpServerChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.MESSAGE, false);

    private static SctpServerChannel newServerSocket() {
        try {
            return SctpServerChannel.open();
        } catch (IOException e) {
            throw new ChannelException("failed to create a sctp server channel", e);
        }
    }

    final SctpServerChannel sch;
    private final SctpServerChannelConfig config;

    public OioSctpServerChannel() {
        this(newServerSocket());
    }

    public OioSctpServerChannel(SctpServerChannel sch) {
        this(null, sch);
    }

    public OioSctpServerChannel(Integer id, SctpServerChannel sch) {
        super(null, id);
        if (sch == null) {
            throw new NullPointerException("sctp server channel");
        }

        this.sch = sch;
        boolean success = false;
        try {
            sch.configureBlocking(true);
            config = new DefaultSctpServerChannelConfig(sch);
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
            for (SocketAddress address : sch.getAllLocalAddresses()) {
                return address;
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
        sch.close();
    }

    @Override
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        if (!isActive()) {
            return -1;
        }

        if (readSuspended) {
            return 0;
        }

        SctpChannel s = null;
        try {
            s = sch.accept();
            if (s != null) {
                buf.add(new OioSctpChannel(this, null, s));
                return 1;
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
