/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.ChannelException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.Socket;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollMptcpTest {

    private static final int SOL_SOCKET = 1;
    private static final int SO_PROTOCOL = 38;
    private static final int IPPROTO_MPTCP = 262;

    @Test
    public void newSocketStreamMptcpWhenSupported() throws Exception {
        Epoll.ensureAvailability();
        assertTrue(Epoll.isAvailable(), "epoll native transport must load");
        Assumptions.assumeTrue(Socket.isMptcpSupported(), "MPTCP not supported on this kernel");

        LinuxSocket socket = LinuxSocket.newSocketStream(SocketProtocolFamily.INET, true);
        try {
            assertTrue(socket.intValue() > 0);
            assertEquals(IPPROTO_MPTCP, socket.getIntOpt(SOL_SOCKET, SO_PROTOCOL),
                "mptcp enable must yield IPPROTO_MPTCP");
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            assertNotNull(socket.localAddress());
        } finally {
            socket.close();
        }
    }

    @Test
    public void newSocketStreamMptcpThrowsWhenUnsupported() {
        Assumptions.assumeFalse(Socket.isMptcpSupported(), "only runs on kernels WITHOUT MPTCP support");
        assertThrows(ChannelException.class,
            () -> LinuxSocket.newSocketStream(SocketProtocolFamily.INET, true));
    }

    @Test
    public void newChannelConstructorsWhenSupportMptcp() throws Exception {
        Epoll.ensureAvailability();
        assertTrue(Epoll.isAvailable(), "epoll native transport must load");
        assertTrue(Socket.isMptcpSupported(), "kernel without MPTCP support");

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollServerSocketChannel server = new EpollServerSocketChannel(SocketProtocolFamily.INET, true);
            group.register(server).sync();
            server.close().sync();

            EpollSocketChannel client = new EpollSocketChannel(SocketProtocolFamily.INET, true);
            group.register(client).sync();
            client.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

}
