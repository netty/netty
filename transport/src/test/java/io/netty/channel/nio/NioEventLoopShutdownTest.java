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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
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

    // https://github.com/netty/netty/issues/17897 (manifestation 1)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void shutdownWithZeroQuietPeriodClosesChannelsWhileTaskIsRunning() throws Exception {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup worker = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        Channel server = null;
        try {
            server = bind(boss, worker, accepted);
            try (Socket client = connect(server)) {
                Channel child = accepted.get(10, TimeUnit.SECONDS);

                CountDownLatch taskStarted = new CountDownLatch(1);
                child.eventLoop().execute(() -> {
                    taskStarted.countDown();
                    sleepQuietly(300);
                });
                assertTrue(taskStarted.await(10, TimeUnit.SECONDS));

                worker.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();

                assertTrue(worker.isTerminated());
                assertPeerSeesClose(client);
                assertFalse(child.isOpen(), "child channel must be closed when its event loop terminates");
            }
        } finally {
            if (server != null) {
                server.close().sync();
            }
            boss.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
            worker.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
        }
    }

    // https://github.com/netty/netty/issues/17897 (manifestation 2)
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void shutdownWithZeroQuietPeriodClosesChannelRegisteredDuringShutdown() throws Exception {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup worker = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        Channel server = null;
        try {
            server = bind(boss, worker, accepted);

            // Keep the worker busy until shutdown starts, so the child registration waits in its task queue.
            EventLoop workerLoop = worker.next();
            CountDownLatch blockerStarted = new CountDownLatch(1);
            workerLoop.execute(() -> {
                blockerStarted.countDown();
                while (!workerLoop.isShuttingDown()) {
                    sleepQuietly(10);
                }
            });
            assertTrue(blockerStarted.await(10, TimeUnit.SECONDS));

            try (Socket client = connect(server)) {
                // Give the boss loop time to accept and queue the child registration on the worker.
                Thread.sleep(200);

                worker.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();

                assertTrue(worker.isTerminated());
                assertPeerSeesClose(client);
            }
        } finally {
            if (server != null) {
                server.close().sync();
            }
            boss.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
            worker.shutdownGracefully(0, 15, TimeUnit.SECONDS).sync();
        }
    }

    private static Channel bind(EventLoopGroup boss, EventLoopGroup worker, CompletableFuture<Channel> accepted)
            throws InterruptedException {
        return new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_LINGER, 0)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        accepted.complete(ch);
                    }
                })
                .bind("127.0.0.1", 0).sync().channel();
    }

    private static Socket connect(Channel server) throws IOException {
        Socket client = new Socket();
        client.connect(server.localAddress(), 5000);
        client.setSoTimeout(5000);
        return client;
    }

    // With the bug the accepted socket is never closed, so the client read times out.
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

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
