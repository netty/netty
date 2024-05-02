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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.UUID;

import static io.netty.channel.socket.nio.NioDomainSocketUtil.newUnixDomainSocketAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_16)
public class NioServerDomainSocketChannelTest extends AbstractNioDomainChannelTest<NioServerDomainSocketChannel> {

    @Test
    public void testCloseOnError() throws Exception {
        ServerSocketChannel jdkChannel = NioServerDomainSocketChannel.newChannel(SelectorProvider.provider());
        NioServerDomainSocketChannel serverSocketChannel = new NioServerDomainSocketChannel(jdkChannel);
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        File file = newRandomTmpFile();
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            serverSocketChannel.bind(newUnixDomainSocketAddress(file.getAbsolutePath()))
                    .syncUninterruptibly();
            assertFalse(serverSocketChannel.closeOnReadError(new IOException()));
            serverSocketChannel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
            file.delete();
        }
    }

    @Test
    public void testIsActiveFalseAfterClose() throws Exception {
        NioServerDomainSocketChannel serverSocketChannel = new NioServerDomainSocketChannel();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        File file = newRandomTmpFile();
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            Channel channel = serverSocketChannel.bind(
                    newUnixDomainSocketAddress(file.getAbsolutePath()))
                    .syncUninterruptibly().channel();
            assertTrue(channel.isActive());
            assertTrue(channel.isOpen());
            channel.close().syncUninterruptibly();
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
        } finally {
            group.shutdownGracefully();
            file.delete();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void testCreateChannelFromJdkChannel(boolean bindJdkChannel) throws Exception {
        File file = newRandomTmpFile();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            SocketAddress localAddress = newUnixDomainSocketAddress(file.getAbsolutePath());
            ServerSocketChannel jdkChannel = NioServerDomainSocketChannel.newChannel(SelectorProvider.provider());
            if (bindJdkChannel) {
                jdkChannel.bind(localAddress);
            }
            NioServerDomainSocketChannel serverSocketChannel = new NioServerDomainSocketChannel(jdkChannel);
            group.register(serverSocketChannel).syncUninterruptibly();
            assertTrue(serverSocketChannel.isOpen());

            assertEquals(bindJdkChannel, serverSocketChannel.isActive());

            serverSocketChannel.close().syncUninterruptibly();
            assertFalse(serverSocketChannel.isOpen());
            assertFalse(serverSocketChannel.isActive());
        } finally {
            group.shutdownGracefully();
            file.delete();
        }
    }

    @Override
    protected NioServerDomainSocketChannel newNioChannel() {
        return new NioServerDomainSocketChannel();
    }

    @Override
    protected NetworkChannel jdkChannel(NioServerDomainSocketChannel channel) {
        return channel.javaChannel();
    }

    @Override
    protected SocketOption<?> newInvalidOption() {
        return StandardSocketOptions.IP_MULTICAST_IF;
    }

    private static File newRandomTmpFile() {
        return new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
    }
}
