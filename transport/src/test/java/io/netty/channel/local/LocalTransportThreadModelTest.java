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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class LocalTransportThreadModelTest {

    private static EventLoopGroup group;
    private static LocalAddress localAddr;

    @BeforeClass
    public static void init() {
        // Configure a test server
        group = new MultithreadEventLoopGroup(LocalHandler.newFactory());
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                      @Override
                      public void channelRead(ChannelHandlerContext ctx, Object msg) {
                          // Discard
                          ReferenceCountUtil.release(msg);
                      }
                  });
              }
          });

        localAddr = (LocalAddress) sb.bind(LocalAddress.ANY).syncUninterruptibly().channel().localAddress();
    }

    @AfterClass
    public static void destroy() throws Exception {
        group.shutdownGracefully().sync();
    }

    @Test(timeout = 30000)
    @Ignore("regression test")
    public void testStagedExecutionMultiple() throws Throwable {
        for (int i = 0; i < 10; i ++) {
            testStagedExecution();
        }
    }

    @Test(timeout = 5000)
    public void testStagedExecution() throws Throwable {
        EventLoopGroup l = new MultithreadEventLoopGroup(4, new DefaultThreadFactory("l"),
                LocalHandler.newFactory());
        EventExecutorGroup e1 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e1"));
        EventExecutorGroup e2 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e2"));
        ThreadNameAuditor h1 = new ThreadNameAuditor();
        ThreadNameAuditor h2 = new ThreadNameAuditor();
        ThreadNameAuditor h3 = new ThreadNameAuditor(true);

        Channel ch = new LocalChannel(l.next());
        // With no EventExecutor specified, h1 will be always invoked by EventLoop 'l'.
        ch.pipeline().addLast(h1);
        // h2 will be always invoked by EventExecutor 'e1'.
        ch.pipeline().addLast(e1, h2);
        // h3 will be always invoked by EventExecutor 'e2'.
        ch.pipeline().addLast(e2, h3);

        ch.register().sync().channel().connect(localAddr).sync();

        // Fire inbound events from all possible starting points.
        ch.pipeline().fireChannelRead("1");
        ch.pipeline().context(h1).fireChannelRead("2");
        ch.pipeline().context(h2).fireChannelRead("3");
        ch.pipeline().context(h3).fireChannelRead("4");
        // Fire outbound events from all possible starting points.
        ch.pipeline().write("5");
        ch.pipeline().context(h3).write("6");
        ch.pipeline().context(h2).write("7");
        ch.pipeline().context(h1).writeAndFlush("8").sync();

        ch.close().sync();

        // Wait until all events are handled completely.
        while (h1.outboundThreadNames.size() < 3 || h3.inboundThreadNames.size() < 3 ||
               h1.removalThreadNames.size() < 1) {
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
            Assert.assertFalse(h1.removalThreadNames.contains(currentName));
            Assert.assertFalse(h2.removalThreadNames.contains(currentName));
            Assert.assertFalse(h3.removalThreadNames.contains(currentName));

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
            for (String name: h1.removalThreadNames) {
                Assert.assertTrue(name.startsWith("l-"));
            }
            for (String name: h2.removalThreadNames) {
                Assert.assertTrue(name.startsWith("e1-"));
            }
            for (String name: h3.removalThreadNames) {
                Assert.assertTrue(name.startsWith("e2-"));
            }

            // Assert that the events for the same handler were handled by the same thread.
            Set<String> names = new HashSet<>();
            names.addAll(h1.inboundThreadNames);
            names.addAll(h1.outboundThreadNames);
            names.addAll(h1.removalThreadNames);
            Assert.assertEquals(1, names.size());

            names.clear();
            names.addAll(h2.inboundThreadNames);
            names.addAll(h2.outboundThreadNames);
            names.addAll(h2.removalThreadNames);
            Assert.assertEquals(1, names.size());

            names.clear();
            names.addAll(h3.inboundThreadNames);
            names.addAll(h3.outboundThreadNames);
            names.addAll(h3.removalThreadNames);
            Assert.assertEquals(1, names.size());

            // Count the number of events
            Assert.assertEquals(1, h1.inboundThreadNames.size());
            Assert.assertEquals(2, h2.inboundThreadNames.size());
            Assert.assertEquals(3, h3.inboundThreadNames.size());
            Assert.assertEquals(3, h1.outboundThreadNames.size());
            Assert.assertEquals(2, h2.outboundThreadNames.size());
            Assert.assertEquals(1, h3.outboundThreadNames.size());
            Assert.assertEquals(1, h1.removalThreadNames.size());
            Assert.assertEquals(1, h2.removalThreadNames.size());
            Assert.assertEquals(1, h3.removalThreadNames.size());
        } catch (AssertionError e) {
            System.out.println("H1I: " + h1.inboundThreadNames);
            System.out.println("H2I: " + h2.inboundThreadNames);
            System.out.println("H3I: " + h3.inboundThreadNames);
            System.out.println("H1O: " + h1.outboundThreadNames);
            System.out.println("H2O: " + h2.outboundThreadNames);
            System.out.println("H3O: " + h3.outboundThreadNames);
            System.out.println("H1R: " + h1.removalThreadNames);
            System.out.println("H2R: " + h2.removalThreadNames);
            System.out.println("H3R: " + h3.removalThreadNames);
            throw e;
        } finally {
            l.shutdownGracefully();
            e1.shutdownGracefully();
            e2.shutdownGracefully();

            l.terminationFuture().sync();
            e1.terminationFuture().sync();
            e2.terminationFuture().sync();
        }
    }

    @Test(timeout = 30000)
    @Ignore
    public void testConcurrentMessageBufferAccess() throws Throwable {
        EventLoopGroup l = new MultithreadEventLoopGroup(4, new DefaultThreadFactory("l"),
                LocalHandler.newFactory());
        EventExecutorGroup e1 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e1"));
        EventExecutorGroup e2 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e2"));
        EventExecutorGroup e3 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e3"));
        EventExecutorGroup e4 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e4"));
        EventExecutorGroup e5 = new DefaultEventExecutorGroup(4, new DefaultThreadFactory("e5"));

        try {
            final MessageForwarder1 h1 = new MessageForwarder1();
            final MessageForwarder2 h2 = new MessageForwarder2();
            final MessageForwarder3 h3 = new MessageForwarder3();
            final MessageForwarder1 h4 = new MessageForwarder1();
            final MessageForwarder2 h5 = new MessageForwarder2();
            final MessageDiscarder  h6 = new MessageDiscarder();

            final Channel ch = new LocalChannel(l.next());

            // inbound:  int -> byte[4] -> int -> int -> byte[4] -> int -> /dev/null
            // outbound: int -> int -> byte[4] -> int -> int -> byte[4] -> /dev/null
            ch.pipeline().addLast(h1)
                         .addLast(e1, h2)
                         .addLast(e2, h3)
                         .addLast(e3, h4)
                         .addLast(e4, h5)
                         .addLast(e5, h6);

            ch.register().sync().channel().connect(localAddr).sync();

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
                        for (int j = start; j < end; j ++) {
                            ch.pipeline().fireChannelRead(Integer.valueOf(j));
                        }
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
                        for (int j = start; j < end; j ++) {
                            ch.write(Integer.valueOf(j));
                        }
                        ch.flush();
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
        } finally {
            l.shutdownGracefully();
            e1.shutdownGracefully();
            e2.shutdownGracefully();
            e3.shutdownGracefully();
            e4.shutdownGracefully();
            e5.shutdownGracefully();

            l.terminationFuture().sync();
            e1.terminationFuture().sync();
            e2.terminationFuture().sync();
            e3.terminationFuture().sync();
            e4.terminationFuture().sync();
            e5.terminationFuture().sync();
        }
    }

    private static class ThreadNameAuditor extends ChannelDuplexHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();

        private final Queue<String> inboundThreadNames = new ConcurrentLinkedQueue<>();
        private final Queue<String> outboundThreadNames = new ConcurrentLinkedQueue<>();
        private final Queue<String> removalThreadNames = new ConcurrentLinkedQueue<>();
        private final boolean discard;

        ThreadNameAuditor() {
            this(false);
        }

        ThreadNameAuditor(boolean discard) {
            this.discard = discard;
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            removalThreadNames.add(Thread.currentThread().getName());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            inboundThreadNames.add(Thread.currentThread().getName());
            if (!discard) {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            outboundThreadNames.add(Thread.currentThread().getName());
            ctx.write(msg, promise);
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
    private static class MessageForwarder1 extends ChannelDuplexHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            ByteBuf out = ctx.alloc().buffer(4);
            int m = ((Integer) msg).intValue();
            int expected = inCnt ++;
            Assert.assertEquals(expected, m);
            out.writeInt(m);

            ctx.fireChannelRead(out);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            // Don't let the write request go to the server-side channel - just swallow.
            boolean swallow = this == ctx.pipeline().first();

            ByteBuf m = (ByteBuf) msg;
            int count = m.readableBytes() / 4;
            for (int j = 0; j < count; j ++) {
                int actual = m.readInt();
                int expected = outCnt ++;
                Assert.assertEquals(expected, actual);
                if (!swallow) {
                    ctx.write(actual);
                }
            }
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER, promise);
            m.release();
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
    private static class MessageForwarder2 extends ChannelDuplexHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            ByteBuf m = (ByteBuf) msg;
            int count = m.readableBytes() / 4;
            for (int j = 0; j < count; j ++) {
                int actual = m.readInt();
                int expected = inCnt ++;
                Assert.assertEquals(expected, actual);
                ctx.fireChannelRead(actual);
            }
            m.release();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            ByteBuf out = ctx.alloc().buffer(4);
            int m = (Integer) msg;
            int expected = outCnt ++;
            Assert.assertEquals(expected, m);
            out.writeInt(m);

            ctx.write(out, promise);
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
    private static class MessageForwarder3 extends ChannelDuplexHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            int actual = (Integer) msg;
            int expected = inCnt ++;
            Assert.assertEquals(expected, actual);

            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            int actual = (Integer) msg;
            int expected = outCnt ++;
            Assert.assertEquals(expected, actual);

            ctx.write(msg, promise);
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
    private static class MessageDiscarder extends ChannelDuplexHandler {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();
        private volatile int inCnt;
        private volatile int outCnt;
        private volatile Thread t;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Thread t = this.t;
            if (t == null) {
                this.t = Thread.currentThread();
            } else {
                Assert.assertSame(t, Thread.currentThread());
            }

            int actual = (Integer) msg;
            int expected = inCnt ++;
            Assert.assertEquals(expected, actual);
        }

        @Override
        public void write(
                ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Assert.assertSame(t, Thread.currentThread());

            int actual = (Integer) msg;
            int expected = outCnt ++;
            Assert.assertEquals(expected, actual);
            ctx.write(msg, promise);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            exception.compareAndSet(null, cause);
            //System.err.print("[" + Thread.currentThread().getName() + "] ");
            //cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }
}
