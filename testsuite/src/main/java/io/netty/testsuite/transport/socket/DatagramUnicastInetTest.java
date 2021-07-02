/*
 * Copyright 2021 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatagramUnicastInetTest extends DatagramUnicastTest {

    @Test
    public void testBindWithPortOnly(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testBindWithPortOnly(bootstrap2);
            }
        });
    }

    private static void testBindWithPortOnly(Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            cb.handler(new ChannelHandlerAdapter() { });
            channel = cb.bind(0).sync().channel();
        } finally {
            closeChannel(channel);
        }
    }

    @Override
    protected boolean isConnected(Channel channel) {
        return ((DatagramChannel) channel).isConnected();
    }

    @Override
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

    @Override
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

    @Override
    protected boolean supportDisconnect() {
        return true;
    }

    @Override
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
}
