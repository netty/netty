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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.Socket;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.netty.util.NetUtil.LOCALHOST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EpollDatagramChannelTest {

    @Before
    public void setUp() {
        Epoll.ensureAvailability();
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
            assertTrue(localAddressAfterBind instanceof InetSocketAddress);
            assertTrue(((InetSocketAddress) localAddressAfterBind).getPort() != 0);

            future.channel().close().syncUninterruptibly();
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
