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

import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultChannelPipelineTailTest {
    private static final long TIMEOUT_SEC = 10L;

    @AutoClose("shutdownGracefully")
    private static EventLoopGroup group;

    @BeforeAll
    public static void init() {
        group = new MultithreadEventLoopGroup(1, LocalIoHandler.newFactory());
    }

    @Test
    public void testOnUnhandledInboundChannelActive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundChannelActive() {
                latch.countDown();
            }
        };

        myChannel.pipeline().fireChannelActive();

        try {
            assertTrue(latch.await(10L, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundChannelInactive() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundChannelInactive() {
                latch.countDown();
            }
        };

        myChannel.pipeline().fireChannelInactive();
        myChannel.close().asStage().sync();

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void testOnUnhandledInboundException() throws Exception {
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
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
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
            assertSame(ex, causeRef.get());
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundMessage() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundMessage(Object msg) {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelRead("testOnUnhandledInboundMessage");
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundReadComplete() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledInboundChannelReadComplete() {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelReadComplete();
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundUserEventTriggered() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledChannelInboundEvent(Object evt) {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelInboundEvent("testOnUnhandledInboundUserEventTriggered");
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    @Test
    public void testOnUnhandledInboundWritabilityChanged() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        EventLoop loop = group.next();
        MyChannel myChannel = new MyChannel(loop) {
            @Override
            protected void onUnhandledChannelWritabilityChanged() {
                latch.countDown();
            }
        };

        try {
            myChannel.pipeline().fireChannelWritabilityChanged();
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        } finally {
            myChannel.close();
        }
    }

    private abstract static class MyChannel extends LocalChannel {
        protected MyChannel(EventLoop eventLoop) {
            super(eventLoop);
        }

        @Override
        protected DefaultChannelPipeline newChannelPipeline() {
            return new MyChannelPipeline(this);
        }

        protected void onUnhandledInboundChannelActive() {
        }

        protected void onUnhandledInboundChannelInactive() {
        }

        protected void onUnhandledInboundException(Throwable cause) {
        }

        protected void onUnhandledInboundMessage(Object msg) {
        }

        protected void onUnhandledInboundChannelReadComplete() {
        }

        protected void onUnhandledChannelInboundEvent(Object evt) {
        }

        protected void onUnhandledChannelWritabilityChanged() {
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
                MyChannel.this.onUnhandledInboundChannelReadComplete();
            }

            @Override
            protected void onUnhandledChannelInboundEvent(Object evt) {
                MyChannel.this.onUnhandledChannelInboundEvent(evt);
            }

            @Override
            protected void onUnhandledChannelWritabilityChanged() {
                MyChannel.this.onUnhandledChannelWritabilityChanged();
            }
        }
    }
}
