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
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.fireChannelOpen;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.DatagramChannelConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

/**
 * Provides an NIO based {@link org.jboss.netty.channel.socket.DatagramChannel}.
 */
public final class NioDatagramChannel extends AbstractNioChannel<DatagramChannel>
                                implements org.jboss.netty.channel.socket.DatagramChannel {

    /**
     * The {@link DatagramChannelConfig}.
     */
    private final NioDatagramChannelConfig config;

    NioDatagramChannel(final ChannelFactory factory,
            final ChannelPipeline pipeline, final ChannelSink sink,
            final NioDatagramWorker worker) {
        super(null, factory, pipeline, sink, worker, openNonBlockingChannel());
        config = new DefaultNioDatagramChannelConfig(channel.socket());
        
        fireChannelOpen(this);

    }

    private static DatagramChannel openNonBlockingChannel() {
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            return channel;
        } catch (final IOException e) {
            throw new ChannelException("Failed to open a DatagramChannel.", e);
        }
    }

    @Override
    public NioDatagramWorker getWorker() {
        return (NioDatagramWorker) super.getWorker();
    }
    
    public boolean isBound() {
        return isOpen() && channel.socket().isBound();
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    public NioDatagramChannelConfig getConfig() {
        return config;
    }

    DatagramChannel getDatagramChannel() {
        return channel;
    }

    public void joinGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    public void joinGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }

    public void leaveGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    public void leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }

    @Override
    InetSocketAddress getLocalSocketAddress() throws Exception {
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    @Override
    InetSocketAddress getRemoteSocketAddress() throws Exception {
        return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }

    }
}
