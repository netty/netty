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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AbstractEventLoopTest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioDomainServerSocketChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NioDomainEventLoopTest {

    static final Path TEST_DOMAIN_SOCKET_PATH = Path.of("/tmp/graalos/udx.socket");
    @BeforeEach
    public void setupBeforeEach() throws IOException {
        TEST_DOMAIN_SOCKET_PATH.toFile().deleteOnExit();
        Files.deleteIfExists(TEST_DOMAIN_SOCKET_PATH);
    }

//    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new NioEventLoopGroup();
    }

//    @Override
    protected Class<? extends ServerChannel> newChannel() {
        return NioDomainServerSocketChannel.class;
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testSelectableChannel() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioEventLoop loop = (NioEventLoop) group.next();

        try {
            Channel channel = new NioDomainServerSocketChannel();
            loop.register(channel).syncUninterruptibly();
            channel.bind(UnixDomainSocketAddress.of(TEST_DOMAIN_SOCKET_PATH)).syncUninterruptibly();
            // connect client to server
            SocketChannel selectableChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
            selectableChannel.configureBlocking(false);
            selectableChannel.connect(channel.localAddress());
            channel.write(Charset.forName("UTF-8").encode("hello"));
            final CountDownLatch latch = new CountDownLatch(1);
            // For TCP socket, there is non-blocking connect() returns channel in ConnectionPending state
            // and can epoll on OP_CONNECT.
            // For Unix socket, the non-blocking connect() always return channel in Connected state
            // and cannot epoll on OP_CONNECT
            // see https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/
            //     master/src/java.base/share/classes/sun/nio/ch/SocketChannelImpl.java#L1036
            //
            // WRITABLE implies CONNECTED
            loop.register(selectableChannel, SelectionKey.OP_WRITE,
                    new NioTask<SocketChannel>() {
                @Override
                public void channelReady(SocketChannel ch, SelectionKey key) {
                    try {
                        latch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail();
                    }
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

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownGracefullyNoQuietPeriod() throws Exception {
        EventLoopGroup loop = newEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(loop)
                .channel(newChannel())
                .childHandler(new ChannelInboundHandlerAdapter());

        // Not close the Channel to ensure the EventLoop is still shutdown in time.
        b.bind(UnixDomainSocketAddress.of(TEST_DOMAIN_SOCKET_PATH)).sync().channel();

        Future<?> f = loop.shutdownGracefully(0, 1, TimeUnit.MINUTES);
        assertTrue(loop.awaitTermination(600, TimeUnit.MILLISECONDS));
        assertTrue(f.syncUninterruptibly().isSuccess());
        assertTrue(loop.isShutdown());
        assertTrue(loop.isTerminated());
    }

    @Test
    public void testReregister() {
        EventLoopGroup group = newEventLoopGroup();
        EventLoopGroup group2 = newEventLoopGroup();
        final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        ChannelFuture future = bootstrap.channel(newChannel()).group(group)
                .childHandler(new ChannelInitializer<NioDomainSocketChannel>() {
                    @Override
                    public void initChannel(NioDomainSocketChannel ch) {
                    }
                }).handler(new ChannelInitializer<NioDomainServerSocketChannel>() {
                    @Override
                    public void initChannel(NioDomainServerSocketChannel ch) {
                        ch.pipeline().addLast(new TestChannelHandler());
                        ch.pipeline().addLast(eventExecutorGroup, new TestChannelHandler2());
                    }
                })
                .bind(UnixDomainSocketAddress.of(TEST_DOMAIN_SOCKET_PATH)).awaitUninterruptibly();

        EventExecutor executor = future.channel().pipeline().context(TestChannelHandler2.class).executor();
        EventExecutor executor1 = future.channel().pipeline().context(TestChannelHandler.class).executor();
        future.channel().deregister().awaitUninterruptibly();
        Channel channel = group2.register(future.channel()).awaitUninterruptibly().channel();
        EventExecutor executorNew = channel.pipeline().context(TestChannelHandler.class).executor();
        assertNotSame(executor1, executorNew);
        assertSame(executor, future.channel().pipeline().context(TestChannelHandler2.class).executor());
    }

    private static final class TestChannelHandler extends ChannelDuplexHandler { }

    private static final class TestChannelHandler2 extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception { }
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
