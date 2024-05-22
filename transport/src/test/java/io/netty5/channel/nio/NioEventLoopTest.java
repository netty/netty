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
package io.netty5.channel.nio;

import io.netty5.channel.AbstractEventLoopTest;
import io.netty5.channel.Channel;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.IoRegistration;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SelectStrategy;
import io.netty5.channel.SelectStrategyFactory;
import io.netty5.channel.SingleThreadEventLoop;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioEventLoopTest extends AbstractEventLoopTest {

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new MultithreadEventLoopGroup(NioIoHandler.newFactory());
    }

    @Override
    protected Class<? extends ServerSocketChannel> newChannel() {
        return NioServerSocketChannel.class;
    }

    @Test
    public void testRebuildSelector() throws Exception {
        final NioIoHandler nioIoHandler = (NioIoHandler) NioIoHandler.newFactory().newHandler();
        EventLoop loop = new SingleThreadEventLoop(new DefaultThreadFactory("ioPool"), nioIoHandler);
        try {
            Channel channel = new NioServerSocketChannel(loop, loop);
            channel.register().asStage().sync();

            Selector selector = loop.submit(nioIoHandler::unwrappedSelector).asStage().get();

            assertSame(selector, loop.submit(nioIoHandler::unwrappedSelector).asStage().get());
            assertTrue(selector.isOpen());

            // Submit to the EventLoop, so we are sure its really executed in a non-async manner.
            loop.submit(nioIoHandler::rebuildSelector0).asStage().sync();

            Selector newSelector = loop.submit(nioIoHandler::unwrappedSelector).asStage().get();
            assertTrue(newSelector.isOpen());
            assertNotSame(selector, newSelector);
            assertFalse(selector.isOpen());

            channel.close().asStage().sync();
        } finally {
            loop.shutdownGracefully();
        }
    }

    @Test
    public void testScheduleBigDelayNotOverflow() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());

        final EventLoop el = group.next();
        Future<?> future = el.schedule(() -> {
            // NOOP
        }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        assertFalse(future.asStage().await(1000, TimeUnit.MILLISECONDS));
        assertTrue(future.cancel());
        group.shutdownGracefully();
    }

    @Test
    public void testInterruptEventLoopThread() throws Exception {
        final NioIoHandler nioIoHandler = (NioIoHandler) NioIoHandler.newFactory().newHandler();
        EventLoop loop = new SingleThreadEventLoop(new DefaultThreadFactory("ioPool"), nioIoHandler);
        try {
            Selector selector = loop.submit(nioIoHandler::unwrappedSelector).asStage().get();
            assertTrue(selector.isOpen());

            loop.submit(() -> {
                    // Interrupt the thread which should not end-up in a busy spin and
                    // so the selector should not have been rebuild.
                    Thread.currentThread().interrupt();
                }).asStage().sync();

            assertTrue(selector.isOpen());

            final CountDownLatch latch = new CountDownLatch(2);
            loop.submit(latch::countDown).asStage().sync();

            loop.schedule(latch::countDown, 2, TimeUnit.SECONDS).asStage().sync();

            latch.await();

            assertSame(selector, loop.submit(nioIoHandler::unwrappedSelector).asStage().get());
            assertTrue(selector.isOpen());
        } finally {
            loop.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testSelectableChannel() throws Exception {
        final NioIoHandler nioIoHandler = (NioIoHandler) NioIoHandler.newFactory().newHandler();
        EventLoop loop = new SingleThreadEventLoop(new DefaultThreadFactory("ioPool"), nioIoHandler);
        try {
            Channel channel = new NioServerSocketChannel(loop, loop);
            channel.register().asStage().sync();
            channel.bind(new InetSocketAddress(0)).asStage().sync();

            final SocketChannel selectableChannel = SocketChannel.open();
            selectableChannel.configureBlocking(false);
            selectableChannel.connect(channel.localAddress());

            final CountDownLatch latch = new CountDownLatch(1);
            loop.register(new NioSelectableChannelIoHandle<>(selectableChannel) {
                @Override
                protected void handle(SocketChannel channel, SelectionKey key) {
                    latch.countDown();
                }
            }).addListener(f -> {
                if (f.isSuccess()) {
                    f.getNow().submit(NioIoOps.CONNECT);
                }
            }).asStage().sync();
            latch.await();

            selectableChannel.close();
            channel.close().asStage().sync();
        } finally {
            loop.shutdownGracefully();
        }
    }

    @Test
    public void testRebuildSelectorOnIOException() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch strategyLatch = new CountDownLatch(1);
        SelectStrategyFactory selectStrategyFactory = () -> new SelectStrategy() {

            private boolean thrown;

            @Override
            public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception {
                strategyLatch.await();
                if (!thrown) {
                    thrown = true;
                    throw new IOException("expected exception!");
                }
                latch.countDown();
                return -1;
            }
        };

        final NioIoHandler nioIoHandler = (NioIoHandler) NioIoHandler.newFactory(SelectorProvider.provider(),
                selectStrategyFactory).newHandler();

        EventLoop loop = new SingleThreadEventLoop(new DefaultThreadFactory("ioPool"), nioIoHandler);
        try {
            Channel channel = new NioServerSocketChannel(loop, loop);
            Selector selector = nioIoHandler.unwrappedSelector();
            strategyLatch.countDown();

            channel.register().asStage().sync();

            latch.await();

            Selector newSelector = loop.submit(nioIoHandler::unwrappedSelector).asStage().get();
            assertTrue(newSelector.isOpen());
            assertNotSame(selector, newSelector);
            assertFalse(selector.isOpen());

            channel.close().asStage().sync();
        } finally {
            loop.shutdownGracefully();
        }
    }

}
