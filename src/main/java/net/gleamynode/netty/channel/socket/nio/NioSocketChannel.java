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

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.gleamynode.netty.channel.AbstractChannel;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.Pipeline;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
abstract class NioSocketChannel extends AbstractChannel {

    final SocketChannel socket;
    private final NioSocketChannelConfig config;

    final Queue<MessageEvent> writeBuffer =
        new ConcurrentLinkedQueue<MessageEvent>();
    MessageEvent currentWriteEvent;
    int currentWriteIndex;

    public NioSocketChannel(
            Channel parent, ChannelFactory factory,
            Pipeline<ChannelEvent> pipeline, SocketChannel socket) {
        super(parent, factory, pipeline);

        this.socket = socket;
        config = new DefaultNioSocketChannelConfig(socket.socket());
    }

    abstract NioWorker getWorker();
    abstract void setWorker(NioWorker worker);

    public NioSocketChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        return socket.socket().getLocalSocketAddress();
    }

    public SocketAddress getRemoteAddress() {
        return socket.socket().getRemoteSocketAddress();
    }

    public boolean isBound() {
        return isOpen() && socket.socket().isBound();
    }

    public boolean isConnected() {
        return isOpen() && socket.socket().isConnected();
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

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return getUnsupportedOperationFuture();
        }
    }
}
