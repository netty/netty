/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ipfilter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class IpSubnetFilterTest {

    @Test
    public void testIpv4DefaultRoute() {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("0.0.0.0", 0, IpFilterRuleType.ACCEPT);
        assertTrue(rule.matches(newSockAddress("91.114.240.43")));
        assertTrue(rule.matches(newSockAddress("10.0.0.3")));
        assertTrue(rule.matches(newSockAddress("192.168.93.2")));
    }

    @Test
    public void testIpv4SubnetMaskCorrectlyHandlesIpv6() {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("0.0.0.0", 0, IpFilterRuleType.ACCEPT);
        assertFalse(rule.matches(newSockAddress("2001:db8:abcd:0000::1")));
    }

    @Test
    public void testIpv6SubnetMaskCorrectlyHandlesIpv4() {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("::", 0, IpFilterRuleType.ACCEPT);
        assertFalse(rule.matches(newSockAddress("91.114.240.43")));
    }

    @Test
    public void testIp4SubnetFilterRule() throws Exception {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("192.168.56.1", 24, IpFilterRuleType.ACCEPT);
        for (int i = 0; i <= 255; i++) {
            assertTrue(rule.matches(newSockAddress(String.format("192.168.56.%d", i))));
        }
        assertFalse(rule.matches(newSockAddress("192.168.57.1")));

        rule = new IpSubnetFilterRule("91.114.240.1", 23, IpFilterRuleType.ACCEPT);
        assertTrue(rule.matches(newSockAddress("91.114.240.43")));
        assertTrue(rule.matches(newSockAddress("91.114.240.255")));
        assertTrue(rule.matches(newSockAddress("91.114.241.193")));
        assertTrue(rule.matches(newSockAddress("91.114.241.254")));
        assertFalse(rule.matches(newSockAddress("91.115.241.2")));
    }

    @Test
    public void testIp6SubnetFilterRule() {
        IpSubnetFilterRule rule;

        rule = new IpSubnetFilterRule("2001:db8:abcd:0000::", 52, IpFilterRuleType.ACCEPT);
        assertTrue(rule.matches(newSockAddress("2001:db8:abcd:0000::1")));
        assertTrue(rule.matches(newSockAddress("2001:db8:abcd:0fff:ffff:ffff:ffff:ffff")));
        assertFalse(rule.matches(newSockAddress("2001:db8:abcd:1000::")));
    }

    @Test
    public void testIp6SubnetFilterDefaultRule() {
        IpFilterRule rule = new IpSubnetFilterRule("::", 0, IpFilterRuleType.ACCEPT);
        assertTrue(rule.matches(newSockAddress("7FFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF")));
        assertTrue(rule.matches(newSockAddress("8000::")));
    }

    @Test
    public void testIpFilterRuleHandler() throws Exception {
        IpFilterRule filter0 = new IpFilterRule() {
            @Override
            public boolean matches(InetSocketAddress remoteAddress) {
                return "192.168.57.1".equals(remoteAddress.getHostName());
            }

            @Override
            public IpFilterRuleType ruleType() {
                return IpFilterRuleType.REJECT;
            }
        };

        RuleBasedIpFilter denyHandler = new RuleBasedIpFilter(filter0) {
            private final byte[] message = {1, 2, 3, 4, 5, 6, 7};

            @Override
            protected ChannelFuture channelRejected(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
                assertTrue(ctx.channel().isActive());
                assertTrue(ctx.channel().isWritable());
                assertEquals("192.168.57.1", remoteAddress.getHostName());

                return ctx.writeAndFlush(Unpooled.wrappedBuffer(message));
            }
        };
        EmbeddedChannel chDeny = newEmbeddedInetChannel("192.168.57.1", denyHandler);
        ByteBuf out = chDeny.readOutbound();
        assertEquals(7, out.readableBytes());
        for (byte i = 1; i <= 7; i++) {
            assertEquals(i, out.readByte());
        }
        assertFalse(chDeny.isActive());
        assertFalse(chDeny.isOpen());

        RuleBasedIpFilter allowHandler = new RuleBasedIpFilter(filter0) {
            @Override
            protected ChannelFuture channelRejected(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
                fail();
                return null;
            }
        };
        EmbeddedChannel chAllow = newEmbeddedInetChannel("192.168.57.2", allowHandler);
        assertTrue(chAllow.isActive());
        assertTrue(chAllow.isOpen());
    }

    @Test
    public void testUniqueIpFilterHandler() {
        UniqueIpFilter handler = new UniqueIpFilter();

        EmbeddedChannel ch1 = newEmbeddedInetChannel("91.92.93.1", handler);
        assertTrue(ch1.isActive());
        EmbeddedChannel ch2 = newEmbeddedInetChannel("91.92.93.2", handler);
        assertTrue(ch2.isActive());
        EmbeddedChannel ch3 = newEmbeddedInetChannel("91.92.93.1", handler);
        assertFalse(ch3.isActive());

        // false means that no data is left to read/write
        assertFalse(ch1.finish());

        EmbeddedChannel ch4 = newEmbeddedInetChannel("91.92.93.1", handler);
        assertTrue(ch4.isActive());
    }

    @Test
    public void testBinarySearch() {
        List<IpSubnetFilterRule> ipSubnetFilterRuleList = new ArrayList<IpSubnetFilterRule>();
        ipSubnetFilterRuleList.add(buildRejectIP("1.2.3.4", 32));
        ipSubnetFilterRuleList.add(buildRejectIP("1.1.1.1", 8));
        ipSubnetFilterRuleList.add(buildRejectIP("200.200.200.200", 32));
        ipSubnetFilterRuleList.add(buildRejectIP("108.0.0.0", 4));
        ipSubnetFilterRuleList.add(buildRejectIP("10.10.10.10", 8));
        ipSubnetFilterRuleList.add(buildRejectIP("2001:db8:abcd:0000::", 52));

        // 1.0.0.0/8
        EmbeddedChannel ch1 = newEmbeddedInetChannel("1.1.1.1", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertFalse(ch1.isActive());
        assertTrue(ch1.close().isSuccess());

        // Nothing applies here
        EmbeddedChannel ch2 = newEmbeddedInetChannel("2.2.2.2", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertTrue(ch2.isActive());
        assertTrue(ch2.close().isSuccess());

        // 108.0.0.0/4
        EmbeddedChannel ch3 = newEmbeddedInetChannel("97.100.100.100", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertFalse(ch3.isActive());
        assertTrue(ch3.close().isSuccess());

        // 200.200.200.200/32
        EmbeddedChannel ch4 = newEmbeddedInetChannel("200.200.200.200", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertFalse(ch4.isActive());
        assertTrue(ch4.close().isSuccess());

        // Nothing applies here
        EmbeddedChannel ch5 = newEmbeddedInetChannel("127.0.0.1", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertTrue(ch5.isActive());
        assertTrue(ch5.close().isSuccess());

        // 10.0.0.0/8
        EmbeddedChannel ch6 = newEmbeddedInetChannel("10.1.1.2", new IpSubnetFilter(ipSubnetFilterRuleList));
        assertFalse(ch6.isActive());
        assertTrue(ch6.close().isSuccess());

        //2001:db8:abcd:0000::/52
        EmbeddedChannel ch7 = newEmbeddedInetChannel("2001:db8:abcd:1000::",
                new IpSubnetFilter(ipSubnetFilterRuleList));
        assertFalse(ch7.isActive());
        assertTrue(ch7.close().isSuccess());
    }

    private static IpSubnetFilterRule buildRejectIP(String ipAddress, int mask) {
        return new IpSubnetFilterRule(ipAddress, mask, IpFilterRuleType.REJECT);
    }

    private static EmbeddedChannel newEmbeddedInetChannel(final String ipAddress, ChannelHandler... handlers) {
        return new EmbeddedChannel(handlers) {
            @Override
            protected SocketAddress remoteAddress0() {
                return isActive()? SocketUtils.socketAddress(ipAddress, 5421) : null;
            }
        };
    }

    private static InetSocketAddress newSockAddress(String ipAddress) {
        return SocketUtils.socketAddress(ipAddress, 1234);
    }
}
