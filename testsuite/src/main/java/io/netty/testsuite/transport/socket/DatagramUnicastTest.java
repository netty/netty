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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Test;

import java.net.BindException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DatagramUnicastTest extends AbstractDatagramTest {

    private static final byte[] BYTES = {0, 1, 2, 3};
    private enum WrapType {
        NONE, DUP, SLICE, READ_ONLY
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

    @SuppressWarnings("deprecation")
    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, ByteBuf buf, boolean bindClient,
                                final byte[] bytes, int count, WrapType wrapType)
            throws Throwable {
        final CountDownLatch latch = new CountDownLatch(count);

        sb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        ByteBuf buf = msg.content();
                        assertEquals(bytes.length, buf.readableBytes());
                        for (byte b: bytes) {
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
        for (int i = 0; i < 3; i ++) {
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

        Channel cc;
        if (bindClient) {
            cc = cb.bind().sync().channel();
        } else {
            cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
            cc = cb.register().sync().channel();
        }

        for (int i = 0; i < count; i++) {
            switch (wrapType) {
                case DUP:
                    cc.write(new DatagramPacket(buf.retain().duplicate(), addr));
                    break;
                case SLICE:
                    cc.write(new DatagramPacket(buf.retain().slice(), addr));
                    break;
                case READ_ONLY:
                    cc.write(new DatagramPacket(buf.retain().asReadOnly(), addr));
                    break;
                case NONE:
                    cc.write(new DatagramPacket(buf.retain(), addr));
                    break;
                default:
                    throw new Error("unknown wrap type: " + wrapType);
            }
        }
        // release as we used buf.retain() before
        buf.release();
        cc.flush();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        sc.close().sync();
        cc.close().sync();
    }
}
