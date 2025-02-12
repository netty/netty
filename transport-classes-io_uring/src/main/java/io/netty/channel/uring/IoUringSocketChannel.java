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
package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class IoUringSocketChannel extends AbstractIoUringStreamChannel implements SocketChannel {
    private final IoUringSocketChannelConfig config;

    public IoUringSocketChannel() {
       super(null, LinuxSocket.newSocketStream(), false);
       this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(Channel parent, LinuxSocket fd) {
        super(parent, fd, true);
        this.config = new IoUringSocketChannelConfig(this);
    }

    IoUringSocketChannel(Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(parent, fd, remote);
        this.config = new IoUringSocketChannelConfig(this);
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }
}
