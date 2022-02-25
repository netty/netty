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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.unix.BufferDomainDatagramPacket;
import io.netty5.channel.unix.DomainDatagramChannel;
import io.netty5.channel.unix.DomainDatagramPacket;
import io.netty5.channel.unix.DomainSocketAddress;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.DatagramUnicastTest;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EpollDomainDatagramUnicastTest extends DatagramUnicastTest {

    @Test
    void testBind(TestInfo testInfo) throws Throwable {
        run(testInfo, (bootstrap, bootstrap2) -> testBind(bootstrap2));
    }

    private void testBind(Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            channel = cb.handler(new ChannelHandlerAdapter() { })
                    .bind(newSocketAddress()).get();
            assertThat(channel.localAddress()).isNotNull()
                    .isInstanceOf(DomainSocketAddress.class);
        } finally {
            closeChannel(channel);
        }
    }

    @Override
    protected boolean supportDisconnect() {
        return false;
    }

    @Override
    protected boolean isConnected(Channel channel) {
        return ((DomainDatagramChannel) channel).isConnected();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.domainDatagram();
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return EpollSocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected Channel setupClientChannel(Bootstrap cb, final byte[] bytes, final CountDownLatch latch,
                                         final AtomicReference<Throwable> errorRef) throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<Object>() {

            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                try {
                    if (msg instanceof BufferDomainDatagramPacket) {
                        BufferDomainDatagramPacket packet = (BufferDomainDatagramPacket) msg;
                        Buffer buf = packet.content();
                        assertEquals(bytes.length, buf.readableBytes());
                        for (int i = 0; i < bytes.length; i++) {
                            assertEquals(bytes[i], buf.getByte(buf.readerOffset() + i));
                        }

                        assertEquals(ctx.channel().localAddress(), packet.recipient());
                    } else {
                        DomainDatagramPacket packet = (DomainDatagramPacket) msg;
                        ByteBuf buf = packet.content();
                        assertEquals(bytes.length, buf.readableBytes());
                        for (int i = 0; i < bytes.length; i++) {
                            assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                        }

                        assertEquals(ctx.channel().localAddress(), packet.recipient());
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
        return cb.bind(newSocketAddress()).get();
    }

    @Override
    protected Channel setupServerChannel(Bootstrap sb, final byte[] bytes, final SocketAddress sender,
                                         final CountDownLatch latch, final AtomicReference<Throwable> errorRef,
                                         final boolean echo) throws Throwable {
        sb.handler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    public boolean acceptInboundMessage(Object msg) throws Exception {
                        return msg instanceof DomainDatagramPacket || msg instanceof BufferDomainDatagramPacket;
                    }

                    @Override
                    public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                        try {
                            if (msg instanceof BufferDomainDatagramPacket) {
                                BufferDomainDatagramPacket packet = (BufferDomainDatagramPacket) msg;
                                if (sender == null) {
                                    assertNotNull(packet.sender());
                                } else {
                                    assertEquals(sender, packet.sender());
                                }

                                Buffer buf = packet.content();
                                assertEquals(bytes.length, buf.readableBytes());
                                for (int i = 0; i < bytes.length; i++) {
                                    assertEquals(bytes[i], buf.getByte(buf.readerOffset() + i));
                                }

                                assertEquals(ctx.channel().localAddress(), packet.recipient());

                                if (echo) {
                                    ctx.writeAndFlush(new BufferDomainDatagramPacket(buf.split(), packet.sender()));
                                }
                            } else {
                                DomainDatagramPacket packet = (DomainDatagramPacket) msg;
                                if (sender == null) {
                                    assertNotNull(packet.sender());
                                } else {
                                    assertEquals(sender, packet.sender());
                                }

                                ByteBuf buf = packet.content();
                                assertEquals(bytes.length, buf.readableBytes());
                                for (int i = 0; i < bytes.length; i++) {
                                    assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                                }

                                assertEquals(ctx.channel().localAddress(), packet.recipient());

                                if (echo) {
                                    ctx.writeAndFlush(new DomainDatagramPacket(buf.retainedDuplicate(),
                                                                               packet.sender()));
                                }
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
        return sb.bind(newSocketAddress()).get();
    }

    @Override
    protected Future<Void> write(Channel cc, ByteBuf buf, SocketAddress remote, WrapType wrapType) {
        switch (wrapType) {
            case DUP:
                return cc.write(new DomainDatagramPacket(buf.retainedDuplicate(), (DomainSocketAddress) remote));
            case SLICE:
                return cc.write(new DomainDatagramPacket(buf.retainedSlice(), (DomainSocketAddress) remote));
            case READ_ONLY:
                return cc.write(new DomainDatagramPacket(buf.retain().asReadOnly(), (DomainSocketAddress) remote));
            case NONE:
                return cc.write(new DomainDatagramPacket(buf.retain(), (DomainSocketAddress) remote));
            default:
                throw new Error("unknown wrap type: " + wrapType);
        }
    }

    @Override
    protected Future<Void> write(Channel cc, Buffer buf, SocketAddress remote) {
        return cc.write(new BufferDomainDatagramPacket(buf, (DomainSocketAddress) remote));
    }
}
