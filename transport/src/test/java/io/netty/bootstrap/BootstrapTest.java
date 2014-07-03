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

import io.netty.channel.ChannelHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Test;

import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BootstrapTest {

    @Test(timeout = 10000)
    public void testBindDeadLock() throws Exception {
        EventLoopGroup groupA = new DefaultEventLoopGroup(1);
        EventLoopGroup groupB = new DefaultEventLoopGroup(1);

        try {
            ChannelHandler dummyHandler = new DummyHandler();

            final Bootstrap bootstrapA = new Bootstrap();
            bootstrapA.group(groupA);
            bootstrapA.channel(LocalChannel.class);
            bootstrapA.handler(dummyHandler);

            final Bootstrap bootstrapB = new Bootstrap();
            bootstrapB.group(groupB);
            bootstrapB.channel(LocalChannel.class);
            bootstrapB.handler(dummyHandler);

            List<Future<?>> bindFutures = new ArrayList<Future<?>>();

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
        } finally {
            groupA.shutdownGracefully();
            groupB.shutdownGracefully();
            groupA.terminationFuture().sync();
            groupB.terminationFuture().sync();
        }
    }

    @Test(timeout = 10000)
    public void testConnectDeadLock() throws Exception {
        EventLoopGroup groupA = new DefaultEventLoopGroup(1);
        EventLoopGroup groupB = new DefaultEventLoopGroup(1);

        try {
            ChannelHandler dummyHandler = new DummyHandler();

            final Bootstrap bootstrapA = new Bootstrap();
            bootstrapA.group(groupA);
            bootstrapA.channel(LocalChannel.class);
            bootstrapA.handler(dummyHandler);

            final Bootstrap bootstrapB = new Bootstrap();
            bootstrapB.group(groupB);
            bootstrapB.channel(LocalChannel.class);
            bootstrapB.handler(dummyHandler);

            List<Future<?>> bindFutures = new ArrayList<Future<?>>();

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
        } finally {
            groupA.shutdownGracefully();
            groupB.shutdownGracefully();
            groupA.terminationFuture().sync();
            groupB.terminationFuture().sync();
        }
    }

    @Test
    public void testLateRegisterSuccess() throws Exception {
        TestEventLoopGroup group = new TestEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.channel(LocalServerChannel.class);
            bootstrap.childHandler(new DummyHandler());
            bootstrap.localAddress(new LocalAddress("1"));
            ChannelFuture future = bootstrap.bind();
            Assert.assertFalse(future.isDone());
            group.promise.setSuccess();
            final BlockingQueue<Boolean> queue = new LinkedBlockingQueue<Boolean>();
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    queue.add(future.channel().eventLoop().inEventLoop(Thread.currentThread()));
                    queue.add(future.isSuccess());
                }
            });
            Assert.assertTrue(queue.take());
            Assert.assertTrue(queue.take());
        } finally {
            group.shutdownGracefully();
            group.terminationFuture().sync();
        }
    }

    @Test
    public void testLateRegisterSuccessBindFailed() throws Exception {
        TestEventLoopGroup group = new TestEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.channelFactory(new ChannelFactory<ServerChannel>() {
                @Override
                public ServerChannel newChannel() {
                    return new LocalServerChannel() {
                        @Override
                        public ChannelFuture bind(SocketAddress localAddress) {
                            return newFailedFuture(new SocketException());
                        }

                        @Override
                        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                            return promise.setFailure(new SocketException());
                        }
                    };
                }
            });
            bootstrap.childHandler(new DummyHandler());
            bootstrap.localAddress(new LocalAddress("1"));
            ChannelFuture future = bootstrap.bind();
            Assert.assertFalse(future.isDone());
            group.promise.setSuccess();
            final BlockingQueue<Boolean> queue = new LinkedBlockingQueue<Boolean>();
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    queue.add(future.channel().eventLoop().inEventLoop(Thread.currentThread()));
                    queue.add(future.isSuccess());
                }
            });
            Assert.assertTrue(queue.take());
            Assert.assertFalse(queue.take());
        } finally {
            group.shutdownGracefully();
            group.terminationFuture().sync();
        }
    }

    private static final class TestEventLoopGroup extends DefaultEventLoopGroup {
        ChannelPromise promise;
        TestEventLoopGroup() {
            super(1);
        }

        @Override
        public ChannelFuture register(Channel channel) {
            super.register(channel).syncUninterruptibly();
            promise = channel.newPromise();
            return promise;
        }

        @Override
        public ChannelFuture register(Channel channel, final ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }
    }

    @Sharable
    private static final class DummyHandler extends ChannelHandlerAdapter { }
}
