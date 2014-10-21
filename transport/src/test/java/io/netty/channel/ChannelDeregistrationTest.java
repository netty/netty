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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.PausableEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * These tests should work with any {@link SingleThreadEventLoop} implementation. We chose the
 * {@link io.netty.channel.nio.NioEventLoop} because it's the most commonly used {@link EventLoop}.
 */
public class ChannelDeregistrationTest {

    private static final Runnable NOEXEC = new Runnable() {
        @Override
        public void run() {
            fail();
        }
    };

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {
        }
    };

    /**
     * Test deregistration and re-registration of a {@link Channel} within a {@link ChannelHandler}.
     *
     * The first {@link ChannelHandler} in the {@link ChannelPipeline} deregisters the {@link Channel} in the
     * {@linkplain ChannelHandler#channelRead(ChannelHandlerContext, Object)} method, while
     * the subsequent {@link ChannelHandler}s make sure that the {@link Channel} really is deregistered. The last
     * {@link ChannelHandler} registers the {@link Channel} with a new {@link EventLoop} and triggers a
     * {@linkplain ChannelHandler#write(ChannelHandlerContext, Object, ChannelPromise)} event, that is then
     * used by all {@link ChannelHandler}s to ensure that the {@link Channel} was correctly registered with the
     * new {@link EventLoop}.
     *
     * Most of the {@link ChannelHandler}s in the pipeline are assigned custom {@link EventExecutorGroup}s.
     * It's important to make sure that they are preserved during and after
     * {@linkplain io.netty.channel.Channel#deregister()}.
     */
    @Test(timeout = 5000)
    public void testDeregisterFromDifferentEventExecutorGroup() throws Exception  {
        final AtomicBoolean handlerExecuted1 = new AtomicBoolean();
        final AtomicBoolean handlerExecuted2 = new AtomicBoolean();
        final AtomicBoolean handlerExecuted3 = new AtomicBoolean();
        final AtomicBoolean handlerExecuted4 = new AtomicBoolean();
        final AtomicBoolean handlerExecuted5 = new AtomicBoolean();

        final EventLoopGroup group1 = new NioEventLoopGroup(1);
        final EventLoopGroup group2 = new NioEventLoopGroup(1);
        final EventLoopGroup group3 = new NioEventLoopGroup(1);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        Channel serverChannel = serverBootstrap.group(group1)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        // Deregister the Channel from the EventLoop group1.
                        ch.pipeline().addLast(new DeregisterHandler(handlerExecuted1, group1.next(), group2.next()));
                        // Ensure that the Channel is deregistered from EventLoop group1. Also make
                        // sure that despite deregistration the ChannelHandler is executed by the
                        // specified EventLoop.
                        ch.pipeline().addLast(group2, new ExpectDeregisteredHandler(handlerExecuted2, group2.next()));
                        ch.pipeline().addLast(group3, new ExpectDeregisteredHandler(handlerExecuted3, group3.next()));
                        ch.pipeline().addLast(group2, new ExpectDeregisteredHandler(handlerExecuted4, group2.next()));
                        // Register the Channel with EventLoop group2.
                        ch.pipeline().addLast(group3,
                                new ReregisterHandler(handlerExecuted5, group3.next(), group2.next()));
                    }
                }).bind(0).sync().channel();
        SocketAddress address = serverChannel.localAddress();
        Socket s = new Socket(NetUtil.LOCALHOST, ((InetSocketAddress) address).getPort());
        // write garbage just so to get channelRead(...) invoked.
        s.getOutputStream().write(1);

        while (!(handlerExecuted1.get() &&
                handlerExecuted2.get() &&
                handlerExecuted3.get() &&
                handlerExecuted4.get() &&
                handlerExecuted5.get())) {
            Thread.sleep(10);
        }

        s.close();
        serverChannel.close();
        group1.shutdownGracefully();
        group2.shutdownGracefully();
        group3.shutdownGracefully();
    }

    /**
     * Make sure the {@link EventLoop} and {@link ChannelHandlerInvoker} accessible from within a
     * {@link ChannelHandler} are wrapped by a {@link PausableEventExecutor}.
     */
    @Test(timeout = 5000)
    public void testWrappedEventLoop() throws Exception {
        final AtomicBoolean channelActiveCalled1 = new AtomicBoolean();
        final AtomicBoolean channelActiveCalled2 = new AtomicBoolean();
        final EventLoopGroup group1 = new NioEventLoopGroup();
        final EventLoopGroup group2 = new NioEventLoopGroup();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        Channel serverChannel = serverBootstrap.group(group1)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestChannelHandler3(channelActiveCalled1));
                        ch.pipeline().addLast(group2, new TestChannelHandler4(channelActiveCalled2));
                    }
                }).bind(0).sync().channel();
        SocketAddress address = serverChannel.localAddress();
        Socket client = new Socket(NetUtil.LOCALHOST, ((InetSocketAddress) address).getPort());

        while (!(channelActiveCalled1.get() && channelActiveCalled2.get())) {
            Thread.sleep(10);
        }

        client.close();
        serverChannel.close();
        group1.shutdownGracefully();
        group2.shutdownGracefully();
    }

    /**
     * Test for https://github.com/netty/netty/issues/803
     */
    @Test
    public void testReregister() throws Exception {
        final EventLoopGroup group1 = new NioEventLoopGroup();
        final EventLoopGroup group2 = new NioEventLoopGroup();
        final EventExecutorGroup group3 = new DefaultEventExecutorGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        ChannelFuture future = bootstrap.channel(NioServerSocketChannel.class).group(group1)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                    }
                }).handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    public void initChannel(ServerSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestChannelHandler1());
                        ch.pipeline().addLast(group3, new TestChannelHandler2());
                    }
                })
                .bind(0).awaitUninterruptibly();

        EventExecutor executor1 = future.channel().pipeline().context(TestChannelHandler1.class).executor();
        EventExecutor unwrapped1 = executor1.unwrap();
        EventExecutor executor3 = future.channel().pipeline().context(TestChannelHandler2.class).executor();

        future.channel().deregister().sync();

        Channel channel = group2.register(future.channel()).sync().channel();
        EventExecutor executor2 = channel.pipeline().context(TestChannelHandler1.class).executor();

        // same wrapped executor
        assertSame(executor1, executor2);
        // different executor under the wrapper
        assertNotSame(unwrapped1, executor2.unwrap());
        // executor3 must remain unchanged
        assertSame(executor3.unwrap(), future.channel().pipeline()
                .context(TestChannelHandler2.class)
                .executor()
                .unwrap());
    }

    /**
     * See https://github.com/netty/netty/issues/2814
     */
    @Test(timeout = 5000)
    public void testPromise() {
        ChannelHandler handler = new TestChannelHandler1();
        AbstractChannel ch = new EmbeddedChannel(handler);
        DefaultChannelPipeline p = new DefaultChannelPipeline(ch);
        DefaultChannelHandlerInvoker invoker = new DefaultChannelHandlerInvoker(GlobalEventExecutor.INSTANCE);

        ChannelHandlerContext ctx = new DefaultChannelHandlerContext(p, invoker, "Test", handler);
        // Make sure no ClassCastException is thrown
        Promise<Integer> promise = ctx.executor().newPromise();
        promise.setSuccess(0);
        assertTrue(promise.isSuccess());

        ctx = new DefaultChannelHandlerContext(p, null, "Test", handler);
        // Make sure no ClassCastException is thrown
        promise = ctx.executor().newPromise();
        promise.setSuccess(0);
        assertTrue(promise.isSuccess());
    }

    private static final class TestChannelHandler1 extends ChannelHandlerAdapter { }

    private static final class TestChannelHandler2 extends ChannelHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception { }
    }

    private static final class TestChannelHandler3 extends ChannelHandlerAdapter {
        AtomicBoolean channelActiveCalled;

        TestChannelHandler3(AtomicBoolean channelActiveCalled) {
            this.channelActiveCalled = channelActiveCalled;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channelActiveCalled.set(true);

            assertTrue(ctx.executor() instanceof PausableEventExecutor);
            assertTrue(ctx.channel().eventLoop() instanceof PausableEventExecutor);
            assertTrue(ctx.invoker().executor() instanceof PausableEventExecutor);
            assertTrue(ctx.channel().eventLoop().asInvoker().executor() instanceof PausableEventExecutor);
            assertSame(ctx.executor().unwrap(), ctx.channel().eventLoop().unwrap());

            super.channelActive(ctx);
        }
    }

    private static final class TestChannelHandler4 extends ChannelHandlerAdapter {
        AtomicBoolean channelActiveCalled;

        TestChannelHandler4(AtomicBoolean channelActiveCalled) {
            this.channelActiveCalled = channelActiveCalled;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channelActiveCalled.set(true);
            assertTrue(ctx.executor() instanceof PausableEventExecutor);
            assertTrue(ctx.channel().eventLoop() instanceof PausableEventExecutor);
            assertTrue(ctx.invoker().executor() instanceof PausableEventExecutor);
            assertTrue(ctx.channel().eventLoop().asInvoker().executor() instanceof PausableEventExecutor);

            // This is executed by its own invoker, which has to be wrapped by
            // a separate PausableEventExecutor.
            assertNotSame(ctx.executor(), ctx.channel().eventLoop());
            assertNotSame(ctx.executor().unwrap(), ctx.channel().eventLoop().unwrap());

            super.channelActive(ctx);
        }
    }

    private static final class DeregisterHandler extends ChannelHandlerAdapter {

        final AtomicBoolean handlerExecuted;
        final EventLoop expectedEventLoop1;
        final EventLoop expectedEventLoop2;

        DeregisterHandler(AtomicBoolean handlerExecuted, EventLoop expectedEventLoop1, EventLoop expectedEventLoop2) {
            this.handlerExecuted = handlerExecuted;
            this.expectedEventLoop1 = expectedEventLoop1;
            this.expectedEventLoop2 = expectedEventLoop2;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
            assertSame(expectedEventLoop1, ctx.executor().unwrap());
            assertTrue(ctx.channel().isRegistered());
            ctx.channel().deregister().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    assertFalse(ctx.channel().isRegistered());

                    boolean success = false;
                    try {
                        ctx.channel().eventLoop().execute(NOEXEC);
                        success = true;
                    } catch (Throwable t) {
                        assertTrue(t instanceof RejectedExecutionException);
                    }
                    assertFalse(success);

                    handlerExecuted.set(true);
                    ctx.fireChannelRead(msg);
                }
            });
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            assertSame(expectedEventLoop2, ctx.executor().unwrap());

            assertTrue(ctx.channel().isRegistered());
            ctx.executor().execute(NOOP);
            ctx.channel().eventLoop().execute(NOOP);

            promise.setSuccess();
        }
    }

    private static final class ExpectDeregisteredHandler extends ChannelHandlerAdapter {

        final AtomicBoolean handlerExecuted;
        final EventLoop expectedEventLoop;

        ExpectDeregisteredHandler(AtomicBoolean handlerExecuted, EventLoop expectedEventLoop) {
            this.handlerExecuted = handlerExecuted;
            this.expectedEventLoop = expectedEventLoop;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            assertSame(expectedEventLoop, ctx.executor().unwrap());
            assertFalse(ctx.channel().isRegistered());

            boolean success = false;
            try {
                ctx.channel().eventLoop().execute(NOEXEC);
                success = true;
            } catch (Throwable t) {
                assertTrue(t instanceof RejectedExecutionException);
            }
            assertFalse(success);

            handlerExecuted.set(true);
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            assertSame(expectedEventLoop, ctx.executor().unwrap());

            assertTrue(ctx.channel().isRegistered());
            ctx.executor().execute(NOOP);
            ctx.channel().eventLoop().execute(NOOP);

            super.write(ctx, msg, promise);
        }
    }

    private static final class ReregisterHandler extends ChannelHandlerAdapter {
        final AtomicBoolean handlerExecuted;
        final EventLoop expectedEventLoop;
        final EventLoop newEventLoop;

        ReregisterHandler(AtomicBoolean handlerExecuted, EventLoop expectedEventLoop, EventLoop newEventLoop) {
            this.handlerExecuted = handlerExecuted;
            this.expectedEventLoop = expectedEventLoop;
            this.newEventLoop = newEventLoop;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
            assertSame(expectedEventLoop, ctx.executor().unwrap());

            assertFalse(ctx.channel().isRegistered());
            newEventLoop.register(ctx.channel()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    assertTrue(ctx.channel().isRegistered());

                    ctx.executor().execute(NOOP);
                    ctx.channel().eventLoop().execute(NOOP);

                    ctx.write(Unpooled.buffer(), ctx.channel().newPromise()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            handlerExecuted.set(true);
                        }
                    });
                }
            });

            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            assertSame(expectedEventLoop, ctx.executor().unwrap());

            assertTrue(ctx.channel().isRegistered());
            ctx.executor().execute(NOOP);
            ctx.channel().eventLoop().execute(NOOP);

            super.write(ctx, msg, promise);
        }
    }
}
