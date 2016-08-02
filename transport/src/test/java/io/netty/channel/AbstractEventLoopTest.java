/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public abstract class AbstractEventLoopTest {

    /**
     * Test for https://github.com/netty/netty/issues/803
     */
    @Test
    public void testReregister() {
        EventLoopGroup group = newEventLoopGroup();
        EventLoopGroup group2 = newEventLoopGroup();
        final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        ChannelFuture future = bootstrap.channel(newChannel()).group(group)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                    }
                }).handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    public void initChannel(ServerSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestChannelHandler());
                        ch.pipeline().addLast(eventExecutorGroup, new TestChannelHandler2());
                    }
                })
                .bind(0).awaitUninterruptibly();

        EventExecutor executor = future.channel().pipeline().context(TestChannelHandler2.class).executor();
        EventExecutor executor1 = future.channel().pipeline().context(TestChannelHandler.class).executor();
        future.channel().deregister().awaitUninterruptibly();
        Channel channel = group2.register(future.channel()).awaitUninterruptibly().channel();
        EventExecutor executorNew = channel.pipeline().context(TestChannelHandler.class).executor();
        assertNotSame(executor1, executorNew);
        assertSame(executor, future.channel().pipeline().context(TestChannelHandler2.class).executor());
    }

    @Test(timeout = 5000)
    public void testShutdownGracefullyNoQuietPeriod() throws Exception {
        EventLoopGroup loop = newEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(loop)
                .channel(newChannel())
                .childHandler(new ChannelInboundHandlerAdapter());

        // Not close the Channel to ensure the EventLoop is still shutdown in time.
        b.bind(0).sync().channel();

        Future<?> f = loop.shutdownGracefully(0, 1, TimeUnit.MINUTES);
        assertTrue(loop.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(f.syncUninterruptibly().isSuccess());
        assertTrue(loop.isShutdown());
        assertTrue(loop.isTerminated());
    }

    private static final class TestChannelHandler extends ChannelDuplexHandler { }

    private static final class TestChannelHandler2 extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception { }
    }

    protected abstract EventLoopGroup newEventLoopGroup();
    protected abstract Class<? extends ServerSocketChannel> newChannel();
}
