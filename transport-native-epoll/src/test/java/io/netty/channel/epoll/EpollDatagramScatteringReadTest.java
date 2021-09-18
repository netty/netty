/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractDatagramTest;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EpollDatagramScatteringReadTest extends AbstractDatagramTest  {

    @BeforeAll
    public static void assumeRecvmmsgSupported() {
        assumeTrue(Native.IS_SUPPORTING_RECVMMSG);
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.epollOnlyDatagram(internetProtocolFamily());
    }

    @Test
    public void testScatteringReadPartial(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringReadPartial(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringReadPartial(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, false, true);
    }

    @Test
    public void testScatteringRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringRead(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringRead(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, false, false);
    }

    @Test
    public void testScatteringReadConnectedPartial(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringReadConnectedPartial(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringReadConnectedPartial(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, true, true);
    }

    @Test
    public void testScatteringConnectedRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringConnectedRead(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringConnectedRead(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, true, false);
    }

    private void testScatteringRead(Bootstrap sb, Bootstrap cb, boolean connected, boolean partial) throws Throwable {
        int packetSize = 512;
        int numPackets = 4;

        sb.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(
                packetSize, packetSize * (partial ? numPackets / 2 : numPackets), 64 * 1024));
        sb.option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, packetSize);

        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
                    // Nothing will be sent.
                }
            });
            cc = cb.bind(newSocketAddress()).sync().channel();
            final SocketAddress ccAddress = cc.localAddress();

            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            final byte[] bytes = new byte[packetSize];
            PlatformDependent.threadLocalRandom().nextBytes(bytes);

            final CountDownLatch latch = new CountDownLatch(numPackets);
            sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                private int counter;
                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    assertTrue(counter > 1);
                    counter = 0;
                    ctx.read();
                }

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                    assertEquals(ccAddress, msg.sender());

                    assertEquals(bytes.length, msg.content().readableBytes());
                    byte[] receivedBytes = new byte[bytes.length];
                    msg.content().readBytes(receivedBytes);
                    assertArrayEquals(bytes, receivedBytes);

                    counter++;
                    latch.countDown();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                    errorRef.compareAndSet(null, cause);
                }
            });

            sb.option(ChannelOption.AUTO_READ, false);
            sc = sb.bind(newSocketAddress()).sync().channel();

            if (connected) {
                sc.connect(cc.localAddress()).syncUninterruptibly();
            }

            InetSocketAddress addr = (InetSocketAddress) sc.localAddress();

            List<ChannelFuture> futures = new ArrayList<ChannelFuture>(numPackets);
            for (int i = 0; i < numPackets; i++) {
                futures.add(cc.write(new DatagramPacket(cc.alloc().directBuffer().writeBytes(bytes), addr)));
            }

            cc.flush();

            for (ChannelFuture f: futures) {
                f.sync();
            }

            // Enable autoread now which also triggers a read, this should cause scattering reads (recvmmsg) to happen.
            sc.config().setAutoRead(true);

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail("Timeout while waiting for packets");
            }
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
        }
    }

    @Test
    public void testScatteringReadWithSmallBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringReadWithSmallBuffer(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringReadWithSmallBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringReadWithSmallBuffer0(sb, cb, false);
    }

    @Test
    public void testScatteringConnectedReadWithSmallBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testScatteringConnectedReadWithSmallBuffer(bootstrap, bootstrap2);
            }
        });
    }

    public void testScatteringConnectedReadWithSmallBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringReadWithSmallBuffer0(sb, cb, true);
    }

    private void testScatteringReadWithSmallBuffer0(Bootstrap sb, Bootstrap cb, boolean connected) throws Throwable {
        int packetSize = 16;

        sb.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(1400, 1400, 64 * 1024));
        sb.option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1400);

        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });
            cc = cb.bind(newSocketAddress()).sync().channel();
            final SocketAddress ccAddress = cc.localAddress();

            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            final byte[] bytes = new byte[packetSize];
            PlatformDependent.threadLocalRandom().nextBytes(bytes);

            final CountDownLatch latch = new CountDownLatch(1);
            sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                    assertEquals(ccAddress, msg.sender());

                    assertEquals(bytes.length, msg.content().readableBytes());
                    byte[] receivedBytes = new byte[bytes.length];
                    msg.content().readBytes(receivedBytes);
                    assertArrayEquals(bytes, receivedBytes);

                    latch.countDown();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                    errorRef.compareAndSet(null, cause);
                }
            });

            sc = sb.bind(newSocketAddress()).sync().channel();

            if (connected) {
                sc.connect(cc.localAddress()).syncUninterruptibly();
            }

            InetSocketAddress addr = (InetSocketAddress) sc.localAddress();

            cc.writeAndFlush(new DatagramPacket(cc.alloc().directBuffer().writeBytes(bytes), addr)).sync();

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail("Timeout while waiting for packets");
            }
        } finally {
            if (cc != null) {
                cc.close().syncUninterruptibly();
            }
            if (sc != null) {
                sc.close().syncUninterruptibly();
            }
        }
    }
}
