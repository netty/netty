/*
 * Copyright 2017 The Netty Project
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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.local.LocalHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DefaultChannelPipelineTailTest {

    private static EventLoopGroup GROUP;

    @BeforeClass
    public static void init() {
        GROUP = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
    }

    @AfterClass
    public static void destroy() {
        GROUP.shutdownGracefully();
    }

    @Test
    public void testOnUnhandledInboundChannelActive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundChannelActive() {
                latch.countDown();
            }
        };

        myChannel.pipeline().fireChannelActive();

        try {
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundChannelInactive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundChannelInactive() {
                latch.countDown();
            }
        };

        myChannel.pipeline().fireChannelInactive();
        myChannel.close().syncUninterruptibly();

        assertTrue(latch.await(1L, TimeUnit.SECONDS));
    }

    @Test
    public void testOnUnhandledInboundException() throws Exception {
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundException(Throwable cause) {
                causeRef.set(cause);
                latch.countDown();
            }
        };

        try {
            IOException ex = new IOException("testOnUnhandledInboundException");
            myChannel.pipeline().fireExceptionCaught(ex);
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
            assertSame(ex, causeRef.get());
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundMessage() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundMessage(Object msg) {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelRead("testOnUnhandledInboundMessage");
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundReadComplete() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundReadComplete() {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelReadComplete();
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundUserEventTriggered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundUserEventTriggered(Object evt) {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireUserEventTriggered("testOnUnhandledInboundUserEventTriggered");
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundWritabilityChanged() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = GROUP.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundWritabilityChanged() {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelWritabilityChanged();
            assertTrue(latch.await(1L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    private abstract static class MyChannel extends AbstractChannel {
        private static final ChannelMetadata METADATA = new ChannelMetadata(false);

        private final ChannelConfig config = new DefaultChannelConfig(this);

        private boolean active;
        private boolean closed;

        protected MyChannel(EventLoop eventLoop) {
            super(null, eventLoop);
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
        public ChannelMetadata metadata() {
            return METADATA;
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new MyUnsafe();
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
        protected void doBind(SocketAddress localAddress) throws Exception {
        }

        @Override
        protected void doDisconnect() throws Exception {
        }

        @Override
        protected void doClose() throws Exception {
            closed = true;
        }

        @Override
        protected void doBeginRead() throws Exception {
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

        private class MyUnsafe extends AbstractUnsafe {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                if (!ensureOpen(promise)) {
                    return;
                }

                if (!active) {
                    active = true;
                    pipeline().fireChannelActive();
                    readIfIsAutoRead();
                }

                promise.setSuccess();
            }
        }

        private class MyChannelPipeline extends DefaultChannelPipeline {

            public MyChannelPipeline(Channel channel) {
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
