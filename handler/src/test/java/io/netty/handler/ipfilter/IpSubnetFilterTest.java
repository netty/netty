/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.ipfilter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class IpSubnetFilterTest {

    @Test
    public void testIp4SubnetFilterRule() throws Exception {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("192.168.56.1", 24, IpFilterRuleType.ALLOW);
        for (int i = 0; i <= 255; i++) {
            Assert.assertTrue(rule.matches(newSockAddress(String.format("192.168.56.%d", i))));
        }
        Assert.assertFalse(rule.matches(newSockAddress("192.168.57.1")));

        rule = new IpSubnetFilterRule("91.114.240.1", 23, IpFilterRuleType.ALLOW);
        Assert.assertTrue(rule.matches(newSockAddress("91.114.240.43")));
        Assert.assertTrue(rule.matches(newSockAddress("91.114.240.255")));
        Assert.assertTrue(rule.matches(newSockAddress("91.114.241.193")));
        Assert.assertTrue(rule.matches(newSockAddress("91.114.241.254")));
        Assert.assertFalse(rule.matches(newSockAddress("91.115.241.2")));
    }

    @Test
    public void testIp6SubnetFilterRule() {
        IpSubnetFilterRule rule;

        rule = new IpSubnetFilterRule("2001:db8:abcd:0000::", 52, IpFilterRuleType.ALLOW);
        Assert.assertTrue(rule.matches(newSockAddress("2001:db8:abcd:0000::1")));
        Assert.assertTrue(rule.matches(newSockAddress("2001:db8:abcd:0fff:ffff:ffff:ffff:ffff")));
        Assert.assertFalse(rule.matches(newSockAddress("2001:db8:abcd:1000::")));
    }

    @Test
    public void testIpFilterRuleHandler() throws Exception {
        IpFilterRule filter0 = new IpFilterRule() {
            @Override
            public boolean matches(InetSocketAddress ipAndPort) {
                return "192.168.57.1".equals(ipAndPort.getHostName());
            }

            @Override
            public IpFilterRuleType ruleType() {
                return IpFilterRuleType.DENY;
            }
        };

        IpFilterRuleHandler denyHandler = new IpFilterRuleHandler(filter0) {
            private final byte[] message = {1, 2, 3, 4, 5, 6, 7};

            @Override
            protected ChannelFuture rejected(ChannelHandlerContext ctx, InetSocketAddress ipAndPort) {
                Assert.assertTrue(ctx.channel().isActive());
                Assert.assertTrue(ctx.channel().isWritable());
                Assert.assertEquals("192.168.57.1", ipAndPort.getHostName());

                return ctx.writeAndFlush(Unpooled.wrappedBuffer(message));
            }
        };
        EmbeddedChannel chDeny = newEmbeddedInetChannel("192.168.57.1", denyHandler);
        ByteBuf out = chDeny.readOutbound();
        Assert.assertEquals(7, out.readableBytes());
        for (byte i = 1; i <= 7; i++) {
            Assert.assertEquals(i, out.readByte());
        }
        Assert.assertFalse(chDeny.isActive());
        Assert.assertFalse(chDeny.isOpen());

        IpFilterRuleHandler allowHandler = new IpFilterRuleHandler(filter0) {
            @Override
            protected ChannelFuture rejected(ChannelHandlerContext ctx, InetSocketAddress ipAndPort) {
                Assert.fail();
                return null;
            }
        };
        EmbeddedChannel chAllow = newEmbeddedInetChannel("192.168.57.2", allowHandler);
        Assert.assertTrue(chAllow.isActive());
        Assert.assertTrue(chAllow.isOpen());
    }

    @Test
    public void testUniqueIpFilterHandler() {
        UniqueIpFilterHandler handler = new UniqueIpFilterHandler();

        EmbeddedChannel ch1 = newEmbeddedInetChannel("91.92.93.1", handler);
        Assert.assertTrue(ch1.isActive());
        EmbeddedChannel ch2 = newEmbeddedInetChannel("91.92.93.2", handler);
        Assert.assertTrue(ch2.isActive());
        EmbeddedChannel ch3 = newEmbeddedInetChannel("91.92.93.1", handler);
        Assert.assertFalse(ch3.isActive());

        // false means that no data is left to read/write
        Assert.assertFalse(ch1.finish());

        EmbeddedChannel ch4 = newEmbeddedInetChannel("91.92.93.1", handler);
        Assert.assertTrue(ch4.isActive());
    }

    private EmbeddedChannel newEmbeddedInetChannel(final String ipAddress, ChannelHandler... handlers) {
        return new EmbeddedChannel(handlers) {
            @Override
            protected SocketAddress remoteAddress0() {
                return isActive()? new InetSocketAddress(ipAddress, 5421) : null;
            }
        };
    }

    private InetSocketAddress newSockAddress(String ipAddress) {
        return new InetSocketAddress(ipAddress, 1234);
    }
}
