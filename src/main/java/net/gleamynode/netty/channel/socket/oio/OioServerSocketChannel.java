/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.oio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.AbstractChannel;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.DefaultChannelFuture;
import net.gleamynode.netty.channel.DefaultChannelStateEvent;
import net.gleamynode.netty.channel.SucceededChannelFuture;
import net.gleamynode.netty.channel.socket.DefaultServerSocketChannelConfig;
import net.gleamynode.netty.channel.socket.ServerSocketChannelConfig;
import net.gleamynode.netty.pipeline.Pipeline;

class OioServerSocketChannel extends AbstractChannel {

    private static final Logger logger = Logger.getLogger(OioServerSocketChannel.class.getName());

    final OioServerSocketChannelFactory factory;
    private final Pipeline<ChannelEvent> pipeline;
    final SucceededChannelFuture succeededFuture;

    final ServerSocket socket;
    private final ServerSocketChannelConfig config;
    volatile boolean readable;

    OioServerSocketChannel(
            OioServerSocketChannelFactory factory,
            Pipeline<ChannelEvent> pipeline) {

        this.factory = factory;
        this.pipeline = pipeline;
        pipeline.setSink(factory.sink);
        succeededFuture = new SucceededChannelFuture(this);

        try {
            socket = new ServerSocket();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }

        try {
            socket.setSoTimeout(1000);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                logger.log(
                        Level.WARNING,
                        "Failed to close a partially initialized socket.", e2);
            }
            throw new ChannelException(
                    "Failed to set the server socket timeout.", e);
        }

        config = new DefaultServerSocketChannelConfig(socket);

        pipeline.sendUpstream(new DefaultChannelStateEvent(
                this, succeededFuture, ChannelState.OPEN, Boolean.TRUE));
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        ChannelFuture future = new DefaultChannelFuture(this, false);
        pipeline.sendDownstream(new DefaultChannelStateEvent(
                this, future, ChannelState.BOUND, localAddress));
        return future;
    }

    public ChannelFuture close() {
        ChannelFuture future = new DefaultChannelFuture(this, false);
        pipeline.sendDownstream(new DefaultChannelStateEvent(
                this, future, ChannelState.BOUND, null));
        pipeline.sendDownstream(new DefaultChannelStateEvent(
                this, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        throw new UnsupportedOperationException();
    }

    public ChannelFuture disconnect() {
        throw new UnsupportedOperationException();
    }

    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public Channel getParent() {
        return null;
    }

    public Pipeline<ChannelEvent> getPipeline() {
        return pipeline;
    }

    public SocketAddress getRemoteAddress() {
        return null;
    }

    public ChannelFactory getFactory() {
        return factory;
    }

    public boolean isBound() {
        return isOpen() & socket.isBound();
    }

    public boolean isConnected() {
        return false;
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    public ChannelFuture write(Object message) {
        throw new UnsupportedOperationException();
    }

    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        throw new UnsupportedOperationException();
    }
}
