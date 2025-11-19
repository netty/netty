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
package io.netty.channel.nio;

import io.netty.channel.AbstractEventLoopTest;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioEventLoopTest extends AbstractEventLoopTest {

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    }

    @Override
    protected Class<? extends ServerSocketChannel> newChannel() {
        return NioServerSocketChannel.class;
    }

    @Override
    protected Class<? extends io.netty.channel.socket.SocketChannel> newSocketChannel() {
        return NioSocketChannel.class;
    }

    @Test
    public void testScheduleBigDelayNotOverflow() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
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
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        try {
            loop.submit(new Runnable() {
                @Override
                public void run() {
                    // Interrupt the thread which should not end-up in a busy spin and
                    // so the selector should not have been rebuild.
                    Thread.currentThread().interrupt();
                }
            }).syncUninterruptibly();

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
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testSelectableChannel() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoop loop = group.next();

        try {
            Channel channel = new NioServerSocketChannel(loop, group);
            channel.register().syncUninterruptibly();
            channel.bind(new InetSocketAddress(0)).syncUninterruptibly();

            SocketChannel selectableChannel = SocketChannel.open();
            selectableChannel.configureBlocking(false);
            selectableChannel.connect(channel.localAddress());

            final CountDownLatch latch = new CountDownLatch(1);

            IoRegistration registration = loop.register(
                            new NioSelectableChannelIoHandle<SocketChannel>(selectableChannel) {
                @Override
                protected void handle(SocketChannel channel, SelectionKey key) {
                    latch.countDown();
                }
            }).get();

            registration.submit(NioIoOps.valueOf(SelectionKey.OP_CONNECT));

            latch.await();

            selectableChannel.close();
            channel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testTaskRemovalOnShutdownThrowsNoUnsupportedOperationException() throws Exception {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        };
        // Just run often enough to trigger it normally.
        for (int i = 0; i < 1000; i++) {
            EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            final EventLoop loop = group.next();

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (;;) {
                            loop.execute(task);
                        }
                    } catch (Throwable cause) {
                        error.set(cause);
                    }
                }
            });
            t.start();
            group.shutdownNow();
            t.join();
            group.terminationFuture().syncUninterruptibly();
            assertInstanceOf(RejectedExecutionException.class, error.get());
            error.set(null);
        }
    }
}
