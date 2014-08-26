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
package org.jboss.netty.handler.ipfilter;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.Assert.*;

public class IpFilterRuleTest {
    public static boolean accept(IpFilterRuleHandler h, InetSocketAddress addr) throws Exception {
        System.err.print("accept(rules(");
        for (int i = 0; i < h.size(); i ++) {
            final IpFilterRule rule = h.get(i);
            if (rule.isAllowRule()) {
                System.err.print("allow(");
            } else {
                System.err.print("deny(");
            }

            if (rule instanceof PatternRule) {
                System.err.print(((PatternRule) rule).getPattern());
            } else {
                System.err.print(rule);
            }
            System.err.print(')');
            if (i != h.size() - 1) {
                System.err.print(", ");
            }
        }
        System.err.print("), ");
        System.err.print(addr);
        System.err.print(") = ");
        boolean result = h.accept(new ChannelHandlerContext() {

            public boolean canHandleDownstream() {
                return false;
            }

            public boolean canHandleUpstream() {
                return false;
            }

            public Object getAttachment() {
                return null;
            }

            public Channel getChannel() {
                return null;
            }

            public ChannelHandler getHandler() {
                return null;
            }

            public String getName() {
                return null;
            }

            public ChannelPipeline getPipeline() {
                return null;
            }

            public void sendDownstream(ChannelEvent e) {
                // NOOP
            }

            public void sendUpstream(ChannelEvent e) {
                // NOOP
            }

            public void setAttachment(Object attachment) {
                // NOOP
            }

        }, new UpstreamMessageEvent(new Channel() {

            public ChannelFuture bind(SocketAddress localAddress) {
                return null;
            }

            public ChannelFuture close() {
                return null;
            }

            public ChannelFuture connect(SocketAddress remoteAddress) {
                return null;
            }

            public ChannelFuture disconnect() {
                return null;
            }

            public ChannelFuture getCloseFuture() {
                return null;
            }

            public ChannelConfig getConfig() {
                return null;
            }

            public ChannelFactory getFactory() {
                return null;
            }

            public Integer getId() {
                return null;
            }

            public int getInterestOps() {
                return 0;
            }

            public SocketAddress getLocalAddress() {
                return null;
            }

            public Channel getParent() {
                return null;
            }

            public ChannelPipeline getPipeline() {
                return null;
            }

            public SocketAddress getRemoteAddress() {
                return null;
            }

            public boolean isBound() {
                return false;
            }

            public boolean isConnected() {
                return false;
            }

            public boolean isOpen() {
                return false;
            }

            public boolean isReadable() {
                return false;
            }

            public boolean isWritable() {
                return false;
            }

            public ChannelFuture setInterestOps(int interestOps) {
                return null;
            }

            public ChannelFuture setReadable(boolean readable) {
                return null;
            }

            public ChannelFuture unbind() {
                return null;
            }

            public ChannelFuture write(Object message) {
                return null;
            }

            public ChannelFuture write(Object message, SocketAddress remoteAddress) {
                return null;
            }

            public int compareTo(Channel o) {
                return 0;
            }

            public int hashCode() {
                return 0;
            }

            public boolean equals(Object o) {
                return this == o;
            }

            public Object getAttachment() {
                return null;
            }

            public void setAttachment(Object attachment) {
                // NOOP
            }

        }, h, addr), addr);
        System.err.println(result);
        return result;
    }

    @Test
    public void testIpFilterRule() throws Exception {
        IpFilterRuleHandler h = new IpFilterRuleHandler();
        h.addAll(new IpFilterRuleList("+n:localhost, -n:*"));
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList("+n:*" + InetAddress.getLocalHost().getHostName().substring(1) + ", -n:*"));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList("+c:" + InetAddress.getLocalHost().getHostAddress() + "/32, -n:*"));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertFalse(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList("+c:0.0.0.0/0, -n:*"));
        addr = new InetSocketAddress("91.114.240.43", 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress("10.0.0.3", 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress("192.168.93.2", 8080);
        assertTrue(accept(h, addr));

        h.clear();
        h.addAll(new IpFilterRuleList(""));
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));

        h.clear();
        addr = new InetSocketAddress(InetAddress.getLocalHost(), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8080);
        assertTrue(accept(h, addr));
        addr = new InetSocketAddress(InetAddress.getByName(InetAddress.getLocalHost().getHostName()), 8080);
        assertTrue(accept(h, addr));
    }

}
