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
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
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
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
final class DefaultLocalChannel extends AbstractChannel implements LocalChannel {

    // TODO Move the state management up to AbstractChannel to remove duplication.
    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    final AtomicInteger state = new AtomicInteger(ST_OPEN);

    private final ChannelConfig config;
    private final ThreadLocalBoolean delivering = new ThreadLocalBoolean();

    final Queue<MessageEvent> writeBuffer = new LinkedTransferQueue<MessageEvent>();

    volatile DefaultLocalChannel pairedChannel;
    volatile LocalAddress localAddress;
    volatile LocalAddress remoteAddress;

    DefaultLocalChannel(LocalServerChannel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, DefaultLocalChannel pairedChannel) {
        super(parent, factory, pipeline, sink);
        this.pairedChannel = pairedChannel;
        config = new DefaultChannelConfig();

        // TODO Move the state variable to AbstractChannel so that we don't need
        //      to add many listeners.
        getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                state.set(ST_CLOSED);
            }
        });

        fireChannelOpen(this);
    }

    public ChannelConfig getConfig() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state.get() >= ST_OPEN;
    }

    public boolean isBound() {
        return state.get() >= ST_BOUND;
    }

    public boolean isConnected() {
        return state.get() == ST_CONNECTED;
    }

    final void setBound() throws ClosedChannelException {
        if (!state.compareAndSet(ST_OPEN, ST_BOUND)) {
            switch (state.get()) {
            case ST_CLOSED:
                throw new ClosedChannelException();
            default:
                throw new ChannelException("already bound");
            }
        }
    }

    final void setConnected() {
        if (state.get() != ST_CLOSED) {
            state.set(ST_CONNECTED);
        }
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
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
