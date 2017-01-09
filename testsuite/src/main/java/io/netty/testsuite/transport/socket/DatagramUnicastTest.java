/*
 * Copyright 2012 The Netty Project
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
import io.netty.channel.socket.oio.OioDatagramChannel;
import org.junit.Test;

import java.net.BindException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DatagramUnicastTest extends AbstractDatagramTest {

    private static final byte[] BYTES = {0, 1, 2, 3};
    @Test
    public void testSimpleSendDirectByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendHeapByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend0(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 1);
        testSimpleSend0(sb, cb, Unpooled.buffer().writeBytes(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeDirectByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeHeapByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeMixedByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeMixedByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf, true, BYTES, 1);

        CompositeByteBuf buf2 = Unpooled.compositeBuffer();
        buf2.addComponent(true, Unpooled.directBuffer().writeBytes(BYTES, 0, 2));
        buf2.addComponent(true, Unpooled.buffer().writeBytes(BYTES, 2, 2));
        testSimpleSend0(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendWithoutBind() throws Throwable {
        run();
    }

    public void testSimpleSendWithoutBind(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), false, BYTES, 1);
        testSimpleSend0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), false, BYTES, 4);
    }

    @Test
    public void testSimpleSendWithConnect() throws Throwable {
        run();
    }

    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSendWithConnect0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), BYTES, 1);
        testSimpleSendWithConnect0(sb, cb, Unpooled.directBuffer().writeBytes(BYTES), BYTES, 4);
    }

    @SuppressWarnings("deprecation")
    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, ByteBuf buf, boolean bindClient,
                                 final byte[] bytes, int count)
            throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
                // Nothing will be sent.
            }
        });

        final CountDownLatch latch = new CountDownLatch(count);
        Channel sc = setupServerChannel(sb, bytes, latch);

        Channel cc;
        if (bindClient) {
            cc = cb.bind().sync().channel();
        } else {
            cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
            cc = cb.register().sync().channel();
        }

        for (int i = 0; i < count; i++) {
            cc.write(new DatagramPacket(buf.retain().duplicate(), addr));
        }
        // release as we used buf.retain() before
        buf.release();
        cc.flush();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        sc.close().sync();
        cc.close().sync();
    }

    private void testSimpleSendWithConnect0(Bootstrap sb, Bootstrap cb, ByteBuf buf, final byte[] bytes, int count)
            throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
                // Nothing will be sent.
            }
        });

        final CountDownLatch latch = new CountDownLatch(count);
        Channel sc = setupServerChannel(sb, bytes, latch);
        DatagramChannel cc = null;
        try {
            cc = (DatagramChannel) cb.connect(sc.localAddress()).sync().channel();

            for (int i = 0; i < count; i++) {
                cc.write(buf.retain().duplicate());
            }
            cc.flush();
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertTrue(cc.isConnected());

            // Test what happens when we call disconnect()
            cc.disconnect().syncUninterruptibly();
            assertFalse(cc.isConnected());

            ChannelFuture future = cc.writeAndFlush(
                    buf.retain().duplicate()).awaitUninterruptibly();
            if (!(cc instanceof OioDatagramChannel)) {
                // TODO: Also handle OIO
                assertTrue(future.cause() instanceof NotYetConnectedException);
            }
        } finally {
            // release as we used buf.retain() before
            buf.release();

            sc.close().sync();
            if (cc != null) {
                cc.close().sync();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Channel setupServerChannel(Bootstrap sb, final byte[] bytes, final CountDownLatch latch)
            throws Throwable {
        sb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        ByteBuf buf = msg.content();
                        assertEquals(bytes.length, buf.readableBytes());
                        for (byte b : bytes) {
                            assertEquals(b, buf.readByte());
                        }
                        latch.countDown();
                    }
                });
            }
        });

        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msgs) throws Exception {
                // Nothing will be sent.
            }
        });

        Channel sc = null;
        BindException bindFailureCause = null;
        for (int i = 0; i < 3; i++) {
            try {
                sc = sb.bind().sync().channel();
                break;
            } catch (Exception e) {
                if (e instanceof BindException) {
                    logger.warn("Failed to bind to a free port; trying again", e);
                    bindFailureCause = (BindException) e;
                    refreshLocalAddress(sb);
                } else {
                    throw e;
                }
            }
        }

        if (sc == null) {
            throw bindFailureCause;
        }
        return sc;
    }
}
