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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

class NioDomainSocketChannelTest {
    @Test
    void accessParent() throws IOException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            NioServerSocketChannel parent = new NioServerSocketChannel(group.next(), group.next());
            SocketChannel ch = SelectorProvider.provider().openSocketChannel(StandardProtocolFamily.UNIX);
            NioSocketChannel child = new NioSocketChannel(group.next(), parent, ch);
            Assertions.assertSame(parent, child.parent());
            ch.close();
        } finally {
            group.shutdownGracefully();
        }
    }
}
