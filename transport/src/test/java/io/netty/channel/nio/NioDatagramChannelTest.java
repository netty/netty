/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.nio;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;


public class NioDatagramChannelTest {

    /**
     * Test try to reproduce issue #1335
     */
    @Test
    public void testBindMultiple() throws Exception {
        DefaultChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            for (int i = 0; i < 100; i++) {
                Bootstrap udpBootstrap = new Bootstrap();
                udpBootstrap.group(group).channel(NioDatagramChannel.class)
                        .option(ChannelOption.SO_BROADCAST, true)
                        .handler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                // Discard
                                ReferenceCountUtil.release(msg);
                            }
                        });
                DatagramChannel datagramChannel = (DatagramChannel) udpBootstrap
                        .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
                channelGroup.add(datagramChannel);
            }
            Assert.assertEquals(100, channelGroup.size());
        } finally {
            channelGroup.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
