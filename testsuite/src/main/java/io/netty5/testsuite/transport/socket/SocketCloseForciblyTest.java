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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.SocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class SocketCloseForciblyTest extends AbstractSocketTest {

    @Test
    public void testCloseForcibly(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testCloseForcibly);
    }

    public void testCloseForcibly(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        sb.handler(new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                final SocketChannel childChannel = (SocketChannel) msg;
                // Dispatch on the EventLoop as all operation on Unsafe should be done while on the EventLoop.
                childChannel.executor().execute(() -> {
                    childChannel.config().setSoLinger(0);
                    childChannel.unsafe().closeForcibly();
                });
            }
        }).childHandler(new ChannelHandler() { });

        cb.handler(new ChannelHandler() { });

        Channel sc = sb.bind().get();

        Channel channel = cb.register().get();
        channel.connect(sc.localAddress());
        channel.closeFuture().syncUninterruptibly();
        sc.close().sync();
    }
}
