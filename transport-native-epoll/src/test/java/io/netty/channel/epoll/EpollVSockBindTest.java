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
package io.netty.channel.epoll;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class EpollVSockBindTest {
    @BeforeAll
    public static void loadJNI() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testBindWithOnlyPortFails() {
        final ServerBootstrap bootstrap = getVSockEchoServer();
        assertThrows(RuntimeException.class, () -> {
            bootstrap.bind(8080).sync();
        }, "Should not be able to start service with only the port");
    }

    @Test
    public void testBindWithInetStringFails() throws RuntimeException {
        final ServerBootstrap bootstrap = getVSockEchoServer();
        assertThrows(RuntimeException.class, () -> {
            bootstrap.bind("1", 8080).sync();
        }, "Should not be able to start service with inetHost and inetPort");
    }

    @Test
    public void testBindWithInetAddressFails() throws RuntimeException {
        final ServerBootstrap bootstrap = getVSockEchoServer();
        assertThrows(RuntimeException.class, () -> {
            bootstrap.bind(InetAddress.getLoopbackAddress(), 8080).sync();
        }, "Should not be able to start service with inetHost and inetPort");
    }

    private static ServerBootstrap getVSockEchoServer() {
        final EchoServerHandler serverHandler = new EchoServerHandler();
        final ServerBootstrap bootstrap = EpollSocketTestPermutation.INSTANCE.serverVSocket().get(0).newInstance();
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(serverHandler);
            }
        });
        return bootstrap;
    }

    private static final class EchoServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buffer = ctx.alloc().buffer();
                ByteBuf buf = (ByteBuf) msg;
                buffer.writeBytes(buf);
                buf.release();
                ctx.channel().writeAndFlush(buffer);
            } else {
                throw new IllegalArgumentException("Unexpected message type: " + msg);
            }
        }
    }
}
