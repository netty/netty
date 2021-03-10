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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.DatagramUnicastTest;
import org.junit.Assume;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.fail;

public class EpollDatagramUnicastTest extends DatagramUnicastTest {
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
    public void testSendSegmentedDatagramPacket() throws Throwable {
        run();
    }

    public void testSendSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb)
            throws Throwable {
        if (!(cb.group() instanceof EpollEventLoopGroup)) {
            // Only supported for the native epoll transport.
            return;
        }
        Assume.assumeTrue(SegmentedDatagramPacket.isSupported());
        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
                    // Nothing will be sent.
                }
            });

            final SocketAddress sender;
            cc = cb.bind(newSocketAddress()).sync().channel();

            final int segmentSize = 512;
            int bufferCapacity = 16 * segmentSize;
            final CountDownLatch latch = new CountDownLatch(bufferCapacity / segmentSize);
            AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            sc = sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                    if (packet.content().readableBytes() == segmentSize) {
                        latch.countDown();
                    }
                }
            }).bind(newSocketAddress()).sync().channel();

            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
            ByteBuf buffer = Unpooled.directBuffer(bufferCapacity).writeZero(bufferCapacity);
            cc.writeAndFlush(new SegmentedDatagramPacket(buffer, segmentSize, addr)).sync();

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
