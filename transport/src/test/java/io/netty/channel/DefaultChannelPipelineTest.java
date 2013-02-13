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


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ReferenceCounted;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class DefaultChannelPipelineTest {
    @Test
    public void testMessageCatchAllInboundSink() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final AtomicBoolean forwarded = new AtomicBoolean();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);
        pipeline.addLast(new ChannelInboundMessageHandlerAdapter<Object>() {
            @Override
            protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                forwarded.set(ctx.nextInboundMessageBuffer().add(msg));
            }

            @Override
            protected void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
                ctx.fireInboundBufferUpdated();
            }
        });
        channel.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                pipeline.fireChannelActive();
                pipeline.inboundMessageBuffer().add(new Object());
                pipeline.fireInboundBufferUpdated();
            }
        }).get();

        assertTrue(forwarded.get());
    }

    @Test
    public void testByteCatchAllInboundSink() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final AtomicBoolean forwarded = new AtomicBoolean();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);
        pipeline.addLast(new ChannelInboundByteHandlerAdapter() {
            @Override
            protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                ByteBuf out = ctx.nextInboundByteBuffer();
                out.writeBytes(in);
                forwarded.set(true);
                ctx.fireInboundBufferUpdated();
            }
        });
        channel.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                pipeline.fireChannelActive();
                pipeline.inboundByteBuffer().writeByte(0);
                pipeline.fireInboundBufferUpdated();
            }
        }).get();

        assertTrue(forwarded.get());
    }

    @Test
    public void testByteCatchAllOutboundSink() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final AtomicBoolean forwarded = new AtomicBoolean();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);
        pipeline.addLast(new ChannelOutboundByteHandlerAdapter() {
            @Override
            protected void flush(ChannelHandlerContext ctx, ByteBuf in, ChannelPromise promise) throws Exception {
                ByteBuf out = ctx.nextOutboundByteBuffer();
                out.writeBytes(in);
                forwarded.set(true);
                ctx.flush(promise);
            }
        });
        channel.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                pipeline.fireChannelActive();
                pipeline.outboundByteBuffer().writeByte(0);
                pipeline.flush();
            }
        }).get();

        Thread.sleep(1000);
        assertTrue(forwarded.get());
    }

    @Test
    public void testFreeCalled() throws InterruptedException{
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
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        StringInboundHandler handler = new StringInboundHandler();
        pipeline.addLast(handler);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.fireChannelActive();
                pipeline.inboundMessageBuffer().add(holder);
                pipeline.fireInboundBufferUpdated();
            }
        });

        assertTrue(free.await(10, TimeUnit.SECONDS));
        assertTrue(handler.called);
    }

    private static final class StringInboundHandler extends ChannelInboundMessageHandlerAdapter<String> {
        boolean called;

        @Override
        protected boolean acceptInboundMessage(Object msg) throws Exception {
            called = true;
            return super.acceptInboundMessage(msg);
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
            fail();
        }
    }


    @Test
    public void testRemoveChannelHandler() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

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
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

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
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());

        final int HANDLER_ARRAY_LEN = 5;
        ChannelHandler[] firstHandlers = newHandlers(HANDLER_ARRAY_LEN);
        ChannelHandler[] lastHandlers = newHandlers(HANDLER_ARRAY_LEN);

        pipeline.addFirst(firstHandlers);
        pipeline.addLast(lastHandlers);

        verifyContextNumber(pipeline, HANDLER_ARRAY_LEN * 2);
    }

    @Test
    public void testPipelineOperation() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());
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
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline(new LocalChannel());
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

    private static void verifyContextNumber(DefaultChannelPipeline pipeline, int expectedNumber) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) pipeline.firstContext();
        int handlerNumber = 0;
        while (ctx != pipeline.tail) {
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
}
