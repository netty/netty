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
package org.jboss.netty.channel.socket.http;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class HttpTunnelingClientSocketPipelineSink extends AbstractChannelSink {

    static final String LINE_TERMINATOR = "\r\n";

    HttpTunnelingClientSocketPipelineSink() {
        super();
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        HttpTunnelingClientSocketChannel channel = (HttpTunnelingClientSocketChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    channel.connectAndSendHeaders(false, ((HttpTunnelAddress) value), future);
                } else {
                    close(channel, future);
                }
                break;
            case INTEREST_OPS:
                final ChannelFuture actualFuture = future;
                setInterestOps(channel.channel, ((Integer) value).intValue()).addListener(
                        new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future)
                                    throws Exception {
                                if (future.isSuccess()) {
                                    actualFuture.setSuccess();
                                } else {
                                    actualFuture.setFailure(future.getCause());
                                }
                            }
                        });
                break;
            }
        } else if (e instanceof MessageEvent) {
            channel.sendChunk(((ChannelBuffer) ((MessageEvent) e).getMessage()), future);
        }
    }

    private void bind(
            HttpTunnelingClientSocketChannel channel, ChannelFuture future,
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

    private void close(
            HttpTunnelingClientSocketChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();  
        try {
            channel.closeSocket();
            if (channel.setClosed()) {
                future.setSuccess();
                if (connected) {
                    fireChannelDisconnected(channel);
                }
                if (bound) {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        }
        catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
}
