/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.local.LocalIoHandler;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.netty.bootstrap.Bootstrap;

public class DefaultChannelPipelineTailTest {

    private static EventLoopGroup GROUP;

    @BeforeAll
    public static void init() {
        GROUP = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    }

    @AfterAll
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    @Test
    public void testOnUnhandledInboundChannelActive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundChannelActive() {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            channel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundChannelInactive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundChannelInactive() {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        channel.close().syncUninterruptibly();

        assertTrue(latch.await(1L, TimeUnit.SECONDS));
    }

    @Test
    public void testOnUnhandledInboundException() throws Exception {
        final AtomicReference<Throwable> causeRef = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundException(Throwable cause) {
                        causeRef.set(cause);
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            IOException ex = new IOException("testOnUnhandledInboundException");
            channel.pipeline().fireExceptionCaught(ex);
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
            assertSame(ex, causeRef.get());
        } finally {
            channel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundMessage() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundMessage(Object msg) {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            channel.pipeline().fireChannelRead("testOnUnhandledInboundMessage");
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            channel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundReadComplete() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundReadComplete() {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            channel.pipeline().fireChannelReadComplete();
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            channel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundUserEventTriggered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundUserEventTriggered(Object evt) {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            channel.pipeline().fireUserEventTriggered("testOnUnhandledInboundUserEventTriggered");
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            channel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundWritabilityChanged() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(e -> new MyChannel(e) {
                    @Override
                    protected void onUnhandledInboundWritabilityChanged() {
                        latch.countDown();
                    }
                })
                .group(GROUP)
                .handler(new ChannelInboundHandler() { })
                .remoteAddress(new InetSocketAddress(0));

        Channel channel = bootstrap.connect()
                .get();

        try {
            channel.pipeline().fireChannelWritabilityChanged();
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            channel.close();
        }
    }

    private abstract static class MyChannel extends AbstractChannel {

        private final ChannelConfig config = new DefaultChannelConfig(this);

        private boolean active;
        private boolean closed;

        protected MyChannel(EventLoop eventLoop) {
            super(eventLoop, null, null);
        }

        @Override
        protected void doShutdown(ChannelShutdownType type, Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected DefaultChannelPipeline newChannelPipeline() {
            return new MyChannelPipeline(this);
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public boolean isActive() {
            return isOpen() && active;
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doDeregister(Promise<Void> promise) {
            promise.setSuccess(null);
        }

        @Override
        protected void doRegister(Promise<Void> promise) {
            promise.setSuccess(null);
        }

        @Override
        protected void doBind(SocketAddress localAddress, Promise<Void> promise) {
            promise.setSuccess(null);
        }

        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
            if (!active) {
                active = true;
            }

            promise.setSuccess(null);
        }

        @Override
        protected void doDisconnect(Promise<Void> promise) {
            promise.setSuccess(null);
        }

        @Override
        protected void doClose(Promise<Void> promise) {
            closed = true;
            promise.setSuccess(null);
        }

        @Override
        protected void doBeginRead() {
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception {
            throw new IOException();
        }

        protected void onUnhandledInboundChannelActive() {
        }

        protected void onUnhandledInboundChannelInactive() {
        }

        protected void onUnhandledInboundException(Throwable cause) {
        }

        protected void onUnhandledInboundMessage(Object msg) {
        }

        protected void onUnhandledInboundReadComplete() {
        }

        protected void onUnhandledInboundUserEventTriggered(Object evt) {
        }

        protected void onUnhandledInboundWritabilityChanged() {
        }

        private class MyChannelPipeline extends DefaultAbstractChannelPipeline {

            MyChannelPipeline(AbstractChannel channel) {
                super(channel);
            }

            @Override
            protected void onUnhandledInboundChannelActive() {
                MyChannel.this.onUnhandledInboundChannelActive();
            }

            @Override
            protected void onUnhandledInboundChannelInactive() {
                MyChannel.this.onUnhandledInboundChannelInactive();
            }

            @Override
            protected void onUnhandledInboundException(Throwable cause) {
                MyChannel.this.onUnhandledInboundException(cause);
            }

            @Override
            protected void onUnhandledInboundMessage(Object msg) {
                MyChannel.this.onUnhandledInboundMessage(msg);
            }

            @Override
            protected void onUnhandledInboundChannelReadComplete() {
                MyChannel.this.onUnhandledInboundReadComplete();
            }

            @Override
            protected void onUnhandledInboundUserEventTriggered(Object evt) {
                MyChannel.this.onUnhandledInboundUserEventTriggered(evt);
            }

            @Override
            protected void onUnhandledChannelWritabilityChanged() {
                MyChannel.this.onUnhandledInboundWritabilityChanged();
            }
        }
    }
}
