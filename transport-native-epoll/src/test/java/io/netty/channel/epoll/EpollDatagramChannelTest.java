/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.Socket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;

import static io.netty.util.NetUtil.LOCALHOST;
import static io.netty.util.NetUtil.LOCALHOST4;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollDatagramChannelTest {

    @BeforeEach
    public void setUp() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testDefaultMaxMessagePerRead() {
        EpollDatagramChannel channel = new EpollDatagramChannel();
        assertEquals(16, channel.config().getMaxMessagesPerRead());
        channel.unsafe().closeForcibly();
    }

    @Test
    public void testNotActiveNoLocalRemoteAddress() throws IOException {
        checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel());
        checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel(InternetProtocolFamily.IPv4));
        checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel(InternetProtocolFamily.IPv6));
    }

    @Test
    public void testActiveHasLocalAddress() throws IOException {
        Socket socket = Socket.newSocketDgram();
        EpollDatagramChannel channel = new EpollDatagramChannel(socket.intValue());
        InetSocketAddress localAddress = channel.localAddress();
        assertTrue(channel.active);
        assertNotNull(localAddress);
        assertEquals(socket.localAddress(), localAddress);
        channel.fd().close();
    }

    @Test
    public void testLocalAddressBeforeAndAfterBind() {
        EventLoopGroup group = new EpollEventLoopGroup(1);
        try {
            TestHandler handler = new TestHandler();
            InetSocketAddress localAddressBeforeBind = new InetSocketAddress(LOCALHOST, 0);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(EpollDatagramChannel.class)
                    .localAddress(localAddressBeforeBind)
                    .handler(handler);

            ChannelFuture future = bootstrap.bind().syncUninterruptibly();

            assertNull(handler.localAddress);

            SocketAddress localAddressAfterBind = future.channel().localAddress();
            assertNotNull(localAddressAfterBind);
            assertInstanceOf(InetSocketAddress.class, localAddressAfterBind);
            assertTrue(((InetSocketAddress) localAddressAfterBind).getPort() != 0);

            future.channel().close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(10)
    public void testIpv4SocketRejectsIpv6RecipientInBatch() throws Exception {
        EventLoopGroup group = new EpollEventLoopGroup(1);
        try (DatagramSocket receiver = new DatagramSocket(new InetSocketAddress(LOCALHOST4, 0))) {
            receiver.setSoTimeout(5000);
            InetSocketAddress ipv4Recipient = (InetSocketAddress) receiver.getLocalSocketAddress();
            // The IPv6 destination ends in the same bytes as the IPv4 destination.
            InetSocketAddress ipv6Recipient = new InetSocketAddress("2001:db8::7f00:1", receiver.getLocalPort());
            Channel sender = new Bootstrap().group(group)
                    .channelFactory(new ChannelFactory<EpollDatagramChannel>() {
                        @Override
                        public EpollDatagramChannel newChannel() {
                            return new EpollDatagramChannel(InternetProtocolFamily.IPv4);
                        }
                    })
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(LOCALHOST4, 0)).sync().channel();
            try {
                ByteBuf rejectedPayload = Unpooled.buffer(1).writeByte(0);
                ChannelFuture rejected = sender.write(new DatagramPacket(rejectedPayload, ipv6Recipient));
                sender.write(new DatagramPacket(Unpooled.buffer(1).writeByte(1), ipv4Recipient));
                sender.writeAndFlush(new DatagramPacket(Unpooled.buffer(1).writeByte(2), ipv4Recipient)).sync();

                assertInstanceOf(UnsupportedAddressTypeException.class, rejected.await().cause());
                assertEquals(0, rejectedPayload.refCnt());

                byte[] buffer = new byte[1];
                java.net.DatagramPacket received = new java.net.DatagramPacket(buffer, buffer.length);
                receiver.receive(received);
                assertEquals(1, buffer[0]);
                receiver.receive(received);
                assertEquals(2, buffer[0]);
            } finally {
                sender.close().sync();
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void checkNotActiveNoLocalRemoteAddress(EpollDatagramChannel channel) throws IOException {
        assertFalse(channel.active);
        assertNull(channel.localAddress());
        assertNull(channel.remoteAddress());
        channel.fd().close();
    }

    private static final class TestHandler extends ChannelInboundHandlerAdapter {
        private volatile SocketAddress localAddress;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.localAddress = ctx.channel().localAddress();
            super.channelRegistered(ctx);
        }
    }
}
