/*
 * Copyright 2026 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NioEventLoopShutdownTest {

    // https://github.com/netty/netty/issues/17897 (shutdown starts while the event loop runs a task)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void shutdownWithZeroQuietPeriodClosesChannelsWhileTaskIsRunning() throws Exception {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup worker = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        Channel server = null;
        try {
            server = bind(boss, worker, accepted, null);
            try (Socket client = connect(server)) {
                Channel child = accepted.get(10, TimeUnit.SECONDS);

                CountDownLatch taskStarted = new CountDownLatch(1);
                CountDownLatch releaseTask = new CountDownLatch(1);
                child.eventLoop().execute(() -> {
                    taskStarted.countDown();
                    awaitQuietly(releaseTask);
                });
                assertTrue(taskStarted.await(10, TimeUnit.SECONDS));

                // Start the shutdown while the task is still running, then let the task finish.
                Future<?> shutdown = worker.shutdownGracefully(0, 15, TimeUnit.SECONDS);
                releaseTask.countDown();
                shutdown.sync();

                assertTrue(worker.isTerminated());
                assertPeerSeesClose(client);
                assertFalse(child.isOpen(), "child channel must be closed when its event loop terminates");
            }
        } finally {
            closeAll(server, boss, worker);
        }
    }

    // https://github.com/netty/netty/issues/17897 (a registration runs after shutdown started)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void shutdownWithZeroQuietPeriodClosesChannelRegisteredDuringShutdown() throws Exception {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup worker = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        CompletableFuture<Void> registrationQueued = new CompletableFuture<>();
        Channel server = null;
        try {
            // Completes once the ServerBootstrapAcceptor queued the child registration on the worker.
            ChannelInboundHandlerAdapter acceptObserver = new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ctx.fireChannelRead(msg);
                    registrationQueued.complete(null);
                }
            };
            server = bind(boss, worker, accepted, acceptObserver);

            // Keep the worker busy so the child registration waits in its task queue.
            EventLoop workerLoop = worker.next();
            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            workerLoop.execute(() -> {
                blockerStarted.countDown();
                awaitQuietly(releaseBlocker);
            });
            assertTrue(blockerStarted.await(10, TimeUnit.SECONDS));

            try (Socket client = connect(server)) {
                registrationQueued.get(10, TimeUnit.SECONDS);

                Future<?> shutdown = worker.shutdownGracefully(0, 15, TimeUnit.SECONDS);
                releaseBlocker.countDown();
                shutdown.sync();

                assertTrue(worker.isTerminated());
                assertPeerSeesClose(client);
            }
        } finally {
            closeAll(server, boss, worker);
        }
    }

    // A registration queued while the event loop is suspending can only run as part of confirmShutdown() once a
    // shutdown has started, after the IO loop is gone. It must be rejected instead of leaking.
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void registrationQueuedWhileSuspendingIsClosedOnShutdown() throws Exception {
        CountDownLatch ioLoopExited = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        SingleThreadIoEventLoop loop = new SingleThreadIoEventLoop(
                null, new ThreadPerTaskExecutor(new DefaultThreadFactory("suspend-test")),
                NioIoHandler.newFactory()) {
            @Override
            protected void run() {
                super.run();
                if (!isShuttingDown()) {
                    // The IO loop returned because the executor is suspending. Hold the thread here, before the
                    // executor re-reads its state, so the test can queue a registration and start the shutdown.
                    ioLoopExited.countDown();
                    awaitQuietly(resume);
                }
            }
        };
        NioSocketChannel channel = new NioSocketChannel();
        try {
            loop.submit(() -> { }).sync();
            assertTrue(loop.trySuspend());
            assertTrue(ioLoopExited.await(10, TimeUnit.SECONDS));

            // The executor is still ST_SUSPENDING, so this only queues the registration without starting a thread.
            loop.register(channel);
            Future<?> shutdown = loop.shutdownGracefully(0, 15, TimeUnit.SECONDS);
            resume.countDown();
            shutdown.sync();

            assertTrue(channel.closeFuture().await(10, TimeUnit.SECONDS), "channel leaked after shutdown");
        } finally {
            resume.countDown();
            channel.unsafe().closeForcibly();
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    private static Channel bind(EventLoopGroup boss, EventLoopGroup worker, CompletableFuture<Channel> accepted,
                                ChannelInboundHandlerAdapter serverHandler) throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_LINGER, 0)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        accepted.complete(ch);
                    }
                });
        if (serverHandler != null) {
            bootstrap.handler(serverHandler);
        }
        return bootstrap.bind("127.0.0.1", 0).sync().channel();
    }

    private static Socket connect(Channel server) throws IOException {
        Socket client = new Socket();
        client.connect(server.localAddress(), 5000);
        client.setSoTimeout(5000);
        return client;
    }

    // If the accepted socket is never closed, the client read times out.
    private static void assertPeerSeesClose(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        try {
            int read = in.read();
            assertEquals(-1, read, "expected EOF");
        } catch (SocketTimeoutException e) {
            fail("the server side socket was left open after the event loop terminated", e);
        } catch (IOException expected) {
            // Connection reset (SO_LINGER 0) is also a valid way of being closed.
        }
    }

    private static void closeAll(Channel server, EventLoopGroup boss, EventLoopGroup worker)
            throws InterruptedException {
        if (server != null) {
            server.close().sync();
        }
        boss.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
        worker.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
