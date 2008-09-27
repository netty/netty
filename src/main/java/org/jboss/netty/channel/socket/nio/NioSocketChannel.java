/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.WriteMessageQueue;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
abstract class NioSocketChannel extends AbstractChannel
                                implements org.jboss.netty.channel.socket.SocketChannel {

    final SocketChannel socket;
    private final NioSocketChannelConfig config;

    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();
    final Runnable writeTask = new WriteTask();
    final Object writeLock = new Object();
    final WriteMessageQueue writeBuffer = new WriteMessageQueue();
    private Queue<MessageEvent> internalWriteBuffer;
    MessageEvent currentWriteEvent;
    int currentWriteIndex;

    public NioSocketChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            SocketChannel socket) {
        super(parent, factory, pipeline, sink);

        this.socket = socket;
        config = new DefaultNioSocketChannelConfig(socket.socket());
    }

    abstract NioWorker getWorker();
    abstract void setWorker(NioWorker worker);

    Queue<MessageEvent> getInternalWriteBuffer() {
        if (internalWriteBuffer == null) {
            internalWriteBuffer = writeBuffer.drainAll();
        }
        return internalWriteBuffer;
    }

    void clearInternalWriteBuffer() {
        internalWriteBuffer = null;
    }

    public NioSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) socket.socket().getLocalSocketAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) socket.socket().getRemoteSocketAddress();
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

    private class WriteTask implements Runnable {

        WriteTask() {
            super();
        }

        public void run() {
            writeTaskInTaskQueue.set(false);
            NioWorker.write(NioSocketChannel.this, false);
        }
    }
}
