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

import java.net.ConnectException;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class LocalClientChannelSink extends AbstractChannelSink {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LocalClientChannelSink.class);

    LocalClientChannelSink() {
        super();
    }

    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;

            DefaultLocalChannel channel =
                  (DefaultLocalChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.closeNow(future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (LocalAddress) value);
                } else {
                    channel.closeNow(future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (LocalAddress) value);
                } else {
                    channel.closeNow(future);
                }
                break;
            case INTEREST_OPS:
                // Unsupported - discard silently.
                future.setSuccess();
                break;
            }
        }
        else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            DefaultLocalChannel channel = (DefaultLocalChannel) event.getChannel();
            boolean offered = channel.writeBuffer.offer(event);
            assert offered;
            channel.flushWriteBuffer();
        }
    }

    private void bind(DefaultLocalChannel channel, ChannelFuture future, LocalAddress localAddress) {
        try {
            if (!LocalChannelRegistry.register(localAddress, channel)) {
                throw new ChannelException("address already in use: " + localAddress);
            }

            if (!channel.bound.compareAndSet(false, true)) {
                throw new ChannelException("already bound");
            }

            channel.localAddress = localAddress;
            future.setSuccess();
            fireChannelBound(channel, localAddress);
        } catch (Throwable t) {
            LocalChannelRegistry.unregister(localAddress);
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(DefaultLocalChannel channel, ChannelFuture future, LocalAddress remoteAddress) {
        Channel remoteChannel = LocalChannelRegistry.getChannel(remoteAddress);
        if (!(remoteChannel instanceof DefaultLocalServerChannel)) {
            future.setFailure(new ConnectException("connection refused"));
            return;
        }

        DefaultLocalServerChannel serverChannel = (DefaultLocalServerChannel) remoteChannel;
        ChannelPipeline pipeline;
        try {
            pipeline = serverChannel.getConfig().getPipelineFactory().getPipeline();
        } catch (Exception e) {
            future.setFailure(e);
            logger.warn(
                    "Failed to initialize an accepted socket.", e);
            return;
        }

        future.setSuccess();
        DefaultLocalChannel acceptedChannel = new DefaultLocalChannel(
                serverChannel, serverChannel.getFactory(), pipeline, this, channel);
        channel.pairedChannel = acceptedChannel;

        bind(channel, succeededFuture(channel), new LocalAddress(LocalAddress.EPHEMERAL));
        channel.remoteAddress = serverChannel.getLocalAddress();
        fireChannelConnected(channel, serverChannel.getLocalAddress());

        acceptedChannel.localAddress = serverChannel.getLocalAddress();
        acceptedChannel.bound.set(true);
        fireChannelBound(acceptedChannel, channel.getRemoteAddress());
        acceptedChannel.remoteAddress = channel.getLocalAddress();
        fireChannelConnected(acceptedChannel, channel.getLocalAddress());

        // Flush something that was written in channelBound / channelConnected
        channel.flushWriteBuffer();
        acceptedChannel.flushWriteBuffer();
    }
}
