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
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LocalChannelTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LocalChannelTest.class);

    private static final LocalAddress TEST_ADDRESS = new LocalAddress("test.id");

    private static EventLoopGroup group1;
    private static EventLoopGroup group2;
    private static EventLoopGroup sharedGroup;

    @BeforeAll
    public static void beforeClass() {
        group1 = new DefaultEventLoopGroup(2);
        group2 = new DefaultEventLoopGroup(2);
        sharedGroup = new DefaultEventLoopGroup(1);
    }

    @AfterAll
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

                assertNull(LocalChannelRegistry.get(TEST_ADDRESS), String.format(
                        "Expected null, got channel '%s' for local address '%s'",
                        LocalChannelRegistry.get(TEST_ADDRESS), TEST_ADDRESS));
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
        final EventLoopGroup clientGroup = new DefaultEventLoopGroup(1) {
            @Override
            protected EventLoop newChild(Executor threadFactory, Object... args)
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
            assertTrue(future.await(2000), "Connection should finish, not time out");
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
                        ccCpy.writeAndFlush(data.retainedDuplicate(), promise);
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
    public void testCloseAfterWriteInSameEventLoopPreservesOrder() throws InterruptedException {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();
        final CountDownLatch messageLatch = new CountDownLatch(3);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[1024]);

        try {
            cb.group(sharedGroup)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ctx.writeAndFlush(data.retainedDuplicate());
                        }

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

            sb.group(sharedGroup)
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (data.equals(msg)) {
                                messageLatch.countDown();
                                ctx.writeAndFlush(data.retainedDuplicate());
                                ctx.close();
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
        final ByteBuf data = Unpooled.buffer();
        final ByteBuf data2 = Unpooled.buffer();
        data.writeInt(Integer.BYTES).writeInt(2);
        data2.writeInt(Integer.BYTES).writeInt(1);

        try {
            cb.group(group1)
            .channel(LocalChannel.class)
            .handler(new TestHandler());

            sb.group(group2)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof ByteBuf) {
                        ByteBuf buf = (ByteBuf) msg;
                        while (buf.isReadable()) {
                            int size = buf.readInt();
                            ByteBuf slice = buf.readRetainedSlice(size);
                            try {
                                if (slice.readInt() == messageLatch.getCount()) {
                                    messageLatch.countDown();
                                }
                            } finally {
                                slice.release();
                            }
                        }
                        buf.release();
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
                                ccCpy.writeAndFlush(data2.retainedDuplicate(), ccCpy.newPromise());
                            }
                        });
                        ccCpy.writeAndFlush(data.retainedDuplicate(), promise);
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
                            serverChannelCpy.writeAndFlush(data2.retainedDuplicate(), serverChannelCpy.newPromise());
                        }
                    });
                    ccCpy.writeAndFlush(data.retainedDuplicate(), promise);
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
                                serverChannelCpy.writeAndFlush(
                                        data2.retainedDuplicate(), serverChannelCpy.newPromise());
                            }
                        });
                        ccCpy.writeAndFlush(data.retainedDuplicate(), promise);
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
                        ccCpy.writeAndFlush(data.retainedDuplicate(), ccCpy.newPromise())
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
                                        serverChannelCpy.writeAndFlush(data2.retainedDuplicate(),
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

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
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

    @Test
    public void testConnectionRefused() {
        final Bootstrap sb = new Bootstrap();
        sb.group(group1)
        .channel(LocalChannel.class)
        .handler(new TestHandler());
        assertThrows(ConnectException.class, new Executable() {
            @Override
            public void execute() {
                sb.connect(LocalAddress.ANY).syncUninterruptibly();
            }
        });
    }

    private static final class LatchChannelFutureListener extends CountDownLatch implements ChannelFutureListener {
        private LatchChannelFutureListener(int count) {
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
            logger.info(String.format("Received message: %s", msg));
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Test
    public void testNotLeakBuffersWhenCloseByRemotePeer() throws Exception {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(sharedGroup)
                .channel(LocalChannel.class)
                .handler(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                        ctx.writeAndFlush(ctx.alloc().buffer().writeZero(100));
                    }

                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
                        // Just drop the buffer
                    }
                });

        sb.group(sharedGroup)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {

                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
                                while (buffer.isReadable()) {
                                    // Fill the ChannelOutboundBuffer with multiple buffers
                                    ctx.write(buffer.readRetainedSlice(1));
                                }
                                // Flush and so transfer the written buffers to the inboundBuffer of the remote peer.
                                // After this point the remote peer is responsible to release all the buffers.
                                ctx.flush();
                                // This close call will trigger the remote peer close as well.
                                ctx.close();
                            }
                        });
                    }
                });

        Channel sc = null;
        LocalChannel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();

            // Connect to the server
            cc = (LocalChannel) cb.connect(sc.localAddress()).sync().channel();

            // Close the channel
            closeChannel(cc);
            assertTrue(cc.inboundBuffer.isEmpty());
            closeChannel(sc);
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    private static void writeAndFlushReadOnSuccess(final ChannelHandlerContext ctx, Object msg) {
        ctx.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.read();
                }
            }
        });
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadDisabledSharedGroup() throws Exception {
        testAutoReadDisabled(sharedGroup, sharedGroup);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testAutoReadDisabledDifferentGroup() throws Exception {
        testAutoReadDisabled(group1, group2);
    }

    private static void testAutoReadDisabled(EventLoopGroup serverGroup, EventLoopGroup clientGroup) throws Exception {
        final CountDownLatch latch = new CountDownLatch(100);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(serverGroup)
                .channel(LocalChannel.class)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                        writeAndFlushReadOnSuccess(ctx, "test");
                    }

                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                        writeAndFlushReadOnSuccess(ctx, msg);
                    }
                });

        sb.group(clientGroup)
                .channel(LocalServerChannel.class)
                .childOption(ChannelOption.AUTO_READ, false)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                        ctx.read();
                    }

                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                        latch.countDown();
                        if (latch.getCount() > 0) {
                            writeAndFlushReadOnSuccess(ctx, msg);
                        }
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();
            cc = cb.connect(TEST_ADDRESS).sync().channel();

            latch.await();
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testMaxMessagesPerReadRespectedWithAutoReadSharedGroup() throws Exception {
        testMaxMessagesPerReadRespected(sharedGroup, sharedGroup, true);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testMaxMessagesPerReadRespectedWithoutAutoReadSharedGroup() throws Exception {
        testMaxMessagesPerReadRespected(sharedGroup, sharedGroup, false);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testMaxMessagesPerReadRespectedWithAutoReadDifferentGroup() throws Exception {
        testMaxMessagesPerReadRespected(group1, group2, true);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testMaxMessagesPerReadRespectedWithoutAutoReadDifferentGroup() throws Exception {
        testMaxMessagesPerReadRespected(group1, group2, false);
    }

    private static void testMaxMessagesPerReadRespected(
            EventLoopGroup serverGroup, EventLoopGroup clientGroup, final boolean autoRead) throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(5);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(serverGroup)
                .channel(LocalChannel.class)
                .option(ChannelOption.AUTO_READ, autoRead)
                .option(ChannelOption.MAX_MESSAGES_PER_READ, 1)
                .handler(new ChannelReadHandler(countDownLatch, autoRead));
        sb.group(clientGroup)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) {
                        for (int i = 0; i < 10; i++) {
                            ctx.write(i);
                        }
                        ctx.flush();
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();
            cc = cb.connect(TEST_ADDRESS).sync().channel();

            countDownLatch.await();
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testServerMaxMessagesPerReadRespectedWithAutoReadSharedGroup() throws Exception {
        testServerMaxMessagesPerReadRespected(sharedGroup, sharedGroup, true);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testServerMaxMessagesPerReadRespectedWithoutAutoReadSharedGroup() throws Exception {
        testServerMaxMessagesPerReadRespected(sharedGroup, sharedGroup, false);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testServerMaxMessagesPerReadRespectedWithAutoReadDifferentGroup() throws Exception {
        testServerMaxMessagesPerReadRespected(group1, group2, true);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testServerMaxMessagesPerReadRespectedWithoutAutoReadDifferentGroup() throws Exception {
        testServerMaxMessagesPerReadRespected(group1, group2, false);
    }

    private void testServerMaxMessagesPerReadRespected(
            EventLoopGroup serverGroup, EventLoopGroup clientGroup, final boolean autoRead) throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(5);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(serverGroup)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // NOOP
                    }
                });

        sb.group(clientGroup)
                .channel(LocalServerChannel.class)
                .option(ChannelOption.AUTO_READ, autoRead)
                .option(ChannelOption.MAX_MESSAGES_PER_READ, 1)
                .handler(new ChannelReadHandler(countDownLatch, autoRead))
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // NOOP
                    }
                });

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();
            for (int i = 0; i < 5; i++) {
                try {
                    cc = cb.connect(TEST_ADDRESS).sync().channel();
                } finally {
                    closeChannel(cc);
                }
            }

            countDownLatch.await();
        } finally {
            closeChannel(sc);
        }
    }

    private static final class ChannelReadHandler extends ChannelInboundHandlerAdapter {

        private final CountDownLatch latch;
        private final boolean autoRead;
        private int read;

        ChannelReadHandler(CountDownLatch latch, boolean autoRead) {
            this.latch = latch;
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (!autoRead) {
                ctx.read();
            }
            ctx.fireChannelActive();
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
            assertEquals(0, read);
            read++;
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext ctx) {
            assertEquals(1, read);
            latch.countDown();
            if (latch.getCount() > 0) {
                if (!autoRead) {
                    // The read will be scheduled 100ms in the future to ensure we not receive any
                    // channelRead calls in the meantime.
                    ctx.executor().schedule(new Runnable() {
                        @Override
                        public void run() {
                            read = 0;
                            ctx.read();
                        }
                    }, 100, TimeUnit.MILLISECONDS);
                } else {
                    read = 0;
                }
            } else {
                read = 0;
            }
            ctx.fireChannelReadComplete();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
            ctx.close();
        }
    }

    @Test
    public void testReadCompleteCalledOnHandle() throws Exception {
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(sharedGroup)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // NOOP
                    }
                });

        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch childLatch = new CountDownLatch(1);

        sb.group(sharedGroup)
                .channel(LocalServerChannel.class)
                .option(ChannelOption.RECVBUF_ALLOCATOR, new ReadCompleteRecvAllocator(serverLatch))
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // NOOP
                    }
                })
                .childOption(ChannelOption.RECVBUF_ALLOCATOR, new ReadCompleteRecvAllocator(childLatch));

        Channel sc = null;
        Channel cc = null;
        try {
            // Start server
            sc = sb.bind(TEST_ADDRESS).sync().channel();
            try {
                cc = cb.connect(TEST_ADDRESS).sync().channel();
                cc.writeAndFlush("msg").sync();
            } finally {
                closeChannel(cc);
            }

            serverLatch.await();
            childLatch.await();
        } finally {
            closeChannel(sc);
        }
    }

    private static final class ReadCompleteRecvAllocator extends DefaultMaxMessagesRecvByteBufAllocator {
        private final CountDownLatch latch;
        ReadCompleteRecvAllocator(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public Handle newHandle() {
            return new MaxMessageHandle() {
                @Override
                public int guess() {
                    return 128;
                }

                @Override
                public void readComplete() {
                    super.readComplete();
                    latch.countDown();
                }
            };
        }
    }
}
