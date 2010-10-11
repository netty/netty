/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
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
        return bound.get() && isOpen();
    }

    public boolean isConnected() {
        return pairedChannel != null && isOpen();
    }

    public LocalAddress getLocalAddress() {
        return localAddress;
    }

    public LocalAddress getRemoteAddress() {
        return remoteAddress;
    }

    void closeNow(ChannelFuture future) {
        LocalAddress localAddress = this.localAddress;
        try {
            // Close the self.
            if (!setClosed()) {
                return;
            }

            DefaultLocalChannel pairedChannel = this.pairedChannel;
            if (pairedChannel != null) {
                this.pairedChannel = null;
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
                fireChannelDisconnected(pairedChannel);
                fireChannelUnbound(pairedChannel);
            }
            fireChannelClosed(pairedChannel);
        } finally {
            future.setSuccess();
            if (localAddress != null && getParent() == null) {
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
