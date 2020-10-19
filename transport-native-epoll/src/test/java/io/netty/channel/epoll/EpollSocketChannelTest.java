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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class EpollSocketChannelTest {

    @Test
    public void testTcpInfo() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            EpollSocketChannel ch = (EpollSocketChannel) bootstrap.group(group)
                    .channel(EpollSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            EpollTcpInfo info = ch.tcpInfo();
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testTcpInfoReuse() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            EpollSocketChannel ch = (EpollSocketChannel) bootstrap.group(group)
                    .channel(EpollSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            EpollTcpInfo info = new EpollTcpInfo();
            ch.tcpInfo(info);
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void assertTcpInfo0(EpollTcpInfo info) throws Exception {
        Assert.assertNotNull(info);

        Assert.assertTrue(info.state() >= 0);
        Assert.assertTrue(info.caState() >= 0);
        Assert.assertTrue(info.retransmits() >= 0);
        Assert.assertTrue(info.probes() >= 0);
        Assert.assertTrue(info.backoff() >= 0);
        Assert.assertTrue(info.options() >= 0);
        Assert.assertTrue(info.sndWscale() >= 0);
        Assert.assertTrue(info.rcvWscale() >= 0);
        Assert.assertTrue(info.rto() >= 0);
        Assert.assertTrue(info.ato() >= 0);
        Assert.assertTrue(info.sndMss() >= 0);
        Assert.assertTrue(info.rcvMss() >= 0);
        Assert.assertTrue(info.unacked() >= 0);
        Assert.assertTrue(info.sacked() >= 0);
        Assert.assertTrue(info.lost() >= 0);
        Assert.assertTrue(info.retrans() >= 0);
        Assert.assertTrue(info.fackets() >= 0);
        Assert.assertTrue(info.lastDataSent() >= 0);
        Assert.assertTrue(info.lastAckSent() >= 0);
        Assert.assertTrue(info.lastDataRecv() >= 0);
        Assert.assertTrue(info.lastAckRecv() >= 0);
        Assert.assertTrue(info.pmtu() >= 0);
        Assert.assertTrue(info.rcvSsthresh() >= 0);
        Assert.assertTrue(info.rtt() >= 0);
        Assert.assertTrue(info.rttvar() >= 0);
        Assert.assertTrue(info.sndSsthresh() >= 0);
        Assert.assertTrue(info.sndCwnd() >= 0);
        Assert.assertTrue(info.advmss() >= 0);
        Assert.assertTrue(info.reordering() >= 0);
        Assert.assertTrue(info.rcvRtt() >= 0);
        Assert.assertTrue(info.rcvSpace() >= 0);
        Assert.assertTrue(info.totalRetrans() >= 0);
    }

    // See https://github.com/netty/netty/issues/7159
    @Test
    public void testSoLingerNoAssertError() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            EpollSocketChannel ch = (EpollSocketChannel) bootstrap.group(group)
                    .channel(EpollSocketChannel.class)
                    .option(ChannelOption.SO_LINGER, 10)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }
}
