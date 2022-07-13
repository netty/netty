/*
 * Copyright 2022 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIf("isSupported")
public class DomainDatagramUnicastTest extends DatagramUnicastTest {

    static boolean isSupported() {
        return NioDomainSocketTestUtil.isDatagramSupported();
    }

    @Test
    void testBind(TestInfo testInfo) throws Throwable {
        run(testInfo, (bootstrap, bootstrap2) -> testBind(bootstrap2));
    }

    private void testBind(Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            channel = cb.handler(new ChannelHandlerAdapter() { })
                    .bind(newSocketAddress()).asStage().get();
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
        return ((DatagramChannel) channel).isConnected();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagram(NioDomainSocketTestUtil.domainSocketFamily());
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return SocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected Channel setupClientChannel(Bootstrap cb, final byte[] bytes, final CountDownLatch latch,
                                         final AtomicReference<Throwable> errorRef) throws Throwable {
        cb.handler(new SimpleChannelInboundHandler<DatagramPacket>() {

            @Override
            public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) {
                try {
                    Buffer buf = msg.content();
                    assertEquals(bytes.length, buf.readableBytes());
                    for (int i = 0; i < bytes.length; i++) {
                        assertEquals(bytes[i], buf.getByte(buf.readerOffset() + i));
                    }

                    assertEquals(ctx.channel().localAddress(), msg.recipient());
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                errorRef.compareAndSet(null, cause);
            }
        });
        return cb.bind(newSocketAddress()).asStage().get();
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
                    public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) {
                        try {
                            if (sender == null) {
                                assertNotNull(msg.sender());
                            } else {
                                assertEquals(sender, msg.sender());
                            }

                            Buffer buf = msg.content();
                            assertEquals(bytes.length, buf.readableBytes());
                            for (int i = 0; i < bytes.length; i++) {
                                assertEquals(bytes[i], buf.getByte(buf.readerOffset() + i));
                            }

                            assertEquals(ctx.channel().localAddress(), msg.recipient());

                            if (echo) {
                                ctx.writeAndFlush(new DatagramPacket(buf.split(), msg.sender()));
                            }
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        errorRef.compareAndSet(null, cause);
                    }
                });
            }
        });
        return sb.bind(newSocketAddress()).asStage().get();
    }

    @Override
    protected Future<Void> write(Channel cc, Buffer buf, SocketAddress remote) {
        return cc.write(new DatagramPacket(buf, remote));
    }
}
