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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;

/**
 * Handler that block any new connection if there are already a currently active
 * channel connected with the same InetAddress (IP).
 * @author frederic bregier
 *
 */
@ChannelPipelineCoverage("all")
public class OneIpFilterHandler extends IpFilteringHandler {
    /**
     * HashMap of current remote connected InetAddress
     */
    private final ConcurrentSkipListSet<InetAddress> connectedSet = 
        new ConcurrentSkipListSet<InetAddress>();

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#accept(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent, java.net.InetSocketAddress)
     */
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e,
            InetSocketAddress inetSocketAddress) throws Exception {
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (this.connectedSet.contains(inetAddress)) {
            return false;
        }
        this.connectedSet.add(inetAddress);
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#handleRefusedChannel(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent, java.net.InetSocketAddress)
     */
    @Override
    protected void handleRefusedChannel(ChannelHandlerContext ctx,
            ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception {
        // Do nothing: could be overridden
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#channelClosedWasBlocked(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    protected boolean channelClosedWasBlocked(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        boolean refused = super.channelClosedWasBlocked(ctx, e);
        if (! refused) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) e.getChannel().getRemoteAddress();
            this.connectedSet.remove(inetSocketAddress.getAddress());
        }
        return refused;
    }

}
