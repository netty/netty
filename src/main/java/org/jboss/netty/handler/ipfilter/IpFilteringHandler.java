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
package org.jboss.netty.handler.ipfilter;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;

/**
 * General class that handle Ip Filtering
 * @author frederic bregier
 *
 */
public abstract class IpFilteringHandler implements ChannelUpstreamHandler {

    /**
     * Called when the channel is connected. It returns True if the corresponding connection
     * is to be allowed. Else it returns False.
     * @param ctx
     * @param e
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @return True if the corresponding connection is allowed, else False.
     * @throws Exception 
     */
    protected abstract boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception;
    /**
     * Called when the channel has the CONNECTED status and the channel was refused by a previous call to accept().
     * This method enables your implementation to send a message back to the client before closing
     * or whatever you need. If a message is sent back, you should await on its completion (using
     * awaitUninterruptibly() for instance).
     * @param ctx
     * @param e
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @throws Exception 
     */
    protected abstract void handleRefusedChannel(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception;
    /**
     * Called if CONNECTED ChannelStateEvent is received
     * @param ctx
     * @param e
     * @return True if the Channel was blocked by the IpFiltering handler 
     * @throws Exception 
     */
    protected boolean channelConnectedAccept(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) e.getChannel().getRemoteAddress();
        if (! this.accept(ctx, e, inetSocketAddress)) {
            ctx.setAttachment(Boolean.TRUE);
            this.handleRefusedChannel(ctx, e, inetSocketAddress);
            Channels.close(e.getChannel());
            return false;
        }
        ctx.setAttachment(null);
        return true;
    }
    /**
     * Called if CLOSED ChannelStateEvent is received.<br>
     * It can overridden if necessary in order to allow the closed event to be passed to the next handler.
     * @param ctx
     * @param e
     * @return True if this Channel was blocked by the IpFiltering handler
     * @throws Exception 
     */
    protected boolean channelClosedWasBlocked(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        boolean refused = this.isBlocked(ctx);
        // don't pass to next level since channel was block early
        return refused;
    }
    /**
     * 
     * @param ctx
     * @return True if the current channel was blocked by this filter
     */
    protected boolean isBlocked(ChannelHandlerContext ctx) {
        return (ctx.getAttachment() != null);
    }
    
    /* (non-Javadoc)
     * @see org.jboss.netty.channel.ChannelUpstreamHandler#handleUpstream(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent)
     */
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (Boolean.FALSE.equals(evt.getValue())) {
                    // CLOSED
                    if (channelClosedWasBlocked(ctx, evt)) {
                        // don't pass to next level since channel was block early
                        return;                        
                    }
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    if (!channelConnectedAccept(ctx,evt)) {
                        // don't pass to next level since channel was block early
                        return;
                    }
                }
                break;
            }
        }
        if (this.isBlocked(ctx)) {
            // don't pass to next level since channel was block early
            return;
        }
        // Whatever it is, if not blocked, goes to the next level
        ctx.sendUpstream(e);
    }
}
