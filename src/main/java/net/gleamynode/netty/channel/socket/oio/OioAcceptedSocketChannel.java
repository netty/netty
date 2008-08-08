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
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketAddress;

import net.gleamynode.netty.channel.AbstractChannel;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.DefaultChannelFuture;
import net.gleamynode.netty.channel.DefaultChannelStateEvent;
import net.gleamynode.netty.channel.DefaultMessageEvent;
import net.gleamynode.netty.channel.SucceededChannelFuture;
import net.gleamynode.netty.channel.socket.DefaultSocketChannelConfig;
import net.gleamynode.netty.channel.socket.SocketChannelConfig;
import net.gleamynode.netty.pipeline.Pipeline;

class OioAcceptedSocketChannel extends AbstractChannel {

    private final ChannelFactory factory;
    private final Channel parent;
    private final Pipeline<ChannelEvent> pipeline;
    final SucceededChannelFuture succeededFuture;

    final Socket socket;
    final PushbackInputStream in;
    final OutputStream out;
    private final SocketChannelConfig config;

    OioAcceptedSocketChannel(
            ChannelFactory factory, Channel parent, Socket socket,
            Pipeline<ChannelEvent> pipeline) {

        this.factory = factory;
        this.parent = parent;
        this.socket = socket;
        this.pipeline = pipeline;
        try {
            in = new PushbackInputStream(socket.getInputStream(), 1);
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an InputStream.", e);
        }
        try {
            out = socket.getOutputStream();
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an OutputStream.", e);
        }

        succeededFuture = new SucceededChannelFuture(this);
        config = new DefaultSocketChannelConfig(socket);

        pipeline.sendUpstream(new DefaultChannelStateEvent(
                this, succeededFuture, ChannelState.OPEN, Boolean.TRUE));
        pipeline.sendUpstream(new DefaultChannelStateEvent(
                this, succeededFuture, ChannelState.BOUND, socket.getLocalSocketAddress()));
        pipeline.sendUpstream(new DefaultChannelStateEvent(
                this, succeededFuture, ChannelState.CONNECTED, socket.getRemoteSocketAddress()));
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    public ChannelFuture close() {
        ChannelFuture future = new DefaultChannelFuture(this, false);
        if (isConnected()) {
            pipeline.sendDownstream(new DefaultChannelStateEvent(
                    this, future, ChannelState.CONNECTED, null));
        }
        if (isBound()) {
            pipeline.sendDownstream(new DefaultChannelStateEvent(
                    this, future, ChannelState.BOUND, null));
        }
        pipeline.sendDownstream(new DefaultChannelStateEvent(
                this, future, ChannelState.OPEN, Boolean.FALSE));
        return future;
    }

    public ChannelFuture connect(SocketAddress remoteAddress) {
        throw new UnsupportedOperationException();
    }

    public ChannelFuture disconnect() {
        return close();
    }

    public SocketChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        return socket.getLocalSocketAddress();
    }

    public Channel getParent() {
        return parent;
    }

    public Pipeline<ChannelEvent> getPipeline() {
        return pipeline;
    }

    public SocketAddress getRemoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    public ChannelFactory getFactory() {
        return factory;
    }

    public boolean isBound() {
        return isOpen() & socket.isBound();
    }

    public boolean isConnected() {
        return isOpen() & socket.isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    public ChannelFuture write(Object message) {
        ChannelFuture future = new DefaultChannelFuture(this, false);
        pipeline.sendDownstream(
                new DefaultMessageEvent(this, future, message, null));
        return future;
    }

    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return write(message);
        }
        throw new UnsupportedOperationException();
    }
}
