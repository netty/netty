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
package net.gleamynode.netty.channel.socket.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.buffer.ConcurrentObjectBuffer;
import net.gleamynode.netty.buffer.ObjectBuffer;
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
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.channel.SucceededChannelFuture;
import net.gleamynode.netty.channel.socket.nio.NioClientSocketPipelineSink.Worker;
import net.gleamynode.netty.pipeline.Pipeline;

class NioClientSocketChannel extends AbstractChannel {

    private static final Logger logger = Logger.getLogger(
            NioClientSocketChannel.class.getName());

    private final ChannelFactory transport;
    private final Pipeline<ChannelEvent> pipeline;
    final SucceededChannelFuture succeededFuture;

    final SocketChannel socket;
    final ObjectBuffer<MessageEvent> writeBuffer =
        new ConcurrentObjectBuffer<MessageEvent>();
    MessageEvent currentWriteEvent;
    int currentWriteIndex;
    volatile Worker worker;
    volatile boolean boundManually;
    private final NioSocketChannelConfig config;

    NioClientSocketChannel(
            ChannelFactory transport, Pipeline<ChannelEvent> pipeline) {

        this.transport = transport;
        this.pipeline = pipeline;
        try {
            socket = SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }

        boolean success = false;
        try {
            socket.configureBlocking(false);
            success = true;
        } catch (IOException e) {
            throw new ChannelException("Failed to enter non-blocking mode.", e);
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log(
                            Level.WARNING,
                            "Failed to close a partially initialized socket.",
                            e);
                }
            }
        }

        succeededFuture = new SucceededChannelFuture(this);
        config = new DefaultNioSocketChannelConfig(socket.socket());

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
        ChannelFuture future = new DefaultChannelFuture(this, true);
        pipeline.sendDownstream(new DefaultChannelStateEvent(
                this, future, ChannelState.CONNECTED, remoteAddress));
        return future;
    }

    public ChannelFuture disconnect() {
        return close();
    }

    public NioSocketChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        return socket.socket().getLocalSocketAddress();
    }

    public Channel getParent() {
        return null;
    }

    public Pipeline<ChannelEvent> getPipeline() {
        return pipeline;
    }

    public SocketAddress getRemoteAddress() {
        return socket.socket().getRemoteSocketAddress();
    }

    public ChannelFactory getFactory() {
        return transport;
    }

    public boolean isBound() {
        return isOpen() && socket.socket().isBound();
    }

    public boolean isConnected() {
        return isOpen() && socket.isConnected();
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
