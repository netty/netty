/*
 * Copyright 2018 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioDomainServerSocketChannelTest extends AbstractNioDomainChannelTest<NioDomainServerSocketChannel> {

    static final Path TEST_DOMAIN_SOCKET_PATH = Path.of("/tmp/graalos/udx.socket");
    @BeforeEach
    public void setupBeforeEach() throws IOException {
        Files.deleteIfExists(TEST_DOMAIN_SOCKET_PATH);
    }

    @Test
    public void testCloseOnError() throws Exception {
        ServerSocketChannel jdkChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        NioDomainServerSocketChannel serverSocketChannel = new NioDomainServerSocketChannel(jdkChannel);
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            serverSocketChannel.bind(UnixDomainSocketAddress.of(TEST_DOMAIN_SOCKET_PATH)).syncUninterruptibly();
            assertFalse(serverSocketChannel.closeOnReadError(new IOException()));
            serverSocketChannel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testIsActiveFalseAfterClose()  {
        NioDomainServerSocketChannel serverSocketChannel = new NioDomainServerSocketChannel();
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            Channel channel = serverSocketChannel.bind(
                     UnixDomainSocketAddress.of(Path.of("/tmp/graalos/udx.socket"))).syncUninterruptibly().channel();
            assertTrue(channel.isActive());
            assertTrue(channel.isOpen());
            channel.close().syncUninterruptibly();
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    protected NioDomainServerSocketChannel newNioChannel() {
        return new NioDomainServerSocketChannel();
    }

    @Override
    protected NetworkChannel jdkChannel(NioDomainServerSocketChannel channel) {
        return channel.javaChannel();
    }

    @Override
    protected SocketOption<?> newInvalidOption() {
        return StandardSocketOptions.IP_MULTICAST_IF;
    }
}
