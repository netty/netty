/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.DatagramUnicastInetTest;
import io.netty5.util.ReferenceCountUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringDatagramUnicastTest extends DatagramUnicastInetTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.datagram(SocketProtocolFamily.INET);
    }

    @Test
    @Timeout(8)
    public void testRecvMsgDontBlock(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testRecvMsgDontBlock);
    }

    public void testRecvMsgDontBlock(Bootstrap sb, Bootstrap cb) throws Throwable {
        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<>() {
                @Override
                protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                    // NOOP.
                }
            });
            cc = cb.bind(newSocketAddress()).asStage().get();

            CountDownLatch readLatch = new CountDownLatch(1);
            CountDownLatch readCompleteLatch = new CountDownLatch(1);
            sc = sb.handler(new ChannelHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    readLatch.countDown();
                    ReferenceCountUtil.release(msg);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext ctx) {
                    readCompleteLatch.countDown();
                }
            }).option(ChannelOption.READ_HANDLE_FACTORY, new FixedReadHandleFactory(2048))
                    .bind(newSocketAddress()).asStage().get();
            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
            cc.writeAndFlush(new DatagramPacket(offHeapAllocator().copyOf(new byte[512]), addr)).asStage().sync();

            readLatch.await();
            readCompleteLatch.await();
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
    public void testSendSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendSegmentedDatagramPacket);
    }

    public void testSendSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, false);
    }

    @Disabled("Loopback does not support packet segmentation.")
    @Test
    public void testSendSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendSegmentedDatagramPacketComposite);
    }

    public void testSendSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, false);
    }

    @Disabled("Loopback does not support packet segmentation.")
    @Test
    public void testSendAndReceiveSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendAndReceiveSegmentedDatagramPacket);
    }

    public void testSendAndReceiveSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, true);
    }

    @Disabled("Loopback does not support packet segmentation.")
    @Test
    public void testSendAndReceiveSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendAndReceiveSegmentedDatagramPacketComposite);
    }

    public void testSendAndReceiveSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, true);
    }

    private void testSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb, boolean composite, boolean gro)
            throws Throwable {
        assumeTrue(IoUringDatagramChannel.isSegmentedDatagramPacketSupported());
        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });

            cc = cb.bind(newSocketAddress()).asStage().get();
            if (!(cc instanceof IoUringDatagramChannel)) {
                // Only supported for the native io_uring transport.
                return;
            }
            final int numBuffers = 16;
            final int segmentSize = 512;
            int bufferCapacity = numBuffers * segmentSize;
            final CountDownLatch latch = new CountDownLatch(numBuffers);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            if (gro) {
                // Enable GRO and also ensure we can read everything with one read as otherwise
                // we will drop things on the floor.
                sb.option(IoUringChannelOption.UDP_GRO, true);
                sb.option(ChannelOption.READ_HANDLE_FACTORY, new FixedReadHandleFactory(bufferCapacity));
            }
            sc = sb.handler(new SimpleChannelInboundHandler<>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof DatagramPacket) {
                        DatagramPacket packet = (DatagramPacket) msg;
                        int packetSize = packet.content().readableBytes();
                        assertEquals(segmentSize, packetSize, "Unexpected datagram packet size");
                        latch.countDown();
                    } else {
                        fail("Unexpected message of type " + msg.getClass() + ": " + msg);
                    }
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    do {
                        Throwable throwable = errorRef.get();
                        if (throwable != null) {
                            if (throwable != cause) {
                                throwable.addSuppressed(cause);
                            }
                            break;
                        }
                    } while (!errorRef.compareAndSet(null, cause));
                    super.channelExceptionCaught(ctx, cause);
                }
            }).bind(newSocketAddress()).asStage().get();

            if (gro && !(sc instanceof IoUringDatagramChannel)) {
                // Only supported for the native io_uring transport.
                return;
            }
            if (sc instanceof IoUringDatagramChannel) {
                assertEquals(gro, sc.getOption(IoUringChannelOption.UDP_GRO));
            }
            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
            final Buffer buffer;
            if (composite) {
                Buffer[] components = new Buffer[numBuffers];
                for (int i = 0; i < numBuffers; i++) {
                    components[i] = offHeapAllocator().allocate(segmentSize);
                    components[i].fill((byte) 0);
                    components[i].skipWritableBytes(segmentSize);
                }
                buffer = offHeapAllocator().compose(stream(components).map(Buffer::send).collect(Collectors.toList()));
            } else {
                buffer = offHeapAllocator().allocate(bufferCapacity);
                buffer.fill((byte) 0);
                buffer.skipWritableBytes(bufferCapacity);
            }
            cc.writeAndFlush(new SegmentedDatagramPacket(buffer, segmentSize, addr)).asStage().sync();

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail();
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

    @Override
    protected boolean supportDisconnect() {
        return false;
    }
}
