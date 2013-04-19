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
import io.netty.buffer.MessageBuf;
import io.netty.buffer.ReferenceCounted;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                forwarded.set(ctx.nextInboundMessageBuffer().add(msg));
            }

            @Override
            public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
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
    public void testFreeCalled() throws InterruptedException {
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

    @Test
    public void testRemoveAndForwardInboundByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelInboundByteHandlerImpl handler1 = new ChannelInboundByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler2 = new ChannelInboundByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).inboundByteBuffer().writeLong(8);
                assertEquals(8, pipeline.context(handler1).inboundByteBuffer().readableBytes());
                assertEquals(0, pipeline.context(handler2).inboundByteBuffer().readableBytes());
                pipeline.remove(handler1);
                assertEquals(8, pipeline.context(handler2).inboundByteBuffer().readableBytes());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.updated);
    }

    @Test
    public void testReplaceAndForwardInboundByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelInboundByteHandlerImpl handler1 = new ChannelInboundByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler2 = new ChannelInboundByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).inboundByteBuffer().writeLong(8);
                assertEquals(8, pipeline.context(handler1).inboundByteBuffer().readableBytes());
                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(8, pipeline.context(handler2).inboundByteBuffer().readableBytes());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.updated);
    }

    @Test
    public void testRemoveAndForwardOutboundByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ChannelOutboundByteHandlerImpl handler2 = new ChannelOutboundByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler2).outboundByteBuffer().writeLong(8);
                assertEquals(8, pipeline.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(0, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                pipeline.remove(handler2);
                assertEquals(8, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler1.flushed);
    }

    @Test
    public void testReplaceAndForwardOutboundByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ChannelOutboundByteHandlerImpl handler2 = new ChannelOutboundByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).outboundByteBuffer().writeLong(8);
                assertEquals(8, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(8, pipeline.context(handler2).outboundByteBuffer().readableBytes());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.flushed);
    }

    @Test
    public void testReplaceAndForwardDuplexByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ByteHandlerImpl handler1 = new ByteHandlerImpl();
        final ByteHandlerImpl handler2 = new ByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).outboundByteBuffer().writeLong(8);
                pipeline.context(handler1).inboundByteBuffer().writeLong(8);

                assertEquals(8, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(8, pipeline.context(handler1).inboundByteBuffer().readableBytes());

                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(8, pipeline.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(8, pipeline.context(handler2).inboundByteBuffer().readableBytes());

                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(((ChannelInboundByteHandlerImpl) handler2.stateHandler()).updated);
        assertTrue(((ChannelOutboundByteHandlerImpl) handler2.operationHandler()).flushed);
    }

    @Test
    public void testRemoveAndForwardDuplexByte() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundByteHandlerImpl handler1 = new ChannelOutboundByteHandlerImpl();
        final ByteHandlerImpl handler2 = new ByteHandlerImpl();
        final ChannelInboundByteHandlerImpl handler3 = new ChannelInboundByteHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        pipeline.addLast("handler3", handler3);

        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler2).outboundByteBuffer().writeLong(8);
                pipeline.context(handler2).inboundByteBuffer().writeLong(8);

                assertEquals(8, pipeline.context(handler2).outboundByteBuffer().readableBytes());
                assertEquals(8, pipeline.context(handler2).inboundByteBuffer().readableBytes());

                assertEquals(0, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(0, pipeline.context(handler3).inboundByteBuffer().readableBytes());

                pipeline.remove(handler2);
                assertEquals(8, pipeline.context(handler1).outboundByteBuffer().readableBytes());
                assertEquals(8, pipeline.context(handler3).inboundByteBuffer().readableBytes());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler1.flushed);
        assertTrue(handler3.updated);
    }

    @Test
    public void testRemoveAndForwardInboundMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelInboundMessageHandlerImpl handler1 = new ChannelInboundMessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler2 = new ChannelInboundMessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).inboundMessageBuffer().add(new Object());
                assertEquals(1, pipeline.context(handler1).inboundMessageBuffer().size());
                assertEquals(0, pipeline.context(handler2).inboundMessageBuffer().size());
                pipeline.remove(handler1);
                assertEquals(1, pipeline.context(handler2).inboundMessageBuffer().size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.updated);
    }

    @Test
    public void testReplaceAndForwardInboundMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelInboundMessageHandlerImpl handler1 = new ChannelInboundMessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler2 = new ChannelInboundMessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).inboundMessageBuffer().add(new Object());
                assertEquals(1, pipeline.context(handler1).inboundMessageBuffer().size());
                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(1, pipeline.context(handler2).inboundMessageBuffer().size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.updated);
    }

    @Test
    public void testRemoveAndForwardOutboundMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final ChannelOutboundMessageHandlerImpl handler2 = new ChannelOutboundMessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler2).outboundMessageBuffer().add(new Object());
                assertEquals(1, pipeline.context(handler2).outboundMessageBuffer().size());
                assertEquals(0, pipeline.context(handler1).outboundMessageBuffer().size());
                pipeline.remove(handler2);
                assertEquals(1, pipeline.context(handler1).outboundMessageBuffer().size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler1.flushed);
    }

    @Test
    public void testReplaceAndForwardOutboundMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final ChannelOutboundMessageHandlerImpl handler2 = new ChannelOutboundMessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).outboundMessageBuffer().add(new Object());
                assertEquals(1, pipeline.context(handler1).outboundMessageBuffer().size());
                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(1, pipeline.context(handler2).outboundMessageBuffer().size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler2.flushed);
    }

    @Test
    public void testReplaceAndForwardDuplexMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final MessageHandlerImpl handler1 = new MessageHandlerImpl();
        final MessageHandlerImpl handler2 = new MessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler1).outboundMessageBuffer().add(new Object());
                pipeline.context(handler1).inboundMessageBuffer().add(new Object());

                assertEquals(1, pipeline.context(handler1).outboundMessageBuffer().size());
                assertEquals(1, pipeline.context(handler1).inboundMessageBuffer().size());

                pipeline.replace(handler1, "handler2", handler2);
                assertEquals(1, pipeline.context(handler2).outboundMessageBuffer().size());
                assertEquals(1, pipeline.context(handler2).inboundMessageBuffer().size());

                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(((ChannelInboundMessageHandlerImpl) handler2.stateHandler()).updated);
        assertTrue(((ChannelOutboundMessageHandlerImpl) handler2.operationHandler()).flushed);
    }

    @Test(timeout = 20000)
    public void testLifeCycleAware() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final List<LifeCycleAwareTestHandler> handlers = new ArrayList<LifeCycleAwareTestHandler>();
        final CountDownLatch addLatch = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            final LifeCycleAwareTestHandler handler = new LifeCycleAwareTestHandler("handler-" + i);

            // Add handler.
            pipeline.addFirst(handler.name, handler);
            channel.eventLoop().execute(new Runnable() {
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

        final CountDownLatch removeLatch = new CountDownLatch(20);

        for (final LifeCycleAwareTestHandler handler : handlers) {
            assertSame(handler, pipeline.remove(handler.name));

            channel.eventLoop().execute(new Runnable() {
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
    public void testRemoveAndForwardDuplexMessage() throws Exception {
        LocalChannel channel = new LocalChannel();
        LocalEventLoopGroup group = new LocalEventLoopGroup();
        group.register(channel).awaitUninterruptibly();
        final DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel);

        final ChannelOutboundMessageHandlerImpl handler1 = new ChannelOutboundMessageHandlerImpl();
        final MessageHandlerImpl handler2 = new MessageHandlerImpl();
        final ChannelInboundMessageHandlerImpl handler3 = new ChannelInboundMessageHandlerImpl();
        pipeline.addLast("handler1", handler1);
        pipeline.addLast("handler2", handler2);
        pipeline.addLast("handler3", handler3);

        final CountDownLatch latch = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                pipeline.context(handler2).outboundMessageBuffer().add(new Object());
                pipeline.context(handler2).inboundMessageBuffer().add(new Object());

                assertEquals(1, pipeline.context(handler2).outboundMessageBuffer().size());
                assertEquals(1, pipeline.context(handler2).inboundMessageBuffer().size());

                assertEquals(0, pipeline.context(handler1).outboundMessageBuffer().size());
                assertEquals(0, pipeline.context(handler3).inboundMessageBuffer().size());

                pipeline.remove(handler2);
                assertEquals(1, pipeline.context(handler1).outboundMessageBuffer().size());
                assertEquals(1, pipeline.context(handler3).inboundMessageBuffer().size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(handler1.flushed);
        assertTrue(handler3.updated);
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
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ctx.inboundMessageBuffer().release();
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
        public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ctx.outboundMessageBuffer().release();
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
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ((ChannelInboundHandler) stateHandler()).freeInboundBuffer(ctx);
        }

        @Override
        public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelOutboundByteHandler) operationHandler()).newOutboundBuffer(ctx);
        }

        @Override
        public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
            ((ChannelOutboundByteHandler) operationHandler()).discardOutboundReadBytes(ctx);
        }

        @Override
        public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ((ChannelOutboundHandler) operationHandler()).freeOutboundBuffer(ctx);
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
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ((ChannelInboundHandler) stateHandler()).freeInboundBuffer(ctx);
        }

        @SuppressWarnings("unchecked")
        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ((ChannelOutboundMessageHandler<Object>) operationHandler()).newOutboundBuffer(ctx);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            ((ChannelOutboundHandler) operationHandler()).freeOutboundBuffer(ctx);
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
        public void afterAdd(ChannelHandlerContext ctx) {
            validate(false, false);

            afterAdd = true;
        }

        @Override
        public void afterRemove(ChannelHandlerContext ctx) {
            validate(true, false);

            afterRemove = true;
        }
    }
}
