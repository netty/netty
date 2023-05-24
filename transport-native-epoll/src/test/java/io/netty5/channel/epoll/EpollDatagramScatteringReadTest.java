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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.AbstractDatagramTest;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
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
        return EpollSocketTestPermutation.INSTANCE.epollOnlyDatagram(protocolFamily());
    }

    @Test
    public void testScatteringReadPartial(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringReadPartial);
    }

    public void testScatteringReadPartial(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, false, true);
    }

    @Test
    public void testScatteringRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringRead);
    }

    public void testScatteringRead(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, false, false);
    }

    @Test
    public void testScatteringReadConnectedPartial(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringReadConnectedPartial);
    }

    public void testScatteringReadConnectedPartial(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, true, true);
    }

    @Test
    public void testScatteringConnectedRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringConnectedRead);
    }

    public void testScatteringConnectedRead(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringRead(sb, cb, true, false);
    }

    private void testScatteringRead(Bootstrap sb, Bootstrap cb, boolean connected, boolean partial) throws Throwable {
        int packetSize = 8;
        int numPackets = 4;

        sb.option(ChannelOption.READ_HANDLE_FACTORY, new AdaptiveReadHandleFactory(1,
                packetSize, packetSize * (partial ? numPackets / 2 : numPackets), 64 * 1024));
        // Set the MAX_DATAGRAM_PAYLOAD_SIZE to something bigger then the actual packet size.
        // This will allow us to check if we correctly thread the received len.
        sb.option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, packetSize * 2);

        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) throws Exception {
                    // Nothing will be sent.
                }
            });
            cc = cb.bind(newSocketAddress()).asStage().get();
            final SocketAddress ccAddress = cc.localAddress();

            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(numPackets);
            sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                private long numRead;
                private int counter;
                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    assertTrue(counter > 1);
                    counter = 0;
                    ctx.read();
                }

                @Override
                protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) {
                    assertEquals(ccAddress, msg.sender());

                    // Each packet contains a long which represent the write iteration.
                    assertEquals(8, msg.content().readableBytes());
                    assertEquals(numRead, msg.content().readLong());
                    numRead++;
                    counter++;
                    latch.countDown();
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                    errorRef.compareAndSet(null, cause);
                }
            });

            sb.option(ChannelOption.AUTO_READ, false);
            sc = sb.bind(newSocketAddress()).asStage().get();

            if (connected) {
                sc.connect(cc.localAddress()).asStage().sync();
            }

            InetSocketAddress addr = (InetSocketAddress) sc.localAddress();

            List<Future<Void>> futures = new ArrayList<>(numPackets);
            for (int i = 0; i < numPackets; i++) {
                futures.add(cc.write(new DatagramPacket(cc.bufferAllocator().allocate(8).writeLong(i), addr)));
            }

            cc.flush();

            for (Future<Void> f: futures) {
                f.asStage().sync();
            }

            // Enable autoread now which also triggers a read, this should cause scattering reads (recvmmsg) to happen.
            sc.setOption(ChannelOption.AUTO_READ, true);

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail("Timeout while waiting for packets");
            }
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
        }
    }

    @Test
    public void testScatteringReadWithSmallBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringReadWithSmallBuffer);
    }

    public void testScatteringReadWithSmallBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringReadWithSmallBuffer0(sb, cb, false);
    }

    @Test
    public void testScatteringConnectedReadWithSmallBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testScatteringConnectedReadWithSmallBuffer);
    }

    public void testScatteringConnectedReadWithSmallBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testScatteringReadWithSmallBuffer0(sb, cb, true);
    }

    private void testScatteringReadWithSmallBuffer0(Bootstrap sb, Bootstrap cb, boolean connected) throws Throwable {
        int packetSize = 16;

        sb.option(ChannelOption.READ_HANDLE_FACTORY, new AdaptiveReadHandleFactory(1, 1400, 1400, 64 * 1024));
        sb.option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1400);

        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });
            cc = cb.bind(newSocketAddress()).asStage().get();
            final SocketAddress ccAddress = cc.localAddress();

            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            final byte[] bytes = new byte[packetSize];
            ThreadLocalRandom.current().nextBytes(bytes);

            final CountDownLatch latch = new CountDownLatch(1);
            sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {

                @Override
                protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) {
                    assertEquals(ccAddress, msg.sender());

                    assertEquals(bytes.length, msg.content().readableBytes());
                    byte[] receivedBytes = new byte[bytes.length];
                    msg.content().readBytes(receivedBytes, 0, receivedBytes.length);
                    assertArrayEquals(bytes, receivedBytes);

                    latch.countDown();
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                    errorRef.compareAndSet(null, cause);
                }
            });

            sc = sb.bind(newSocketAddress()).asStage().get();

            if (connected) {
                sc.connect(cc.localAddress()).asStage().sync();
            }

            InetSocketAddress addr = (InetSocketAddress) sc.localAddress();

            cc.writeAndFlush(new DatagramPacket(cc.bufferAllocator().copyOf(bytes), addr)).asStage().sync();

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail("Timeout while waiting for packets");
            }
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
        }
    }
}
