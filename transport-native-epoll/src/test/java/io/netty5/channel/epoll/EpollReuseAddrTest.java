/*
 * Copyright 2014 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.AbstractBootstrap;
import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.util.NetUtil;
import io.netty5.util.Resource;
import io.netty5.util.ResourceLeakDetector;
import io.netty5.util.internal.logging.InternalLogLevel;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EpollReuseAddrTest {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(EpollReuseAddrTest.class);

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
                int bugFix;
                try {
                    bugFix = Integer.parseInt(versionParts[2]);
                } catch (NumberFormatException ignore) {
                    // the last part of the version may include all kind of different things. Especially when
                    // someone compiles his / her own kernel.
                    // Just ignore a parse error here and use 0.
                    bugFix = 0;
                }
                BUGFIX = bugFix;
            } else {
                BUGFIX = 0;
            }
        } else {
            LOGGER.log(InternalLogLevel.INFO, "Unable to parse kernel version: " + kernelVersion);
            MAJOR = 0;
            MINOR = 0;
            BUGFIX = 0;
        }
    }

    @Test
    public void testMultipleBindSocketChannelWithoutReusePortFails() throws Exception {
        assumeTrue(versionEqOrGt(3, 9, 0));
        testMultipleBindDatagramChannelWithoutReusePortFails0(createServerBootstrap());
    }

    @Test
    public void testMultipleBindDatagramChannelWithoutReusePortFails() throws Exception {
        assumeTrue(versionEqOrGt(3, 9, 0));
        testMultipleBindDatagramChannelWithoutReusePortFails0(createBootstrap());
    }

    private static void testMultipleBindDatagramChannelWithoutReusePortFails0(AbstractBootstrap<?, ?, ?> bootstrap)
            throws Exception {
        bootstrap.handler(new LoggingHandler(LogLevel.ERROR));
        Channel channel = bootstrap.bind().get();
        try {
            bootstrap.bind(channel.localAddress()).sync();
            fail();
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IOException);
        }
        channel.close().sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testMultipleBindSocketChannel() throws Exception {
        assumeTrue(versionEqOrGt(3, 9, 0));
        ServerBootstrap bootstrap = createServerBootstrap();
        bootstrap.option(UnixChannelOption.SO_REUSEPORT, true);
        final AtomicBoolean accepted1 = new AtomicBoolean();
        bootstrap.childHandler(new ServerSocketTestHandler(accepted1));
        Channel firstChannel = bootstrap.bind().get();
        InetSocketAddress address1 = (InetSocketAddress) firstChannel.localAddress();

        final AtomicBoolean accepted2 = new AtomicBoolean();
        bootstrap.childHandler(new ServerSocketTestHandler(accepted2));
        Channel secondChannel = bootstrap.bind(address1).get();
        InetSocketAddress address2 = (InetSocketAddress) secondChannel.localAddress();

        assertEquals(address1, address2);
        while (!accepted1.get() || !accepted2.get()) {
            Socket socket = new Socket(address1.getAddress(), address1.getPort());
            socket.setReuseAddress(true);
            socket.close();
        }
        firstChannel.close().sync();
        secondChannel.close().sync();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    @Disabled // TODO: Unignore after making it pass on centos6-1 and debian7-1
    public void testMultipleBindDatagramChannel() throws Exception {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        assumeTrue(versionEqOrGt(3, 9, 0));
        Bootstrap bootstrap = createBootstrap();
        bootstrap.option(UnixChannelOption.SO_REUSEPORT, true);
        final AtomicBoolean received1 = new AtomicBoolean();
        bootstrap.handler(new DatagramSocketTestHandler(received1));
        Channel firstChannel = bootstrap.bind().get();
        final InetSocketAddress address1 = (InetSocketAddress) firstChannel.localAddress();

        final AtomicBoolean received2 = new AtomicBoolean();
        bootstrap.handler(new DatagramSocketTestHandler(received2));
        Channel secondChannel = bootstrap.bind(address1).get();
        final InetSocketAddress address2 = (InetSocketAddress) secondChannel.localAddress();

        assertEquals(address1, address2);
        final byte[] bytes = "data".getBytes();

        // fire up 16 Threads and send DatagramPackets to make sure we stress it enough to see DatagramPackets received
        // on both sockets.
        int count = 16;
        final CountDownLatch latch = new CountDownLatch(count);
        Runnable r = () -> {
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
        };

        ExecutorService executor = Executors.newFixedThreadPool(count);
        for (int i = 0 ; i < count; i++) {
            executor.execute(r);
        }
        latch.await();
        executor.shutdown();
        firstChannel.close().sync();
        secondChannel.close().sync();
        assertTrue(received1.get());
        assertTrue(received2.get());
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
                return BUGFIX >= bugfix;
            }
        }
        return false;
    }

    private static class ServerSocketTestHandler implements ChannelHandler {
        private final AtomicBoolean accepted;

        ServerSocketTestHandler(AtomicBoolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            accepted.set(true);
            ctx.close();
        }
    }

    private static class DatagramSocketTestHandler implements ChannelHandler {
        private final AtomicBoolean received;

        DatagramSocketTestHandler(AtomicBoolean received) {
            this.received = received;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Resource.dispose(msg);
            received.set(true);
        }
    }

    private static final class DummyHandler implements ChannelHandler {
        @Override
        public boolean isSharable() {
            return true;
        }
    }
}
