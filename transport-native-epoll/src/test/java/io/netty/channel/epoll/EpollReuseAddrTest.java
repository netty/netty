/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class EpollReuseAddrTest {
    private static final int MAJOR;
    private static final int MINOR;
    private static final int BUGFIX;
    static {
        String kernelVersion = Native.KERNEL_VERSION;
        int index = kernelVersion.indexOf('-');
        if (index > -1) {
            kernelVersion = kernelVersion.substring(0, index);
        }
        String[] versionParts = kernelVersion.split("\\.");
        if (versionParts.length <= 3) {
            MAJOR = Integer.parseInt(versionParts[0]);
            MINOR = Integer.parseInt(versionParts[1]);
            if (versionParts.length == 3) {
                BUGFIX = Integer.parseInt(versionParts[2]);
            } else {
                BUGFIX = 0;
            }
        } else {
            throw new IllegalStateException("Can not parse kernel version " + kernelVersion);
        }
    }

    @Test
    public void testMultipleBindSocketChannelWithoutReusePortFails() {
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        testMultipleBindDatagramChannelWithoutReusePortFails0(createServerBootstrap());
    }

    @Test
    public void testMultipleBindDatagramChannelWithoutReusePortFails() {
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        testMultipleBindDatagramChannelWithoutReusePortFails0(createBootstrap());
    }

    private static void testMultipleBindDatagramChannelWithoutReusePortFails0(AbstractBootstrap<?, ?> bootstrap) {
        bootstrap.handler(new DummyHandler());
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        try {
            bootstrap.bind(future.channel().localAddress()).syncUninterruptibly();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IOException);
        }
        future.channel().close().syncUninterruptibly();
    }

    @Test(timeout = 10000)
    public void testMultipleBindSocketChannel() throws Exception {
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        ServerBootstrap bootstrap = createServerBootstrap();
        bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
        final AtomicBoolean accepted1 = new AtomicBoolean();
        bootstrap.childHandler(new ServerSocketTestHandler(accepted1));
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        InetSocketAddress address1 = (InetSocketAddress) future.channel().localAddress();

        final AtomicBoolean accepted2 = new AtomicBoolean();
        bootstrap.childHandler(new ServerSocketTestHandler(accepted2));
        ChannelFuture future2 = bootstrap.bind(address1).syncUninterruptibly();
        InetSocketAddress address2 = (InetSocketAddress) future2.channel().localAddress();

        Assert.assertEquals(address1, address2);
        while (!accepted1.get() || !accepted2.get()) {
            Socket socket = new Socket(address1.getAddress(), address1.getPort());
            socket.setReuseAddress(true);
            socket.close();
        }
        future.channel().close().syncUninterruptibly();
        future2.channel().close().syncUninterruptibly();
    }

    @Test(timeout = 10000)
    @Ignore // TODO: Unignore after making it pass on centos6-1 and debian7-1
    public void testMultipleBindDatagramChannel() throws Exception {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        Bootstrap bootstrap = createBootstrap();
        bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
        final AtomicBoolean received1 = new AtomicBoolean();
        bootstrap.handler(new DatagramSocketTestHandler(received1));
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        final InetSocketAddress address1 = (InetSocketAddress) future.channel().localAddress();

        final AtomicBoolean received2 = new AtomicBoolean();
        bootstrap.handler(new DatagramSocketTestHandler(received2));
        ChannelFuture future2 = bootstrap.bind(address1).syncUninterruptibly();
        final InetSocketAddress address2 = (InetSocketAddress) future2.channel().localAddress();

        Assert.assertEquals(address1, address2);
        final byte[] bytes = "data".getBytes();

        // fire up 16 Threads and send DatagramPackets to make sure we stress it enough to see DatagramPackets received
        // on both sockets.
        int count = 16;
        final CountDownLatch latch = new CountDownLatch(count);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    while (!received1.get() || !received2.get()) {
                        socket.send(new DatagramPacket(
                                bytes, 0, bytes.length, address1.getAddress(), address1.getPort()));
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(count);
        for (int i = 0 ; i < count; i++) {
            executor.execute(r);
        }
        latch.await();
        executor.shutdown();
        future.channel().close().syncUninterruptibly();
        future2.channel().close().syncUninterruptibly();
        Assert.assertTrue(received1.get());
        Assert.assertTrue(received2.get());
    }

    private static ServerBootstrap createServerBootstrap() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(EpollSocketTestPermutation.EPOLL_BOSS_GROUP, EpollSocketTestPermutation.EPOLL_WORKER_GROUP);
        bootstrap.channel(EpollServerSocketChannel.class);
        bootstrap.childHandler(new DummyHandler());
        InetSocketAddress address = new InetSocketAddress(NetUtil.LOCALHOST, 0);
        bootstrap.localAddress(address);
        return bootstrap;
    }

    private static Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(EpollSocketTestPermutation.EPOLL_WORKER_GROUP);
        bootstrap.channel(EpollDatagramChannel.class);
        InetSocketAddress address = new InetSocketAddress(NetUtil.LOCALHOST, 0);
        bootstrap.localAddress(address);
        return bootstrap;
    }

    private static boolean versionEqOrGt(int major, int minor, int bugfix)  {
        if (MAJOR > major) {
            return true;
        }
        if (MAJOR == major) {
            if (MINOR > minor) {
                return true;
            } else if (MINOR == minor) {
                if (BUGFIX >= bugfix) {
                    return true;
                }
            }
        }
        return false;
    }

    @ChannelHandler.Sharable
    private static class ServerSocketTestHandler extends ChannelInboundHandlerAdapter {
        private final AtomicBoolean accepted;

        ServerSocketTestHandler(AtomicBoolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            accepted.set(true);
            ctx.close();
        }
    }

    @ChannelHandler.Sharable
    private static class DatagramSocketTestHandler extends ChannelInboundHandlerAdapter {
        private final AtomicBoolean received;

        DatagramSocketTestHandler(AtomicBoolean received) {
            this.received = received;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg);
            received.set(true);
        }
    }

    @ChannelHandler.Sharable
    private static final class DummyHandler extends ChannelHandlerAdapter { }
}
