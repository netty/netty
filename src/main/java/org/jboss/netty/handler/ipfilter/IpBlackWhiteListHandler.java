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
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;

/**
 * Implementation of Banned or Allowed IP.<br>
 * <br>
 * 
 * The black list has the priority on white list (a InetAddress that would be included in both
 * black and white lists will be refused).<br>
 * The black list is first checked. If the {@link InetAddress}
 * is compatible with one {@link IpSubnet}, then it is refused.<br>
 * The white list is then checked. If the {@link InetAddress}
 * is compatible with one {@link IpSubnet}, then it is accepted.<br>
 * <br>
 * An empty black list means allow all (no limitation).<br><br>
 * An empty white list means allow all (no limitation).<br><br>
 * <br>
 * <b>This handler should be created only once and reused on every pipeline since it handles
 * a global status of what is allowed or blocked.
 * @author frederic bregier
 *
 */
@ChannelPipelineCoverage("all")
public class IpBlackWhiteListHandler extends IpFilteringHandler {
    /**
     * Black List of IpSubnet
     */
    private final CopyOnWriteArrayList<IpSubnet> blackList = new CopyOnWriteArrayList<IpSubnet>();
    /**
     * White List of IpSubnet
     */
    private final CopyOnWriteArrayList<IpSubnet> whiteList = new CopyOnWriteArrayList<IpSubnet>();
    /**
     * Constructor from Black and White lists of IpSubnet
     * @param blackList
     * @param whiteList
     */
    public IpBlackWhiteListHandler(List<IpSubnet> blackList, List<IpSubnet> whiteList) {
        if (blackList != null) {
            this.blackList.addAll(blackList);
        }
        if (whiteList != null) {
            this.whiteList.addAll(whiteList);
        }
    }
    /**
     * Empty constructor (no IpSubnet in the blackList and whitelist at construction). In such a situation, 
     * empty lists implies allow all (empty white list meaning allow all).
     */
    public IpBlackWhiteListHandler() {
        try {
            this.whiteList.add(new IpSubnet("0.0.0.0/0"));
        } catch (UnknownHostException e) {
        }
    }
    /**
     * Add an IpSubnet in the black list
     * @param ipSubnet
     */
    public void block(IpSubnet ipSubnet) {
        if (ipSubnet == null) {
            throw new NullPointerException("IpSubnet can not be null");
        }
        this.blackList.add(ipSubnet);
    }
    
    /**
     * Remove the IpSubnet from the black list
     * @param ipSubnet
     */
    public void unblock(IpSubnet ipSubnet) {
        if (ipSubnet == null) {
            throw new NullPointerException("IpSubnet can not be null");
        }
        this.blackList.remove(ipSubnet);
    }
    /**
     * Clear the black list
     */
    public void unblockAll() {
        this.blackList.clear();
    }
    /**
     * Add an IpSubnet in the white list
     * @param ipSubnet
     */
    public void allow(IpSubnet ipSubnet) {
        if (ipSubnet == null) {
            throw new NullPointerException("IpSubnet can not be null");
        }
        this.whiteList.add(ipSubnet);
    }
    /**
     * Remove the IpSubnet from the white list
     * @param ipSubnet
     */
    public void unallow(IpSubnet ipSubnet) {
        if (ipSubnet == null) {
            throw new NullPointerException("IpSubnet can not be null");
        }
        this.whiteList.remove(ipSubnet);
    }
    /**
     * Clear the white list
     */
    public void allowAll() {
        this.whiteList.clear();
    }
    
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e,
            InetSocketAddress inetSocketAddress) throws Exception {
        if (this.blackList.isEmpty() && this.whiteList.isEmpty()) {
            // No limitation in deny, and no limitation in allow
            return true;
        }
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (! this.blackList.isEmpty()) {
            Iterator<IpSubnet> iterator = this.blackList.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().contains(inetAddress)) {
                    // Limitation in deny founds
                    return false;
                }
            }
        }
        if (! this.whiteList.isEmpty()) {
            Iterator<IpSubnet> iterator = this.whiteList.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().contains(inetAddress)) {
                    // No limitation in deny, and allow founds
                    return true;
                }
            }
            // No limitation in deny, but no allow founds in a not empty list
            return false;
        } else {
            // No limitation in deny, and no limitation in allow
            return true;
        }
    }
    @Override
    protected void handleRefusedChannel(ChannelHandlerContext ctx,
            ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception {
        // Do nothing: could be overridden
    }

}
