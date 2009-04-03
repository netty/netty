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

/**
 * Implementation of a Ip based Filter handlers.<br>
 * <br><br>
 *
 *
 * <P>The main goal of this package is to allow to filter connections based on IP rules. 
 * The main class is <tt>IpFilteringHandler</tt> which all filters will extend.</P>
 *
 * <P>Two IP filtering are proposed:<br>
 * <ul>
 * <li> <tt>{@link OneIpFilterHandler}</tt>: This filter proposes to allow only one connection by client's IP Address.
 * I.E. this filter will prevent two connections from the same client based on its IP address.</li><br><br>
 *
 * <li> <tt>{@link IpFilterRuleHandler}</tt>: This filter proposes to allow or block IP range (based on standard notation
 * or on CIDR notation) when the connection is running. It relies on another class <tt>IpSubnetFilterRule</tt> 
 * which implements those Ip ranges.</li><br><br>
 *
 * </ul></P>
 *
 * <P>Standard use could be as follow: There are at least two methods that can be overridden (of course you can
 * overridden others).</P>
 *
 * <P><ul>
 * <li><tt>accept</tt> method allow to specify your way of choosing if a new connection is
 * to be allowed or not.</li><br>
 * In <tt>OneIpFilterHandler</tt> and <tt>IpFilterRuleHandler</tt>, 
 * this method is already made.<br>
 * <br>
 *
 * <li><tt>handleRefusedChannel</tt> method is executed when the accept method filters (blocks, so returning false)
 * the new connection. This method allows you to implement specific actions to be taken before the channel is
 * closed. After this method is called, the channel is immediately closed.</li><br>
 * So if you want to send back a message to the client, <b>don't forget to return a respectful ChannelFuture, 
 * otherwise the message could be missed since the channel will be closed immediately after this
 * call and the waiting on this channelFuture</b> (at least with respect of asynchronous operations).<br><br>
 *
 * <li><tt>isBlocked</tt> is called when the CLOSED or any other event appears.</li><br>
 * It returns True if the channel was previously blocked and therefore any new events will not go to next handlers 
 * in the pipeline if any. This is intend to prevent any action unnecessary since the connection is refused.<br>
 * However, you could change its behavior for instance because you don't want that any event
 * will be blocked by this filter by returning always false.<br>
 * <b>Note that OPENED and BOUND events are still passed to the next entry in the pipeline since
 * those events come out before the CONNECTED event, so there is no possibility to filter those two events
 * before the CONNECTED event shows up.</b><br><br>
 * 
 * <li>Finally <tt>handleUpstream</tt> traps the CONNECTED and CLOSED events.</li><br>
 * If in the CONNECTED events the channel is blocked (<tt>accept</tt> refused the channel),
 * then any new events on this channel (except CLOSED) will be blocked.<br>
 * However, you could change its behavior for instance because you don't want that all events
 * will be blocked by this filter by testing the result of isBlocked, and if so, 
 * calling <tt>ctx.sendUpstream(e);</tt> after calling the super method or by changing the <tt>isBlocked</tt> method.<br><br>

 * </ul></P><br><br>
 *
 *
 * @apiviz.exclude ^java\.lang\.
 */
package org.jboss.netty.handler.ipfilter;
