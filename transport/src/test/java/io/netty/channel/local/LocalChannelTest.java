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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalChannelTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LocalChannelTest.class);

    private static final LocalAddress TEST_ADDRESS = new LocalAddress("test.id");

    private static EventLoopGroup group1;
    private static EventLoopGroup group2;
    private static EventLoopGroup sharedGroup;

    @BeforeClass
    public static void beforeClass() {
        group1 = new LocalEventLoopGroup(2);
        group2 = new LocalEventLoopGroup(2);
        sharedGroup = new LocalEventLoopGroup(1);
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        Future<?> group1Future = group1.shutdownGracefully(0, 0, SECONDS);
        Future<?> group2Future = group2.shutdownGracefully(0, 0, SECONDS);
        Future<?> sharedGroupFuture = sharedGroup.shutdownGracefully(0, 0, SECONDS);
        group1Future.await();
        group2Future.await();
        sharedGroupFuture.await();
    }

    @Test
    public void testLocalAddressReuse() throws Exception {
        for (int i = 0; i < 2; i ++) {
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(group1)
              .channel(LocalChannel.class)
              .handler(new TestHandler());

            sb.group(group2)
              .channel(LocalServerChannel.class)
              .childHandler(new ChannelInitializer<LocalChannel>() {
                  @Override
                  public void initChannel(LocalChannel ch) throws Exception {
                      ch.pipeline().addLast(new TestHandler());
                  }
              });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).sync().channel();

                final CountDownLatch latch = new CountDownLatch(1);
                // Connect to the server
                cc = cb.connect(sc.localAddress()).sync().channel();
                final Channel ccCpy = cc;
                cc.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Send a message event up the pipeline.
                        ccCpy.pipeline().fireChannelRead("Hello, World");
                        latch.countDown();
                    }
                });
                assertTrue(latch.await(5, SECONDS));

                // Close the channel
                closeChannel(cc);
                closeChannel(sc);
                sc.closeFuture().sync();

                assertNull(String.format(
                        "Expected null, got channel '%s' for local address '%s'",
                        LocalChannelRegistry.get(TEST_ADDRESS), TEST_ADDRESS), LocalChannelRegistry.get(TEST_ADDRESS));
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        }
    }

    @Test
    public void testWriteFailsFastOnClosedChannel() throws Exception {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(group1)
                .channel(LocalChannel.class)
                .handler(new TestHandler());

        sb.group(group2)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();

            // Connect to the server
            cc = cb.connect(sc.localAddress()).sync().channel();

            // Close the channel and write something.
            cc.close().sync();
            try {
                cc.writeAndFlush(new Object()).sync();
                fail("must raise a ClosedChannelException");
            } catch (Exception e) {
                assertThat(e, is(instanceOf(ClosedChannelException.class)));
                // Ensure that the actual write attempt on a closed channel was never made by asserting that
                // the ClosedChannelException has been created by AbstractUnsafe rather than transport implementations.
                if (e.getStackTrace().length > 0) {
                    assertThat(
                            e.getStackTrace()[0].getClassName(), is(AbstractChannel.class.getName() +
                                    "$AbstractUnsafe"));
                    e.printStackTrace();
                }
            }
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test
    public void testServerCloseChannelSameEventLoop() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ServerBootstrap sb = new ServerBootstrap()
                .group(group2)
                .channel(LocalServerChannel.class)
                .childHandler(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ctx.close();
                        latch.countDown();
                    }
                });
        Channel sc = null;
        Channel cc = null;
        try {
            sc = sb.bind(TEST_ADDRESS).sync().channel();

            Bootstrap b = new Bootstrap()
                    .group(group2)
                    .channel(LocalChannel.class)
                    .handler(new SimpleChannelInboundHandler<Object>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                            // discard
                        }
                    });
            cc = b.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(new Object());
            assertTrue(latch.await(5, SECONDS));
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test
    public void localChannelRaceCondition() throws Exception {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final EventLoopGroup clientGroup = new LocalEventLoopGroup(1) {
            @Override
            protected EventExecutor newChild(ThreadFactory threadFactory, Object... args)
                    throws Exception {
                return new SingleThreadEventLoop(this, threadFactory, true) {
                    @Override
                    protected void run() {
                        for (;;) {
                            Runnable task = takeTask();
                            if (task != null) {
                                /* Only slow down the anonymous class in LocalChannel#doRegister() */
                                if (task.getClass().getEnclosingClass() == LocalChannel.class) {
                                    try {
                                        closeLatch.await();
                                    } catch (InterruptedException e) {
                                        throw new Error(e);
                                    }
                                }
                                task.run();
                                updateLastExecutionTime();
                            }

                            if (confirmShutdown()) {
                                break;
                            }
                        }
                    }
                };
            }
        };
        Channel sc = null;
        Channel cc = null;
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sc = sb.group(group2).
                    channel(LocalServerChannel.class).
                    childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.close();
                            closeLatch.countDown();
                        }
                    }).
                    bind(TEST_ADDRESS).
                    sync().channel();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(clientGroup).
                    channel(LocalChannel.class).
                    handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            /* Do nothing */
                        }
                    });
            ChannelFuture future = bootstrap.connect(sc.localAddress());
            assertTrue("Connection should finish, not time out", future.await(200));
            cc = future.channel();
        } finally {
            closeChannel(cc);
            closeChannel(sc);
            clientGroup.shutdownGracefully(0, 0, SECONDS).await();
        }
    }

    @Test
    public void testReRegister() {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(group1)
                .channel(LocalChannel.class)
                .handler(new TestHandler());

        sb.group(group2)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

            // Connect to the server
            cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();

            cc.deregister().syncUninterruptibly();
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test
    public void testCloseInWritePromiseCompletePreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(2);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);

        try {
            cb.group(group1)
            .channel(LocalChannel.class)
            .handler(new TestHandler());

            sb.group(group2)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg.equals(data)) {
                        ReferenceCountUtil.safeRelease(msg);
                        messageLatch.countDown();
                    } else {
                        super.channelRead(ctx, msg);
                    }
                }
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    messageLatch.countDown();
                    super.channelInactive(ctx);
                }
            });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

                // Connect to the server
                cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();

                final Channel ccCpy = cc;
                // Make sure a write operation is executed in the eventloop
                cc.pipeline().lastContext().executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise promise = ccCpy.newPromise();
                        promise.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                ccCpy.pipeline().lastContext().close();
                            }
                        });
                        ccCpy.writeAndFlush(data.duplicate().retain(), promise);
                    }
                });

                assertTrue(messageLatch.await(5, SECONDS));
                assertFalse(cc.isOpen());
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            data.release();
        }
    }

    @Test
    public void testWriteInWritePromiseCompletePreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(2);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);
        final ByteBuf data2 = Unpooled.wrappedBuffer(new byte[512]);

        try {
            cb.group(group1)
            .channel(LocalChannel.class)
            .handler(new TestHandler());

            sb.group(group2)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    final long count = messageLatch.getCount();
                    if ((data.equals(msg) && count == 2) || (data2.equals(msg) && count == 1)) {
                        ReferenceCountUtil.safeRelease(msg);
                        messageLatch.countDown();
                    } else {
                        super.channelRead(ctx, msg);
                    }
                }
            });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

                // Connect to the server
                cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();

                final Channel ccCpy = cc;
                // Make sure a write operation is executed in the eventloop
                cc.pipeline().lastContext().executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise promise = ccCpy.newPromise();
                        promise.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                ccCpy.writeAndFlush(data2.duplicate().retain(), ccCpy.newPromise());
                            }
                        });
                        ccCpy.writeAndFlush(data.duplicate().retain(), promise);
                    }
                });

                assertTrue(messageLatch.await(5, SECONDS));
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            data.release();
            data2.release();
        }
    }

    @Test
    public void testPeerWriteInWritePromiseCompleteDifferentEventLoopPreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(2);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);
        final ByteBuf data2 = Unpooled.wrappedBuffer(new byte[512]);
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();

        cb.group(group1)
                .channel(LocalChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (data2.equals(msg)) {
                            ReferenceCountUtil.safeRelease(msg);
                            messageLatch.countDown();
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                });

        sb.group(group2)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (data.equals(msg)) {
                                    ReferenceCountUtil.safeRelease(msg);
                                    messageLatch.countDown();
                                } else {
                                    super.channelRead(ctx, msg);
                                }
                            }
                        });
                        serverChannelRef.set(ch);
                        serverChannelLatch.countDown();
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

            // Connect to the server
            cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();
            assertTrue(serverChannelLatch.await(5, SECONDS));

            final Channel ccCpy = cc;
            // Make sure a write operation is executed in the eventloop
            cc.pipeline().lastContext().executor().execute(new Runnable() {
                @Override
                public void run() {
                    ChannelPromise promise = ccCpy.newPromise();
                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            Channel serverChannelCpy = serverChannelRef.get();
                            serverChannelCpy.writeAndFlush(data2.duplicate().retain(), serverChannelCpy.newPromise());
                        }
                    });
                    ccCpy.writeAndFlush(data.duplicate().retain(), promise);
                }
            });

            assertTrue(messageLatch.await(5, SECONDS));
        } finally {
            closeChannel(cc);
            closeChannel(sc);
            data.release();
            data2.release();
        }
    }

    @Test
    public void testPeerWriteInWritePromiseCompleteSameEventLoopPreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(2);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);
        final ByteBuf data2 = Unpooled.wrappedBuffer(new byte[512]);
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();

        try {
            cb.group(sharedGroup)
            .channel(LocalChannel.class)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (data2.equals(msg) && messageLatch.getCount() == 1) {
                        ReferenceCountUtil.safeRelease(msg);
                        messageLatch.countDown();
                    } else {
                        super.channelRead(ctx, msg);
                    }
                }
            });

            sb.group(sharedGroup)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<LocalChannel>() {
                @Override
                public void initChannel(LocalChannel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (data.equals(msg) && messageLatch.getCount() == 2) {
                                ReferenceCountUtil.safeRelease(msg);
                                messageLatch.countDown();
                            } else {
                                super.channelRead(ctx, msg);
                            }
                        }
                    });
                    serverChannelRef.set(ch);
                    serverChannelLatch.countDown();
                }
            });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

                // Connect to the server
                cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();
                assertTrue(serverChannelLatch.await(5, SECONDS));

                final Channel ccCpy = cc;
                // Make sure a write operation is executed in the eventloop
                cc.pipeline().lastContext().executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise promise = ccCpy.newPromise();
                        promise.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                Channel serverChannelCpy = serverChannelRef.get();
                                serverChannelCpy.writeAndFlush(data2.duplicate().retain(),
                                        serverChannelCpy.newPromise());
                            }
                        });
                        ccCpy.writeAndFlush(data.duplicate().retain(), promise);
                    }
                });

                assertTrue(messageLatch.await(5, SECONDS));
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            data.release();
            data2.release();
        }
    }

    @Test
    public void testClosePeerInWritePromiseCompleteSameEventLoopPreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(2);
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();

        try {
            cb.group(sharedGroup)
            .channel(LocalChannel.class)
            .handler(new TestHandler());

            sb.group(sharedGroup)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<LocalChannel>() {
                @Override
                public void initChannel(LocalChannel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg.equals(data)) {
                                ReferenceCountUtil.safeRelease(msg);
                                messageLatch.countDown();
                            } else {
                                super.channelRead(ctx, msg);
                            }
                        }
                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            messageLatch.countDown();
                            super.channelInactive(ctx);
                        }
                    });
                    serverChannelRef.set(ch);
                    serverChannelLatch.countDown();
                }
            });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

                // Connect to the server
                cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();

                assertTrue(serverChannelLatch.await(5, SECONDS));

                final Channel ccCpy = cc;
                // Make sure a write operation is executed in the eventloop
                cc.pipeline().lastContext().executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise promise = ccCpy.newPromise();
                        promise.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                serverChannelRef.get().close();
                            }
                        });
                        ccCpy.writeAndFlush(data.duplicate().retain(), promise);
                    }
                });

                assertTrue(messageLatch.await(5, SECONDS));
                assertFalse(cc.isOpen());
                assertFalse(serverChannelRef.get().isOpen());
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            data.release();
        }
    }

    @Test
    public void testWriteWhilePeerIsClosedReleaseObjectAndFailPromise() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch serverMessageLatch = new CountDownLatch(1);
        final LatchChannelFutureListener serverChannelCloseLatch = new LatchChannelFutureListener(1);
        final LatchChannelFutureListener clientChannelCloseLatch = new LatchChannelFutureListener(1);
        final CountDownLatch writeFailLatch = new CountDownLatch(1);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);
        final ByteBuf data2 = Unpooled.wrappedBuffer(new byte[512]);
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<Channel>();

        try {
            cb.group(group1)
            .channel(LocalChannel.class)
            .handler(new TestHandler());

            sb.group(group2)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<LocalChannel>() {
                @Override
                public void initChannel(LocalChannel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (data.equals(msg)) {
                                ReferenceCountUtil.safeRelease(msg);
                                serverMessageLatch.countDown();
                            } else {
                                super.channelRead(ctx, msg);
                            }
                        }
                    });
                    serverChannelRef.set(ch);
                    serverChannelLatch.countDown();
                }
            });

            Channel sc = null;
            Channel cc = null;
            try {
                // Start server
                sc = sb.bind(TEST_ADDRESS).syncUninterruptibly().channel();

                // Connect to the server
                cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();
                assertTrue(serverChannelLatch.await(5, SECONDS));

                final Channel ccCpy = cc;
                final Channel serverChannelCpy = serverChannelRef.get();
                serverChannelCpy.closeFuture().addListener(serverChannelCloseLatch);
                ccCpy.closeFuture().addListener(clientChannelCloseLatch);

                // Make sure a write operation is executed in the eventloop
                cc.pipeline().lastContext().executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        ccCpy.writeAndFlush(data.duplicate().retain(), ccCpy.newPromise())
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                serverChannelCpy.eventLoop().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        // The point of this test is to write while the peer is closed, so we should
                                        // ensure the peer is actually closed before we write.
                                        int waitCount = 0;
                                        while (ccCpy.isOpen()) {
                                            try {
                                                Thread.sleep(50);
                                            } catch (InterruptedException ignored) {
                                                // ignored
                                            }
                                            if (++waitCount > 5) {
                                                fail();
                                            }
                                        }
                                        serverChannelCpy.writeAndFlush(data2.duplicate().retain(),
                                                                       serverChannelCpy.newPromise())
                                            .addListener(new ChannelFutureListener() {
                                            @Override
                                            public void operationComplete(ChannelFuture future) throws Exception {
                                                if (!future.isSuccess() &&
                                                    future.cause() instanceof ClosedChannelException) {
                                                    writeFailLatch.countDown();
                                                }
                                            }
                                        });
                                    }
                                });
                                ccCpy.close();
                            }
                        });
                    }
                });

                assertTrue(serverMessageLatch.await(5, SECONDS));
                assertTrue(writeFailLatch.await(5, SECONDS));
                assertTrue(serverChannelCloseLatch.await(5, SECONDS));
                assertTrue(clientChannelCloseLatch.await(5, SECONDS));
                assertFalse(ccCpy.isOpen());
                assertFalse(serverChannelCpy.isOpen());
            } finally {
                closeChannel(cc);
                closeChannel(sc);
            }
        } finally {
            data.release();
            data2.release();
        }
    }

    @Test(timeout = 3000)
    public void testConnectFutureBeforeChannelActive() throws Exception {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(group1)
                .channel(LocalChannel.class)
                .handler(new ChannelInboundHandlerAdapter());

        sb.group(group2)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();

            cc = cb.register().sync().channel();

            final ChannelPromise promise = cc.newPromise();
            final Promise<Void> assertPromise = cc.eventLoop().newPromise();

            cc.pipeline().addLast(new TestHandler() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    // Ensure the promise was done before the handler method is triggered.
                    if (promise.isDone()) {
                        assertPromise.setSuccess(null);
                    } else {
                        assertPromise.setFailure(new AssertionError("connect promise should be done"));
                    }
                }
            });
            // Connect to the server
            cc.connect(sc.localAddress(), promise).sync();

            assertPromise.syncUninterruptibly();
            assertTrue(promise.isSuccess());
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test(expected = ConnectException.class)
    public void testConnectionRefused() {
        Bootstrap sb = new Bootstrap();
        sb.group(group1)
        .channel(LocalChannel.class)
        .handler(new TestHandler())
        .connect(LocalAddress.ANY).syncUninterruptibly();
    }

    private static final class LatchChannelFutureListener extends CountDownLatch implements ChannelFutureListener {
        public LatchChannelFutureListener(int count) {
            super(count);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            countDown();
        }
    }

    private static void closeChannel(Channel cc) {
        if (cc != null) {
            cc.close().syncUninterruptibly();
        }
    }

    static class TestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            logger.info(String.format("Received mesage: %s", msg));
            ReferenceCountUtil.safeRelease(msg);
        }
    }
}
