/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PollRemoveTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    private static void ioUringTest() throws Exception {
        Class<? extends ServerSocketChannel> clazz = IoUringServerSocketChannel.class;
        final EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, IoUringIoHandler.newFactory());

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup)
                    .channel(clazz)
                    .handler(new LoggingHandler(LogLevel.TRACE))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) { }
                    });

            Channel sc = b.bind(2020).asStage().get();

            // close ServerChannel
            sc.close().asStage().sync();
        } finally {
            bossGroup.shutdownGracefully().asStage().sync();
        }
    }

    @Test
    @Timeout(10)
    public void test() throws Exception {
        ioUringTest();
        ioUringTest();
    }
}

