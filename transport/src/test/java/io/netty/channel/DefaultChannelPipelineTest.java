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
import io.netty.buffer.MessageBuf;
import io.netty.buffer.ReferenceCounted;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DefaultChannelPipelineTest {

    private static final EventLoopGroup group = new LocalEventLoopGroup(1);

    private Channel self;
    private Channel peer;

    @AfterClass
    public static void afterClass() {
        group.shutdownGracefully();
    }

    private void setUp(final ChannelHandler... handlers) throws Exception {
        final AtomicReference<Channel> peerRef = new AtomicReference<Channel>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group).channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInboundMessageHandlerAdapter<Object>() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                peerRef.set(ctx.channel());
            }

            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                // Swallow.
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
    public void testMessageCatchAllInboundSink() throws Exception {
        final AtomicBoolean forwarded = new AtomicBoolean();

        setUp(new ChannelInboundMessageHandlerAdapter<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                forwarded.set(ctx.nextInboundMessageBuffer().add(msg));
            }

            @Override
            public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
                ctx.fireInboundBufferUpdated();
            }
        });

        peer.write(new Object()).sync();

        assertTrue(forwarded.get());
    }

    @Test
    public void testByteCatchAllInboundSink() throws Exception {
        final AtomicBoolean forwarded = new AtomicBoolean();
        setUp(new ChannelInboundByteHandlerAdapter() {
            @Override
            protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                ByteBuf out = ctx.nextInboundByteBuffer();
                out.writeBytes(in);
                forwarded.set(true);
                ctx.fireInboundBufferUpdated();
            }
        });

        // Not using peer.write() because the pipeline will convert the bytes into a message automatically.
        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                self.pipeline().inboundByteBuffer().writeByte(0);
                self.pipeline().fireInboundBufferUpdated();
            }
        }).sync();

        assertTrue(forwarded.get());
    }

    @Test
    public void testByteCatchAllOutboundSink() throws Exception {
        final AtomicBoolean forwarded = new AtomicBoolean();
        setUp(new ChannelOutboundByteHandlerAdapter() {
            @Override
            protected void flush(ChannelHandlerContext ctx, ByteBuf in, ChannelPromise promise) throws Exception {
                ByteBuf out = ctx.nextOutboundByteBuffer();
                out.writeBytes(in);
                forwarded.set(true);
                ctx.flush(promise);
            }
        });

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                self.pipeline().outboundByteBuffer().writeByte(0);
                self.pipeline().flush();
            }
        }).sync();

        assertTrue(forwarded.get());
    }

    @Test
    public void testFreeCalled() throws Exception {
        final CountDownLatch free = new CountDownLatch(1);

        final ReferenceCounted holder = new ReferenceCounted() {
            @Override
            public int refCnt() {
                return (int) free.getCount();
            }

            @Override
            public ReferenceCounted retain() {
                fail();
                return this;
            }

            @Override
            public ReferenceCounted retain(int increment) {
                fail();
                return this;
            }

            @Override
            public boolean release() {
                assertEquals(1, refCnt());
                free.countDown();
                return true;
            }

            @Override
            public boolean release(int decrement) {
                for (int i = 0; i < decrement; i ++) {
                    release();
                }
                return true;
            }
        };

        StringInboundHandler handler = new StringInboundHandler();
        setUp(handler);

        peer.write(holder).sync();

        assertTrue(free.await(10, TimeUnit.SECONDS));
        assertTrue(handler.called);
    }

    private static final class StringInboundHandler extends ChannelInboundMessageHandlerAdapter<String> {
        boolean called;

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            called = true;
            return super.acceptInboundMessage(msg);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
            fail();
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
        pipeline.remove(handler2);
        pipeline.remove(handler3);
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

    @Test(timeout = 100000)
    public void testRemoveAndForwardInboundByte() throws Exception {
        final ChannelInboundByteHandlerImpl handler1 = new ChannelInboundByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler2 = new ChannelInboundByteHandlerImpl();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).inboundByteBuffer().writeLong(8);
                assertEquals(8, p.context(handler1).inboundByteBuffer().readableBytes());
                assertEquals(0, p.context(handler2).inboundByteBuffer().readableBytes());
                p.remove(handler1);
                assertEquals(8, p.context(handler2).inboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(handler2.updated);
    }

    @Test(timeout = 100000)
    public void testReplaceAndForwardInboundByte() throws Exception {
        final ChannelInboundByteHandlerImpl handler1 = new ChannelInboundByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler2 = new ChannelInboundByteHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).inboundByteBuffer().writeLong(8);
                assertEquals(8, p.context(handler1).inboundByteBuffer().readableBytes());
                p.replace(handler1, "handler2", handler2);
                assertEquals(8, p.context(handler2).inboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(handler2.updated);
    }

    @Test(timeout = 10000)
    public void testRemoveAndForwardOutboundByte() throws Exception {
        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ChannelOutboundByteHandlerImpl handler2 = new ChannelOutboundByteHandlerImpl();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler2).outboundByteBuffer().writeLong(8);
                assertEquals(8, p.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(0, p.context(handler1).outboundByteBuffer().readableBytes());
                self.pipeline().remove(handler2);
                assertEquals(8, p.context(handler1).outboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(handler1.flushed);
    }

    @Test(timeout = 10000)
    public void testReplaceAndForwardOutboundByte() throws Exception {
        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ChannelOutboundByteHandlerImpl handler2 = new ChannelOutboundByteHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).outboundByteBuffer().writeLong(8);
                assertEquals(8, p.context(handler1).outboundByteBuffer().readableBytes());
                p.replace(handler1, "handler2", handler2);
                assertEquals(8, p.context(handler2).outboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(handler2.flushed);
    }

    @Test(timeout = 10000)
    public void testReplaceAndForwardDuplexByte() throws Exception {
        final ByteHandlerImpl handler1 = new ByteHandlerImpl();
        final ByteHandlerImpl handler2 = new ByteHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).outboundByteBuffer().writeLong(8);
                p.context(handler1).inboundByteBuffer().writeLong(8);

                assertEquals(8, p.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(8, p.context(handler1).inboundByteBuffer().readableBytes());

                p.replace(handler1, "handler2", handler2);
                assertEquals(8, p.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(8, p.context(handler2).inboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(((ChannelInboundByteHandlerImpl) handler2.stateHandler()).updated);
        assertTrue(((ChannelOutboundByteHandlerImpl) handler2.operationHandler()).flushed);
    }

    @Test(timeout = 10000)
    public void testRemoveAndForwardDuplexByte() throws Exception {
        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ByteHandlerImpl handler2 = new ByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler3 = new ChannelInboundByteHandlerImpl();

        setUp(handler1, handler2, handler3);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler2).outboundByteBuffer().writeLong(8);
                p.context(handler2).inboundByteBuffer().writeLong(8);

                assertEquals(8, p.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(8, p.context(handler2).inboundByteBuffer().readableBytes());

                assertEquals(0, p.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(0, p.context(handler3).inboundByteBuffer().readableBytes());

                p.remove(handler2);
                assertEquals(8, p.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(8, p.context(handler3).inboundByteBuffer().readableBytes());
            }
        }).sync();

        assertTrue(handler1.flushed);
        assertTrue(handler3.updated);
    }

    @Test(timeout = 10000)
    public void testRemoveAndForwardInboundMessage() throws Exception {
        final ChannelInboundMessageHandlerImpl handler1 = new ChannelInboundMessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler2 = new ChannelInboundMessageHandlerImpl();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).inboundMessageBuffer().add(new Object());
                assertEquals(1, p.context(handler1).inboundMessageBuffer().size());
                assertEquals(0, p.context(handler2).inboundMessageBuffer().size());
                p.remove(handler1);
                assertEquals(1, p.context(handler2).inboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(handler2.updated);
    }

    @Test(timeout = 10000)
    public void testReplaceAndForwardInboundMessage() throws Exception {
        final ChannelInboundMessageHandlerImpl handler1 = new ChannelInboundMessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler2 = new ChannelInboundMessageHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).inboundMessageBuffer().add(new Object());
                assertEquals(1, p.context(handler1).inboundMessageBuffer().size());
                p.replace(handler1, "handler2", handler2);
                assertEquals(1, p.context(handler2).inboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(handler2.updated);
    }

    @Test(timeout = 10000)
    public void testRemoveAndForwardOutboundMessage() throws Exception {
        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final ChannelOutboundMessageHandlerImpl handler2 = new ChannelOutboundMessageHandlerImpl();

        setUp(handler1, handler2);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler2).outboundMessageBuffer().add(new Object());
                assertEquals(1, p.context(handler2).outboundMessageBuffer().size());
                assertEquals(0, p.context(handler1).outboundMessageBuffer().size());
                p.remove(handler2);
                assertEquals(1, p.context(handler1).outboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(handler1.flushed);
    }

    @Test(timeout = 10000)
    public void testReplaceAndForwardOutboundMessage() throws Exception {
        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final ChannelOutboundMessageHandlerImpl handler2 = new ChannelOutboundMessageHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).outboundMessageBuffer().add(new Object());
                assertEquals(1, p.context(handler1).outboundMessageBuffer().size());
                p.replace(handler1, "handler2", handler2);
                assertEquals(1, p.context(handler2).outboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(handler2.flushed);
    }

    @Test(timeout = 10000)
    public void testReplaceAndForwardDuplexMessage() throws Exception {
        final MessageHandlerImpl handler1 = new MessageHandlerImpl();
        final MessageHandlerImpl handler2 = new MessageHandlerImpl();

        setUp(handler1);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler1).outboundMessageBuffer().add(new Object());
                p.context(handler1).inboundMessageBuffer().add(new Object());

                assertEquals(1, p.context(handler1).outboundMessageBuffer().size());
                assertEquals(1, p.context(handler1).inboundMessageBuffer().size());

                p.replace(handler1, "handler2", handler2);
                assertEquals(1, p.context(handler2).outboundMessageBuffer().size());
                assertEquals(1, p.context(handler2).inboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(((ChannelInboundMessageHandlerImpl) handler2.stateHandler()).updated);
        assertTrue(((ChannelOutboundMessageHandlerImpl) handler2.operationHandler()).flushed);
    }

    @Test(timeout = 10000)
    public void testRemoveAndForwardDuplexMessage() throws Exception {
        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final MessageHandlerImpl handler2 = new MessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler3 = new ChannelInboundMessageHandlerImpl();

        setUp(handler1, handler2, handler3);

        self.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                ChannelPipeline p = self.pipeline();
                p.context(handler2).outboundMessageBuffer().add(new Object());
                p.context(handler2).inboundMessageBuffer().add(new Object());

                assertEquals(1, p.context(handler2).outboundMessageBuffer().size());
                assertEquals(1, p.context(handler2).inboundMessageBuffer().size());

                assertEquals(0, p.context(handler1).outboundMessageBuffer().size());
                assertEquals(0, p.context(handler3).inboundMessageBuffer().size());

                p.remove(handler2);
                assertEquals(1, p.context(handler1).outboundMessageBuffer().size());
                assertEquals(1, p.context(handler3).inboundMessageBuffer().size());
            }
        }).sync();

        assertTrue(handler1.flushed);
        assertTrue(handler3.updated);
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

    private static void verifyContextNumber(ChannelPipeline pipeline, int expectedNumber) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) pipeline.firstContext();
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
    private static class TestHandler extends ChannelDuplexHandler {
        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            ctx.flush(promise);
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            ctx.fireInboundBufferUpdated();
        }
    }

    private static final class ChannelInboundByteHandlerImpl extends ChannelInboundByteHandlerAdapter {
        boolean updated;

        @Override
        protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            updated = true;
        }
    }

    private static final class ChannelOutboundByteHandlerImpl extends ChannelOutboundByteHandlerAdapter {
        boolean flushed;

        @Override
        protected void flush(ChannelHandlerContext ctx, ByteBuf in, ChannelPromise promise) throws Exception {
            promise.setSuccess();
            flushed = true;
        }
    }

    private static final class ChannelInboundMessageHandlerImpl extends ChannelStateHandlerAdapter
            implements ChannelInboundMessageHandler<Object> {
        boolean updated;
        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            updated = true;
        }
    }

    private static final class ChannelOutboundMessageHandlerImpl extends ChannelOperationHandlerAdapter
            implements ChannelOutboundMessageHandler<Object> {
        boolean flushed;
        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            promise.setSuccess();
            flushed = true;
        }
    }

    private static final class ByteHandlerImpl extends CombinedChannelDuplexHandler
            implements ChannelInboundByteHandler, ChannelOutboundByteHandler {
        ByteHandlerImpl() {
            super(new ChannelInboundByteHandlerImpl(), new ChannelOutboundByteHandlerImpl());
        }

        @Override
        public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelInboundByteHandler) stateHandler()).newInboundBuffer(ctx);
        }

        @Override
        public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
            ((ChannelInboundByteHandler) stateHandler()).discardInboundReadBytes(ctx);
        }

        @Override
        public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelOutboundByteHandler) operationHandler()).newOutboundBuffer(ctx);
        }

        @Override
        public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
            ((ChannelOutboundByteHandler) operationHandler()).discardOutboundReadBytes(ctx);
        }
    }

    private static final class MessageHandlerImpl extends CombinedChannelDuplexHandler
            implements ChannelInboundMessageHandler<Object>, ChannelOutboundMessageHandler<Object> {
        MessageHandlerImpl() {
            super(new ChannelInboundMessageHandlerImpl(), new ChannelOutboundMessageHandlerImpl());
        }

        @SuppressWarnings("unchecked")
        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelInboundMessageHandler<Object>) stateHandler()).newInboundBuffer(ctx);
        }

        @SuppressWarnings("unchecked")
        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelOutboundMessageHandler<Object>) operationHandler()).newOutboundBuffer(ctx);
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
