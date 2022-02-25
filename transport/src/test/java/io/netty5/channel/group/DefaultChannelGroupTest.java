/*
 * Copyright 2013 The Netty Project
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
package io.netty5.channel.group;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.Test;

public class DefaultChannelGroupTest {

    // Test for #1183
    @Test
    public void testNotThrowBlockingOperationException() throws Exception {
        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());

        final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup);
        b.childHandler(new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                allChannels.add(ctx.channel());
            }
        });
        b.channel(NioServerSocketChannel.class);

        Future<Channel> f = b.bind(0).syncUninterruptibly();

        if (f.isSuccess()) {
            allChannels.add(f.getNow());
            allChannels.close().awaitUninterruptibly();
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        bossGroup.terminationFuture().sync();
        workerGroup.terminationFuture().sync();
    }
}
