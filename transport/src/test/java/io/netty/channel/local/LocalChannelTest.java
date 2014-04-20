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
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class LocalChannelTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LocalChannelTest.class);

    private static final String LOCAL_ADDR_ID = "test.id";

    @Test
    public void testLocalAddressReuse() throws Exception {
        for (int i = 0; i < 2; i ++) {
            EventLoopGroup clientGroup = new LocalEventLoopGroup();
            EventLoopGroup serverGroup = new LocalEventLoopGroup();
            LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(clientGroup)
              .channel(LocalChannel.class)
              .handler(new TestHandler());

            sb.group(serverGroup)
              .channel(LocalServerChannel.class)
              .childHandler(new ChannelInitializer<LocalChannel>() {
                  @Override
                  public void initChannel(LocalChannel ch) throws Exception {
                      ch.pipeline().addLast(new TestHandler());
                  }
              });

            // Start server
            Channel sc = sb.bind(addr).sync().channel();

            final CountDownLatch latch = new CountDownLatch(1);
            // Connect to the server
            final Channel cc = cb.connect(addr).sync().channel();
            cc.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    // Send a message event up the pipeline.
                    cc.pipeline().fireChannelRead("Hello, World");
                    latch.countDown();
                }
            });
            latch.await();

            // Close the channel
            cc.close().sync();

            serverGroup.shutdownGracefully();
            clientGroup.shutdownGracefully();

            sc.closeFuture().sync();

            assertNull(String.format(
                    "Expected null, got channel '%s' for local address '%s'",
                    LocalChannelRegistry.get(addr), addr), LocalChannelRegistry.get(addr));

            serverGroup.terminationFuture().sync();
            clientGroup.terminationFuture().sync();
        }
    }

    @Test
    public void testWriteFailsFastOnClosedChannel() throws Exception {
        EventLoopGroup clientGroup = new LocalEventLoopGroup();
        EventLoopGroup serverGroup = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
        Bootstrap cb = new Bootstrap();
        ServerBootstrap sb = new ServerBootstrap();

        cb.group(clientGroup)
                .channel(LocalChannel.class)
                .handler(new TestHandler());

        sb.group(serverGroup)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new TestHandler());
                    }
                });

        // Start server
        sb.bind(addr).sync();

        // Connect to the server
        final Channel cc = cb.connect(addr).sync().channel();

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
                        e.getStackTrace()[0].getClassName(), is(AbstractChannel.class.getName() + "$AbstractUnsafe"));
                e.printStackTrace();
            }
        }

        serverGroup.shutdownGracefully();
        clientGroup.shutdownGracefully();
        serverGroup.terminationFuture().sync();
        clientGroup.terminationFuture().sync();
    }

    @Test
    public void testServerCloseChannelSameEventLoop() throws Exception {
        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
        LocalEventLoopGroup group = new LocalEventLoopGroup(1);
        final CountDownLatch latch = new CountDownLatch(1);
        ServerBootstrap sb = new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ctx.close();
                        latch.countDown();
                    }
                });
        sb.bind(addr).sync();

        Bootstrap b = new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                        // discard
                    }
                });
        Channel channel = b.connect(addr).sync().channel();
        channel.writeAndFlush(new Object());
        latch.await();
        group.shutdownGracefully();
        group.terminationFuture().sync();
    }

    @Test
    public void localChannelRaceCondition() throws Exception {
        final LocalAddress address = new LocalAddress("test");
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final EventLoopGroup serverGroup = new LocalEventLoopGroup(1);
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
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(serverGroup).
                    channel(LocalServerChannel.class).
                    childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.close();
                            closeLatch.countDown();
                        }
                    }).
                    bind(address).
                    sync();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(clientGroup).
                    channel(LocalChannel.class).
                    handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            /* Do nothing */
                        }
                    });
            ChannelFuture future = bootstrap.connect(address);
            assertTrue("Connection should finish, not time out", future.await(200));
        } finally {
            serverGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).await();
            clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).await();
        }
    }

    @Test
    public void testReRegister() {
        EventLoopGroup group1 = new LocalEventLoopGroup();
        EventLoopGroup group2 = new LocalEventLoopGroup();
        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
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

        // Start server
        final Channel sc = sb.bind(addr).syncUninterruptibly().channel();

        // Connect to the server
        final Channel cc = cb.connect(addr).syncUninterruptibly().channel();

        cc.deregister().syncUninterruptibly();
        // Change event loop group.
        group2.register(cc).syncUninterruptibly();
        cc.close().syncUninterruptibly();
        sc.close().syncUninterruptibly();
    }
    static class TestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            logger.info(String.format("Received mesage: %s", msg));
        }
    }
}
