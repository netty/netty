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

import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import net.gleamynode.netty.channel.AbstractChannel;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelSink;
import net.gleamynode.netty.channel.socket.DefaultSocketChannelConfig;
import net.gleamynode.netty.channel.socket.SocketChannel;
import net.gleamynode.netty.channel.socket.SocketChannelConfig;

abstract class OioSocketChannel extends AbstractChannel
                                implements SocketChannel {

    final Socket socket;
    private final SocketChannelConfig config;
    volatile Thread workerThread;

    OioSocketChannel(
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink,
            Socket socket) {

        super(parent, factory, pipeline, sink);

        this.socket = socket;
        config = new DefaultSocketChannelConfig(socket);
    }

    public SocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
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

    @Override
    protected void setInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    protected ChannelFuture getSucceededFuture() {
        return super.getSucceededFuture();
    }

    abstract PushbackInputStream getInputStream();
    abstract void setInputStream(PushbackInputStream in);
    abstract OutputStream getOutputStream();
    abstract void setOutputStream(OutputStream out);

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return getUnsupportedOperationFuture();
        }
    }
}
