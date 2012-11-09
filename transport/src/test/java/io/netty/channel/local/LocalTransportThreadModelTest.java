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
package io.netty.channel.local;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundByteHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.DefaultEventExecutorGroup;
import io.netty.channel.EventExecutorGroup;
import io.netty.channel.EventLoopGroup;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LocalTransportThreadModelTest {

    private static ServerBootstrap sb;
    private static LocalAddress ADDR;

    @BeforeClass
    public static void init() {
        // Configure a test server
        sb = new ServerBootstrap();
        sb.group(new LocalEventLoopGroup())
          .channel(LocalServerChannel.class)
          .localAddress(LocalAddress.ANY)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundMessageHandlerAdapter<Object>() {
                    @Override
                    public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                        // Discard
                    }
                  });
              }
          });

        ADDR = (LocalAddress) sb.bind().syncUninterruptibly().channel().localAddress();
    }

    @AfterClass
    public static void destroy() {
        sb.shutdown();
    }

    @Test(timeout = 5000)
    public void testStagedExecutionMultiple() throws Throwable {
        for (int i = 0; i < 10; i ++) {
            testStagedExecution();
        }
    }

    @Test(timeout = 5000)
    public void testStagedExecution() throws Throwable {
        EventLoopGroup l = new LocalEventLoopGroup(4, new PrefixThreadFactory("l"));
        EventExecutorGroup e1 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e1"));
        EventExecutorGroup e2 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e2"));
        ThreadNameAuditor h1 = new ThreadNameAuditor();
        ThreadNameAuditor h2 = new ThreadNameAuditor();
        ThreadNameAuditor h3 = new ThreadNameAuditor();

        Channel ch = new LocalChannel();
        // With no EventExecutor specified, h1 will be always invoked by EventLoop 'l'.
        ch.pipeline().addLast(h1);
        // h2 will be always invoked by EventExecutor 'e1'.
        ch.pipeline().addLast(e1, h2);
        // h3 will be always invoked by EventExecutor 'e2'.
        ch.pipeline().addLast(e2, h3);

        l.register(ch).sync().channel().connect(ADDR).sync();

        // Fire inbound events from all possible starting points.
        ch.pipeline().fireInboundBufferUpdated();
        ch.pipeline().context(h1).fireInboundBufferUpdated();
        ch.pipeline().context(h2).fireInboundBufferUpdated();
        ch.pipeline().context(h3).fireInboundBufferUpdated();
        // Fire outbound events from all possible starting points.
        ch.pipeline().flush();
        ch.pipeline().context(h3).flush();
        ch.pipeline().context(h2).flush();
        ch.pipeline().context(h1).flush().sync();

        // Wait until all events are handled completely.
        while (h1.outboundThreadNames.size() < 3 || h3.inboundThreadNames.size() < 3) {
            if (h1.exception.get() != null) {
                throw h1.exception.get();
            }
            if (h2.exception.get() != null) {
                throw h2.exception.get();
            }
            if (h3.exception.get() != null) {
                throw h3.exception.get();
            }

            Thread.sleep(10);
        }

        String currentName = Thread.currentThread().getName();

        try {
            // Events should never be handled from the current thread.
            Assert.assertFalse(h1.inboundThreadNames.contains(currentName));
            Assert.assertFalse(h2.inboundThreadNames.contains(currentName));
            Assert.assertFalse(h3.inboundThreadNames.contains(currentName));
            Assert.assertFalse(h1.outboundThreadNames.contains(currentName));
            Assert.assertFalse(h2.outboundThreadNames.contains(currentName));
            Assert.assertFalse(h3.outboundThreadNames.contains(currentName));

            // Assert that events were handled by the correct executor.
            for (String name: h1.inboundThreadNames) {
                Assert.assertTrue(name.startsWith("l-"));
            }
            for (String name: h2.inboundThreadNames) {
                Assert.assertTrue(name.startsWith("e1-"));
            }
            for (String name: h3.inboundThreadNames) {
                Assert.assertTrue(name.startsWith("e2-"));
            }
            for (String name: h1.outboundThreadNames) {
                Assert.assertTrue(name.startsWith("l-"));
            }
            for (String name: h2.outboundThreadNames) {
                Assert.assertTrue(name.startsWith("e1-"));
            }
            for (String name: h3.outboundThreadNames) {
                Assert.assertTrue(name.startsWith("e2-"));
            }

            // Assert that the events for the same handler were handled by the same thread.
            Set<String> names = new HashSet<String>();
            names.addAll(h1.inboundThreadNames);
            names.addAll(h1.outboundThreadNames);
            Assert.assertEquals(1, names.size());

            names.clear();
            names.addAll(h2.inboundThreadNames);
            names.addAll(h2.outboundThreadNames);
            Assert.assertEquals(1, names.size());

            names.clear();
            names.addAll(h3.inboundThreadNames);
            names.addAll(h3.outboundThreadNames);
            Assert.assertEquals(1, names.size());

            // Count the number of events
            Assert.assertEquals(1, h1.inboundThreadNames.size());
            Assert.assertEquals(2, h2.inboundThreadNames.size());
            Assert.assertEquals(3, h3.inboundThreadNames.size());
            Assert.assertEquals(3, h1.outboundThreadNames.size());
            Assert.assertEquals(2, h2.outboundThreadNames.size());
            Assert.assertEquals(1, h3.outboundThreadNames.size());
        } catch (AssertionError e) {
            System.out.println("H1I: " + h1.inboundThreadNames);
            System.out.println("H2I: " + h2.inboundThreadNames);
            System.out.println("H3I: " + h3.inboundThreadNames);
            System.out.println("H1O: " + h1.outboundThreadNames);
            System.out.println("H2O: " + h2.outboundThreadNames);
            System.out.println("H3O: " + h3.outboundThreadNames);
            throw e;
        } finally {
            l.shutdown();
            l.awaitTermination(5, TimeUnit.SECONDS);
            e1.shutdown();
            e1.awaitTermination(5, TimeUnit.SECONDS);
            e2.shutdown();
            e2.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test(timeout = 30000)
    public void testConcurrentMessageBufferAccess() throws Throwable {
        EventLoopGroup l = new LocalEventLoopGroup(4, new PrefixThreadFactory("l"));
        EventExecutorGroup e1 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e1"));
        EventExecutorGroup e2 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e2"));
        EventExecutorGroup e3 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e3"));
        EventExecutorGroup e4 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e4"));
        EventExecutorGroup e5 = new DefaultEventExecutorGroup(4, new PrefixThreadFactory("e5"));

        try {
            final MessageForwarder1 h1 = new MessageForwarder1();
            final MessageForwarder2 h2 = new MessageForwarder2();
            final MessageForwarder3 h3 = new MessageForwarder3();
            final MessageForwarder1 h4 = new MessageForwarder1();
            final MessageForwarder2 h5 = new MessageForwarder2();
            final MessageDiscarder  h6 = new MessageDiscarder();

            final Channel ch = new LocalChannel();

            // inbound:  int -> byte[4] -> int -> int -> byte[4] -> int -> /dev/null
            // outbound: int -> int -> byte[4] -> int -> int -> byte[4] -> /dev/null
            ch.pipeline().addLast(h1)
                         .addLast(e1, h2)
                         .addLast(e2, h3)
                         .addLast(e3, h4)
                         .addLast(e4, h5)
                         .addLast(e5, h6);

            l.register(ch).sync().channel().connect(ADDR).sync();

            final int ROUNDS = 1024;
            final int ELEMS_PER_ROUNDS = 8192;
            final int TOTAL_CNT = ROUNDS * ELEMS_PER_ROUNDS;
            for (int i = 0; i < TOTAL_CNT;) {
                final int start = i;
                final int end = i + ELEMS_PER_ROUNDS;
                i = end;

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        MessageBuf<Object> buf = ch.pipeline().inboundMessageBuffer();
                        for (int j = start; j < end; j ++) {
                            buf.add(Integer.valueOf(j));
                        }
                        ch.pipeline().fireInboundBufferUpdated();
                    }
                });
            }

            while (h1.inCnt < TOTAL_CNT || h2.inCnt < TOTAL_CNT || h3.inCnt < TOTAL_CNT ||
                    h4.inCnt < TOTAL_CNT || h5.inCnt < TOTAL_CNT || h6.inCnt < TOTAL_CNT) {
                if (h1.exception.get() != null) {
                    throw h1.exception.get();
                }
                if (h2.exception.get() != null) {
                    throw h2.exception.get();
                }
                if (h3.exception.get() != null) {
                    throw h3.exception.get();
                }
                if (h4.exception.get() != null) {
                    throw h4.exception.get();
                }
                if (h5.exception.get() != null) {
                    throw h5.exception.get();
                }
                if (h6.exception.get() != null) {
                    throw h6.exception.get();
                }
                Thread.sleep(10);
            }

            for (int i = 0; i < TOTAL_CNT;) {
                final int start = i;
                final int end = i + ELEMS_PER_ROUNDS;
                i = end;

                ch.pipeline().context(h6).executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        MessageBuf<Object> buf = ch.pipeline().outboundMessageBuffer();
                        for (int j = start; j < end; j ++) {
                            buf.add(Integer.valueOf(j));
                        }
                        ch.pipeline().flush();
                    }
                });
            }

            while (h1.outCnt < TOTAL_CNT || h2.outCnt < TOTAL_CNT || h3.outCnt < TOTAL_CNT ||
                    h4.outCnt < TOTAL_CNT || h5.outCnt < TOTAL_CNT || h6.outCnt < TOTAL_CNT) {
                if (h1.exception.get() != null) {
                    throw h1.exception.get();
                }
                if (h2.exception.get() != null) {
                    throw h2.exception.get();
                }
                if (h3.exception.get() != null) {
                    throw h3.exception.get();
                }
                if (h4.exception.get() != null) {
                    throw h4.exception.get();
                }
                if (h5.exception.get() != null) {
                    throw h5.exception.get();
                }
                if (h6.exception.get() != null) {
                    throw h6.exception.get();
                }
                Thread.sleep(10);
            }

            ch.close().sync();
            h6.latch.await(); // Wait until channelInactive() is triggered.

        } finally {
            l.shutdown();
            e1.shutdown();
            e2.shutdown();
            e3.shutdown();
            e4.shutdown();
            e5.shutdown();
        }
    }

    private static class ThreadNameAuditor
            extends ChannelHandlerAdapter
            implements ChannelInboundMessageHandler<Object>,
                       ChannelOutboundMessageHandler<Object> {

        private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        private final Queue<String> inboundThreadNames = new ConcurrentLinkedQueue<String>();
        private final Queue<String> outboundThreadNames = new ConcurrentLinkedQueue<String>();

        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx) throws Exception {
            ctx.inboundMessageBuffer().clear();
            inboundThreadNames.add(Thread.currentThread().getName());
            ctx.fireInboundBufferUpdated();
        }

        @Override
        public void flush(ChannelHandlerContext ctx,
                ChannelFuture future) throws Exception {
            ctx.outboundMessageBuffer().clear();
            outboundThreadNames.add(Thread.currentThread().getName());
            ctx.flush(future);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            System.err.print('[' + Thread.currentThread().getName() + "] ");
            cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Converts integers into a binary stream.
     */
    private static class MessageForwarder1
            extends ChannelHandlerAdapter
            implements ChannelInboundMessageHandler<Integer>, ChannelOutboundByteHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public MessageBuf<Integer> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.buffer();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            MessageBuf<Integer> in = ctx.inboundMessageBuffer();
            ByteBuf out = ctx.nextInboundByteBuffer();

            for (;;) {
                Integer msg = in.poll();
                if (msg == null) {
                    break;
                }

                int expected = inCnt ++;
                Assert.assertEquals(expected, msg.intValue());
                out.writeInt(msg);
            }
            ctx.fireInboundBufferUpdated();
        }

        @Override
        public void flush(ChannelHandlerContext ctx,
                ChannelFuture future) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            // Don't let the write request go to the server-side channel - just swallow.
            boolean swallow = this == ctx.pipeline().first();

            ByteBuf in = ctx.outboundByteBuffer();
            MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
            while (in.readableBytes() >= 4) {
                int msg = in.readInt();
                int expected = outCnt ++;
                Assert.assertEquals(expected, msg);
                if (!swallow) {
                    out.add(msg);
                }
            }
            in.unsafe().discardSomeReadBytes();
            if (swallow) {
                future.setSuccess();
            } else {
                ctx.flush(future);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            //System.err.print("[" + Thread.currentThread().getName() + "] ");
            //cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Converts a binary stream into integers.
     */
    private static class MessageForwarder2
            extends ChannelHandlerAdapter
            implements ChannelInboundByteHandler, ChannelOutboundMessageHandler<Integer> {

        private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public ByteBuf newInboundBuffer(
                ChannelHandlerContext ctx) throws Exception {
            return Unpooled.buffer();
        }

        @Override
        public MessageBuf<Integer> newOutboundBuffer(
                ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            ByteBuf in = ctx.inboundByteBuffer();
            MessageBuf<Object> out = ctx.nextInboundMessageBuffer();

            while (in.readableBytes() >= 4) {
                int msg = in.readInt();
                int expected = inCnt ++;
                Assert.assertEquals(expected, msg);
                out.add(msg);
            }
            in.discardReadBytes();
            ctx.fireInboundBufferUpdated();
        }

        @Override
        public void flush(ChannelHandlerContext ctx,
                ChannelFuture future) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            MessageBuf<Integer> in = ctx.outboundMessageBuffer();
            ByteBuf out = ctx.nextOutboundByteBuffer();

            for (;;) {
                Integer msg = in.poll();
                if (msg == null) {
                    break;
                }

                int expected = outCnt ++;
                Assert.assertEquals(expected, msg.intValue());
                out.writeInt(msg);
            }
            ctx.flush(future);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            //System.err.print("[" + Thread.currentThread().getName() + "] ");
            //cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Simply forwards the received object to the next handler.
     */
    private static class MessageForwarder3
            extends ChannelHandlerAdapter
            implements ChannelInboundMessageHandler<Object>, ChannelOutboundMessageHandler<Object> {

        private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            MessageBuf<Object> in = ctx.inboundMessageBuffer();
            MessageBuf<Object> out = ctx.nextInboundMessageBuffer();
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                int expected = inCnt ++;
                Assert.assertEquals(expected, msg);
                out.add(msg);
            }
            ctx.fireInboundBufferUpdated();
        }

        @Override
        public void flush(ChannelHandlerContext ctx,
                ChannelFuture future) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            MessageBuf<Object> in = ctx.outboundMessageBuffer();
            MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                int expected = outCnt ++;
                Assert.assertEquals(expected, msg);
                out.add(msg);
            }
            ctx.flush(future);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            System.err.print('[' + Thread.currentThread().getName() + "] ");
            cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Discards all received messages.
     */
    private static class MessageDiscarder
            extends ChannelHandlerAdapter
            implements ChannelInboundMessageHandler<Object>, ChannelOutboundMessageHandler<Object> {

        private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            MessageBuf<Object> in = ctx.inboundMessageBuffer();
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }
                int expected = inCnt ++;
                Assert.assertEquals(expected, msg);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx,
                ChannelFuture future) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            MessageBuf<Object> in = ctx.outboundMessageBuffer();
            MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
            for (;;) {
                Object msg = in.poll();
                if (msg == null) {
                    break;
                }

                int expected = outCnt ++;
                Assert.assertEquals(expected, msg);
                out.add(msg);
            }
            ctx.flush(future);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            //System.err.print("[" + Thread.currentThread().getName() + "] ");
            //cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    private static class PrefixThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger id = new AtomicInteger();

        public PrefixThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + '-' + id.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
