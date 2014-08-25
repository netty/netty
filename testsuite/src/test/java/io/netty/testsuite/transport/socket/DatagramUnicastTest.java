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
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DatagramUnicastTest extends AbstractDatagramTest {

    @Test
    public void testSimpleSendDirectByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend0(sb, cb, Unpooled.directBuffer());
    }

    @Test
    public void testSimpleSendHeapByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend0(sb, cb, Unpooled.directBuffer());
    }

    @Test
    public void testSimpleSendCompositeDirectByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeDirectByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(Unpooled.directBuffer(2, 2));
        buf.addComponent(Unpooled.directBuffer(2, 2));
        testSimpleSend0(sb, cb, buf);
    }

    @Test
    public void testSimpleSendCompositeHeapByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeHeapByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(Unpooled.buffer(2, 2));
        buf.addComponent(Unpooled.buffer(2, 2));
        testSimpleSend0(sb, cb, buf);
    }

    @Test
    public void testSimpleSendCompositeMixedByteBuf() throws Throwable {
        run();
    }

    public void testSimpleSendCompositeMixedByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        CompositeByteBuf buf = Unpooled.compositeBuffer();
        buf.addComponent(Unpooled.directBuffer(2, 2));
        buf.addComponent(Unpooled.buffer(2, 2));
        testSimpleSend0(sb, cb, buf);
    }

    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, ByteBuf buf) throws Throwable {
        buf.writeInt(1);
        final CountDownLatch latch = new CountDownLatch(1);

        sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                assertEquals(1, msg.content().readInt());
                latch.countDown();
            }
        });

        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msgs) throws Exception {
                // Nothing will be sent.
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.bind().sync().channel();

        cc.writeAndFlush(new DatagramPacket(buf, addr)).sync();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        sc.close().sync();
        cc.close().sync();
    }

    @Test
    public void testSimpleSendWithoutBind() throws Throwable {
        run();
    }

    @SuppressWarnings("deprecation")
    public void testSimpleSendWithoutBind(Bootstrap sb, Bootstrap cb) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);

        sb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                assertEquals(1, msg.content().readInt());
                latch.countDown();
            }
        });

        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msgs) throws Exception {
                // Nothing will be sent.
            }
        });
        cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.register().sync().channel();

        cc.writeAndFlush(new DatagramPacket(Unpooled.copyInt(1), addr)).sync();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        sc.close().sync();
        cc.close().sync();
    }
}
