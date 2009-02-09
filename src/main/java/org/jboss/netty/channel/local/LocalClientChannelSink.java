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

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
final class LocalClientChannelSink extends AbstractChannelSink {

    private final Channel serverChannel;

    private final ChannelSink serverSink;

    LocalClientChannelSink(Channel channel, ChannelSink sink) {
        serverChannel = channel;
        serverSink = sink;
    }

    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;

            LocalChannel channel =
                  (LocalChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();
            switch (state) {
                case OPEN:
                    if (Boolean.FALSE.equals(value)) {
                        future.setSuccess();
                        fireChannelDisconnected(channel);
                        fireChannelClosed(channel);
                        fireChannelDisconnected(channel.pairedChannel);
                        fireChannelClosed(channel.pairedChannel);
                    }
                    break;
                case BOUND:
                    break;
                case CONNECTED:
                    connect(channel, future, (LocalAddress) value);
                    break;
                case INTEREST_OPS:
                    break;
            }
        }
        else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            LocalChannel channel = (LocalChannel) event.getChannel();
            channel.pairedChannel.writeBuffer.offer(event);
            channel.pairedChannel.writeNow(channel.pairedChannel);
        }
    }

    private void connect(LocalChannel channel, ChannelFuture future, LocalAddress localAddress) throws Exception {
        future.setSuccess();
        ChannelPipeline pipeline = serverChannel.getConfig().getPipelineFactory().getPipeline();
        LocalChannel acceptedChannel = new LocalChannel(serverChannel.getFactory(), pipeline, serverSink);
        channel.pairedChannel = acceptedChannel;
        acceptedChannel.pairedChannel = channel;
        Channels.fireChannelConnected(channel, localAddress);
        Channels.fireChannelConnected(acceptedChannel, localAddress);
    }
}
