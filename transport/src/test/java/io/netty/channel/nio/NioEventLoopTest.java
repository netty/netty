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
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    @Timeout(10)
    public void testShutdownFromTaskClosesRegisteredChannel() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        SocketChannel socket = SocketChannel.open();
        Channel channel = new NioSocketChannel(socket);
        try {
            group.register(channel).sync();
            channel.eventLoop().submit(() -> group.shutdownGracefully(0, 5, TimeUnit.SECONDS)).sync();
            group.terminationFuture().sync();

            assertFalse(channel.isOpen());
            assertFalse(socket.isOpen());
            assertTrue(channel.closeFuture().isDone());
            assertFalse(channel.isRegistered());
        } finally {
            socket.close();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    @Timeout(10)
    public void testShutdownDuringRunningTaskClosesRegisteredChannel() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        SocketChannel socket = SocketChannel.open();
        Channel channel = new NioSocketChannel(socket);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            group.register(channel).sync();
            Future<?> heldTask = channel.eventLoop().submit(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> termination = group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            release.countDown();
            heldTask.sync();
            termination.sync();

            assertFalse(channel.isOpen());
            assertFalse(socket.isOpen());
            assertTrue(channel.closeFuture().isDone());
            assertFalse(channel.isRegistered());
        } finally {
            release.countDown();
            socket.close();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    @Timeout(10)
    public void testShutdownCloseListenerRegistersAnotherChannel() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        SocketChannel firstSocket = SocketChannel.open();
        SocketChannel secondSocket = SocketChannel.open();
        Channel first = new NioSocketChannel(firstSocket);
        Channel second = new NioSocketChannel(secondSocket);
        AtomicBoolean registeredDuringClose = new AtomicBoolean();
        try {
            group.register(first).sync();
            first.closeFuture().addListener(future -> {
                registeredDuringClose.set(group.register(second).isSuccess());
            });
            first.eventLoop().submit(() -> group.shutdownGracefully(0, 5, TimeUnit.SECONDS)).sync();
            group.terminationFuture().sync();

            assertTrue(registeredDuringClose.get());
            assertFalse(first.isOpen());
            assertFalse(second.isOpen());
            assertFalse(firstSocket.isOpen());
            assertFalse(secondSocket.isOpen());
            assertTrue(first.closeFuture().isDone());
            assertTrue(second.closeFuture().isDone());
            assertFalse(first.isRegistered());
            assertFalse(second.isRegistered());
        } finally {
            firstSocket.close();
            secondSocket.close();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
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

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testSelectableChannel() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        IoEventLoop loop = group.next();

        try {
            Channel channel = new NioServerSocketChannel();
            loop.register(channel).syncUninterruptibly();
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
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean loopStarted = new AtomicBoolean();
        final Runnable task = () -> {
            // NOOP
        };
        // Just run often enough to trigger it normally.
        for (int i = 0; i < 250; i++) {
            EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            final EventLoop loop = group.next();

            Thread t = new Thread(() -> {
                try {
                    for (;;) {
                        loop.execute(task);
                        loopStarted.set(true);
                    }
                } catch (Throwable cause) {
                    error.set(cause);
                }
            });
            t.start();
            do {
                Thread.yield();
            } while (!loopStarted.get());
            group.shutdownNow();
            t.join();
            group.terminationFuture().syncUninterruptibly();
            assertInstanceOf(RejectedExecutionException.class, error.get());
            error.set(null);
            loopStarted.set(false);
        }
    }

    @Test
    public void testRebuildSelectorOnIOException() {
        SelectStrategyFactory selectStrategyFactory = new SelectStrategyFactory() {
            @Override
            public SelectStrategy newSelectStrategy() {
                return new SelectStrategy() {

                    private boolean thrown;

                    @Override
                    public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception {
                        if (!thrown) {
                            thrown = true;
                            throw new IOException();
                        }
                        return -1;
                    }
                };
            }
        };

        EventLoopGroup group = new NioEventLoopGroup(1, new DefaultThreadFactory("ioPool"),
                                                     SelectorProvider.provider(), selectStrategyFactory);
        final NioEventLoop loop = (NioEventLoop) group.next();
        try {
            Channel channel = new NioServerSocketChannel();
            Selector selector = loop.unwrappedSelector();

            loop.register(channel).syncUninterruptibly();

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
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsRegistered() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            final SingleThreadIoEventLoop loop = (SingleThreadIoEventLoop) group.next();
            final Channel ch1 = new NioServerSocketChannel();
            final Channel ch2 = new NioServerSocketChannel();

            assertEquals(0, registeredChannels(loop));

            assertTrue(loop.register(ch1).syncUninterruptibly().isSuccess());
            assertTrue(loop.register(ch2).syncUninterruptibly().isSuccess());
            assertEquals(2, registeredChannels(loop));

            assertTrue(ch1.deregister().syncUninterruptibly().isSuccess());

            int registered;
            // As SelectionKeys are removed in a lazy fashion in the JDK implementation we may need to query a few
            // times before we see the right number of registered chanels.
            while ((registered = registeredChannels(loop)) == 2) {
                Thread.sleep(50);
            }
            assertEquals(1, registered);
        } finally {
            group.shutdownGracefully();
        }
    }

    // Only reliable if run from event loop
    private static int registeredChannels(final SingleThreadEventLoop loop) throws Exception {
        return loop.submit(new Callable<Integer>() {
            @Override
            public Integer call() {
                return loop.registeredChannels();
            }
        }).get(1, TimeUnit.SECONDS);
    }
}
