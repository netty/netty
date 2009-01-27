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
package org.jboss.netty.channel.socket.servlet;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
class ServletClientSocketPipelineSink  extends AbstractChannelSink {

    static String LINE_TERMINATOR = "\r\n";
    private static      Logger log = Logger.getLogger(ServletClientSocketPipelineSink.class);
    private final Executor workerExecutor;

    ServletClientSocketPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        ServletClientSocketChannel channel = (ServletClientSocketChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    ServletWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    ServletWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    ServletWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                ServletWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            ServletWorker.write(
                    channel, future,
                    ((MessageEvent) e).getMessage());
        }
    }

    private void bind(
            ServletClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.bindSocket(localAddress);
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(
            ServletClientSocketChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {

        boolean bound = channel.isBound();
        boolean connected = false;
        boolean workerStarted = false;

        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                if (future.isCancelled()) {
                    future.getChannel().close();
                }
            }
        });

        try {
            channel.connectAndSendHeaders(false, remoteAddress);
            // Fire events.
            future.setSuccess();
            if (!bound) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());

            // Start the business.
            workerExecutor.execute(new ThreadRenamingRunnable(
                    new ServletWorker(channel),
                    "Old I/O client worker (channelId: " + channel.getId() + ", " +
                    channel.getLocalAddress() + " => " +
                    channel.getRemoteAddress() + ')'));

            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (connected && !workerStarted) {
                ServletWorker.close(channel, future);
            }
        }
    }

}
