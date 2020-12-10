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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class QuicChannelDatagramTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[512];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testDatagramFlushInChannelRead() throws Throwable {
        testDatagram(false, false);
    }

    @Test
    public void testDatagramFlushInChannelReadComplete() throws Throwable {
        testDatagram(true, false);
    }

    @Test
    public void testDatagramFlushInChannelReadWriteDatagramPacket() throws Throwable {
        testDatagram(false, true);
    }

    @Test
    public void testDatagramFlushInChannelReadCompleteWriteDatagramPacket() throws Throwable {
        testDatagram(true, true);
    }

    private void testDatagram(boolean flushInReadComplete, boolean writeDatagramPacket) throws Throwable {
        AtomicReference<QuicDatagramExtensionEvent> serverEventRef = new AtomicReference<>();

        Channel server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder().datagram(10, 10),
                InsecureQuicTokenHandler.INSTANCE, new ChannelInboundHandlerAdapter() {

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof ByteBuf) {
                    final ChannelFuture future;
                    final Object message;
                    if (writeDatagramPacket) {
                        message = new DatagramPacket((ByteBuf) msg, (InetSocketAddress) ctx.channel().remoteAddress());
                    } else {
                        message = msg;
                    }
                    if (!flushInReadComplete) {
                        future = ctx.writeAndFlush(message);
                    } else {
                        future = ctx.write(message);
                    }
                    future.addListener(ChannelFutureListener.CLOSE);
                } else {
                    ctx.fireChannelRead(msg);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (flushInReadComplete) {
                    ctx.flush();
                }
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof QuicDatagramExtensionEvent) {
                    serverEventRef.set((QuicDatagramExtensionEvent) evt);
                }
            }
        }, new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();

        Promise<ByteBuf> receivedBuffer = ImmediateEventExecutor.INSTANCE.newPromise();
        AtomicReference<QuicDatagramExtensionEvent> clientEventRef = new AtomicReference<>();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder().datagram(10, 10));
        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (!receivedBuffer.trySuccess((ByteBuf) msg)) {
                                ReferenceCountUtil.release(msg);
                            }
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt instanceof QuicDatagramExtensionEvent) {
                                clientEventRef.set((QuicDatagramExtensionEvent) evt);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            receivedBuffer.tryFailure(cause);
                        }
                    })
                    .remoteAddress(address)
                    .connect()
                    .get();
            quicChannel.writeAndFlush(Unpooled.copiedBuffer(data)).sync();

            ByteBuf buffer = receivedBuffer.get();
            ByteBuf expected = Unpooled.wrappedBuffer(data);
            assertEquals(expected, buffer);
            buffer.release();
            expected.release();

            assertNotEquals(0, serverEventRef.get().maxLength());
            assertNotEquals(0, clientEventRef.get().maxLength());

            quicChannel.close().sync();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }
}
