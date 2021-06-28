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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DatagramUnicastTest extends AbstractDatagramTest {

    private static final byte[] BYTES = {0, 1, 2, 3};
    protected enum WrapType {
        NONE, DUP, SLICE, READ_ONLY
    }

    @Test
    public void testSimpleSendDirectByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendDirectByteBuf(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendHeapByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendHeapByteBuf(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeDirectByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendCompositeDirectByteBuf(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendCompositeDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeHeapByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendCompositeHeapByteBuf(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendCompositeHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeMixedByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendCompositeMixedByteBuf(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendCompositeMixedByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendWithoutBind(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendWithoutBind(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendWithoutBind(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), false, BYTES, 1);
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), false, BYTES, 4);
    }

    private void testSimpleSend(Bootstrap sb, Bootstrap cb, ByteBuf buf, boolean bindClient,
                                final byte[] bytes, int count) throws Throwable {
        for (WrapType type: WrapType.values()) {
            testSimpleSend0(sb, cb, buf.retain(), bindClient, bytes, count, type);
        }
        assertTrue(buf.release());
    }

    @Test
    public void testSimpleSendWithConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testSimpleSendWithConnect(bootstrap, bootstrap2);
            }
        });
    }

    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSendWithConnect(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), BYTES, 1);
        testSimpleSendWithConnect(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), BYTES, 4);
    }

    @SuppressWarnings("deprecation")
    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, ByteBuf buf, boolean bindClient,
                                final byte[] bytes, int count, WrapType wrapType)
            throws Throwable {
        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void channelRead0(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });

            final SocketAddress sender;
            if (bindClient) {
                cc = cb.bind(newSocketAddress()).sync().channel();
                sender = cc.localAddress();
            } else {
                cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
                cc = cb.register().sync().channel();
                sender = null;
            }

            final CountDownLatch latch = new CountDownLatch(count);
            AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            sc = setupServerChannel(sb, bytes, sender, latch, errorRef, false);

            SocketAddress localAddr = sc.localAddress();
            SocketAddress addr = localAddr instanceof InetSocketAddress ?
                    sendToAddress((InetSocketAddress) sc.localAddress()) : localAddr;
            List<ChannelFuture> futures = new ArrayList<ChannelFuture>(count);
            for (int i = 0; i < count; i++) {
                futures.add(write(cc, buf, addr, wrapType));
            }
            // release as we used buf.retain() before
            cc.flush();

            for (ChannelFuture future: futures) {
                future.sync();
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail();
            }
        } finally {
            // release as we used buf.retain() before
            buf.release();

            closeChannel(cc);
            closeChannel(sc);
        }
    }

    protected ChannelFuture write(Channel cc, ByteBuf buf, SocketAddress remote, WrapType wrapType) {
        switch (wrapType) {
            case DUP:
                return cc.write(new DatagramPacket(buf.retainedDuplicate(), (InetSocketAddress) remote));
            case SLICE:
                return cc.write(new DatagramPacket(buf.retainedSlice(), (InetSocketAddress) remote));
            case READ_ONLY:
                return cc.write(new DatagramPacket(buf.retain().asReadOnly(), (InetSocketAddress) remote));
            case NONE:
                return cc.write(new DatagramPacket(buf.retain(), (InetSocketAddress) remote));
            default:
                throw new Error("unknown wrap type: " + wrapType);
        }
    }

    private void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb, ByteBuf buf, final byte[] bytes, int count)
            throws Throwable {
        try {
            for (WrapType type : WrapType.values()) {
                testSimpleSendWithConnect0(sb, cb, buf.retain(), bytes, count, type);
            }
        } finally {
            assertTrue(buf.release());
        }
    }

    private void testSimpleSendWithConnect0(Bootstrap sb, Bootstrap cb, ByteBuf buf, final byte[] bytes, int count,
                                            WrapType wrapType) throws Throwable {
        Channel sc = null;
        Channel cc = null;
        try {
            final CountDownLatch latch = new CountDownLatch(count);
            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            final CountDownLatch clientLatch = new CountDownLatch(count);
            final AtomicReference<Throwable> clientErrorRef = new AtomicReference<Throwable>();
            cc = setupClientChannel(cb, bytes, clientLatch, clientErrorRef);
            sc = setupServerChannel(sb, bytes, cc.localAddress(), latch, errorRef, true);

            SocketAddress localAddr = sc.localAddress();
            SocketAddress addr = localAddr instanceof InetSocketAddress ?
                    sendToAddress((InetSocketAddress) sc.localAddress()) : localAddr;
            cc.connect(addr).syncUninterruptibly();

            List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
            for (int i = 0; i < count; i++) {
                futures.add(write(cc, buf, wrapType));
            }
            cc.flush();

            for (ChannelFuture future: futures) {
                future.sync();
            }

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable cause = errorRef.get();
                if (cause != null) {
                    throw cause;
                }
                fail();
            }
            if (!clientLatch.await(10, TimeUnit.SECONDS)) {
                Throwable cause = clientErrorRef.get();
                if (cause != null) {
                    throw cause;
                }
                fail();
            }
            assertTrue(isConnected(cc));

            assertNotNull(cc.localAddress());
            assertNotNull(cc.remoteAddress());

            if (supportDisconnect()) {
                // Test what happens when we call disconnect()
                cc.disconnect().syncUninterruptibly();
                assertFalse(isConnected(cc));
                assertNotNull(cc.localAddress());
                assertNull(cc.remoteAddress());

                ChannelFuture future = cc.writeAndFlush(
                        buf.retain().duplicate()).awaitUninterruptibly();
                assertTrue(future.cause() instanceof NotYetConnectedException,
                        "NotYetConnectedException expected, got: " + future.cause());
            }
        } finally {
            // release as we used buf.retain() before
            buf.release();

            closeChannel(cc);
            closeChannel(sc);
        }
    }

    private static ChannelFuture write(Channel cc, ByteBuf buf, WrapType wrapType) {
        switch (wrapType) {
            case DUP:
                return cc.write(buf.retainedDuplicate());
            case SLICE:
                return cc.write(buf.retainedSlice());
            case READ_ONLY:
                return cc.write(buf.retain().asReadOnly());
            case NONE:
                return cc.write(buf.retain());
            default:
                throw new Error("unknown wrap type: " + wrapType);
        }
    }

    protected boolean isConnected(Channel channel) {
        return ((DatagramChannel) channel).isConnected();
    }

    protected Channel setupClientChannel(Bootstrap cb, final byte[] bytes, final CountDownLatch latch,
                                         final AtomicReference<Throwable> errorRef) throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                try {
                    ByteBuf buf = msg.content();
                    assertEquals(bytes.length, buf.readableBytes());
                    for (int i = 0; i < bytes.length; i++) {
                        assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                    }

                    InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
                    if (localAddress.getAddress().isAnyLocalAddress()) {
                        assertEquals(localAddress.getPort(), msg.recipient().getPort());
                    } else {
                        // Test that the channel's localAddress is equal to the message's recipient
                        assertEquals(localAddress, msg.recipient());
                    }
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                errorRef.compareAndSet(null, cause);
            }
        });
        return cb.bind(newSocketAddress()).sync().channel();
    }

    protected Channel setupServerChannel(Bootstrap sb, final byte[] bytes, final SocketAddress sender,
                                       final CountDownLatch latch, final AtomicReference<Throwable> errorRef,
                                       final boolean echo) throws Throwable {
        sb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        try {
                            if (sender == null) {
                                assertNotNull(msg.sender());
                            } else {
                                InetSocketAddress senderAddress = (InetSocketAddress) sender;
                                if (senderAddress.getAddress().isAnyLocalAddress()) {
                                    assertEquals(senderAddress.getPort(), msg.sender().getPort());
                                } else {
                                    assertEquals(sender, msg.sender());
                                }
                            }

                            ByteBuf buf = msg.content();
                            assertEquals(bytes.length, buf.readableBytes());
                            for (int i = 0; i < bytes.length; i++) {
                                assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                            }

                            // Test that the channel's localAddress is equal to the message's recipient
                            assertEquals(ctx.channel().localAddress(), msg.recipient());

                            if (echo) {
                                ctx.writeAndFlush(new DatagramPacket(buf.retainedDuplicate(), msg.sender()));
                            }
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        errorRef.compareAndSet(null, cause);
                    }
                });
            }
        });
        return sb.bind(newSocketAddress()).sync().channel();
    }

    protected static void closeChannel(Channel channel) throws Exception {
        if (channel != null) {
            channel.close().sync();
        }
    }

    protected InetSocketAddress sendToAddress(InetSocketAddress serverAddress) {
        InetAddress addr = serverAddress.getAddress();
        if (addr.isAnyLocalAddress()) {
            if (addr instanceof Inet6Address) {
                return new InetSocketAddress(NetUtil.LOCALHOST6, serverAddress.getPort());
            }
            return new InetSocketAddress(NetUtil.LOCALHOST4, serverAddress.getPort());
        }
        return serverAddress;
    }

    protected boolean supportDisconnect() {
        return true;
    }
}
