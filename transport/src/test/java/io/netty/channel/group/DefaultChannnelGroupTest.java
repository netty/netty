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
package io.netty.channel.group;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.Test;

public class DefaultChannnelGroupTest {

    // Test for #1183
    @Test
    public void testNotThrowBlockingOperationException() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup);
        b.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                allChannels.add(ctx.channel());
            }
        });
        b.channel(NioServerSocketChannel.class);

        ChannelFuture f = b.bind(0).syncUninterruptibly();

        if (f.isSuccess()) {
            allChannels.add(f.channel());
            allChannels.close().awaitUninterruptibly();
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        bossGroup.terminationFuture().sync();
        workerGroup.terminationFuture().sync();
    }
}
