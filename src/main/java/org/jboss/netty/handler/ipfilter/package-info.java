/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/**
 * Implementation of a Ip based Filter handlers.<br>
 * <br><br>
 * <P>The main goal of this package is to allow to filter connections based on IP rules.
 * The main interface is <tt>{@link org.jboss.netty.handler.ipfilter.IpFilteringHandler}</tt> which
 * all filters will extend.</P>
 *
 * <P>Two IP filtering are proposed:<br>
 * <ul>
 * <li>{@link org.jboss.netty.handler.ipfilter.OneIpFilterHandler}: This filter proposes to allow
 *     only one connection by client's IP Address.  I.E. this filter will prevent two connections
 *     from the same client based on its IP address.</li><br><br>
 *
 * <li>{@link org.jboss.netty.handler.ipfilter.IpFilterRuleHandler}: This filter proposes to allow
 *     or block IP range (based on standard notation or on CIDR notation) when the connection is
 *     running. It relies on another class like <tt>IpV4SubnetFilterRule</tt> (IPV4 support only),
 *     <tt>IpSubnetFilterRule</tt> (IPV4 and IPV6 support) or <tt>PatternRule</tt> (string pattern
 *     support) which implements those Ip ranges.</li><br><br>
 *
 * </ul></P>
 *
 * <P>Standard use could be as follow: The accept method must be overridden (of course you can
 * override others).</P>
 *
 * <P><ul>
 * <li><tt>accept</tt> method allows to specify your way of choosing if a new connection is
 * to be allowed or not.</li><br>
 * In <tt>OneIpFilterHandler</tt> and <tt>IpFilterRuleHandler</tt>,
 * this method is already implemented.<br>
 * <br>
 *
 * <li>handleRefusedChannel method is executed when the accept method filters (blocks, so returning
 *     false) the new connection. This method allows you to implement specific actions to be taken
 *     before the channel is closed. After this method is called, the channel is immediately closed.</li><br>
 *
 * So if you want to send back a message to the client, <b>don't forget to return a respectful
 * ChannelFuture, otherwise the message could be missed since the channel will be closed immediately
 * after this call and the waiting on this channelFuture</b> (at least with respect of asynchronous
 * operations).<br><br>
 * Per default implementation this method invokes an {@link org.jboss.netty.handler.ipfilter.IpFilterListener}
 * or returns null if no listener has been set.
 * <br><br>
 *
 * <li><tt>continues</tt> is called when any event appears after CONNECTED event and only for
 * blocked channels.</li><br>
 * It should return True if this new event has to go to next handlers
 * in the pipeline if any, and False (default) if no events has to be passed to the next
 * handlers when a channel is blocked. This is intend to prevent any unnecessary action since the
 * connection is refused.<br>
 * However, you could change its behavior for instance because you don't want that any event
 * will be blocked by this filter by returning always true or according to some events.<br>
 * <b>Note that OPENED and BOUND events are still passed to the next entry in the pipeline since
 * those events come out before the CONNECTED event, so there is no possibility to filter those two events
 * before the CONNECTED event shows up. Therefore, you might want to let CLOSED and UNBOUND be passed
 * to the next entry in the pipeline.</b><br><br>
 * Per default implementation this method invokes an {@link org.jboss.netty.handler.ipfilter.IpFilterListener}
 * or returns false if no listener has been set.
 * <br><br>
 *
 * <li>Finally <tt>handleUpstream</tt> traps the CONNECTED and DISCONNECTED events.</li><br>
 * If in the CONNECTED events the channel is blocked (<tt>accept</tt> refused the channel),
 * then any new events on this channel will be blocked.<br>
 * However, you could change its behavior for instance because you don't want that all events
 * will be blocked by this filter by testing the result of isBlocked, and if so,
 * calling <tt>ctx.sendUpstream(e);</tt> after calling the super method or by changing the
 * <tt>continues</tt> method.<br><br>

 * </ul></P><br><br>
 *
 * A typical setup for ip filter for TCP/IP socket would be:
 *
 * <pre>
 * {@link org.jboss.netty.channel.ChannelPipeline} pipeline = ...;
 *
 * IpFilterRuleHandler firewall = new IpFilterRuleHandler();
 * firewall.addAll(new IpFilterRuleList("+n:localhost, +c:192.168.0.0/27, -n:*"));
 * pipeline.addFirst(&quot;firewall&quot;, firewall);
 * </pre>
 *
 * @apiviz.exclude ^java\.lang\.
 */
package org.jboss.netty.handler.ipfilter;


