/*
 * Copyright 2013 The Netty Project
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

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.ServerChannelFactory;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.AfterClass;
import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class BootstrapTest {

    private static final EventLoopGroup groupA = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
    private static final EventLoopGroup groupB = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
    private static final ChannelInboundHandler dummyHandler = new DummyHandler();

    @AfterClass
    public static void destroy() {
        groupA.shutdownGracefully();
        groupB.shutdownGracefully();
        groupA.terminationFuture().syncUninterruptibly();
        groupB.terminationFuture().syncUninterruptibly();
    }

    @Test(timeout = 10000)
    public void testBindDeadLock() throws Exception {
        final Bootstrap bootstrapA = new Bootstrap();
        bootstrapA.group(groupA);
        bootstrapA.channel(LocalChannel.class);
        bootstrapA.handler(dummyHandler);

        final Bootstrap bootstrapB = new Bootstrap();
        bootstrapB.group(groupB);
        bootstrapB.channel(LocalChannel.class);
        bootstrapB.handler(dummyHandler);

        List<Future<?>> bindFutures = new ArrayList<>();

        // Try to bind from each other.
        for (int i = 0; i < 1024; i ++) {
            bindFutures.add(groupA.next().submit(new Runnable() {
                @Override
                public void run() {
                    bootstrapB.bind(LocalAddress.ANY);
                }
            }));

            bindFutures.add(groupB.next().submit(new Runnable() {
                @Override
                public void run() {
                    bootstrapA.bind(LocalAddress.ANY);
                }
            }));
        }

        for (Future<?> f: bindFutures) {
            f.sync();
        }
    }

    @Test(timeout = 10000)
    public void testConnectDeadLock() throws Exception {
        final Bootstrap bootstrapA = new Bootstrap();
        bootstrapA.group(groupA);
        bootstrapA.channel(LocalChannel.class);
        bootstrapA.handler(dummyHandler);

        final Bootstrap bootstrapB = new Bootstrap();
        bootstrapB.group(groupB);
        bootstrapB.channel(LocalChannel.class);
        bootstrapB.handler(dummyHandler);

        List<Future<?>> bindFutures = new ArrayList<>();

        // Try to connect from each other.
        for (int i = 0; i < 1024; i ++) {
            bindFutures.add(groupA.next().submit(new Runnable() {
                @Override
                public void run() {
                    bootstrapB.connect(LocalAddress.ANY);
                }
            }));

            bindFutures.add(groupB.next().submit(new Runnable() {
                @Override
                public void run() {
                    bootstrapA.connect(LocalAddress.ANY);
                }
            }));
        }

        for (Future<?> f: bindFutures) {
            f.sync();
        }
    }

    @Test
    public void testLateRegisterSuccess() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        try {
            LateRegisterHandler registerHandler = new LateRegisterHandler();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.handler(registerHandler);
            bootstrap.channel(LocalServerChannel.class);
            bootstrap.childHandler(new DummyHandler());
            bootstrap.localAddress(new LocalAddress("1"));
            ChannelFuture future = bootstrap.bind();
            assertFalse(future.isDone());
            registerHandler.registerPromise().setSuccess();
            final BlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    queue.add(future.channel().eventLoop().inEventLoop(Thread.currentThread()));
                    queue.add(future.isSuccess());
                }
            });
            assertTrue(queue.take());
            assertTrue(queue.take());
        } finally {
            group.shutdownGracefully();
            group.terminationFuture().sync();
        }
    }

    @Test
    public void testLateRegisterSuccessBindFailed() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        LateRegisterHandler registerHandler = new LateRegisterHandler();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.channelFactory(new ServerChannelFactory<ServerChannel>() {
                @Override
                public ServerChannel newChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
                    return new LocalServerChannel(eventLoop, childEventLoopGroup) {
                        @Override
                        public ChannelFuture bind(SocketAddress localAddress) {
                            // Close the Channel to emulate what NIO and others impl do on bind failure
                            // See https://github.com/netty/netty/issues/2586
                            close();
                            return newFailedFuture(new SocketException());
                        }

                        @Override
                        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                            // Close the Channel to emulate what NIO and others impl do on bind failure
                            // See https://github.com/netty/netty/issues/2586
                            close();
                            return promise.setFailure(new SocketException());
                        }
                    };
                }
            });
            bootstrap.childHandler(new DummyHandler());
            bootstrap.handler(registerHandler);
            bootstrap.localAddress(new LocalAddress("1"));
            ChannelFuture future = bootstrap.bind();
            assertFalse(future.isDone());
            registerHandler.registerPromise().setSuccess();
            final BlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    queue.add(future.channel().eventLoop().inEventLoop(Thread.currentThread()));
                    queue.add(future.isSuccess());
                }
            });
            assertTrue(queue.take());
            assertFalse(queue.take());
        } finally {
            group.shutdownGracefully();
            group.terminationFuture().sync();
        }
    }

    @Test(expected = ConnectException.class, timeout = 10000)
    public void testLateRegistrationConnect() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        LateRegisterHandler registerHandler = new LateRegisterHandler();
        try {
            final Bootstrap bootstrapA = new Bootstrap();
            bootstrapA.group(group);
            bootstrapA.channel(LocalChannel.class);
            bootstrapA.handler(registerHandler);
            ChannelFuture future = bootstrapA.connect(LocalAddress.ANY);
            assertFalse(future.isDone());
            registerHandler.registerPromise().setSuccess();
            future.syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testAsyncResolutionSuccess() throws Exception {
        final Bootstrap bootstrapA = new Bootstrap();
        bootstrapA.group(groupA);
        bootstrapA.channel(LocalChannel.class);
        bootstrapA.resolver(new TestAddressResolverGroup(true));
        bootstrapA.handler(dummyHandler);

        final ServerBootstrap bootstrapB = new ServerBootstrap();
        bootstrapB.group(groupB);
        bootstrapB.channel(LocalServerChannel.class);
        bootstrapB.childHandler(dummyHandler);
        SocketAddress localAddress = bootstrapB.bind(LocalAddress.ANY).sync().channel().localAddress();

        // Connect to the server using the asynchronous resolver.
        bootstrapA.connect(localAddress).sync();
    }

    @Test
    public void testAsyncResolutionFailure() throws Exception {
        final Bootstrap bootstrapA = new Bootstrap();
        bootstrapA.group(groupA);
        bootstrapA.channel(LocalChannel.class);
        bootstrapA.resolver(new TestAddressResolverGroup(false));
        bootstrapA.handler(dummyHandler);

        final ServerBootstrap bootstrapB = new ServerBootstrap();
        bootstrapB.group(groupB);
        bootstrapB.channel(LocalServerChannel.class);
        bootstrapB.childHandler(dummyHandler);
        SocketAddress localAddress = bootstrapB.bind(LocalAddress.ANY).sync().channel().localAddress();

        // Connect to the server using the asynchronous resolver.
        ChannelFuture connectFuture = bootstrapA.connect(localAddress);

        // Should fail with the UnknownHostException.
        assertThat(connectFuture.await(10000), is(true));
        assertThat(connectFuture.cause(), is(instanceOf(UnknownHostException.class)));
        assertThat(connectFuture.channel().isOpen(), is(false));
    }

    @Test
    public void testChannelFactoryFailureNotifiesPromise() throws Exception {
        final RuntimeException exception = new RuntimeException("newChannel crash");

        final Bootstrap bootstrap = new Bootstrap()
                .handler(dummyHandler)
                .group(groupA)
                .channelFactory(new ChannelFactory<Channel>() {
            @Override
            public Channel newChannel(EventLoop eventLoop) {
                throw exception;
            }
        });

        ChannelFuture connectFuture = bootstrap.connect(LocalAddress.ANY);

        // Should fail with the RuntimeException.
        assertThat(connectFuture.await(10000), is(true));
        assertThat(connectFuture.cause(), sameInstance((Throwable) exception));
        assertThat(connectFuture.channel(), is(not(nullValue())));
    }

    private static final class LateRegisterHandler extends ChannelOutboundHandlerAdapter {

        private final CountDownLatch latch = new CountDownLatch(1);
        private ChannelPromise registerPromise;

        @Override
        public void register(ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
            registerPromise = promise;
            latch.countDown();
            ChannelPromise newPromise = ctx.newPromise();
            newPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        registerPromise.tryFailure(future.cause());
                    }
                }
            });
            super.register(ctx, newPromise);
        }

        ChannelPromise registerPromise() throws InterruptedException {
            latch.await();
            return registerPromise;
        }
    }

    @Sharable
    private static final class DummyHandler extends ChannelInboundHandlerAdapter { }

    private static final class TestAddressResolverGroup extends AddressResolverGroup<SocketAddress> {

        private final boolean success;

        TestAddressResolverGroup(boolean success) {
            this.success = success;
        }

        @Override
        protected AddressResolver<SocketAddress> newResolver(EventExecutor executor) throws Exception {
            return new AbstractAddressResolver<SocketAddress>(executor) {

                @Override
                protected boolean doIsResolved(SocketAddress address) {
                    return false;
                }

                @Override
                protected void doResolve(
                        final SocketAddress unresolvedAddress, final Promise<SocketAddress> promise) {
                    executor().execute(new Runnable() {
                        @Override
                        public void run() {
                            if (success) {
                                promise.setSuccess(unresolvedAddress);
                            } else {
                                promise.setFailure(new UnknownHostException(unresolvedAddress.toString()));
                            }
                        }
                    });
                }

                @Override
                protected void doResolveAll(
                        final SocketAddress unresolvedAddress, final Promise<List<SocketAddress>> promise)
                        throws Exception {
                    executor().execute(new Runnable() {
                        @Override
                        public void run() {
                            if (success) {
                                promise.setSuccess(Collections.singletonList(unresolvedAddress));
                            } else {
                                promise.setFailure(new UnknownHostException(unresolvedAddress.toString()));
                            }
                        }
                    });
                }
            };
        }
    }
}
