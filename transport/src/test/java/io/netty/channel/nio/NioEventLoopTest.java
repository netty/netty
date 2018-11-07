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
package io.netty.channel.nio;

import io.netty.channel.AbstractEventLoopTest;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NioEventLoopTest extends AbstractEventLoopTest {

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new NioEventLoopGroup();
    }

    @Override
    protected Class<? extends ServerSocketChannel> newChannel() {
        return NioServerSocketChannel.class;
    }

    @Test
    public void testRebuildSelector() {
        EventLoopGroup group = new NioEventLoopGroup(1);
        final NioEventLoop loop = (NioEventLoop) group.next();
        try {
            Channel channel = new NioServerSocketChannel();
            loop.register(channel).syncUninterruptibly();

            Selector selector = loop.unwrappedSelector();
            assertSame(selector, ((NioEventLoop) channel.eventLoop()).unwrappedSelector());
            assertTrue(selector.isOpen());

            // Submit to the EventLoop so we are sure its really executed in a non-async manner.
            loop.submit(new Runnable() {
                @Override
                public void run() {
                    loop.rebuildSelector();
                }
            }).syncUninterruptibly();

            Selector newSelector = ((NioEventLoop) channel.eventLoop()).unwrappedSelector();
            assertTrue(newSelector.isOpen());
            assertNotSame(selector, newSelector);
            assertFalse(selector.isOpen());

            channel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testScheduleBigDelayNotOverflow() {
        EventLoopGroup group = new NioEventLoopGroup(1);

        final EventLoop el = group.next();
        Future<?> future = el.schedule(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        assertFalse(future.awaitUninterruptibly(1000));
        assertTrue(future.cancel(true));
        group.shutdownGracefully();
    }

    @Test
    public void testInterruptEventLoopThread() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        final NioEventLoop loop = (NioEventLoop) group.next();
        try {
            Selector selector = loop.unwrappedSelector();
            assertTrue(selector.isOpen());

            loop.submit(new Runnable() {
                @Override
                public void run() {
                    // Interrupt the thread which should not end-up in a busy spin and
                    // so the selector should not have been rebuild.
                    Thread.currentThread().interrupt();
                }
            }).syncUninterruptibly();

            assertTrue(selector.isOpen());

            final CountDownLatch latch = new CountDownLatch(2);
            loop.submit(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }).syncUninterruptibly();

            loop.schedule(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            }, 2, TimeUnit.SECONDS).syncUninterruptibly();

            latch.await();

            assertSame(selector, loop.unwrappedSelector());
            assertTrue(selector.isOpen());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test(timeout = 3000)
    public void testSelectableChannel() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioEventLoop loop = (NioEventLoop) group.next();

        try {
            Channel channel = new NioServerSocketChannel();
            loop.register(channel).syncUninterruptibly();
            channel.bind(new InetSocketAddress(0)).syncUninterruptibly();

            SocketChannel selectableChannel = SocketChannel.open();
            selectableChannel.configureBlocking(false);
            selectableChannel.connect(channel.localAddress());

            final CountDownLatch latch = new CountDownLatch(1);

            loop.register(selectableChannel, SelectionKey.OP_CONNECT, new NioTask<SocketChannel>() {
                @Override
                public void channelReady(SocketChannel ch, SelectionKey key) {
                    latch.countDown();
                }

                @Override
                public void channelUnregistered(SocketChannel ch, Throwable cause) {
                }
            });

            latch.await();

            selectableChannel.close();
            channel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }
}
