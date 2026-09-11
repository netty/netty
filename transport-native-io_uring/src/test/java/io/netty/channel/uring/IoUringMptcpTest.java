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
package io.netty.channel.uring;

import io.netty.channel.ChannelException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.Socket;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IoUringMptcpTest {

    @Test
    public void newChannelConstructorsWhenSupportMptcp() throws Exception {
        IoUring.ensureAvailability();
        assertTrue(IoUring.isAvailable(), "io_uring native transport must load");
        Assumptions.assumeTrue(Socket.isMptcpSupported(), "MPTCP not supported on this kernel");

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            IoUringServerSocketChannel server = new IoUringServerSocketChannel(SocketProtocolFamily.INET, true);
            group.register(server).sync();
            server.close().sync();

            IoUringSocketChannel client = new IoUringSocketChannel(SocketProtocolFamily.INET, true);
            group.register(client).sync();
            client.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void constructThrowsWhenMptcpUnsupported() {
        IoUring.ensureAvailability();
        assertTrue(IoUring.isAvailable(), "io_uring native transport must load");
        Assumptions.assumeFalse(Socket.isMptcpSupported(), "Only runs on kernels without MPTCP");

        ChannelException ex = assertThrows(ChannelException.class,
            () -> new IoUringServerSocketChannel(SocketProtocolFamily.INET, true));
        assertTrue(ex.getMessage().contains("MPTCP is not supported"));
        ex = assertThrows(ChannelException.class,
            () -> new IoUringSocketChannel(SocketProtocolFamily.INET, true));
        assertTrue(ex.getMessage().contains("MPTCP is not supported"));
    }
}
