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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;

/**
 * Implementation of Filter of IP based on ALLOW and DENY netmask (standard notations or CIDR notations).<br>
 * <br><br>
 * This implementation could be changed by implementing a new {@link IpFilterRule} than default one
 * {@link IpV4SubnetFilterRule} (IPV4 support only) or {@link IpSubnetFilterRule} (IPV4 and IPV6 support).<br>
 * <br>
 * The check is done by going from step to step in the underlying array of IpFilterRule.<br>
 * Each {@link IpFilterRule} answers to the method accept if the {@link InetAddress} is accepted or not,
 * according to its implementation. If an InetAddress arrives at the end of the list, as in Firewall
 * usual rules, the InetAddress is therefore accepted by default.<br>
 * For the {@link IpV4SubnetFilterRule} and {@link IpSubnetFilterRule} implementations:<br>
 * <ul>
 * <li>If it was constructed with True as first argument,
 * the IpFilterRule is an ALLOW rule (every InetAddress that fits in the subnet will be accepted).</li>
 * <li>If it was constructed with False as first argument,
 * the IpFilterRule is a DENY rule (every InetAddress that fits in the subnet will be refused).</li>
 * </ul><br>
 * <br>
 * An empty list means allow all (no limitation).<br><br>
 * <b>For efficiency reason, you should not add/remove too frequently IpFilterRules to/from this handler.
 * You should prefer to replace an entry (<tt>set</tt> method) with an ALLOW/DENY ALL IpFilterRule
 * if possible.</b><br><br><br>
 * <b>This handler should be created only once and reused on every pipeline since it handles
 * a global status of what is allowed or blocked.</b><br><br>
 *
 * Note that {@link IpSubnetFilterRule} which supports IPV4 and IPV6 should be used with as much as
 * possible no mixed IP protocol. Both IPV4 and IPV6 are supported but a mix (IpFilter in IPV6 notation
 * and the address from the channel in IPV4, or the reverse) can lead to wrong result.
 * @author frederic bregier
 *
 */
@ChannelPipelineCoverage("all")
public class IpFilterRuleHandler extends IpFilteringHandler {
    /**
     * List of {@link IpFilterRule}
     */
    private final CopyOnWriteArrayList<IpFilterRule> ipFilterRuleList = new CopyOnWriteArrayList<IpFilterRule>();

    /**
     * Constructor from a new list of IpFilterRule
     * @param newList
     */
    public IpFilterRuleHandler(List<IpFilterRule> newList) {
        if (newList != null) {
            ipFilterRuleList.addAll(newList);
        }
    }

    /**
     * Empty constructor (no IpFilterRule in the List at construction). In such a situation,
     * empty list implies allow all.
     */
    public IpFilterRuleHandler() {
    }

    // Below are methods directly inspired from CopyOnWriteArrayList methods
    /**
     * Add an ipFilterRule in the list at the end
     * @param ipFilterRule
     */
    public void add(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.add(ipFilterRule);
    }

    /**
     * Add an ipFilterRule in the list at the specified position (shifting to the right other elements)
     * @param index
     * @param ipFilterRule
     */
    public void add(int index, IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.add(index, ipFilterRule);
    }

    /**
     * Appends all of the elements in the specified collection to the end of this list,
     * in the order that they are returned by the specified collection's iterator.
     * @param c
     */
    public void addAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.addAll(c);
    }

    /**
     * Inserts all of the elements in the specified collection into this list, starting at the specified position.
     * @param index
     * @param c
     */
    public void addAll(int index, Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.addAll(index, c);
    }

    /**
     * Append the element if not present.
     * @param c
     * @return the number of elements added
     */
    public int addAllAbsent(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        return ipFilterRuleList.addAllAbsent(c);
    }

    /**
     * Append the element if not present.
     * @param ipFilterRule
     * @return true if the element was added
     */
    public boolean addIfAbsent(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.addIfAbsent(ipFilterRule);
    }

    /**
     * Clear the list
     */
    public void clear() {
        ipFilterRuleList.clear();
    }

    /**
     * Returns true if this list contains the specified element
     * @param ipFilterRule
     * @return true if this list contains the specified element
     */
    public boolean contains(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.contains(ipFilterRule);
    }

    /**
     * Returns true if this list contains all of the elements of the specified collection
     * @param c
     * @return true if this list contains all of the elements of the specified collection
     */
    public boolean containsAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        return ipFilterRuleList.containsAll(c);
    }

    /**
     * Returns the element at the specified position in this list
     * @param index
     * @return the element at the specified position in this list
     */
    public IpFilterRule get(int index) {
        return ipFilterRuleList.get(index);
    }

    /**
     * Returns true if this list contains no elements
     * @return true if this list contains no elements
     */
    public boolean isEmpty() {
        return ipFilterRuleList.isEmpty();
    }

    /**
     * Remove the ipFilterRule from the list
     * @param ipFilterRule
     */
    public void remove(IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        ipFilterRuleList.remove(ipFilterRule);
    }

    /**
     * Removes the element at the specified position in this list
     * @param index
     * @return the element previously at the specified position
     */
    public IpFilterRule remove(int index) {
        return ipFilterRuleList.remove(index);
    }

    /**
     * Removes from this list all of its elements that are contained in the specified collection
     * @param c
     */
    public void removeAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.removeAll(c);
    }

    /**
     * Retains only the elements in this list that are contained in the specified collection
     * @param c
     */
    public void retainAll(Collection<IpFilterRule> c) {
        if (c == null) {
            throw new NullPointerException("Collection can not be null");
        }
        ipFilterRuleList.retainAll(c);
    }

    /**
     * Replaces the element at the specified position in this list with the specified element
     * @param index
     * @param ipFilterRule
     * @return the element previously at the specified position
     */
    public IpFilterRule set(int index, IpFilterRule ipFilterRule) {
        if (ipFilterRule == null) {
            throw new NullPointerException("IpFilterRule can not be null");
        }
        return ipFilterRuleList.set(index, ipFilterRule);
    }

    /**
     * Returns the number of elements in this list.
     * @return the number of elements in this list.
     */
    public int size() {
        return ipFilterRuleList.size();
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.IpFilteringHandler#accept(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelEvent, java.net.InetSocketAddress)
     */
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e,
            InetSocketAddress inetSocketAddress) throws Exception {
        if (ipFilterRuleList.isEmpty()) {
            // No limitation neither in deny or allow, so accept
            return true;
        }
        InetAddress inetAddress = inetSocketAddress.getAddress();
        Iterator<IpFilterRule> iterator = ipFilterRuleList.iterator();
        IpFilterRule ipFilterRule = null;
        while (iterator.hasNext()) {
            ipFilterRule = iterator.next();
            if (ipFilterRule.contains(inetAddress)) {
                // Match founds, is it a ALLOW or DENY rule
                return ipFilterRule.isAllowRule();
            }
        }
        // No limitation founds and no allow either, but as it is like Firewall rules, it is therefore accepted
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
}
