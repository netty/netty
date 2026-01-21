/*
 * Copyright 2017 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class SocketCloseForciblyTest extends AbstractSocketTest {

    @Test
    public void testCloseForcibly(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testCloseForcibly(serverBootstrap, bootstrap);
            }
        });
    }

    public void testCloseForcibly(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        sb.handler(new ChannelInboundHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                SocketChannel childChannel = (SocketChannel) msg;
                childChannel.config().setOption(ChannelOption.SO_LINGER, 0);
                childChannel.close(CompletionHandler.ignore());
            }
        }).childHandler(new ChannelInboundHandler() { });

        cb.handler(new ChannelInboundHandler() { });

        Channel sc = sb.bind().get();

        Future<Channel> cf = cb.connect(sc.localAddress()).awaitUninterruptibly();
        if (cf.isSuccess()) {
            cf.getNow().closeFuture().syncUninterruptibly();
        }
        sc.close().sync();
    }
}
