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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.DatagramUnicastInetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EpollDatagramUnicastTest extends DatagramUnicastInetTest {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.datagram(InternetProtocolFamily.IPv4);
    }

    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        // Run this test with IP_RECVORIGDSTADDR option enabled
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, true);
        super.testSimpleSendWithConnect(sb, cb);
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, false);
    }

    @Test
    public void testSendSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSendSegmentedDatagramPacket(bootstrap, bootstrap2);
            }
        });
    }

    public void testSendSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, false);
    }

    @Test
    public void testSendSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSendSegmentedDatagramPacketComposite(bootstrap, bootstrap2);
            }
        });
    }

    public void testSendSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, false);
    }

    @Test
    public void testSendAndReceiveSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSendAndReceiveSegmentedDatagramPacket(bootstrap, bootstrap2);
            }
        });
    }

    public void testSendAndReceiveSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, true);
    }

    @Test
    public void testSendAndReceiveSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSendAndReceiveSegmentedDatagramPacketComposite(bootstrap, bootstrap2);
            }
        });
    }

    public void testSendAndReceiveSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, true);
    }

    private void testSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb, boolean composite, boolean gro)
            throws Throwable {
        if (!(cb.group() instanceof EpollEventLoopGroup)) {
            // Only supported for the native epoll transport.
            return;
        }
        if (gro && !(sb.group() instanceof EpollEventLoopGroup)) {
            // Only supported for the native epoll transport.
            return;
        }
        assumeTrue(EpollDatagramChannel.isSegmentedDatagramPacketSupported());
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

            final int numBuffers = 16;
            final int segmentSize = 512;
            int bufferCapacity = numBuffers * segmentSize;
            final CountDownLatch latch = new CountDownLatch(numBuffers);
            AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            if (gro) {
                // Enable GRO and also ensure we can read everything with one read as otherwise
                // we will drop things on the floor.
                sb.option(EpollChannelOption.UDP_GRO, true);
                sb.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(bufferCapacity));
            }
            sc = sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                    if (packet.content().readableBytes() == segmentSize) {
                        latch.countDown();
                    }
                }
            }).bind(newSocketAddress()).sync().channel();

            if (sc instanceof EpollDatagramChannel) {
                assertEquals(gro, sc.config().getOption(EpollChannelOption.UDP_GRO));
            }
            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
            final ByteBuf buffer;
            if (composite) {
                CompositeByteBuf compositeBuffer = Unpooled.compositeBuffer();
                for (int i = 0; i < numBuffers; i++) {
                    compositeBuffer.addComponent(true,
                            Unpooled.directBuffer(segmentSize).writeZero(segmentSize));
                }
                buffer = compositeBuffer;
            } else {
                buffer = Unpooled.directBuffer(bufferCapacity).writeZero(bufferCapacity);
            }
            cc.writeAndFlush(new io.netty.channel.unix.SegmentedDatagramPacket(buffer, segmentSize, addr)).sync();

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail();
            }
        } finally {
            if (cc != null) {
                cc.close().sync();
            }
            if (sc != null) {
                sc.close().sync();
            }
        }
    }
}
