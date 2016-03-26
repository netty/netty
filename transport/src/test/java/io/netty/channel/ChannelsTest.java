/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel;


import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ChannelsTest {

    @Test
    public void oioTest() {
        Class<? extends EventLoopGroup> groupClass = OioEventLoopGroup.class;

        Class<DatagramChannel> datagram = Channels.datagramChannelType(groupClass);
        Class<SocketChannel> socket = Channels.socketChannelType(groupClass);
        Class<ServerSocketChannel> serverSocket = Channels.serverSocketChannelType(groupClass);

        assertTrue("datagram channel mismatch.", OioDatagramChannel.class.equals(datagram));
        assertTrue("socket channel mismatch.", OioSocketChannel.class.equals(socket));
        assertTrue("server socket channel mismatch.", OioServerSocketChannel.class.equals(serverSocket));
    }

    @Test
    public void nioTest() {
        Class<? extends EventLoopGroup> groupClass = NioEventLoopGroup.class;

        Class<DatagramChannel> datagram = Channels.datagramChannelType(groupClass);
        Class<SocketChannel> socket = Channels.socketChannelType(groupClass);
        Class<ServerSocketChannel> serverSocket = Channels.serverSocketChannelType(groupClass);

        assertTrue("datagram channel mismatch.", NioDatagramChannel.class.equals(datagram));
        assertTrue("socket channel mismatch.", NioSocketChannel.class.equals(socket));
        assertTrue("server socket channel mismatch.", NioServerSocketChannel.class.equals(serverSocket));
    }
}
