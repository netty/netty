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
package io.netty5.channel;


import io.netty5.buffer.api.Buffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultChannelPipelineTailTest {

    private static EventLoopGroup GROUP;

    @BeforeAll
    public static void init() {
        GROUP = new MultithreadEventLoopGroup(1, () -> new IoHandler() {
            private final Object lock = new Object();
            @Override
            public int run(IoExecutionContext context) {
                if (context.canBlock()) {
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                    }
                }
                return 0;
            }

            @Override
            public void prepareToDestroy() {
                // NOOP
            }

            @Override
            public void destroy() {
                // NOOP
            }

            @Override
            public void register(IoHandle handle) {
                // NOOP
            }

            @Override
            public void deregister(IoHandle handle) {
                // NOOP
            }

            @Override
            public void wakeup(boolean inEventLoop) {
                if (!inEventLoop) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }

            @Override
            public boolean isCompatible(Class<? extends IoHandle> handleType) {
                return MyChannel.class.isAssignableFrom(handleType);
            }
        });
    }

    @AfterAll
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
        myChannel.close().asStage().sync();

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
            myChannel.pipeline().fireChannelExceptionCaught(ex);
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
            protected void onUnhandledChannelInboundEvent(Object evt) {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelInboundEvent("testOnUnhandledInboundUserEventTriggered");
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

    private abstract static class MyChannel extends AbstractChannel<Channel, SocketAddress, SocketAddress> {
        private boolean active;
        private boolean closed;
        private boolean inputShutdown;
        private boolean outputShutdown;

        protected MyChannel(EventLoop eventLoop) {
            super(null, eventLoop, false);
        }

        @Override
        protected DefaultChannelPipeline newChannelPipeline() {
            return new MyChannelPipeline(this);
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
        protected void doRead(boolean wasReadPendingAlready) throws Exception {
        }

        @Override
        protected boolean doReadNow(ReadSink readSink) {
            return false;
        }

        @Override
        protected void doWriteNow(WriteSink writeHandle) throws Exception {
            throw new IOException();
        }

        @Override
        protected void doShutdown(ChannelShutdownDirection direction) {
            switch (direction) {
                case Inbound:
                    inputShutdown = true;
                    break;
                case Outbound:
                    outputShutdown = true;
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public boolean isShutdown(ChannelShutdownDirection direction) {
            if (!isActive()) {
                return true;
            }
            switch (direction) {
                case Inbound:
                    return inputShutdown;
                case Outbound:
                    return outputShutdown;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initalData) {
            active = true;
            return true;
        }

        @Override
        protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
            return true;
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

        protected void onUnhandledChannelInboundEvent(Object evt) {
        }

        protected void onUnhandledInboundWritabilityChanged() {
        }

        private class MyChannelPipeline extends DefaultAbstractChannelPipeline {

            MyChannelPipeline(AbstractChannel<?, ?, ?> channel) {
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
            protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
                MyChannel.this.onUnhandledInboundMessage(msg);
            }

            @Override
            protected void onUnhandledInboundChannelReadComplete() {
                MyChannel.this.onUnhandledInboundReadComplete();
            }

            @Override
            protected void onUnhandledChannelInboundEvent(Object evt) {
                MyChannel.this.onUnhandledChannelInboundEvent(evt);
            }

            @Override
            protected void onUnhandledChannelWritabilityChanged() {
                MyChannel.this.onUnhandledInboundWritabilityChanged();
            }
        }
    }
}
