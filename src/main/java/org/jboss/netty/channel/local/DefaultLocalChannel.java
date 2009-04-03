/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.local;

import static org.jboss.netty.channel.Channels.*;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class DefaultLocalChannel extends AbstractChannel implements LocalChannel {

    private final ChannelConfig config;
    private final ThreadLocalBoolean delivering = new ThreadLocalBoolean();
    final AtomicBoolean bound = new AtomicBoolean();
    final Queue<MessageEvent> writeBuffer = new LinkedTransferQueue<MessageEvent>();

    volatile DefaultLocalChannel pairedChannel;
    volatile LocalAddress localAddress;
    volatile LocalAddress remoteAddress;

    DefaultLocalChannel(LocalServerChannel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, DefaultLocalChannel pairedChannel) {
        super(parent, factory, pipeline, sink);
        this.pairedChannel = pairedChannel;
        config = new DefaultChannelConfig();
        fireChannelOpen(this);
    }

    public ChannelConfig getConfig() {
        return config;
    }

    public boolean isBound() {
        return isOpen() && bound.get();
    }

    public boolean isConnected() {
        return localAddress != null && remoteAddress != null;
    }

    public LocalAddress getLocalAddress() {
        return isBound()? localAddress : null;
    }

    public LocalAddress getRemoteAddress() {
        return remoteAddress;
    }

    void closeNow(ChannelFuture future) {
        LocalAddress localAddress = this.localAddress;
        try {
            // Close the self.
            if (!setClosed()) {
                future.setSuccess();
                return;
            }

            DefaultLocalChannel pairedChannel = this.pairedChannel;
            if (pairedChannel != null) {
                this.pairedChannel = null;
                this.localAddress = null;
                fireChannelDisconnected(this);
                fireChannelUnbound(this);
            }
            fireChannelClosed(this);

            // Close the peer.
            if (pairedChannel == null || !pairedChannel.setClosed()) {
                return;
            }

            DefaultLocalChannel me = pairedChannel.pairedChannel;
            if (me != null) {
                pairedChannel.pairedChannel = null;
                pairedChannel.localAddress = null;
                fireChannelDisconnected(pairedChannel);
                fireChannelUnbound(pairedChannel);
            }
            fireChannelClosed(pairedChannel);
        } finally {
            if (localAddress != null) {
                LocalChannelRegistry.unregister(localAddress);
            }
        }
    }

    void flushWriteBuffer() {
        DefaultLocalChannel pairedChannel = this.pairedChannel;
        if (pairedChannel != null) {
            if (pairedChannel.isConnected()){
                // Channel is open and connected and channelConnected event has
                // been fired.
                if (!delivering.get()) {
                    delivering.set(true);
                    try {
                        for (;;) {
                            MessageEvent e = writeBuffer.poll();
                            if(e == null) {
                                break;
                            }

                            e.getFuture().setSuccess();
                            fireMessageReceived(pairedChannel, e.getMessage());
                            fireWriteComplete(this, 1);
                        }
                    } finally {
                        delivering.set(false);
                    }
                }
            } else {
                // Channel is open and connected but channelConnected event has
                // not been fired yet.
            }
        } else {
            // Channel is closed or not connected yet - notify as failures.
            Exception cause;
            if (isOpen()) {
                cause = new NotYetConnectedException();
            } else {
                cause = new ClosedChannelException();
            }

            for (;;) {
                MessageEvent e = writeBuffer.poll();
                if(e == null) {
                    break;
                }

                e.getFuture().setFailure(cause);
                fireExceptionCaught(this, cause);
            }
        }
    }
}
