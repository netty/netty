/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import io.netty.buffer.BufType;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSctpServerChannelConfig;
import io.netty.channel.socket.SctpServerChannelConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link io.netty.channel.socket.SctpServerChannel} implementation which use non-blocking mode to accept new
 * connections and create the {@link NioSctpChannel} for them.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class NioSctpServerChannel extends AbstractNioMessageChannel
        implements io.netty.channel.socket.SctpServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);

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
        super(null, null, newSocket(), SelectionKey.OP_ACCEPT);
        config = new DefaultSctpServerChannelConfig(this, javaChannel());
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public Set<SocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
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
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        SctpChannel ch = javaChannel().accept();
        if (ch == null) {
            return 0;
        }
        buf.add(new NioSctpChannel(this, null, ch));
        return 1;
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
    protected int doWriteMessages(MessageBuf<Object> buf, boolean lastSpin) throws Exception {
        throw new UnsupportedOperationException();
    }
}
