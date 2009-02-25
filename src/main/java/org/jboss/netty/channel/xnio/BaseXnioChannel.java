/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.nio.channels.GatheringByteChannel;
import java.util.Queue;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.LinkedTransferQueue;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ConnectedChannel;
import org.jboss.xnio.channels.MultipointWritableMessageChannel;
import org.jboss.xnio.channels.WritableMessageChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev: 937 $, $Date: 2009-02-25 19:43:03 +0900 (Wed, 25 Feb 2009) $
 */
@SuppressWarnings("unchecked")
class BaseXnioChannel extends AbstractChannel implements XnioChannel {

    private final XnioChannelConfig config;
    volatile java.nio.channels.Channel xnioChannel;

    final Object writeLock = new Object();
    final Queue<MessageEvent> writeBuffer = new LinkedTransferQueue<MessageEvent>();
    MessageEvent currentWriteEvent;
    int currentWriteIndex;

    // TODO implement high / low water mark

    /**
     * @param parent
     * @param factory
     * @param pipeline
     * @param sink
     */
    BaseXnioChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            XnioChannelConfig config) {
        super(parent, factory, pipeline, sink);
        this.config = config;
    }

    public XnioChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (!isOpen() || !(xnioChannel instanceof BoundChannel)) {
            return null;
        }

        return (SocketAddress) ((BoundChannel) xnioChannel).getLocalAddress();
    }

    public SocketAddress getRemoteAddress() {
        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (!isOpen() || !(xnioChannel instanceof ConnectedChannel)) {
            return null;
        }

        return (SocketAddress) ((ConnectedChannel) xnioChannel).getPeerAddress();
    }

    public boolean isBound() {
        return getLocalAddress() != null;
    }

    public boolean isConnected() {
        return getRemoteAddress() != null;
    }

    @Override
    public ChannelFuture write(Object message) {
        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (xnioChannel instanceof MultipointWritableMessageChannel) {
            SocketAddress remoteAddress = getRemoteAddress();
            if (remoteAddress != null) {
                return write(message, remoteAddress);
            } else {
                return getUnsupportedOperationFuture();
            }
        }

        if (xnioChannel instanceof GatheringByteChannel ||
            xnioChannel instanceof WritableMessageChannel) {
            return super.write(message);
        } else {
            return getUnsupportedOperationFuture();
        }
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return write(message);
        }

        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (xnioChannel instanceof MultipointWritableMessageChannel) {
            return super.write(message);
        } else {
            return getUnsupportedOperationFuture();
        }
    }

    void closeNow(ChannelFuture future) {
        SocketAddress localAddress = getLocalAddress();
        SocketAddress remoteAddress = getRemoteAddress();

        if (!setClosed()) {
            future.setSuccess();
            return;
        }

        IoUtils.safeClose(xnioChannel);
        xnioChannel = null;
        XnioChannelRegistry.unregisterChannelMapping(this);

        if (remoteAddress != null) {
            fireChannelDisconnected(this);
        }
        if (localAddress != null) {
            fireChannelUnbound(this);
        }

        fireChannelClosed(this);
    }
}
