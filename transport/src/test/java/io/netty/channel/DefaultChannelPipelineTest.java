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
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.bind(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelConnect() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.connect(new LocalAddress("test"), promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelDisconnect() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.disconnect(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelClose() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.close(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelDeregister() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();

        ChannelPromise promise = pipeline.channel().newPromise();
        assertTrue(promise.cancel(false));
        ChannelFuture future = pipeline.deregister(promise);
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancelWrite() throws Exception {
        ChannelPipeline pipeline = new LocalChannel().pipeline();
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
}
