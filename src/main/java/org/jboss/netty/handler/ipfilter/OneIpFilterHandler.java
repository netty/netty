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
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.ConcurrentHashMap;

/**
 * Handler that block any new connection if there are already a currently active
 * channel connected with the same InetAddress (IP).<br>
 * <br>
 *
 * Take care to not change isBlocked method except if you know what you are doing
 * since it is used to test if the current closed connection is to be removed
 * or not from the map of currently connected channel.
 *
 * @author frederic bregier
 *
 */
@ChannelPipelineCoverage("all")
public class OneIpFilterHandler extends IpFilteringHandler {
    /**
     * HashMap of current remote connected InetAddress
     */
    private final ConcurrentMap<InetAddress, Boolean> connectedSet = new ConcurrentHashMap<InetAddress, Boolean>();

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#accept(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent, java.net.InetSocketAddress)
     */
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e,
            InetSocketAddress inetSocketAddress) throws Exception {
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (connectedSet.containsKey(inetAddress)) {
            return false;
        }
        connectedSet.put(inetAddress, Boolean.TRUE);
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#handleRefusedChannel(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent, java.net.InetSocketAddress)
     */
    @Override
    protected ChannelFuture handleRefusedChannel(ChannelHandlerContext ctx,
            ChannelEvent e, InetSocketAddress inetSocketAddress)
            throws Exception {
        // Do nothing: could be overridden
        return null;
    }

    @Override
    protected boolean continues(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        return false;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#handleUpstream(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent)
     */
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        super.handleUpstream(ctx, e);
        // Try to remove entry from Map if already exists
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            if (evt.getState() == ChannelState.CONNECTED) {
                if (evt.getValue() == null) {
                    // DISCONNECTED but was this channel blocked or not
                    if (isBlocked(ctx)) {
                        // remove inetsocketaddress from set since this channel was not blocked before
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) e
                                .getChannel().getRemoteAddress();
                        connectedSet.remove(inetSocketAddress.getAddress());
                    }
                }
            }
        }
    }

}
