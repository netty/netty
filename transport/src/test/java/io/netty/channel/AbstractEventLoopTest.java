/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

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
                    public void initChannel(SocketChannel ch) {
                    }
                }).handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    public void initChannel(ServerSocketChannel ch) {
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

    /**
     * Test for https://github.com/netty/netty/issues/14923
     */
    @ParameterizedTest
    @EnumSource(DeregisterMethod.class)
    void testReregisterOnChannelHandlerContext(DeregisterMethod method) throws Exception {
        EventLoopGroup bossGroup = newEventLoopGroup();
        EventLoopGroup workerGroup = newEventLoopGroup();
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        try {
            ServerBootstrap b = new ServerBootstrap();
            ReRegisterHandler reRegisterHandler = new ReRegisterHandler(workerGroup, method, throwable);
            b.group(bossGroup, workerGroup)
                    .channel(newChannel())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("logging", new LoggingHandler());
                            p.addLast(reRegisterHandler);
                        }
                    });

            ChannelFuture f = b.bind(0).sync();

            Channel client = new Bootstrap()
                    .group(workerGroup)
                    .channel(newSocketChannel())
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            ReferenceCountUtil.release(msg);
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                            super.channelReadComplete(ctx);
                            ctx.close();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            throwable.set(cause);
                            super.exceptionCaught(ctx, cause);
                            ctx.close();
                        }
                    })
                    .connect(f.channel().localAddress())
                    .sync()
                    .channel();
            client.closeFuture().addListener(ignore -> f.channel().close());
            client.writeAndFlush(Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII));

            f.channel().closeFuture().sync();
            Throwable caughtThrowable = throwable.get();
            if (caughtThrowable != null) {
                fail(caughtThrowable);
            }
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    enum DeregisterMethod implements Function<ChannelHandlerContext, ChannelFuture> {
        CONTEXT {
            @Override
            public ChannelFuture apply(ChannelHandlerContext ctx) {
                return ctx.deregister();
            }
        },
        CONTEXT_PROMISE {
            @Override
            public ChannelFuture apply(ChannelHandlerContext ctx) {
                return ctx.deregister(ctx.newPromise());
            }
        },
        CHANNEL {
            @Override
            public ChannelFuture apply(ChannelHandlerContext ctx) {
                return ctx.channel().deregister();
            }
        },
        CHANNEL_PROMISE {
            @Override
            public ChannelFuture apply(ChannelHandlerContext ctx) {
                return ctx.channel().deregister(ctx.channel().newPromise());
            }
        }
    }

    @ChannelHandler.Sharable
    static class ReRegisterHandler extends ChannelInboundHandlerAdapter {

        final EventLoopGroup eventLoopGroup;
        private final DeregisterMethod method;
        private final AtomicReference<Throwable> throwable;

        ReRegisterHandler(EventLoopGroup eventLoopGroup,
                                 DeregisterMethod method,
                                 AtomicReference<Throwable> throwable) {
            this.eventLoopGroup = eventLoopGroup;
            this.method = method;
            this.throwable = throwable;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            throwable.set(cause);
            ctx.close();
        }

        @Override
        public void channelRead(ChannelHandlerContext outCtx, Object msg) throws Exception {
            final EventLoop newLoop = anyNotEqual(outCtx.channel().eventLoop());
            ChannelFutureListener pipelieModifyingListener = register -> {
                outCtx.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext inCtx, Object inMsg) {
                        outCtx.writeAndFlush(inMsg);
                    }
                });
                outCtx.fireChannelRead(msg);
            };
            ChannelFutureListener reregisteringListener = deregister -> {
                deregister.channel().pipeline().fireUserEventTriggered("Unregistered user event");
                newLoop.register(deregister.channel()).addListener(pipelieModifyingListener);
            };
            method.apply(outCtx).addListener(reregisteringListener);
        }

        private EventLoop anyNotEqual(EventLoop eventLoop) {
            EventLoop next;
            do {
                next = eventLoopGroup.next();
            } while (eventLoop == next);

            return next;
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownGracefullyNoQuietPeriod() throws Exception {
        EventLoopGroup loop = newEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(loop)
                .channel(newChannel())
                .childHandler(new ChannelInboundHandlerAdapter());

        // Not close the Channel to ensure the EventLoop is still shutdown in time.
        b.bind(0).sync().channel();

        Future<?> f = loop.shutdownGracefully(0, 1, TimeUnit.MINUTES);
        assertTrue(loop.awaitTermination(600, TimeUnit.MILLISECONDS));
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
    protected abstract Class<? extends ServerChannel> newChannel();
    protected abstract Class<? extends SocketChannel> newSocketChannel();
}
