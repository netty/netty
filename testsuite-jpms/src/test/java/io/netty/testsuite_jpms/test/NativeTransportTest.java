/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeTransportTest {

    @EnabledOnOs(OS.MAC)
    @Test
    public void testKQueue() throws Exception {
        mySetupClientHostnameValidation(
                KQueueIoHandler.newFactory(),
                KQueueServerSocketChannel.class,
                KQueueSocketChannel.class);
    }

    @EnabledOnOs(OS.LINUX)
    @Test
    public void testEpoll() throws Exception {
        mySetupClientHostnameValidation(
                EpollIoHandler.newFactory(),
                EpollServerSocketChannel.class,
                EpollSocketChannel.class);
    }

    @EnabledOnOs(OS.LINUX)
    @Test
    public void testIoUring() throws Exception {
        mySetupClientHostnameValidation(
                IoUringIoHandler.newFactory(),
                IoUringServerSocketChannel.class,
                IoUringSocketChannel.class);
    }

    private void mySetupClientHostnameValidation(IoHandlerFactory ioHandlerFactory,
                                                 Class<? extends ServerSocketChannel> serverSocketChannelFactory,
                                                 Class<? extends SocketChannel> socketChannelFactory
    ) throws InterruptedException {

        ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();

        sb.group(new MultiThreadIoEventLoopGroup(ioHandlerFactory),
                new MultiThreadIoEventLoopGroup(ioHandlerFactory));
        sb.channel(serverSocketChannelFactory);
        sb.childHandler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();

                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ByteBuf) {
                            ctx.write(msg);
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                        ctx.flush();
                    }
                });
            }
        });

        List<String> received = Collections.synchronizedList(new ArrayList<>());

        cb.group(new MultiThreadIoEventLoopGroup(ioHandlerFactory));
        cb.channel(socketChannelFactory);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ByteBuf) {
                            ByteBuf buff = (ByteBuf) msg;
                            try {
                                received.add(buff.toString(StandardCharsets.UTF_8));
                            } finally {
                                buff.release();
                            }
                        }
                    }
                });
            }
        });

        Channel serverChannel = sb.bind(new InetSocketAddress("localhost", 0)).sync().channel();
        final int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress("localhost", port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        Channel clientChannel = ccf.channel();
        clientChannel.writeAndFlush(Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8)).sync();

        while (received.isEmpty()) {
            Thread.sleep(100);
        }
        assertEquals("hello", received.get(0));
    }
}
