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
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
final class LocalServerChannelSink extends AbstractChannelSink {

    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            if (e.getChannel() instanceof LocalServerChannel) {
                handleServerChannel(event);
            }
            else if (e.getChannel() instanceof LocalChannel) {
                handleLocalChannel(event);
            }
        }
        else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            LocalChannel channel = (LocalChannel) event.getChannel();
            channel.pairedChannel.writeBuffer.offer(event);
            channel.pairedChannel.writeNow(channel.pairedChannel);
        }

    }

    private void handleLocalChannel(ChannelStateEvent event) {
        LocalChannel localChannel =
              (LocalChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();
        switch (state) {
        // FIXME: Proper event emission.
        case OPEN:
            if (Boolean.FALSE.equals(value)) {
                future.setSuccess();
                fireChannelDisconnected(localChannel);
                fireChannelUnbound(localChannel);
                fireChannelClosed(localChannel);
                fireChannelDisconnected(localChannel.pairedChannel);
                fireChannelUnbound(localChannel.pairedChannel);
                fireChannelClosed(localChannel.pairedChannel);
            }
            break;
        case BOUND:
            break;
        }
    }

    private void handleServerChannel(ChannelStateEvent event) {
        LocalServerChannel serverChannel =
              (LocalServerChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();
        switch (state) {
            case OPEN:
                break;
            case BOUND:
                if (value != null) {
                    bind(future, serverChannel);
                }
                break;
        }
    }

    private void bind(ChannelFuture future, LocalServerChannel serverChannel) {
        future.setSuccess();
        fireChannelBound(serverChannel, serverChannel.getLocalAddress());
    }
}
