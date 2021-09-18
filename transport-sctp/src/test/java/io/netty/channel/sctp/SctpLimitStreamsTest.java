/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.sctp;

import com.sun.nio.sctp.SctpStandardSocketOptions;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.SuppressForbidden;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class SctpLimitStreamsTest {

    @BeforeAll
    public static void checkSupported() {
        assumeTrue(SctpTestUtil.isSctpSupported());
    }

    @SuppressForbidden(reason = "test-only")
    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testSctpInitMaxstreams() throws Exception {
        EventLoopGroup loop = newEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(loop)
                    .channel(serverClass())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(SctpChannelOption.SCTP_INIT_MAXSTREAMS,
                            SctpStandardSocketOptions.InitMaxStreams.create(1, 1))
                    .localAddress(new InetSocketAddress(0))
                    .childHandler(new ChannelInboundHandlerAdapter());

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(loop)
                    .channel(clientClass())
                    .option(SctpChannelOption.SCTP_INIT_MAXSTREAMS,
                            SctpStandardSocketOptions.InitMaxStreams.create(112, 112))
                    .handler(new ChannelInboundHandlerAdapter());

            Channel serverChannel = serverBootstrap.bind()
                    .syncUninterruptibly().channel();
            SctpChannel clientChannel = (SctpChannel) clientBootstrap.connect(serverChannel.localAddress())
                    .syncUninterruptibly().channel();
            assertEquals(1, clientChannel.association().maxOutboundStreams());
            assertEquals(1, clientChannel.association().maxInboundStreams());
            serverChannel.close().syncUninterruptibly();
            clientChannel.close().syncUninterruptibly();
        } finally {
            loop.shutdownGracefully();
        }
    }

    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract Class<? extends SctpChannel> clientClass();
    protected abstract Class<? extends SctpServerChannel> serverClass();
}
