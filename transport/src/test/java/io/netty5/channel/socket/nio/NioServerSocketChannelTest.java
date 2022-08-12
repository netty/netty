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
package io.netty5.channel.socket.nio;

import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioServerSocketChannelTest extends AbstractNioChannelTest<NioServerSocketChannel> {

    @Test
    public void testIsActiveFalseAfterClose() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        NioServerSocketChannel channel = new NioServerSocketChannel(group.next(), group);
        try {
            channel.register().asStage().sync();
            channel.bind(new InetSocketAddress(0)).asStage().sync();
            assertTrue(channel.isActive());
            assertTrue(channel.isOpen());
            channel.close().asStage().sync();
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    protected NioServerSocketChannel newNioChannel(EventLoopGroup group) {
        return new NioServerSocketChannel(group.next(), group);
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
