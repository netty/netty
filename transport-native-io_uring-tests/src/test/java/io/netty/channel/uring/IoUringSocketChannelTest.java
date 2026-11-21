/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSocketChannelTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testTcpInfo() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());

        try {
            Bootstrap bootstrap = new Bootstrap();
            IoUringSocketChannel ch = (IoUringSocketChannel) bootstrap.group(group)
                    .channel(IoUringSocketChannel.class)
                    .handler(new ChannelInboundHandler() { })
                    .bind(new InetSocketAddress(0)).get();
            IoUringTcpInfo info = ch.tcpInfo();
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testTcpInfoReuse() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());

        try {
            Bootstrap bootstrap = new Bootstrap();
            IoUringSocketChannel ch = (IoUringSocketChannel) bootstrap.group(group)
                    .channel(IoUringSocketChannel.class)
                    .handler(new ChannelInboundHandler() { })
                    .bind(new InetSocketAddress(0)).get();
            IoUringTcpInfo info = new IoUringTcpInfo();
            ch.tcpInfo(info);
            assertTcpInfo0(info);
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void assertTcpInfo0(IoUringTcpInfo info) throws Exception {
        assertNotNull(info);

        assertThat(info.state()).isGreaterThanOrEqualTo(0);
        assertThat(info.caState()).isGreaterThanOrEqualTo(0);
        assertThat(info.retransmits()).isGreaterThanOrEqualTo(0);
        assertThat(info.probes()).isGreaterThanOrEqualTo(0);
        assertThat(info.backoff()).isGreaterThanOrEqualTo(0);
        assertThat(info.options()).isGreaterThanOrEqualTo(0);
        assertThat(info.sndWscale()).isGreaterThanOrEqualTo(0);
        assertThat(info.rcvWscale()).isGreaterThanOrEqualTo(0);
        assertThat(info.rto()).isGreaterThanOrEqualTo(0);
        assertThat(info.ato()).isGreaterThanOrEqualTo(0);
        assertThat(info.sndMss()).isGreaterThanOrEqualTo(0);
        assertThat(info.rcvMss()).isGreaterThanOrEqualTo(0);
        assertThat(info.unacked()).isGreaterThanOrEqualTo(0);
        assertThat(info.sacked()).isGreaterThanOrEqualTo(0);
        assertThat(info.lost()).isGreaterThanOrEqualTo(0);
        assertThat(info.retrans()).isGreaterThanOrEqualTo(0);
        assertThat(info.fackets()).isGreaterThanOrEqualTo(0);
        assertThat(info.lastDataSent()).isGreaterThanOrEqualTo(0);
        assertThat(info.lastAckSent()).isGreaterThanOrEqualTo(0);
        assertThat(info.lastDataRecv()).isGreaterThanOrEqualTo(0);
        assertThat(info.lastAckRecv()).isGreaterThanOrEqualTo(0);
        assertThat(info.pmtu()).isGreaterThanOrEqualTo(0);
        assertThat(info.rcvSsthresh()).isGreaterThanOrEqualTo(0);
        assertThat(info.rtt()).isGreaterThanOrEqualTo(0);
        assertThat(info.rttvar()).isGreaterThanOrEqualTo(0);
        assertThat(info.sndSsthresh()).isGreaterThanOrEqualTo(0);
        assertThat(info.sndCwnd()).isGreaterThanOrEqualTo(0);
        assertThat(info.advmss()).isGreaterThanOrEqualTo(0);
        assertThat(info.reordering()).isGreaterThanOrEqualTo(0);
        assertThat(info.rcvRtt()).isGreaterThanOrEqualTo(0);
        assertThat(info.rcvSpace()).isGreaterThanOrEqualTo(0);
        assertThat(info.totalRetrans()).isGreaterThanOrEqualTo(0);
    }
}
