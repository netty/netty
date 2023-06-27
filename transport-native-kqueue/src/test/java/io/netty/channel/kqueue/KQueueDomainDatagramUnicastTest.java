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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.unix.DomainDatagramChannel;
import io.netty.channel.unix.DomainDatagramPacket;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.DatagramUnicastTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KQueueDomainDatagramUnicastTest extends DatagramUnicastTest {

    @Test
    void testBind(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testBind(bootstrap2);
            }
        });
    }

    private void testBind(Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            channel = cb.handler(new ChannelInboundHandlerAdapter())
                        .bind(newSocketAddress()).sync().channel();
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
        return KQueueSocketTestPermutation.INSTANCE.domainDatagram();
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return KQueueSocketTestPermutation.newSocketAddress();
    }

    @Override
    protected Channel setupClientChannel(Bootstrap cb, final byte[] bytes, final CountDownLatch latch,
                                         final AtomicReference<Throwable> errorRef) throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<DomainDatagramPacket>() {

            @Override
            public void channelRead0(ChannelHandlerContext ctx, DomainDatagramPacket msg) {
                try {
                    ByteBuf buf = msg.content();
                    assertEquals(bytes.length, buf.readableBytes());
                    for (int i = 0; i < bytes.length; i++) {
                        assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                    }

                    assertEquals(ctx.channel().localAddress(), msg.recipient());
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
        sb.handler(new SimpleChannelInboundHandler<DomainDatagramPacket>() {

            @Override
            public void channelRead0(ChannelHandlerContext ctx, DomainDatagramPacket msg) {
                try {
                    if (sender == null) {
                        assertNotNull(msg.sender());
                    } else {
                        assertEquals(sender, msg.sender());
                    }

                    ByteBuf buf = msg.content();
                    assertEquals(bytes.length, buf.readableBytes());
                    for (int i = 0; i < bytes.length; i++) {
                        assertEquals(bytes[i], buf.getByte(buf.readerIndex() + i));
                    }

                    assertEquals(ctx.channel().localAddress(), msg.recipient());

                    if (echo) {
                        ctx.writeAndFlush(new DomainDatagramPacket(buf.retainedDuplicate(), msg.sender()));
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
        return sb.bind(newSocketAddress()).sync().channel();
    }

    @Override
    protected ChannelFuture write(Channel cc, ByteBuf buf, SocketAddress remote, WrapType wrapType) {
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
}
