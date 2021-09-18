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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerMask.Skip;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultChannelPipelineTest {

    private static EventLoopGroup group;

    private Channel self;
    private Channel peer;

    @BeforeAll
    public static void beforeClass() throws Exception {
        group = new DefaultEventLoopGroup(1);
    }

    @AfterAll
    public static void afterClass() throws Exception {
        group.shutdownGracefully().sync();
    }

    private void setUp(final ChannelHandler... handlers) throws Exception {
        final AtomicReference<Channel> peerRef = new AtomicReference<Channel>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group).channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                peerRef.set(ctx.channel());
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ReferenceCountUtil.release(msg);
            }
        });

        ChannelFuture bindFuture = sb.bind(LocalAddress.ANY).sync();

        Bootstrap b = new Bootstrap();
        b.group(group).channel(LocalChannel.class);
        b.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            protected void initChannel(LocalChannel ch) {
                ch.pipeline().addLast(handlers);
            }
        });

        self = b.connect(bindFuture.channel().localAddress()).sync().channel();
        peer = peerRef.get();

        bindFuture.channel().close().sync();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (peer != null) {
            peer.close();
            peer = null;
        }
        if (self != null) {
            self = null;
        }
    }

    @Test
    public void testFreeCalled() throws Exception {
        final CountDownLatch free = new CountDownLatch(1);

        final ReferenceCounted holder = new AbstractReferenceCounted() {
            @Override
            protected void deallocate() {
                free.countDown();
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        };

        StringInboundHandler handler = new StringInboundHandler();
        setUp(handler);

        peer.writeAndFlush(holder).sync();

        assertTrue(free.await(10, TimeUnit.SECONDS));
        assertTrue(handler.called);
    }

    private static final class StringInboundHandler extends ChannelInboundHandlerAdapter {
        boolean called;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            called = true;
            if (!(msg instanceof String)) {
                ctx.fireChannelRead(msg);
            }
        }
    }

    @Test
    public void testRemoveChannelHandler() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        ChannelHandler handler3 = newHandler();

        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        pipeline.addLast("handler3", handler3);
        assertSame(pipeline.get("handler1"), handler1);
        assertSame(pipeline.get("handler2"), handler2);
        assertSame(pipeline.get("handler3"), handler3);

        pipeline.remove(handler1);
        assertNull(pipeline.get("handler1"));
        pipeline.remove(handler2);
        assertNull(pipeline.get("handler2"));
        pipeline.remove(handler3);
        assertNull(pipeline.get("handler3"));
    }

    @Test
    public void testRemoveIfExists() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        ChannelHandler handler3 = newHandler();

        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        pipeline.addLast("handler3", handler3);

        assertNotNull(pipeline.removeIfExists(handler1));
        assertNull(pipeline.get("handler1"));

        assertNotNull(pipeline.removeIfExists("handler2"));
        assertNull(pipeline.get("handler2"));

        assertNotNull(pipeline.removeIfExists(TestHandler.class));
        assertNull(pipeline.get("handler3"));
    }

    @Test
    public void testRemoveIfExistsDoesNotThrowException() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        pipeline.addLast("handler1", handler1);

        assertNull(pipeline.removeIfExists("handlerXXX"));
        assertNull(pipeline.removeIfExists(handler2));
        assertNull(pipeline.removeIfExists(ChannelOutboundHandlerAdapter.class));
        assertNotNull(pipeline.get("handler1"));
    }

    @Test
    public void testRemoveThrowNoSuchElementException() {
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

        ChannelHandler handler1 = newHandler();
        pipeline.addLast("handler1", handler1);

        assertThrows(NoSuchElementException.class, new Executable() {
            @Override
            public void execute() {
                pipeline.remove("handlerXXX");
            }
        });
    }

    @Test
    public void testReplaceChannelHandler() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler1);
        pipeline.addLast("handler3", handler1);
        assertSame(pipeline.get("handler1"), handler1);
        assertSame(pipeline.get("handler2"), handler1);
        assertSame(pipeline.get("handler3"), handler1);

        ChannelHandler newHandler1 = newHandler();
        pipeline.replace("handler1", "handler1", newHandler1);
        assertSame(pipeline.get("handler1"), newHandler1);

        ChannelHandler newHandler3 = newHandler();
        pipeline.replace("handler3", "handler3", newHandler3);
        assertSame(pipeline.get("handler3"), newHandler3);

        ChannelHandler newHandler2 = newHandler();
        pipeline.replace("handler2", "handler2", newHandler2);
        assertSame(pipeline.get("handler2"), newHandler2);
    }

    @Test
    public void testReplaceHandlerChecksDuplicateNames() {
        final ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);

        final ChannelHandler newHandler1 = newHandler();
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                pipeline.replace("handler1", "handler2", newHandler1);
            }
        });
    }

    @Test
    public void testReplaceNameWithGenerated() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        pipeline.addLast("handler1", handler1);
        assertSame(pipeline.get("handler1"), handler1);

        ChannelHandler newHandler1 = newHandler();
        pipeline.replace("handler1", null, newHandler1);
        assertSame(pipeline.get("DefaultChannelPipelineTest$TestHandler#0"), newHandler1);
        assertNull(pipeline.get("handler1"));
    }

    @Test
    public void testRenameChannelHandler() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler1);
        pipeline.addLast("handler3", handler1);
        assertSame(pipeline.get("handler1"), handler1);
        assertSame(pipeline.get("handler2"), handler1);
        assertSame(pipeline.get("handler3"), handler1);

        ChannelHandler newHandler1 = newHandler();
        pipeline.replace("handler1", "newHandler1", newHandler1);
        assertSame(pipeline.get("newHandler1"), newHandler1);
        assertNull(pipeline.get("handler1"));

        ChannelHandler newHandler3 = newHandler();
        pipeline.replace("handler3", "newHandler3", newHandler3);
        assertSame(pipeline.get("newHandler3"), newHandler3);
        assertNull(pipeline.get("handler3"));

        ChannelHandler newHandler2 = newHandler();
        pipeline.replace("handler2", "newHandler2", newHandler2);
        assertSame(pipeline.get("newHandler2"), newHandler2);
        assertNull(pipeline.get("handler2"));
    }

    @Test
    public void testChannelHandlerContextNavigation() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        final int HANDLER_ARRAY_LEN = 5;
        ChannelHandler[] firstHandlers = newHandlers(HANDLER_ARRAY_LEN);
        ChannelHandler[] lastHandlers = newHandlers(HANDLER_ARRAY_LEN);

        pipeline.addFirst(firstHandlers);
        pipeline.addLast(lastHandlers);

        verifyContextNumber(pipeline, HANDLER_ARRAY_LEN * 2);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testThrowInExceptionCaught() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        Channel channel = new LocalChannel();
        try {
            group.register(channel).syncUninterruptibly();
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                class TestException extends Exception { }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                    throw new TestException();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    if (cause instanceof TestException) {
                        ctx.executor().execute(new Runnable() {
                            @Override
                            public void run() {
                                latch.countDown();
                            }
                        });
                    }
                    counter.incrementAndGet();
                    throw new Exception();
                }
            });

            channel.pipeline().fireChannelReadComplete();
            latch.await();
            assertEquals(1, counter.get());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testThrowInOtherHandlerAfterInvokedFromExceptionCaught() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        Channel channel = new LocalChannel();
        try {
            group.register(channel).syncUninterruptibly();
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    ctx.fireChannelReadComplete();
                }
            }, new ChannelInboundHandlerAdapter() {
                class TestException extends Exception { }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                    throw new TestException();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    if (cause instanceof TestException) {
                        ctx.executor().execute(new Runnable() {
                            @Override
                            public void run() {
                                latch.countDown();
                            }
                        });
                    }
                    counter.incrementAndGet();
                    throw new Exception();
                }
            });

            channel.pipeline().fireExceptionCaught(new Exception());
            latch.await();
            assertEquals(1, counter.get());
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    public void testFireChannelRegistered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) {
                        latch.countDown();
                    }
                });
            }
        });
        group.register(pipeline.channel());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testPipelineOperation() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        final int handlerNum = 5;
        ChannelHandler[] handlers1 = newHandlers(handlerNum);
        ChannelHandler[] handlers2 = newHandlers(handlerNum);

        final String prefixX = "x";
        for (int i = 0; i < handlerNum; i++) {
            if (i % 2 == 0) {
                pipeline.addFirst(prefixX + i, handlers1[i]);
            } else {
                pipeline.addLast(prefixX + i, handlers1[i]);
            }
        }

        for (int i = 0; i < handlerNum; i++) {
            if (i % 2 != 0) {
                pipeline.addBefore(prefixX + i, String.valueOf(i), handlers2[i]);
            } else {
                pipeline.addAfter(prefixX + i, String.valueOf(i), handlers2[i]);
            }
        }

        verifyContextNumber(pipeline, handlerNum * 2);
    }

    @Test
    public void testChannelHandlerContextOrder() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        pipeline.addFirst("1", newHandler());
        pipeline.addLast("10", newHandler());

        pipeline.addBefore("10", "5", newHandler());
        pipeline.addAfter("1", "3", newHandler());
        pipeline.addBefore("5", "4", newHandler());
        pipeline.addAfter("5", "6", newHandler());

        pipeline.addBefore("1", "0", newHandler());
        pipeline.addAfter("10", "11", newHandler());

        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) pipeline.firstContext();
        assertNotNull(ctx);
        while (ctx != null) {
            int i = toInt(ctx.name());
            int j = next(ctx);
            if (j != -1) {
                assertTrue(i < j);
            } else {
                assertNull(ctx.next.next);
            }
            ctx = ctx.next;
        }

        verifyContextNumber(pipeline, 8);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testLifeCycleAwareness() throws Exception {
        setUp();

        ChannelPipeline p = self.pipeline();

        final List<LifeCycleAwareTestHandler> handlers = new ArrayList<LifeCycleAwareTestHandler>();
        final int COUNT = 20;
        final CountDownLatch addLatch = new CountDownLatch(COUNT);
        for (int i = 0; i < COUNT; i++) {
            final LifeCycleAwareTestHandler handler = new LifeCycleAwareTestHandler("handler-" + i);

            // Add handler.
            p.addFirst(handler.name, handler);
            self.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    // Validate handler life-cycle methods called.
                    handler.validate(true, false);

                    // Store handler into the list.
                    handlers.add(handler);

                    addLatch.countDown();
                }
            });
        }
        addLatch.await();

        // Change the order of remove operations over all handlers in the pipeline.
        Collections.shuffle(handlers);

        final CountDownLatch removeLatch = new CountDownLatch(COUNT);

        for (final LifeCycleAwareTestHandler handler : handlers) {
            assertSame(handler, p.remove(handler.name));

            self.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    // Validate handler life-cycle methods called.
                    handler.validate(true, true);
                    removeLatch.countDown();
                }
            });
        }
        removeLatch.await();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardInbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                handler1.inboundBuffer.add(8);
                assertEquals(8, handler1.inboundBuffer.peek());
                assertTrue(handler2.inboundBuffer.isEmpty());
                p.remove(handler1);
                assertEquals(1, handler2.inboundBuffer.size());
                assertEquals(8, handler2.inboundBuffer.peek());
            }
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                handler2.outboundBuffer.add(8);
                assertEquals(8, handler2.outboundBuffer.peek());
                assertTrue(handler1.outboundBuffer.isEmpty());
                p.remove(handler2);
                assertEquals(1, handler1.outboundBuffer.size());
                assertEquals(8, handler1.outboundBuffer.peek());
            }
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testReplaceAndForwardOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                handler1.outboundBuffer.add(8);
                assertEquals(8, handler1.outboundBuffer.peek());
                assertTrue(handler2.outboundBuffer.isEmpty());
                p.replace(handler1, "handler2", handler2);
                assertEquals(8, handler2.outboundBuffer.peek());
            }
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testReplaceAndForwardInboundAndOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                handler1.inboundBuffer.add(8);
                handler1.outboundBuffer.add(8);

                assertEquals(8, handler1.inboundBuffer.peek());
                assertEquals(8, handler1.outboundBuffer.peek());
                assertTrue(handler2.inboundBuffer.isEmpty());
                assertTrue(handler2.outboundBuffer.isEmpty());

                p.replace(handler1, "handler2", handler2);
                assertEquals(8, handler2.outboundBuffer.peek());
                assertEquals(8, handler2.inboundBuffer.peek());
            }
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardInboundOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();
        final BufferedTestHandler handler3 = new BufferedTestHandler();

        setUp(handler1, handler2, handler3);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                handler2.inboundBuffer.add(8);
                handler2.outboundBuffer.add(8);

                assertEquals(8, handler2.inboundBuffer.peek());
                assertEquals(8, handler2.outboundBuffer.peek());

                assertEquals(0, handler1.outboundBuffer.size());
                assertEquals(0, handler3.inboundBuffer.size());

                p.remove(handler2);
                assertEquals(8, handler3.inboundBuffer.peek());
                assertEquals(8, handler1.outboundBuffer.peek());
            }
        }).sync();
    }

    // Tests for https://github.com/netty/netty/issues/2349
    @Test
    public void testCancelBind() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.bind(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelConnect() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.connect(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelDisconnect() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.disconnect(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelClose() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.close(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testWrongPromiseChannel() throws Exception {
        final ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel()).sync();

        ChannelPipeline pipeline2 = new LocalChannel().pipeline();
        group.register(pipeline2.channel()).sync();

        try {
            final ChannelPromise promise2 = pipeline2.channel().newPromise();
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    pipeline.close(promise2);
                }
            });
        } finally {
            pipeline.close();
            pipeline2.close();
        }
    }

    @Test
    public void testUnexpectedVoidChannelPromise() throws Exception {
        final ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel()).sync();

        try {
            final ChannelPromise promise = new VoidChannelPromise(pipeline.channel(), false);
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    pipeline.close(promise);
                }
            });
        } finally {
            pipeline.close();
        }
    }

    @Test
    public void testUnexpectedVoidChannelPromiseCloseFuture() throws Exception {
        final ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel()).sync();

        try {
            final ChannelPromise promise = (ChannelPromise) pipeline.channel().closeFuture();
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    pipeline.close(promise);
                }
            });
        } finally {
            pipeline.close();
        }
    }

    @Test
    public void testCancelDeregister() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.deregister(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelWrite() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ByteBuf buffer = Unpooled.buffer();
        assertEquals(1, buffer.refCnt());
        ChannelFuture future = pipeline.write(buffer, promise);
        assertTrue(future.isCancelled());
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testCancelWriteAndFlush() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ByteBuf buffer = Unpooled.buffer();
        assertEquals(1, buffer.refCnt());
        ChannelFuture future = pipeline.writeAndFlush(buffer, promise);
        assertTrue(future.isCancelled());
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testFirstContextEmptyPipeline() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.firstContext());
    }

    @Test
    public void testLastContextEmptyPipeline() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.lastContext());
    }

    @Test
    public void testFirstHandlerEmptyPipeline() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.first());
    }

    @Test
    public void testLastHandlerEmptyPipeline() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.last());
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testChannelInitializerException() throws Exception {
        final IllegalStateException exception = new IllegalStateException();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                throw exception;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                super.exceptionCaught(ctx, cause);
                error.set(cause);
                latch.countDown();
            }
        });
        latch.await();
        assertFalse(channel.isActive());
        assertSame(exception, error.get());
    }

    @Test
    public void testChannelUnregistrationWithCustomExecutor() throws Exception {
        final CountDownLatch channelLatch = new CountDownLatch(1);
        final CountDownLatch handlerLatch = new CountDownLatch(1);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new WrapperExecutor(),
                        new ChannelInboundHandlerAdapter() {

                            @Override
                            public void channelUnregistered(ChannelHandlerContext ctx) {
                                channelLatch.countDown();
                            }

                            @Override
                            public void handlerRemoved(ChannelHandlerContext ctx) {
                                handlerLatch.countDown();
                            }
                        });
            }
        });
        Channel channel = pipeline.channel();
        group.register(channel);
        channel.close();
        channel.deregister();
        assertTrue(channelLatch.await(2, TimeUnit.SECONDS));
        assertTrue(handlerLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddHandlerBeforeRegisteredThenRemove() {
        final EventLoop loop = group.next();

        CheckEventExecutorHandler handler = new CheckEventExecutorHandler(loop);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addFirst(handler);
        assertFalse(handler.addedPromise.isDone());
        group.register(pipeline.channel());
        handler.addedPromise.syncUninterruptibly();
        pipeline.remove(handler);
        handler.removedPromise.syncUninterruptibly();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddHandlerBeforeRegisteredThenReplace() throws Exception {
        final EventLoop loop = group.next();
        final CountDownLatch latch = new CountDownLatch(1);

        CheckEventExecutorHandler handler = new CheckEventExecutorHandler(loop);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addFirst(handler);
        assertFalse(handler.addedPromise.isDone());
        group.register(pipeline.channel());
        handler.addedPromise.syncUninterruptibly();
        pipeline.replace(handler, null, new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                latch.countDown();
            }
        });
        handler.removedPromise.syncUninterruptibly();
        latch.await();
    }

    @Test
    public void testAddRemoveHandlerNotRegistered() throws Throwable {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        ChannelHandler handler = new ErrorChannelHandler(error);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addFirst(handler);
        pipeline.remove(handler);

        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    public void testAddReplaceHandlerNotRegistered() throws Throwable {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        ChannelHandler handler = new ErrorChannelHandler(error);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addFirst(handler);
        pipeline.replace(handler, null, new ErrorChannelHandler(error));

        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerAddedAndRemovedCalledInCorrectOrder() throws Throwable {
        final EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        final EventExecutorGroup group2 = new DefaultEventExecutorGroup(1);

        try {
            BlockingQueue<CheckOrderHandler> addedQueue = new LinkedBlockingQueue<CheckOrderHandler>();
            BlockingQueue<CheckOrderHandler> removedQueue = new LinkedBlockingQueue<CheckOrderHandler>();

            CheckOrderHandler handler1 = new CheckOrderHandler(addedQueue, removedQueue);
            CheckOrderHandler handler2 = new CheckOrderHandler(addedQueue, removedQueue);
            CheckOrderHandler handler3 = new CheckOrderHandler(addedQueue, removedQueue);
            CheckOrderHandler handler4 = new CheckOrderHandler(addedQueue, removedQueue);

            ChannelPipeline pipeline = new LocalChannel().pipeline();
            pipeline.addLast(handler1);
            group.register(pipeline.channel()).syncUninterruptibly();
            pipeline.addLast(group1, handler2);
            pipeline.addLast(group2, handler3);
            pipeline.addLast(handler4);

            assertTrue(removedQueue.isEmpty());
            pipeline.channel().close().syncUninterruptibly();
            assertHandler(addedQueue.take(), handler1);

            // Depending on timing this can be handler2 or handler3 as these use different EventExecutorGroups.
            assertHandler(addedQueue.take(), handler2, handler3, handler4);
            assertHandler(addedQueue.take(), handler2, handler3, handler4);
            assertHandler(addedQueue.take(), handler2, handler3, handler4);

            assertTrue(addedQueue.isEmpty());

            assertHandler(removedQueue.take(), handler4);
            assertHandler(removedQueue.take(), handler3);
            assertHandler(removedQueue.take(), handler2);
            assertHandler(removedQueue.take(), handler1);
            assertTrue(removedQueue.isEmpty());
        } finally {
            group1.shutdownGracefully();
            group2.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerAddedExceptionFromChildHandlerIsPropagated() {
        final EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        try {
            final Promise<Void> promise = group1.next().newPromise();
            final AtomicBoolean handlerAdded = new AtomicBoolean();
            final Exception exception = new RuntimeException();
            ChannelPipeline pipeline = new LocalChannel().pipeline();
            pipeline.addLast(group1, new CheckExceptionHandler(exception, promise));
            pipeline.addFirst(new ChannelHandlerAdapter() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                    handlerAdded.set(true);
                    throw exception;
                }
            });
            assertFalse(handlerAdded.get());
            group.register(pipeline.channel());
            promise.syncUninterruptibly();
        } finally {
            group1.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerRemovedExceptionFromChildHandlerIsPropagated() {
        final EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        try {
            final Promise<Void> promise = group1.next().newPromise();
            String handlerName = "foo";
            final Exception exception = new RuntimeException();
            ChannelPipeline pipeline = new LocalChannel().pipeline();
            pipeline.addLast(handlerName, new ChannelHandlerAdapter() {
                @Override
                public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                    throw exception;
                }
            });
            pipeline.addLast(group1, new CheckExceptionHandler(exception, promise));
            group.register(pipeline.channel()).syncUninterruptibly();
            pipeline.remove(handlerName);
            promise.syncUninterruptibly();
        } finally {
            group1.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerAddedThrowsAndRemovedThrowsException() throws InterruptedException {
        final EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final Promise<Void> promise = group1.next().newPromise();
            final Exception exceptionAdded = new RuntimeException();
            final Exception exceptionRemoved = new RuntimeException();
            String handlerName = "foo";
            ChannelPipeline pipeline = new LocalChannel().pipeline();
            pipeline.addLast(group1, new CheckExceptionHandler(exceptionAdded, promise));
            pipeline.addFirst(handlerName, new ChannelHandlerAdapter() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                    throw exceptionAdded;
                }

                @Override
                public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                    // Execute this later so we are sure the exception is handled first.
                    ctx.executor().execute(new Runnable() {
                        @Override
                        public void run() {
                            latch.countDown();
                        }
                    });
                    throw exceptionRemoved;
                }
            });
            group.register(pipeline.channel()).syncUninterruptibly();
            latch.await();
            assertNull(pipeline.context(handlerName));
            promise.syncUninterruptibly();
        } finally {
            group1.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testAddRemoveHandlerCalledOnceRegistered() throws Throwable {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        CallbackCheckHandler handler = new CallbackCheckHandler();

        pipeline.addFirst(handler);
        pipeline.remove(handler);

        assertNull(handler.addedHandler.getNow());
        assertNull(handler.removedHandler.getNow());

        group.register(pipeline.channel()).syncUninterruptibly();
        Throwable cause = handler.error.get();
        if (cause != null) {
            throw cause;
        }

        assertTrue(handler.addedHandler.get());
        assertTrue(handler.removedHandler.get());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddReplaceHandlerCalledOnceRegistered() throws Throwable {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        CallbackCheckHandler handler = new CallbackCheckHandler();
        CallbackCheckHandler handler2 = new CallbackCheckHandler();

        pipeline.addFirst(handler);
        pipeline.replace(handler, null, handler2);

        assertNull(handler.addedHandler.getNow());
        assertNull(handler.removedHandler.getNow());
        assertNull(handler2.addedHandler.getNow());
        assertNull(handler2.removedHandler.getNow());

        group.register(pipeline.channel()).syncUninterruptibly();
        Throwable cause = handler.error.get();
        if (cause != null) {
            throw cause;
        }

        assertTrue(handler.addedHandler.get());
        assertTrue(handler.removedHandler.get());

        Throwable cause2 = handler2.error.get();
        if (cause2 != null) {
            throw cause2;
        }

        assertTrue(handler2.addedHandler.get());
        assertNull(handler2.removedHandler.getNow());
        pipeline.remove(handler2);
        assertTrue(handler2.removedHandler.get());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddBefore() throws Throwable {
        ChannelPipeline pipeline1 = new LocalChannel().pipeline();
        ChannelPipeline pipeline2 = new LocalChannel().pipeline();

        EventLoopGroup defaultGroup = new DefaultEventLoopGroup(2);
        try {
            EventLoop eventLoop1 = defaultGroup.next();
            EventLoop eventLoop2 = defaultGroup.next();

            eventLoop1.register(pipeline1.channel()).syncUninterruptibly();
            eventLoop2.register(pipeline2.channel()).syncUninterruptibly();

            CountDownLatch latch = new CountDownLatch(2 * 10);
            for (int i = 0; i < 10; i++) {
                eventLoop1.execute(new TestTask(pipeline2, latch));
                eventLoop2.execute(new TestTask(pipeline1, latch));
            }
            latch.await();
        } finally {
            defaultGroup.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddInListenerNio() {
        testAddInListener(new NioSocketChannel(), new NioEventLoopGroup(1));
    }

    @SuppressWarnings("deprecation")
    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddInListenerOio() {
        testAddInListener(new OioSocketChannel(), new OioEventLoopGroup(1));
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddInListenerLocal() {
        testAddInListener(new LocalChannel(), new DefaultEventLoopGroup(1));
    }

    private static void testAddInListener(Channel channel, EventLoopGroup group) {
        ChannelPipeline pipeline1 = channel.pipeline();
        try {
            final Object event = new Object();
            final Promise<Object> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            group.register(pipeline1.channel()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    ChannelPipeline pipeline = future.channel().pipeline();
                    final AtomicBoolean handlerAddedCalled = new AtomicBoolean();
                    pipeline.addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            handlerAddedCalled.set(true);
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            promise.setSuccess(event);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            promise.setFailure(cause);
                        }
                    });
                    if (!handlerAddedCalled.get()) {
                        promise.setFailure(new AssertionError("handlerAdded(...) should have been called"));
                        return;
                    }
                    // This event must be captured by the added handler.
                    pipeline.fireUserEventTriggered(event);
                }
            });
            assertSame(event, promise.syncUninterruptibly().getNow());
        } finally {
            pipeline1.channel().close().syncUninterruptibly();
            group.shutdownGracefully();
        }
    }

    @Test
    public void testNullName() {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addLast(newHandler());
        pipeline.addLast(null, newHandler());
        pipeline.addFirst(newHandler());
        pipeline.addFirst(null, newHandler());

        pipeline.addLast("test", newHandler());
        pipeline.addAfter("test", null, newHandler());

        pipeline.addBefore("test", null, newHandler());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testUnorderedEventExecutor() throws Throwable {
        ChannelPipeline pipeline1 = new LocalChannel().pipeline();
        EventExecutorGroup eventExecutors = new UnorderedThreadPoolEventExecutor(2);
        EventLoopGroup defaultGroup = new DefaultEventLoopGroup(1);
        try {
            EventLoop eventLoop1 = defaultGroup.next();
            eventLoop1.register(pipeline1.channel()).syncUninterruptibly();
            final CountDownLatch latch = new CountDownLatch(1);
            pipeline1.addLast(eventExecutors, new ChannelInboundHandlerAdapter() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) {
                    // Just block one of the two threads.
                    LockSupport.park();
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    latch.countDown();
                }
            });
            // Trigger an event, as we use UnorderedEventExecutor userEventTriggered should be called even when
            // handlerAdded(...) blocks.
            pipeline1.fireUserEventTriggered("");
            latch.await();
        } finally {
            defaultGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            eventExecutors.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void testPinExecutor() {
        EventExecutorGroup group = new DefaultEventExecutorGroup(2);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        ChannelPipeline pipeline2 = new LocalChannel().pipeline();

        pipeline.addLast(group, "h1", new ChannelInboundHandlerAdapter());
        pipeline.addLast(group, "h2", new ChannelInboundHandlerAdapter());
        pipeline2.addLast(group, "h3", new ChannelInboundHandlerAdapter());

        EventExecutor executor1 = pipeline.context("h1").executor();
        EventExecutor executor2 = pipeline.context("h2").executor();
        assertNotNull(executor1);
        assertNotNull(executor2);
        assertSame(executor1, executor2);
        EventExecutor executor3 = pipeline2.context("h3").executor();
        assertNotNull(executor3);
        assertNotSame(executor3, executor2);
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    }

    @Test
    public void testNotPinExecutor() {
        EventExecutorGroup group = new DefaultEventExecutorGroup(2);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.channel().config().setOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false);

        pipeline.addLast(group, "h1", new ChannelInboundHandlerAdapter());
        pipeline.addLast(group, "h2", new ChannelInboundHandlerAdapter());

        EventExecutor executor1 = pipeline.context("h1").executor();
        EventExecutor executor2 = pipeline.context("h2").executor();
        assertNotNull(executor1);
        assertNotNull(executor2);
        assertNotSame(executor1, executor2);
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testVoidPromiseNotify() {
        ChannelPipeline pipeline1 = new LocalChannel().pipeline();

        EventLoopGroup defaultGroup = new DefaultEventLoopGroup(1);
        EventLoop eventLoop1 = defaultGroup.next();
        final Promise<Throwable> promise = eventLoop1.newPromise();
        final Exception exception = new IllegalArgumentException();
        try {
            eventLoop1.register(pipeline1.channel()).syncUninterruptibly();
            pipeline1.addLast(new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    throw exception;
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    promise.setSuccess(cause);
                }
            });
            pipeline1.write("test", pipeline1.voidPromise());
            assertSame(exception, promise.syncUninterruptibly().getNow());
        } finally {
            pipeline1.channel().close().syncUninterruptibly();
            defaultGroup.shutdownGracefully();
        }
    }

    // Test for https://github.com/netty/netty/issues/8676.
    @Test
    public void testHandlerRemovedOnlyCalledWhenHandlerAddedCalled() throws Exception {
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            final AtomicReference<Error> errorRef = new AtomicReference<Error>();

            // As this only happens via a race we will verify 500 times. This was good enough to have it failed most of
            // the time.
            for (int i = 0; i < 500; i++) {

                ChannelPipeline pipeline = new LocalChannel().pipeline();
                group.register(pipeline.channel()).sync();

                final CountDownLatch latch = new CountDownLatch(1);

                pipeline.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                        // Block just for a bit so we have a chance to trigger the race mentioned in the issue.
                        latch.await(50, TimeUnit.MILLISECONDS);
                    }
                });

                // Close the pipeline which will call destroy0(). This will remove each handler in the pipeline and
                // should call handlerRemoved(...) if and only if handlerAdded(...) was called for the handler before.
                pipeline.close();

                pipeline.addLast(new ChannelInboundHandlerAdapter() {
                    private boolean handerAddedCalled;

                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        handerAddedCalled = true;
                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) {
                        if (!handerAddedCalled) {
                            errorRef.set(new AssertionError(
                                    "handlerRemoved(...) called without handlerAdded(...) before"));
                        }
                    }
                });

                latch.countDown();

                pipeline.channel().closeFuture().syncUninterruptibly();

                // Schedule something on the EventLoop to ensure all other scheduled tasks had a chance to complete.
                pipeline.channel().eventLoop().submit(new Runnable() {
                    @Override
                    public void run() {
                        // NOOP
                    }
                }).syncUninterruptibly();
                Error error = errorRef.get();
                if (error != null) {
                    throw error;
                }
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testSkipHandlerMethodsIfAnnotated() {
        EmbeddedChannel channel = new EmbeddedChannel(true);
        ChannelPipeline pipeline = channel.pipeline();

        final class SkipHandler implements ChannelInboundHandler, ChannelOutboundHandler {
            private int state = 2;
            private Error errorRef;

            private void fail() {
                errorRef = new AssertionError("Method should never been called");
            }

            @Skip
            @Override
            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
                fail();
                ctx.bind(localAddress, promise);
            }

            @Skip
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) {
                fail();
                ctx.connect(remoteAddress, localAddress, promise);
            }

            @Skip
            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
                fail();
                ctx.disconnect(promise);
            }

            @Skip
            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                fail();
                ctx.close(promise);
            }

            @Skip
            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
                fail();
                ctx.deregister(promise);
            }

            @Skip
            @Override
            public void read(ChannelHandlerContext ctx) {
                fail();
                ctx.read();
            }

            @Skip
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                fail();
                ctx.write(msg, promise);
            }

            @Skip
            @Override
            public void flush(ChannelHandlerContext ctx) {
                fail();
                ctx.flush();
            }

            @Skip
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelRegistered();
            }

            @Skip
            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelUnregistered();
            }

            @Skip
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelActive();
            }

            @Skip
            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelInactive();
            }

            @Skip
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                fail();
                ctx.fireChannelRead(msg);
            }

            @Skip
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelReadComplete();
            }

            @Skip
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                fail();
                ctx.fireUserEventTriggered(evt);
            }

            @Skip
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelWritabilityChanged();
            }

            @Skip
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                fail();
                ctx.fireExceptionCaught(cause);
            }

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                state--;
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                state--;
            }

            void assertSkipped() {
                assertEquals(0, state);
                Error error = errorRef;
                if (error != null) {
                    throw error;
                }
            }
        }

        final class OutboundCalledHandler extends ChannelOutboundHandlerAdapter {
            private static final int MASK_BIND = 1;
            private static final int MASK_CONNECT = 1 << 1;
            private static final int MASK_DISCONNECT = 1 << 2;
            private static final int MASK_CLOSE = 1 << 3;
            private static final int MASK_DEREGISTER = 1 << 4;
            private static final int MASK_READ = 1 << 5;
            private static final int MASK_WRITE = 1 << 6;
            private static final int MASK_FLUSH = 1 << 7;
            private static final int MASK_ADDED = 1 << 8;
            private static final int MASK_REMOVED = 1 << 9;

            private int executionMask;

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                executionMask |= MASK_ADDED;
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                executionMask |= MASK_REMOVED;
            }

            @Override
            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
                executionMask |= MASK_BIND;
                promise.setSuccess();
            }

            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) {
                executionMask |= MASK_CONNECT;
                promise.setSuccess();
            }

            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
                executionMask |= MASK_DISCONNECT;
                promise.setSuccess();
            }

            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                executionMask |= MASK_CLOSE;
                promise.setSuccess();
            }

            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
                executionMask |= MASK_DEREGISTER;
                promise.setSuccess();
            }

            @Override
            public void read(ChannelHandlerContext ctx) {
                executionMask |= MASK_READ;
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                executionMask |= MASK_WRITE;
                promise.setSuccess();
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                executionMask |= MASK_FLUSH;
            }

            void assertCalled() {
                assertCalled("handlerAdded", MASK_ADDED);
                assertCalled("handlerRemoved", MASK_REMOVED);
                assertCalled("bind", MASK_BIND);
                assertCalled("connect", MASK_CONNECT);
                assertCalled("disconnect", MASK_DISCONNECT);
                assertCalled("close", MASK_CLOSE);
                assertCalled("deregister", MASK_DEREGISTER);
                assertCalled("read", MASK_READ);
                assertCalled("write", MASK_WRITE);
                assertCalled("flush", MASK_FLUSH);
            }

            private void assertCalled(String methodName, int mask) {
                assertTrue((executionMask & mask) != 0, methodName + " was not called");
            }
        }

        final class InboundCalledHandler extends ChannelInboundHandlerAdapter {

            private static final int MASK_CHANNEL_REGISTER = 1;
            private static final int MASK_CHANNEL_UNREGISTER = 1 << 1;
            private static final int MASK_CHANNEL_ACTIVE = 1 << 2;
            private static final int MASK_CHANNEL_INACTIVE = 1 << 3;
            private static final int MASK_CHANNEL_READ = 1 << 4;
            private static final int MASK_CHANNEL_READ_COMPLETE = 1 << 5;
            private static final int MASK_USER_EVENT_TRIGGERED = 1 << 6;
            private static final int MASK_CHANNEL_WRITABILITY_CHANGED = 1 << 7;
            private static final int MASK_EXCEPTION_CAUGHT = 1 << 8;
            private static final int MASK_ADDED = 1 << 9;
            private static final int MASK_REMOVED = 1 << 10;

            private int executionMask;

            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                executionMask |= MASK_ADDED;
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                executionMask |= MASK_REMOVED;
            }

            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_REGISTER;
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_UNREGISTER;
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_ACTIVE;
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_INACTIVE;
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                executionMask |= MASK_CHANNEL_READ;
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_READ_COMPLETE;
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                executionMask |= MASK_USER_EVENT_TRIGGERED;
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_WRITABILITY_CHANGED;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                executionMask |= MASK_EXCEPTION_CAUGHT;
            }

            void assertCalled() {
                assertCalled("handlerAdded", MASK_ADDED);
                assertCalled("handlerRemoved", MASK_REMOVED);
                assertCalled("channelRegistered", MASK_CHANNEL_REGISTER);
                assertCalled("channelUnregistered", MASK_CHANNEL_UNREGISTER);
                assertCalled("channelActive", MASK_CHANNEL_ACTIVE);
                assertCalled("channelInactive", MASK_CHANNEL_INACTIVE);
                assertCalled("channelRead", MASK_CHANNEL_READ);
                assertCalled("channelReadComplete", MASK_CHANNEL_READ_COMPLETE);
                assertCalled("userEventTriggered", MASK_USER_EVENT_TRIGGERED);
                assertCalled("channelWritabilityChanged", MASK_CHANNEL_WRITABILITY_CHANGED);
                assertCalled("exceptionCaught", MASK_EXCEPTION_CAUGHT);
            }

            private void assertCalled(String methodName, int mask) {
                assertTrue((executionMask & mask) != 0, methodName + " was not called");
            }
        }

        OutboundCalledHandler outboundCalledHandler = new OutboundCalledHandler();
        SkipHandler skipHandler = new SkipHandler();
        InboundCalledHandler inboundCalledHandler = new InboundCalledHandler();
        pipeline.addLast(outboundCalledHandler, skipHandler, inboundCalledHandler);

        pipeline.fireChannelRegistered();
        pipeline.fireChannelUnregistered();
        pipeline.fireChannelActive();
        pipeline.fireChannelInactive();
        pipeline.fireChannelRead("");
        pipeline.fireChannelReadComplete();
        pipeline.fireChannelWritabilityChanged();
        pipeline.fireUserEventTriggered("");
        pipeline.fireExceptionCaught(new Exception());

        pipeline.deregister().syncUninterruptibly();
        pipeline.bind(new SocketAddress() {
        }).syncUninterruptibly();
        pipeline.connect(new SocketAddress() {
        }).syncUninterruptibly();
        pipeline.disconnect().syncUninterruptibly();
        pipeline.close().syncUninterruptibly();
        pipeline.write("");
        pipeline.flush();
        pipeline.read();

        pipeline.remove(outboundCalledHandler);
        pipeline.remove(inboundCalledHandler);
        pipeline.remove(skipHandler);

        assertFalse(channel.finish());

        outboundCalledHandler.assertCalled();
        inboundCalledHandler.assertCalled();
        skipHandler.assertSkipped();
    }

    @Test
    public void testWriteThrowsReleaseMessage() {
        testWriteThrowsReleaseMessage0(false);
    }

    @Test
    public void testWriteAndFlushThrowsReleaseMessage() {
        testWriteThrowsReleaseMessage0(true);
    }

    private void testWriteThrowsReleaseMessage0(boolean flush) {
        ReferenceCounted referenceCounted = new AbstractReferenceCounted() {
            @Override
            protected void deallocate() {
                // NOOP
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        };
        assertEquals(1, referenceCounted.refCnt());

        Channel channel = new LocalChannel();
        Channel channel2 = new LocalChannel();
        group.register(channel).syncUninterruptibly();
        group.register(channel2).syncUninterruptibly();

        try {
            if (flush) {
                channel.writeAndFlush(referenceCounted, channel2.newPromise());
            } else {
                channel.write(referenceCounted, channel2.newPromise());
            }
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(0, referenceCounted.refCnt());

        channel.close().syncUninterruptibly();
        channel2.close().syncUninterruptibly();
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerAddedFailedButHandlerStillRemoved() throws InterruptedException {
        testHandlerAddedFailedButHandlerStillRemoved0(false);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerAddedFailedButHandlerStillRemovedWithLaterRegister() throws InterruptedException {
        testHandlerAddedFailedButHandlerStillRemoved0(true);
    }

    private static void testHandlerAddedFailedButHandlerStillRemoved0(boolean lateRegister)
            throws InterruptedException {
        EventExecutorGroup executorGroup = new DefaultEventExecutorGroup(16);
        final int numHandlers = 32;
        try {
            Channel channel = new LocalChannel();
            channel.config().setOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false);
            if (!lateRegister) {
                group.register(channel).sync();
            }
            channel.pipeline().addFirst(newHandler());

            List<CountDownLatch> latchList = new ArrayList<CountDownLatch>(numHandlers);
            for (int i = 0; i < numHandlers; i++) {
                CountDownLatch latch = new CountDownLatch(1);
                channel.pipeline().addFirst(executorGroup, "h" + i, new BadChannelHandler(latch));
                latchList.add(latch);
            }
            if (lateRegister) {
                group.register(channel).sync();
            }

            for (int i = 0; i < numHandlers; i++) {
                // Wait until the latch was countDown which means handlerRemoved(...) was called.
                latchList.get(i).await();
                assertNull(channel.pipeline().get("h" + i));
            }
        } finally {
            executorGroup.shutdownGracefully();
        }
    }

    private static final class BadChannelHandler extends ChannelHandlerAdapter {
        private final CountDownLatch latch;

        BadChannelHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            TimeUnit.MILLISECONDS.sleep(10);
            throw new RuntimeException();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            latch.countDown();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void handlerAddedStateUpdatedBeforeHandlerAddedDoneForceEventLoop() throws InterruptedException {
        handlerAddedStateUpdatedBeforeHandlerAddedDone(true);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void handlerAddedStateUpdatedBeforeHandlerAddedDoneOnCallingThread() throws InterruptedException {
        handlerAddedStateUpdatedBeforeHandlerAddedDone(false);
    }

    private static void handlerAddedStateUpdatedBeforeHandlerAddedDone(boolean executeInEventLoop)
            throws InterruptedException {
        final ChannelPipeline pipeline = new LocalChannel().pipeline();
        final Object userEvent = new Object();
        final Object writeObject = new Object();
        final CountDownLatch doneLatch = new CountDownLatch(1);

        group.register(pipeline.channel());

        Runnable r = new Runnable() {
            @Override
            public void run() {
                pipeline.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == userEvent) {
                            ctx.write(writeObject);
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                });
                pipeline.addFirst(new ChannelDuplexHandler() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        ctx.fireUserEventTriggered(userEvent);
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        if (msg == writeObject) {
                            doneLatch.countDown();
                        }
                        ctx.write(msg, promise);
                    }
                });
            }
        };

        if (executeInEventLoop) {
            pipeline.channel().eventLoop().execute(r);
        } else {
            r.run();
        }

        doneLatch.await();
    }

    private static final class TestTask implements Runnable {

        private final ChannelPipeline pipeline;
        private final CountDownLatch latch;

        TestTask(ChannelPipeline pipeline, CountDownLatch latch) {
            this.pipeline = pipeline;
            this.latch = latch;
        }

        @Override
        public void run() {
            pipeline.addLast(new ChannelInboundHandlerAdapter());
            latch.countDown();
        }
    }

    private static final class CallbackCheckHandler extends ChannelHandlerAdapter {
        final Promise<Boolean> addedHandler = ImmediateEventExecutor.INSTANCE.newPromise();
        final Promise<Boolean> removedHandler = ImmediateEventExecutor.INSTANCE.newPromise();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            if (!addedHandler.trySuccess(true)) {
                error.set(new AssertionError("handlerAdded(...) called multiple times: " + ctx.name()));
            } else if (removedHandler.getNow() == Boolean.TRUE) {
                error.set(new AssertionError("handlerRemoved(...) called before handlerAdded(...): " + ctx.name()));
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (!removedHandler.trySuccess(true)) {
                error.set(new AssertionError("handlerRemoved(...) called multiple times: " + ctx.name()));
            } else if (addedHandler.getNow() == Boolean.FALSE) {
                error.set(new AssertionError("handlerRemoved(...) called before handlerAdded(...): " + ctx.name()));
            }
        }
    }

    private static final class CheckExceptionHandler extends ChannelInboundHandlerAdapter {
        private final Throwable expected;
        private final Promise<Void> promise;

        CheckExceptionHandler(Throwable expected, Promise<Void> promise) {
            this.expected = expected;
            this.promise = promise;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause instanceof ChannelPipelineException && cause.getCause() == expected) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(new AssertionError("cause not the expected instance"));
            }
        }
    }

    private static void assertHandler(CheckOrderHandler actual, CheckOrderHandler... handlers) throws Throwable {
        for (CheckOrderHandler h : handlers) {
            if (h == actual) {
                actual.checkError();
                return;
            }
        }
        fail("handler was not one of the expected handlers");
    }

    private static final class CheckOrderHandler extends ChannelHandlerAdapter {
        private final Queue<CheckOrderHandler> addedQueue;
        private final Queue<CheckOrderHandler> removedQueue;
        private final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        CheckOrderHandler(Queue<CheckOrderHandler> addedQueue, Queue<CheckOrderHandler> removedQueue) {
            this.addedQueue = addedQueue;
            this.removedQueue = removedQueue;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            addedQueue.add(this);
            checkExecutor(ctx);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            removedQueue.add(this);
            checkExecutor(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            error.set(cause);
        }

        void checkError() throws Throwable {
            Throwable cause = error.get();
            if (cause != null) {
                throw cause;
            }
        }

        private void checkExecutor(ChannelHandlerContext ctx) {
            if (!ctx.executor().inEventLoop()) {
                error.set(new AssertionError());
            }
        }
    }

    private static final class CheckEventExecutorHandler extends ChannelHandlerAdapter {
        final EventExecutor executor;
        final Promise<Void> addedPromise;
        final Promise<Void> removedPromise;

        CheckEventExecutorHandler(EventExecutor executor) {
            this.executor = executor;
            addedPromise = executor.newPromise();
            removedPromise = executor.newPromise();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            assertExecutor(ctx, addedPromise);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            assertExecutor(ctx, removedPromise);
        }

        private void assertExecutor(ChannelHandlerContext ctx, Promise<Void> promise) {
            final boolean same;
            try {
                same = executor == ctx.executor();
            } catch (Throwable cause) {
                promise.setFailure(cause);
                return;
            }
            if (same) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(new AssertionError("EventExecutor not the same"));
            }
        }
    }
    private static final class ErrorChannelHandler extends ChannelHandlerAdapter {
        private final AtomicReference<Throwable> error;

        ErrorChannelHandler(AtomicReference<Throwable> error) {
            this.error = error;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            error.set(new AssertionError());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            error.set(new AssertionError());
        }
    }

    private static int next(AbstractChannelHandlerContext ctx) {
        AbstractChannelHandlerContext next = ctx.next;
        if (next == null) {
            return Integer.MAX_VALUE;
        }

        return toInt(next.name());
    }

    private static int toInt(String name) {
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void verifyContextNumber(ChannelPipeline pipeline, int expectedNumber) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) pipeline.firstContext();
        int handlerNumber = 0;
        while (ctx != ((DefaultChannelPipeline) pipeline).tail) {
            handlerNumber++;
            ctx = ctx.next;
        }
        assertEquals(expectedNumber, handlerNumber);
    }

    private static ChannelHandler[] newHandlers(int num) {
        assert num > 0;

        ChannelHandler[] handlers = new ChannelHandler[num];
        for (int i = 0; i < num; i++) {
            handlers[i] = newHandler();
        }

        return handlers;
    }

    private static ChannelHandler newHandler() {
        return new TestHandler();
    }

    @Sharable
    private static class TestHandler extends ChannelDuplexHandler { }

    private static class BufferedTestHandler extends ChannelDuplexHandler {
        final Queue<Object> inboundBuffer = new ArrayDeque<Object>();
        final Queue<Object> outboundBuffer = new ArrayDeque<Object>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            outboundBuffer.add(msg);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            inboundBuffer.add(msg);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!inboundBuffer.isEmpty()) {
                for (Object o: inboundBuffer) {
                    ctx.fireChannelRead(o);
                }
                ctx.fireChannelReadComplete();
            }
            if (!outboundBuffer.isEmpty()) {
                for (Object o: outboundBuffer) {
                    ctx.write(o);
                }
                ctx.flush();
            }
        }
    }

    /** Test handler to validate life-cycle aware behavior. */
    private static final class LifeCycleAwareTestHandler extends ChannelHandlerAdapter {
        private final String name;

        private boolean afterAdd;
        private boolean afterRemove;

        /**
         * Constructs life-cycle aware test handler.
         *
         * @param name Handler name to display in assertion messages.
         */
        private LifeCycleAwareTestHandler(String name) {
            this.name = name;
        }

        public void validate(boolean afterAdd, boolean afterRemove) {
            assertEquals(afterAdd, this.afterAdd, name);
            assertEquals(afterRemove, this.afterRemove, name);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            validate(false, false);

            afterAdd = true;
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            validate(true, false);

            afterRemove = true;
        }
    }

    private static final class WrapperExecutor extends AbstractEventExecutor {

        private final ExecutorService wrapped = Executors.newSingleThreadExecutor();

        @Override
        public boolean isShuttingDown() {
            return wrapped.isShutdown();
        }

        @Override
        public Future<?> shutdownGracefully(long l, long l2, TimeUnit timeUnit) {
            throw new IllegalStateException();
        }

        @Override
        public Future<?> terminationFuture() {
            throw new IllegalStateException();
        }

        @Override
        public void shutdown() {
            wrapped.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return wrapped.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return wrapped.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return wrapped.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return wrapped.awaitTermination(timeout, unit);
        }

        @Override
        public EventExecutorGroup parent() {
            return null;
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            wrapped.execute(command);
        }
    }
}
