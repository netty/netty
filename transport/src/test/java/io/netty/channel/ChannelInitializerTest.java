/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ChannelInitializerTest {
    private static final int TIMEOUT_MILLIS = 1000;
    private static final LocalAddress SERVER_ADDRESS = new LocalAddress("addr");
    private EventLoopGroup group;
    private ServerBootstrap server;
    private Bootstrap client;
    private InspectableHandler testHandler;

    @BeforeEach
    public void setUp() {
        group = new DefaultEventLoopGroup(1);
        server = new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .localAddress(SERVER_ADDRESS);
        client = new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        testHandler = new InspectableHandler();
    }

    @AfterEach
    public void tearDown() {
        group.shutdownGracefully(0, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void testInitChannelThrowsRegisterFirst() {
        testInitChannelThrows(true);
    }

    @Test
    public void testInitChannelThrowsRegisterAfter() {
        testInitChannelThrows(false);
    }

    private void testInitChannelThrows(boolean registerFirst) {
        final Exception exception = new Exception();
        final AtomicReference<Throwable> causeRef = new AtomicReference<Throwable>();

        ChannelPipeline pipeline = new LocalChannel().pipeline();

        if (registerFirst) {
            group.register(pipeline.channel()).syncUninterruptibly();
        }
        pipeline.addFirst(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                throw exception;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                causeRef.set(cause);
                super.exceptionCaught(ctx, cause);
            }
        });

        if (!registerFirst) {
            group.register(pipeline.channel()).syncUninterruptibly();
        }
        pipeline.channel().close().syncUninterruptibly();
        pipeline.channel().closeFuture().syncUninterruptibly();

        assertSame(exception, causeRef.get());
    }

    @Test
    public void testChannelInitializerInInitializerCorrectOrdering() {
        final ChannelInboundHandlerAdapter handler1 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler2 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler3 = new ChannelInboundHandlerAdapter();
        final ChannelInboundHandlerAdapter handler4 = new ChannelInboundHandlerAdapter();

        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(handler1);
                ch.pipeline().addLast(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(handler2);
                        ch.pipeline().addLast(handler3);
                    }
                });
                ch.pipeline().addLast(handler4);
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().syncUninterruptibly().channel();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }).syncUninterruptibly();
            Iterator<Map.Entry<String, ChannelHandler>> handlers = channel.pipeline().iterator();
            assertSame(handler1, handlers.next().getValue());
            assertSame(handler2, handlers.next().getValue());
            assertSame(handler3, handlers.next().getValue());
            assertSame(handler4, handlers.next().getValue());
            assertFalse(handlers.hasNext());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    public void testChannelInitializerReentrance() {
        final AtomicInteger registeredCalled = new AtomicInteger(0);
        final ChannelInboundHandlerAdapter handler1 = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                registeredCalled.incrementAndGet();
            }
        };
        final AtomicInteger initChannelCalled = new AtomicInteger(0);
        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                initChannelCalled.incrementAndGet();
                ch.pipeline().addLast(handler1);
                ch.pipeline().fireChannelRegistered();
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().syncUninterruptibly().channel();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.eventLoop().submit(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }).syncUninterruptibly();
            assertEquals(1, initChannelCalled.get());
            assertEquals(2, registeredCalled.get());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = TIMEOUT_MILLIS, unit = TimeUnit.MILLISECONDS)
    public void firstHandlerInPipelineShouldReceiveChannelRegisteredEvent() {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addFirst(testHandler);
            }
        });
    }

    @Test
    @Timeout(value = TIMEOUT_MILLIS, unit = TimeUnit.MILLISECONDS)
    public void lastHandlerInPipelineShouldReceiveChannelRegisteredEvent() {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addLast(testHandler);
            }
        });
    }

    @Test
    public void testAddFirstChannelInitializer() {
        testAddChannelInitializer(true);
    }

    @Test
    public void testAddLastChannelInitializer() {
        testAddChannelInitializer(false);
    }

    private static void testAddChannelInitializer(final boolean first) {
        final AtomicBoolean called = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelHandler handler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        called.set(true);
                    }
                };
                if (first) {
                    ch.pipeline().addFirst(handler);
                } else {
                    ch.pipeline().addLast(handler);
                }
            }
        });
        channel.finish();
        assertTrue(called.get());
    }

    private void testChannelRegisteredEventPropagation(ChannelInitializer<LocalChannel> init) {
        Channel clientChannel = null, serverChannel = null;
        try {
            server.childHandler(init);
            serverChannel = server.bind().syncUninterruptibly().channel();
            clientChannel = client.connect(SERVER_ADDRESS).syncUninterruptibly().channel();
            assertEquals(1, testHandler.channelRegisteredCount.get());
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testChannelInitializerEventExecutor() throws Throwable {
        final AtomicInteger invokeCount = new AtomicInteger();
        final AtomicInteger completeCount = new AtomicInteger();
        final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
        LocalAddress addr = new LocalAddress("test");

        final EventExecutor executor = new DefaultEventLoop() {
            private final ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor();
            private Thread thread;

            @Override
            public void shutdown() {
                execService.shutdown();
            }

            @Override
            public synchronized boolean inEventLoop(Thread thread) {
                // Return false every other time which will ensure we hit the execute(...) path
                if (thread == null) {
                    thread = Thread.currentThread();
                    return false;
                }
                boolean result = thread == Thread.currentThread();
                thread = null;
                return result;
            }

            @Override
            public boolean isShuttingDown() {
                return false;
            }

            @Override
            public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                throw new IllegalStateException();
            }

            @Override
            public Future<?> terminationFuture() {
                throw new IllegalStateException();
            }

            @Override
            public boolean isShutdown() {
                return execService.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return execService.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return execService.awaitTermination(timeout, unit);
            }

            @Override
            public void execute(Runnable command) {
                execService.execute(command);
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .localAddress(addr)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) {
                        ch.pipeline().addLast(executor, new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                invokeCount.incrementAndGet();
                                ChannelHandlerContext ctx = ch.pipeline().context(this);
                                assertNotNull(ctx);
                                ch.pipeline().addAfter(ctx.executor(),
                                        ctx.name(), null, new ChannelInboundHandlerAdapter() {
                                            @Override
                                            public void channelRead(ChannelHandlerContext ctx, Object msg)  {
                                                // just drop on the floor.
                                            }

                                            @Override
                                            public void handlerRemoved(ChannelHandlerContext ctx) {
                                                latch.countDown();
                                            }
                                        });
                                completeCount.incrementAndGet();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                if (cause instanceof AssertionError) {
                                    errorRef.set(cause);
                                }
                            }
                        });
                    }
                });

        Channel server = serverBootstrap.bind().sync().channel();

        Bootstrap clientBootstrap = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .remoteAddress(addr)
                .handler(new ChannelInboundHandlerAdapter());

        Channel client = clientBootstrap.connect().sync().channel();
        client.writeAndFlush("Hello World").sync();

        client.close().sync();
        server.close().sync();

        client.closeFuture().sync();
        server.closeFuture().sync();

        // Wait until the handler is removed from the pipeline and so no more events are handled by it.
        latch.await();

        assertEquals(1, invokeCount.get());
        assertEquals(invokeCount.get(), completeCount.get());

        Throwable cause = errorRef.get();
        if (cause != null) {
            throw cause;
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void closeChannel(Channel c) {
        if (c != null) {
            c.close().syncUninterruptibly();
        }
    }

    private static final class InspectableHandler extends ChannelDuplexHandler {
        final AtomicInteger channelRegisteredCount = new AtomicInteger(0);

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            channelRegisteredCount.incrementAndGet();
            ctx.fireChannelRegistered();
        }
    }
}
