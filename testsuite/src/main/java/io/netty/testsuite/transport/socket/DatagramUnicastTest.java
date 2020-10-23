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
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.NetUtil;
import org.junit.Test;

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

import static org.junit.Assert.*;

public class DatagramUnicastTest extends AbstractDatagramTest {

    private static final byte[] BYTES = {0, 1, 2, 3};
    private enum WrapType {
        NONE, DUP, SLICE, READ_ONLY
    }

    @Test
    public void testBindWithPortOnly() throws Throwable {
        run();
    }

    public void testBindWithPortOnly(Bootstrap sb, Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            cb.handler(new ChannelHandlerAdapter() { });
            channel = cb.bind(0).sync().channel();
        } finally {
            closeChannel(channel);
        }
    }

    @Test
    public void testSimpleSendDirectByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendHeapByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeDirectByteBuf() throws Throwable {
        run();
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
    public void testSimpleSendCompositeHeapByteBuf() throws Throwable {
        run();
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
    public void testSimpleSendCompositeMixedByteBuf() throws Throwable {
        run();
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
    public void testSimpleSendWithoutBind() throws Throwable {
        run();
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
    public void testSimpleSendWithConnect() throws Throwable {
        run();
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
                public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
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

            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
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

    private static ChannelFuture write(Channel cc, ByteBuf buf, InetSocketAddress remote, WrapType wrapType) {
        switch (wrapType) {
            case DUP:
                return cc.write(new DatagramPacket(buf.retainedDuplicate(), remote));
            case SLICE:
                return cc.write(new DatagramPacket(buf.retainedSlice(), remote));
            case READ_ONLY:
                return cc.write(new DatagramPacket(buf.retain().asReadOnly(), remote));
            case NONE:
                return cc.write(new DatagramPacket(buf.retain(), remote));
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
        final CountDownLatch clientLatch = new CountDownLatch(count);
        final AtomicReference<Throwable> clientErrorRef = new AtomicReference<Throwable>();
        cb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
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
                    clientLatch.countDown();
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                clientErrorRef.compareAndSet(null, cause);
            }
        });

        Channel sc = null;
        DatagramChannel cc = null;
        try {
            final CountDownLatch latch = new CountDownLatch(count);
            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            cc = (DatagramChannel) cb.bind(newSocketAddress()).sync().channel();
            sc = setupServerChannel(sb, bytes, cc.localAddress(), latch, errorRef, true);

            cc.connect(sendToAddress((InetSocketAddress) sc.localAddress())).syncUninterruptibly();

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
            assertTrue(cc.isConnected());

            assertNotNull(cc.localAddress());
            assertNotNull(cc.remoteAddress());

            // Test what happens when we call disconnect()
            cc.disconnect().syncUninterruptibly();
            assertFalse(cc.isConnected());
            assertNotNull(cc.localAddress());
            assertNull(cc.remoteAddress());

            ChannelFuture future = cc.writeAndFlush(
                    buf.retain().duplicate()).awaitUninterruptibly();
            assertTrue("NotYetConnectedException expected, got: " + future.cause(),
                    future.cause() instanceof NotYetConnectedException);
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

    @SuppressWarnings("deprecation")
    private Channel setupServerChannel(Bootstrap sb, final byte[] bytes, final SocketAddress sender,
                                       final CountDownLatch latch, final AtomicReference<Throwable> errorRef,
                                       final boolean echo)
            throws Throwable {
        sb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
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

    private static void closeChannel(Channel channel) throws Exception {
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
}
