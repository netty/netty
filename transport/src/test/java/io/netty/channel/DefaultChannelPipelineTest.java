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


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.*;

public class DefaultChannelPipelineTest {

    private static final EventLoopGroup group = new LocalEventLoopGroup(1);

    private Channel self;
    private Channel peer;

    @AfterClass
    public static void afterClass() throws Exception {
        group.shutdownGracefully().sync();
    }

    private void setUp(final ChannelHandler... handlers) throws Exception {
        final AtomicReference<Channel> peerRef = new AtomicReference<Channel>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group).channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                peerRef.set(ctx.channel());
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ReferenceCountUtil.release(msg);
            }
        });

        ChannelFuture bindFuture = sb.bind(LocalAddress.ANY).sync();

        Bootstrap b = new Bootstrap();
        b.group(group).channel(LocalChannel.class);
        b.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            protected void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline().addLast(handlers);
            }
        });

        self = b.connect(bindFuture.channel().localAddress()).sync().channel();
        peer = peerRef.get();

        bindFuture.channel().close().sync();
    }

    @After
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
    public void testFireChannelRegistered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
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

    @Test(timeout = 10000)
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

    @Test(timeout = 100000)
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

    @Test(timeout = 10000)
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

    @Test(timeout = 10000)
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

    @Test(timeout = 10000)
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

    @Test(timeout = 10000)
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
    public void testCancelBind() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.bind(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelConnect() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.connect(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelDisconnect() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.disconnect(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelClose() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.close(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelDeregister() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        group.register(pipeline.channel());

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.deregister(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelWrite() throws Exception {
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
    public void testCancelWriteAndFlush() throws Exception {
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
    public void testFirstContextEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.firstContext());
    }

    @Test
    public void testLastContextEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.lastContext());
    }

    @Test
    public void testFirstHandlerEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.first());
    }

    @Test
    public void testLastHandlerEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        assertNull(pipeline.last());
    }

    @Test(timeout = 5000)
    public void testChannelInitializerException() throws Exception {
        final IllegalStateException exception = new IllegalStateException();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
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
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new WrapperExecutor(),
                        new ChannelInboundHandlerAdapter() {

                            @Override
                            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                channelLatch.countDown();
                            }

                            @Override
                            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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

    @Test(timeout = 3000)
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

    @Test(timeout = 3000)
    public void testAddHandlerBeforeRegisteredThenReplace() throws Exception {
        final EventLoop loop = group.next();
        final CountDownLatch latch = new CountDownLatch(1);

        CheckEventExecutorHandler handler = new CheckEventExecutorHandler(loop);
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        pipeline.addFirst(handler);
        assertFalse(handler.addedPromise.isDone());
        group.register(pipeline.channel());
        handler.addedPromise.syncUninterruptibly();
        pipeline.replace(handler, "newHandler", new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
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
        pipeline.replace(handler, "newHandler", new ErrorChannelHandler(error));

        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test(timeout = 3000)
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

    @Test(timeout = 3000)
    public void testHandlerAddedExceptionFromChildHandlerIsPropegated() {
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

    @Test(timeout = 3000)
    public void testHandlerRemovedExceptionFromChildHandlerIsPropegated() {
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

    @Test(timeout = 3000)
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

    @Test(timeout = 2000)
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

    @Test(timeout = 3000)
    public void testAddReplaceHandlerCalledOnceRegistered() throws Throwable {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
        CallbackCheckHandler handler = new CallbackCheckHandler();
        CallbackCheckHandler handler2 = new CallbackCheckHandler();

        pipeline.addFirst(handler);
        pipeline.replace(handler, "newHandler", handler2);

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

    @Test(timeout = 3000)
    public void testAddBefore() throws Throwable {
        ChannelPipeline pipeline1 = new LocalChannel().pipeline();
        ChannelPipeline pipeline2 = new LocalChannel().pipeline();

        EventLoopGroup defaultGroup = new LocalEventLoopGroup(2);
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

    @Test(timeout = 3000)
    public void testAddInListenerNio() throws Throwable {
        testAddInListener(new NioSocketChannel(), new NioEventLoopGroup(1));
    }

    @Test(timeout = 3000)
    public void testAddInListenerOio() throws Throwable {
        testAddInListener(new OioSocketChannel(), new OioEventLoopGroup(1));
    }

    @Test(timeout = 3000)
    public void testAddInListenerLocal() throws Throwable {
        testAddInListener(new LocalChannel(), new LocalEventLoopGroup(1));
    }

    private static void testAddInListener(Channel channel, EventLoopGroup group) throws Throwable {
        ChannelPipeline pipeline1 = channel.pipeline();
        try {
            final Object event = new Object();
            final Promise<Object> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            group.register(pipeline1.channel()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    ChannelPipeline pipeline = future.channel().pipeline();
                    final AtomicBoolean handlerAddedCalled = new AtomicBoolean();
                    pipeline.addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                            handlerAddedCalled.set(true);
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            promise.setSuccess(event);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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

    @Test(timeout = 3000)
    public void testUnorderedEventExecutor() throws Throwable {
        ChannelPipeline pipeline1 = new LocalChannel().pipeline();
        EventExecutorGroup eventExecutors = new UnorderedThreadPoolEventExecutor(2);
        EventLoopGroup defaultGroup = new LocalEventLoopGroup(1);
        try {
            EventLoop eventLoop1 = defaultGroup.next();
            eventLoop1.register(pipeline1.channel()).syncUninterruptibly();
            final CountDownLatch latch = new CountDownLatch(1);
            pipeline1.addLast(eventExecutors, new ChannelInboundHandlerAdapter() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                    // Just block one of the two threads.
                    LockSupport.park();
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
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
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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
            assertEquals(name, afterAdd, this.afterAdd);
            assertEquals(name, afterRemove, this.afterRemove);
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
