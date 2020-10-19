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
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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
            NioEventLoopGroup group = new NioEventLoopGroup(1);
            final NioEventLoop loop = (NioEventLoop) group.next();

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
            assertThat(error.get(), instanceOf(RejectedExecutionException.class));
            error.set(null);
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

    @Test(timeout = 3000L)
    public void testChannelsRegistered() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        final NioEventLoop loop = (NioEventLoop) group.next();

        try {
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

    @Test
    public void testCustomQueue()  {
        final AtomicBoolean called = new AtomicBoolean();
        NioEventLoopGroup group = new NioEventLoopGroup(1,
                new ThreadPerTaskExecutor(new DefaultThreadFactory(NioEventLoopGroup.class)),
                DefaultEventExecutorChooserFactory.INSTANCE, SelectorProvider.provider(),
                DefaultSelectStrategyFactory.INSTANCE, RejectedExecutionHandlers.reject(),
                new EventLoopTaskQueueFactory() {
                    @Override
                    public Queue<Runnable> newTaskQueue(int maxCapacity) {
                        called.set(true);
                        return new LinkedBlockingQueue<Runnable>(maxCapacity);
                    }
        });

        final NioEventLoop loop = (NioEventLoop) group.next();

        try {
            loop.submit(new Runnable() {
                @Override
                public void run() {
                    // NOOP.
                }
            }).syncUninterruptibly();
            assertTrue(called.get());
        } finally {
            group.shutdownGracefully();
        }
    }

}
