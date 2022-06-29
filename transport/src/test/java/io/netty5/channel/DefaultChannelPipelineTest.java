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
package io.netty5.channel;


import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.ChannelHandlerMask.Skip;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.util.AbstractReferenceCounted;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultChannelPipelineTest {

    private static final EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());

    private Channel self;
    private Channel peer;

    @AfterAll
    public static void afterClass() throws Exception {
        group.shutdownGracefully().sync();
    }

    private void setUp(final ChannelHandler... handlers) throws Exception {
        final AtomicReference<Channel> peerRef = new AtomicReference<>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group).channel(LocalServerChannel.class);
        sb.childHandler(new ChannelHandler() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                peerRef.set(ctx.channel());
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Resource.dispose(msg);
            }
        });

        Channel channel = sb.bind(LocalAddress.ANY).get();

        Bootstrap b = new Bootstrap();
        b.group(group).channel(LocalChannel.class);
        b.handler(new ChannelInitializer<LocalChannel>() {
            @Override
            protected void initChannel(LocalChannel ch) throws Exception {
                ch.pipeline().addLast(handlers);
            }
        });

        self = b.connect(channel.localAddress()).get();
        peer = peerRef.get();

        channel.close().sync();
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

    private static final class StringInboundHandler implements ChannelHandler {
        boolean called;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            called = true;
            if (!(msg instanceof String)) {
                ctx.fireChannelRead(msg);
            }
        }
    }

    private static LocalChannel newLocalChannel() {
        return new LocalChannel(group.next());
    }

    @Test
    public void testAddLastVarArgsSkipsNull() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

        pipeline.addLast(null, newHandler(), null);
        assertEquals(1, pipeline.names().size());
        assertEquals("DefaultChannelPipelineTest$TestHandler#0", pipeline.names().get(0));

        pipeline.addLast(newHandler(), null, newHandler());
        assertEquals(3, pipeline.names().size());
        assertEquals("DefaultChannelPipelineTest$TestHandler#0", pipeline.names().get(0));
        assertEquals("DefaultChannelPipelineTest$TestHandler#1", pipeline.names().get(1));
        assertEquals("DefaultChannelPipelineTest$TestHandler#2", pipeline.names().get(2));

        pipeline.addLast((ChannelHandler) null);
        assertEquals(3, pipeline.names().size());
    }

    @Test
    public void testAddFirstVarArgsSkipsNull() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

        pipeline.addFirst(null, newHandler(), null);
        assertEquals(1, pipeline.names().size());
        assertEquals("DefaultChannelPipelineTest$TestHandler#0", pipeline.names().get(0));

        pipeline.addFirst(newHandler(), null, newHandler());
        assertEquals(3, pipeline.names().size());
        assertEquals("DefaultChannelPipelineTest$TestHandler#2", pipeline.names().get(0));
        assertEquals("DefaultChannelPipelineTest$TestHandler#1", pipeline.names().get(1));
        assertEquals("DefaultChannelPipelineTest$TestHandler#0", pipeline.names().get(2));

        pipeline.addFirst((ChannelHandler) null);
        assertEquals(3, pipeline.names().size());
    }

    @Test
    public void testRemoveChannelHandler() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) newLocalChannel().pipeline();

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
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) newLocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        pipeline.addLast("handler1", handler1);

        assertNull(pipeline.removeIfExists("handlerXXX"));
        assertNull(pipeline.removeIfExists(handler2));

        class NonExistingHandler implements ChannelHandler { }

        assertNull(pipeline.removeIfExists(NonExistingHandler.class));
        assertNotNull(pipeline.get("handler1"));
    }

    @Test
    public void testRemoveThrowNoSuchElementException() {
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) newLocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        pipeline.addLast("handler1", handler1);

        assertThrows(NoSuchElementException.class, () -> pipeline.remove("handlerXXX"));
    }

    @Test
    public void testReplaceChannelHandler() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
        ChannelPipeline pipeline = newLocalChannel().pipeline();

        ChannelHandler handler1 = newHandler();
        ChannelHandler handler2 = newHandler();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);

        ChannelHandler newHandler1 = newHandler();
        assertThrows(IllegalArgumentException.class, () -> pipeline.replace("handler1", "handler2", newHandler1));
    }

    @Test
    public void testReplaceNameWithGenerated() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
    public void testChannelHandlerContextNavigation() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
        Channel channel = newLocalChannel();
        try {
            channel.register().sync();
            channel.pipeline().addLast(new ChannelHandler() {
                class TestException extends Exception { }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                    throw new TestException();
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
            channel.close().sync();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testThrowInOtherHandlerAfterInvokedFromExceptionCaught() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        Channel channel = newLocalChannel();
        try {
            channel.register().sync();
            channel.pipeline().addLast(new ChannelHandler() {
                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    ctx.fireChannelReadComplete();
                }
            }, new ChannelHandler() {
                class TestException extends Exception { }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                    throw new TestException();
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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

            channel.pipeline().fireChannelExceptionCaught(new Exception());
            latch.await();
            assertEquals(1, counter.get());
        } finally {
            channel.close().sync();
        }
    }

    @Test
    public void testFireChannelRegistered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        pipeline.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                        latch.countDown();
                    }
                });
            }
        });
        pipeline.channel().register();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testPipelineOperation() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

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
    public void testChannelHandlerContextOrder() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        CountDownLatch latch = new CountDownLatch(8);

        pipeline.addFirst("1", newHandler(latch));
        pipeline.addLast("10", newHandler(latch));

        pipeline.addBefore("10", "5", newHandler(latch));
        pipeline.addAfter("1", "3", newHandler(latch));
        pipeline.addBefore("5", "4", newHandler(latch));
        pipeline.addAfter("5", "6", newHandler(latch));

        pipeline.addBefore("1", "0", newHandler(latch));
        pipeline.addAfter("10", "11", newHandler(latch));

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) pipeline.firstContext();
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

        final List<LifeCycleAwareTestHandler> handlers = new ArrayList<>();
        final int COUNT = 20;
        final CountDownLatch addLatch = new CountDownLatch(COUNT);
        for (int i = 0; i < COUNT; i++) {
            final LifeCycleAwareTestHandler handler = new LifeCycleAwareTestHandler("handler-" + i);

            // Add handler.
            p.addFirst(handler.name, handler);
            self.executor().execute(() -> {
                // Validate handler life-cycle methods called.
                handler.validate(true, false);

                // Store handler into the list.
                handlers.add(handler);

                addLatch.countDown();
            });
        }
        addLatch.await();

        // Change the order of remove operations over all handlers in the pipeline.
        Collections.shuffle(handlers);

        final CountDownLatch removeLatch = new CountDownLatch(COUNT);

        for (final LifeCycleAwareTestHandler handler : handlers) {
            assertSame(handler, p.remove(handler.name));

            self.executor().execute(() -> {
                // Validate handler life-cycle methods called.
                handler.validate(true, true);
                removeLatch.countDown();
            });
        }
        removeLatch.await();
    }

    @Test
    @Timeout(value = 100000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardInbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1, handler2);

        self.executor().submit(() -> {
            ChannelPipeline p = self.pipeline();
            handler1.inboundBuffer.add(8);
            assertEquals(8, handler1.inboundBuffer.peek());
            assertTrue(handler2.inboundBuffer.isEmpty());
            p.remove(handler1);
            assertEquals(1, handler2.inboundBuffer.size());
            assertEquals(8, handler2.inboundBuffer.peek());
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1, handler2);

        self.executor().submit(() -> {
            ChannelPipeline p = self.pipeline();
            handler2.outboundBuffer.add(8);
            assertEquals(8, handler2.outboundBuffer.peek());
            assertTrue(handler1.outboundBuffer.isEmpty());
            p.remove(handler2);
            assertEquals(1, handler1.outboundBuffer.size());
            assertEquals(8, handler1.outboundBuffer.peek());
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testReplaceAndForwardOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1);

        self.executor().submit(() -> {
            ChannelPipeline p = self.pipeline();
            handler1.outboundBuffer.add(8);
            assertEquals(8, handler1.outboundBuffer.peek());
            assertTrue(handler2.outboundBuffer.isEmpty());
            p.replace(handler1, "handler2", handler2);
            assertEquals(8, handler2.outboundBuffer.peek());
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testReplaceAndForwardInboundAndOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();

        setUp(handler1);

        self.executor().submit(() -> {
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
        }).sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testRemoveAndForwardInboundOutbound() throws Exception {
        final BufferedTestHandler handler1 = new BufferedTestHandler();
        final BufferedTestHandler handler2 = new BufferedTestHandler();
        final BufferedTestHandler handler3 = new BufferedTestHandler();

        setUp(handler1, handler2, handler3);

        self.executor().submit(() -> {
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
        }).sync();
    }

    @Test
    public void testFirstContextEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        assertNull(pipeline.firstContext());
    }

    @Test
    public void testLastContextEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        assertNull(pipeline.lastContext());
    }

    @Test
    public void testFirstHandlerEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        assertNull(pipeline.first());
    }

    @Test
    public void testLastHandlerEmptyPipeline() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        assertNull(pipeline.last());
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testChannelInitializerException() throws Exception {
        final IllegalStateException exception = new IllegalStateException();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        EmbeddedChannel channel = new EmbeddedChannel(false, false, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                throw exception;
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                super.channelExceptionCaught(ctx, cause);
                error.set(cause);
                latch.countDown();
            }
        });
        latch.await();
        assertFalse(channel.isActive());
        assertSame(exception, error.get());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddHandlerBeforeRegisteredThenRemove() throws Exception {
        final EventLoop loop = group.next();

        CheckEventExecutorHandler handler = new CheckEventExecutorHandler(loop);
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        pipeline.addFirst(handler);
        handler.addedFuture.sync();
        pipeline.channel().register();
        pipeline.remove(handler);
        handler.removedFuture.sync();

        pipeline.channel().close().sync();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddHandlerBeforeRegisteredThenReplace() throws Exception {
        final EventLoop loop = group.next();
        final CountDownLatch latch = new CountDownLatch(1);

        CheckEventExecutorHandler handler = new CheckEventExecutorHandler(loop);
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        pipeline.addFirst(handler);
        handler.addedFuture.sync();
        pipeline.channel().register();
        pipeline.replace(handler, null, new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
            }
        });
        handler.removedFuture.sync();
        latch.await();

        pipeline.channel().close().sync();
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testAddRemoveHandlerCalled() throws Throwable {
        ChannelPipeline pipeline = newLocalChannel().pipeline();

        CallbackCheckHandler handler = new CallbackCheckHandler();
        pipeline.addFirst(handler);
        pipeline.remove(handler);
        assertTrue(handler.addedHandler.get());
        assertTrue(handler.removedHandler.get());

        CallbackCheckHandler handlerType = new CallbackCheckHandler();
        pipeline.addFirst(handlerType);
        pipeline.remove(handlerType.getClass());
        assertTrue(handlerType.addedHandler.get());
        assertTrue(handlerType.removedHandler.get());

        CallbackCheckHandler handlerName = new CallbackCheckHandler();
        pipeline.addFirst("handler", handlerName);
        pipeline.remove("handler");
        assertTrue(handlerName.addedHandler.get());
        assertTrue(handlerName.removedHandler.get());

        CallbackCheckHandler first = new CallbackCheckHandler();
        pipeline.addFirst(first);
        pipeline.removeFirst();
        assertTrue(first.addedHandler.get());
        assertTrue(first.removedHandler.get());

        CallbackCheckHandler last = new CallbackCheckHandler();
        pipeline.addFirst(last);
        pipeline.removeLast();
        assertTrue(last.addedHandler.get());
        assertTrue(last.removedHandler.get());

        pipeline.channel().register().sync();
        Throwable cause = handler.error.get();
        Throwable causeName = handlerName.error.get();
        Throwable causeType = handlerType.error.get();
        Throwable causeFirst = first.error.get();
        Throwable causeLast = last.error.get();
        pipeline.channel().close().sync();
        rethrowIfNotNull(cause);
        rethrowIfNotNull(causeName);
        rethrowIfNotNull(causeType);
        rethrowIfNotNull(causeFirst);
        rethrowIfNotNull(causeLast);
    }

    private static void rethrowIfNotNull(Throwable cause) throws Throwable {
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testOperationsFailWhenRemoved() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        try {
            pipeline.channel().register().sync();

            ChannelHandler handler = new ChannelHandler() { };
            pipeline.addFirst(handler);
            ChannelHandlerContext ctx = pipeline.context(handler);
            pipeline.remove(handler);

            testOperationsFailsOnContext(ctx);
        } finally {
            pipeline.channel().close().sync();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testOperationsFailWhenReplaced() throws Exception {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        try {
            pipeline.channel().register().sync();

            ChannelHandler handler = new ChannelHandler() { };
            pipeline.addFirst(handler);
            ChannelHandlerContext ctx = pipeline.context(handler);
            pipeline.replace(handler, null, new ChannelHandler() { });

            testOperationsFailsOnContext(ctx);
        } finally {
            pipeline.channel().close().sync();
        }
    }

    private static void testOperationsFailsOnContext(ChannelHandlerContext ctx) throws Exception {
        assertChannelPipelineException(ctx.writeAndFlush(""));
        assertChannelPipelineException(ctx.write(""));
        assertChannelPipelineException(ctx.bind(new SocketAddress() { }));
        assertChannelPipelineException(ctx.close());
        assertChannelPipelineException(ctx.connect(new SocketAddress() { }));
        assertChannelPipelineException(ctx.connect(new SocketAddress() { }, new SocketAddress() { }));
        assertChannelPipelineException(ctx.deregister());
        assertChannelPipelineException(ctx.disconnect());

        class ChannelPipelineExceptionValidator implements ChannelHandler {

            private Promise<Void> validationPromise = ImmediateEventExecutor.INSTANCE.newPromise();

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                try {
                    assertThat(cause, Matchers.instanceOf(ChannelPipelineException.class));
                } catch (Throwable error) {
                    validationPromise.setFailure(error);
                    return;
                }
                validationPromise.setSuccess(null);
            }

            void validate() throws Exception {
                validationPromise.asFuture().sync();
                validationPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            }
        }

        ChannelPipelineExceptionValidator validator = new ChannelPipelineExceptionValidator();
        ctx.pipeline().addLast(validator);

        ctx.fireChannelRead("");
        validator.validate();

        ctx.fireChannelInboundEvent("");
        validator.validate();

        ctx.fireChannelReadComplete();
        validator.validate();

        ctx.fireChannelExceptionCaught(new Exception());
        validator.validate();

        ctx.fireChannelActive();
        validator.validate();

        ctx.fireChannelRegistered();
        validator.validate();

        ctx.fireChannelInactive();
        validator.validate();

        ctx.fireChannelUnregistered();
        validator.validate();

        ctx.fireChannelWritabilityChanged();
        validator.validate();
    }

    private static void assertChannelPipelineException(Future<Void> f) {
        try {
            f.sync();
        } catch (CompletionException e) {
            assertThat(e.getCause(), Matchers.instanceOf(ChannelPipelineException.class));
        } catch (Exception e) {
            fail("Unexpected exception", e);
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddReplaceHandlerCalled() throws Throwable {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        CallbackCheckHandler handler = new CallbackCheckHandler();
        CallbackCheckHandler handler2 = new CallbackCheckHandler();

        pipeline.addFirst(handler);
        pipeline.replace(handler, null, handler2);

        assertTrue(handler.addedHandler.get());
        assertTrue(handler.removedHandler.get());
        assertTrue(handler2.addedHandler.get());
        assertFalse(handler2.removedHandler.isDone());

        pipeline.channel().register().sync();
        Throwable cause = handler.error.get();
        if (cause != null) {
            throw cause;
        }

        Throwable cause2 = handler2.error.get();
        if (cause2 != null) {
            throw cause2;
        }

        assertFalse(handler2.removedHandler.isDone());
        pipeline.remove(handler2);
        assertTrue(handler2.removedHandler.get());
        pipeline.channel().close().sync();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddBefore() throws Throwable {
        EventLoopGroup defaultGroup = new MultithreadEventLoopGroup(2, LocalHandler.newFactory());
        try {
            EventLoop eventLoop1 = defaultGroup.next();
            EventLoop eventLoop2 = defaultGroup.next();

            ChannelPipeline pipeline1 = new LocalChannel(eventLoop1).pipeline();
            ChannelPipeline pipeline2 = new LocalChannel(eventLoop2).pipeline();

            pipeline1.channel().register().sync();
            pipeline2.channel().register().sync();

            CountDownLatch latch = new CountDownLatch(2 * 10);
            for (int i = 0; i < 10; i++) {
                eventLoop1.execute(new TestTask(pipeline2, latch));
                eventLoop2.execute(new TestTask(pipeline1, latch));
            }
            latch.await();
            pipeline1.channel().close().sync();
            pipeline2.channel().close().sync();
        } finally {
            defaultGroup.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddInListenerNio() throws Throwable {
        EventLoopGroup nioEventLoopGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        try {
            testAddInListener(new NioSocketChannel(nioEventLoopGroup.next()));
        } finally {
            nioEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testAddInListenerLocal() throws Throwable {
        testAddInListener(newLocalChannel());
    }

    private static void testAddInListener(Channel channel) throws Throwable {
        ChannelPipeline pipeline1 = channel.pipeline();
        try {
            final Object event = new Object();
            final Promise<Object> promise = ImmediateEventExecutor.INSTANCE.newPromise();
            pipeline1.channel().register().addListener(channel, (ch, future) -> {
                ChannelPipeline pipeline = ch.pipeline();
                final AtomicBoolean handlerAddedCalled = new AtomicBoolean();
                pipeline.addLast(new ChannelHandler() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                        handlerAddedCalled.set(true);
                    }

                    @Override
                    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                        promise.setSuccess(event);
                    }

                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        promise.setFailure(cause);
                    }
                });
                if (!handlerAddedCalled.get()) {
                    promise.setFailure(new AssertionError("handlerAdded(...) should have been called"));
                    return;
                }
                // This event must be captured by the added handler.
                pipeline.fireChannelInboundEvent(event);
            });
            assertSame(event, promise.asFuture().sync().getNow());
        } finally {
            pipeline1.channel().close().sync();
        }
    }

    @Test
    public void testNullName() {
        ChannelPipeline pipeline = newLocalChannel().pipeline();
        pipeline.addLast(newHandler());
        pipeline.addLast(null, newHandler());
        pipeline.addFirst(newHandler());
        pipeline.addFirst(null, newHandler());

        pipeline.addLast("test", newHandler());
        pipeline.addAfter("test", null, newHandler());

        pipeline.addBefore("test", null, newHandler());
    }

    // Test for https://github.com/netty/netty/issues/8676.
    @Test
    public void testHandlerRemovedOnlyCalledWhenHandlerAddedCalled() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        try {
            final AtomicReference<Error> errorRef = new AtomicReference<>();

            // As this only happens via a race we will verify 500 times. This was good enough to have it failed most of
            // the time.
            for (int i = 0; i < 500; i++) {

                ChannelPipeline pipeline = new LocalChannel(group.next()).pipeline();
                pipeline.channel().register().sync();

                final CountDownLatch latch = new CountDownLatch(1);

                pipeline.addLast(new ChannelHandler() {
                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                        // Block just for a bit so we have a chance to trigger the race mentioned in the issue.
                        latch.await(50, TimeUnit.MILLISECONDS);
                    }
                });

                // Close the pipeline which will call destroy0(). This will remove each handler in the pipeline and
                // should call handlerRemoved(...) if and only if handlerAdded(...) was called for the handler before.
                pipeline.close();

                pipeline.addLast(new ChannelHandler() {
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

                pipeline.channel().closeFuture().sync();

                // Schedule something on the EventLoop to ensure all other scheduled tasks had a chance to complete.
                pipeline.channel().executor().submit(() -> {
                    // NOOP
                }).sync();
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
    public void testSkipHandlerMethodsIfAnnotated() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(true);
        ChannelPipeline pipeline = channel.pipeline();

        final class SkipHandler implements ChannelHandler {
            private int state = 2;
            private Error errorRef;

            private void fail() {
                errorRef = new AssertionError("Method should never been called");
            }

            @Skip
            @Override
            public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
                fail();
                return ctx.bind(localAddress);
            }

            @Skip
            @Override
            public Future<Void> connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                         SocketAddress localAddress) {
                fail();
                return ctx.connect(remoteAddress, localAddress);
            }

            @Skip
            @Override
            public Future<Void> disconnect(ChannelHandlerContext ctx) {
                fail();
                return ctx.disconnect();
            }

            @Skip
            @Override
            public Future<Void> close(ChannelHandlerContext ctx) {
                fail();
                return ctx.close();
            }

            @Skip
            @Override
            public Future<Void> register(ChannelHandlerContext ctx) {
                fail();
                return ctx.register();
            }

            @Skip
            @Override
            public Future<Void> deregister(ChannelHandlerContext ctx) {
                fail();
                return ctx.deregister();
            }

            @Skip
            @Override
            public void read(ChannelHandlerContext ctx) {
                fail();
                ctx.read();
            }

            @Skip
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                fail();
                return ctx.write(msg);
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
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                fail();
                ctx.fireChannelInboundEvent(evt);
            }

            @Skip
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                fail();
                ctx.fireChannelWritabilityChanged();
            }

            @Skip
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                fail();
                ctx.fireChannelExceptionCaught(cause);
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

        final class OutboundCalledHandler implements ChannelHandler {
            private static final int MASK_BIND = 1;
            private static final int MASK_CONNECT = 1 << 1;
            private static final int MASK_DISCONNECT = 1 << 2;
            private static final int MASK_CLOSE = 1 << 3;
            private static final int MASK_REGISTER = 1 << 4;
            private static final int MASK_DEREGISTER = 1 << 5;
            private static final int MASK_READ = 1 << 6;
            private static final int MASK_WRITE = 1 << 7;
            private static final int MASK_FLUSH = 1 << 8;
            private static final int MASK_ADDED = 1 << 9;
            private static final int MASK_REMOVED = 1 << 10;
            private static final int MASK_TRIGGER_CUSTOM_OUTBOUND_EVENT = 1 << 9;

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
            public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
                executionMask |= MASK_BIND;
                return ctx.newSucceededFuture();
            }

            @Override
            public Future<Void> connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress) {
                executionMask |= MASK_CONNECT;
                return ctx.newSucceededFuture();
            }

            @Override
            public Future<Void> disconnect(ChannelHandlerContext ctx) {
                executionMask |= MASK_DISCONNECT;
                return ctx.newSucceededFuture();
            }

            @Override
            public Future<Void> close(ChannelHandlerContext ctx) {
                executionMask |= MASK_CLOSE;
                return ctx.newSucceededFuture();
            }

            @Override
            public Future<Void> register(ChannelHandlerContext ctx) {
                executionMask |= MASK_REGISTER;
                return ctx.newSucceededFuture();
            }

            @Override
            public Future<Void> deregister(ChannelHandlerContext ctx) {
                executionMask |= MASK_DEREGISTER;
                return ctx.newSucceededFuture();
            }

            @Override
            public void read(ChannelHandlerContext ctx) {
                executionMask |= MASK_READ;
            }

            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                executionMask |= MASK_WRITE;
                Resource.dispose(msg);
                return ctx.newSucceededFuture();
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                executionMask |= MASK_FLUSH;
            }

            @Override
            public Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
                executionMask |= MASK_TRIGGER_CUSTOM_OUTBOUND_EVENT;
                Resource.dispose(event);
                return ctx.newSucceededFuture();
            }

            void assertCalled() {
                assertCalled("handlerAdded", MASK_ADDED);
                assertCalled("handlerRemoved", MASK_REMOVED);
                assertCalled("bind", MASK_BIND);
                assertCalled("connect", MASK_CONNECT);
                assertCalled("disconnect", MASK_DISCONNECT);
                assertCalled("close", MASK_CLOSE);
                assertCalled("register", MASK_REGISTER);
                assertCalled("deregister", MASK_DEREGISTER);
                assertCalled("read", MASK_READ);
                assertCalled("write", MASK_WRITE);
                assertCalled("flush", MASK_FLUSH);
                assertCalled("triggerCustomOutboundEvent", MASK_TRIGGER_CUSTOM_OUTBOUND_EVENT);
            }

            private void assertCalled(String methodName, int mask) {
                assertTrue((executionMask & mask) != 0, methodName + " was not called");
            }
        }

        final class InboundCalledHandler implements ChannelHandler {

            private static final int MASK_CHANNEL_REGISTER = 1;
            private static final int MASK_CHANNEL_UNREGISTER = 1 << 1;
            private static final int MASK_CHANNEL_ACTIVE = 1 << 2;
            private static final int MASK_CHANNEL_INACTIVE = 1 << 3;
            private static final int MASK_CHANNEL_READ = 1 << 4;
            private static final int MASK_CHANNEL_READ_COMPLETE = 1 << 5;
            private static final int MASK_CUSTOM_INBOUND_EVENT_TRIGGERED = 1 << 6;
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
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                executionMask |= MASK_CUSTOM_INBOUND_EVENT_TRIGGERED;
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                executionMask |= MASK_CHANNEL_WRITABILITY_CHANGED;
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
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
                assertCalled("userEventTriggered", MASK_CUSTOM_INBOUND_EVENT_TRIGGERED);
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
        pipeline.fireChannelInboundEvent("");
        pipeline.fireChannelExceptionCaught(new Exception());

        pipeline.register().sync();
        pipeline.deregister().sync();
        pipeline.bind(new SocketAddress() {
        }).sync();
        pipeline.connect(new SocketAddress() {
        }).sync();
        pipeline.disconnect().sync();
        pipeline.close().sync();
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
        final ChannelPipeline pipeline = newLocalChannel().pipeline();
        final Object userEvent = new Object();
        final Object writeObject = new Object();
        final CountDownLatch doneLatch = new CountDownLatch(1);

        Runnable r = () -> {
            pipeline.addLast(new ChannelHandler() {
                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                    if (evt == userEvent) {
                        ctx.write(writeObject);
                    }
                    ctx.fireChannelInboundEvent(evt);
                }
            });
            pipeline.addFirst(new ChannelHandler() {
                @Override
                public void handlerAdded(ChannelHandlerContext ctx) {
                    ctx.fireChannelInboundEvent(userEvent);
                }

                @Override
                public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                    if (msg == writeObject) {
                        doneLatch.countDown();
                    }
                    return ctx.write(msg);
                }
            });
        };

        if (executeInEventLoop) {
            pipeline.channel().executor().execute(r);
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
            pipeline.addLast(new ChannelHandler() { });
            latch.countDown();
        }
    }

    private static final class CallbackCheckHandler extends ChannelHandlerAdapter {
        private final Promise<Boolean> addedHandlerPromise = ImmediateEventExecutor.INSTANCE.newPromise();
        private final Promise<Boolean> removedHandlerPromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final Future<Boolean> addedHandler = addedHandlerPromise.asFuture();
        final Future<Boolean> removedHandler = removedHandlerPromise.asFuture();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            if (!addedHandlerPromise.trySuccess(true)) {
                error.set(new AssertionError("handlerAdded(...) called multiple times: " + ctx.name()));
            } else if (removedHandler.isDone() && removedHandler.getNow() == Boolean.TRUE) {
                error.set(new AssertionError("handlerRemoved(...) called before handlerAdded(...): " + ctx.name()));
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!removedHandlerPromise.trySuccess(true)) {
                error.set(new AssertionError("handlerRemoved(...) called multiple times: " + ctx.name()));
            } else if (addedHandler.isDone() && addedHandler.getNow() == Boolean.FALSE) {
                error.set(new AssertionError("handlerRemoved(...) called before handlerAdded(...): " + ctx.name()));
            }
        }
    }

    private static final class CheckEventExecutorHandler extends ChannelHandlerAdapter {
        final EventExecutor executor;
        final Future<Void> addedFuture;
        final Future<Void> removedFuture;
        private final Promise<Void> addedPromise;
        private final Promise<Void> removedPromise;

        CheckEventExecutorHandler(EventExecutor executor) {
            this.executor = executor;
            addedPromise = executor.newPromise();
            addedFuture = addedPromise.asFuture();
            removedPromise = executor.newPromise();
            removedFuture = removedPromise.asFuture();
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

    private static int next(DefaultChannelHandlerContext ctx) {
        DefaultChannelHandlerContext next = ctx.next;
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

    private static void verifyContextNumber(ChannelPipeline pipeline, int expectedNumber) throws Exception {
        assertEquals(expectedNumber, pipeline.names().size());
        assertEquals(expectedNumber, pipeline.toMap().size());

        pipeline.executor().submit(() -> {
            DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) pipeline.firstContext();
            int handlerNumber = 0;
            if (ctx != null) {
                for (;;) {
                    handlerNumber++;
                    if (ctx == pipeline.lastContext()) {
                        break;
                    }
                    ctx = ctx.next;
                }
            }
            assertEquals(expectedNumber, handlerNumber);
        }).sync();
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
        return new TestHandler(null);
    }

    private static ChannelHandler newHandler(CountDownLatch latch) {
        return new TestHandler(latch);
    }

    private static class TestHandler implements ChannelHandler {
        private final CountDownLatch latch;

        TestHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ChannelHandler.super.handlerAdded(ctx);
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    private static class BufferedTestHandler implements ChannelHandler {
        final Queue<Object> inboundBuffer = new ArrayDeque<>();
        final Queue<Object> outboundBuffer = new ArrayDeque<>();

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            outboundBuffer.add(msg);
            return ctx.newSucceededFuture();
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
}
