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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;

import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioServerSocketChannelTest extends AbstractNioChannelTest<NioServerSocketChannel> {

    @Test
    public void testCloseOnError() throws Exception {
        ServerSocketChannel jdkChannel = ServerSocketChannel.open();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel(group.next(), group, jdkChannel);
        try {
            serverSocketChannel.register().syncUninterruptibly();
            serverSocketChannel.bind(new InetSocketAddress(0)).syncUninterruptibly();
            assertFalse(serverSocketChannel.closeOnReadError(new IOException()));
            assertTrue(serverSocketChannel.closeOnReadError(new IllegalArgumentException()));
            serverSocketChannel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testIsActiveFalseAfterClose()  {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel(group.next(), group);
        try {
            serverSocketChannel.register().syncUninterruptibly();
            serverSocketChannel.bind(new InetSocketAddress(0)).syncUninterruptibly();
            assertTrue(serverSocketChannel.isActive());
            assertTrue(serverSocketChannel.isOpen());
            serverSocketChannel.close().syncUninterruptibly();
            assertFalse(serverSocketChannel.isOpen());
            assertFalse(serverSocketChannel.isActive());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    protected NioServerSocketChannel newNioChannel(EventLoopGroup eventLoopGroup) {
        return new NioServerSocketChannel(eventLoopGroup.next(), eventLoopGroup);
    }

    @Override
    protected NetworkChannel jdkChannel(NioServerSocketChannel channel) {
        return channel.javaChannel();
    }

    @Override
    protected SocketOption<?> newInvalidOption() {
        return StandardSocketOptions.IP_MULTICAST_IF;
    }
}
