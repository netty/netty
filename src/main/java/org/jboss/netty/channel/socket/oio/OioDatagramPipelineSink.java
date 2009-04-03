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
package org.jboss.netty.channel.socket.oio;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.internal.ThreadRenamingRunnable;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class OioDatagramPipelineSink extends AbstractChannelSink {

    private final Executor workerExecutor;

    OioDatagramPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        OioDatagramChannel channel = (OioDatagramChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    OioDatagramWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    OioDatagramWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    OioDatagramWorker.disconnect(channel, future);
                }
                break;
            case INTEREST_OPS:
                OioDatagramWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent evt = (MessageEvent) e;
            OioDatagramWorker.write(
                    channel, future, evt.getMessage(), evt.getRemoteAddress());
        }
    }

    private void bind(
            OioDatagramChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        boolean bound = false;
        boolean workerStarted = false;
        try {
            channel.socket.bind(localAddress);
            bound = true;

            // Fire events
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());

            // Start the business.
            workerExecutor.execute(new ThreadRenamingRunnable(
                    new OioDatagramWorker(channel),
                    "Old I/O datagram worker (channelId: " + channel.getId() + ", " +
                    channel.getLocalAddress() + ')'));

            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (bound && !workerStarted) {
                OioDatagramWorker.close(channel, future);
            }
        }
    }

    private void connect(
            OioDatagramChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {

        boolean bound = channel.isBound();
        boolean connected = false;
        boolean workerStarted = false;

        future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        try {
            channel.socket.connect(remoteAddress);
            connected = true;

            // Fire events.
            future.setSuccess();
            if (!bound) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());

            String threadName =
                "Old I/O datagram worker (channelId: " + channel.getId() + ", " +
                channel.getLocalAddress() + " => " +
                channel.getRemoteAddress() + ')';
            if (!bound) {
                // Start the business.
                workerExecutor.execute(new ThreadRenamingRunnable(
                        new OioDatagramWorker(channel), threadName));
            } else {
                // Worker started by bind() - just rename.
                Thread workerThread = channel.workerThread;
                if (workerThread != null) {
                    try {
                        workerThread.setName(threadName);
                    } catch (SecurityException e) {
                        // Ignore.
                    }
                }
            }

            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (connected && !workerStarted) {
                OioDatagramWorker.close(channel, future);
            }
        }
    }
}
