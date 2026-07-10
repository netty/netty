/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.unix;

import io.netty.channel.ChannelException;
import io.netty.channel.socket.SocketProtocolFamily;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnixSocketMptcpTest {

    @Test
    void newSocketStreamMptcpWhenSupported() throws Exception {
        Assumptions.assumeTrue(Socket.isMptcpSupported(), "MPTCP not supported on this kernel");
        int fd = Socket.newSocketStream0(SocketProtocolFamily.INET, true);
        Socket socket = new Socket(fd);
        try {
            assertTrue(socket.intValue() > 0);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            assertNotNull(socket.localAddress());
        } finally {
            socket.close();
        }
    }

    @Test
    void newSocketStreamMptcpThrowsWhenUnsupported() {
        Assumptions.assumeTrue(!Socket.isMptcpSupported(), "only runs on kernels WITHOUT MPTCP support");
        assertThrows(ChannelException.class,
                () -> Socket.newSocketStream0(SocketProtocolFamily.INET, true));
    }
}

