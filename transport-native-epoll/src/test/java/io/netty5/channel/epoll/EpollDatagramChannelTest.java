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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.channel.unix.Socket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.netty5.util.NetUtil.LOCALHOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollDatagramChannelTest {

    @BeforeEach
    public void setUp() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testNotActiveNoLocalRemoteAddress() throws IOException {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollHandler.newFactory());
        try {
            checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel(group.next()));
            checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel(group.next(), InternetProtocolFamily.IPv4));
            checkNotActiveNoLocalRemoteAddress(new EpollDatagramChannel(group.next(), InternetProtocolFamily.IPv6));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testActiveHasLocalAddress() throws IOException {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollHandler.newFactory());
        try {
            Socket socket = Socket.newSocketDgram();
            EpollDatagramChannel channel = new EpollDatagramChannel(group.next(), socket.intValue());
            InetSocketAddress localAddress = channel.localAddress();
            assertTrue(channel.active);
            assertNotNull(localAddress);
            assertEquals(socket.localAddress(), localAddress);
            channel.fd().close();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testLocalAddressBeforeAndAfterBind() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollHandler.newFactory());
        try {
            TestHandler handler = new TestHandler();
            InetSocketAddress localAddressBeforeBind = new InetSocketAddress(LOCALHOST, 0);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(EpollDatagramChannel.class)
                    .localAddress(localAddressBeforeBind)
                    .handler(handler);

            Channel channel = bootstrap.bind().get();

            assertNull(handler.localAddress);

            SocketAddress localAddressAfterBind = channel.localAddress();
            assertNotNull(localAddressAfterBind);
            assertTrue(localAddressAfterBind instanceof InetSocketAddress);
            assertTrue(((InetSocketAddress) localAddressAfterBind).getPort() != 0);

            channel.close().syncUninterruptibly();
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

    private static final class TestHandler implements ChannelHandler {
        private volatile SocketAddress localAddress;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            localAddress = ctx.channel().localAddress();
            ctx.fireChannelRegistered();
        }
    }
}
