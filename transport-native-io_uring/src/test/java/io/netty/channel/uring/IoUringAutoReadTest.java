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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringAutoReadTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void testLateAutoRead() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            ServerSocketChannel server = (ServerSocketChannel) new ServerBootstrap()
                    .group(group)
                    .channel(IoUringServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ctx.channel().config().setAutoRead(false);
                            ctx.writeAndFlush(msg, ctx.voidPromise());
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            ctx.read();
                        }
                    })
                    .bind(0).sync().channel();

            try (Socket sock = new Socket(server.localAddress().getAddress(), server.localAddress().getPort())) {
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                out.write(1);
                out.flush();
                Assertions.assertEquals(1, in.read());

                out.write(2);
                out.flush();
                Assertions.assertEquals(2, in.read());
            }
        } finally {
            group.shutdownGracefully();
        }
    }
}
