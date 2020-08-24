/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.Channel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public final class IOUringServerSocketChannel extends AbstractIOUringServerChannel implements ServerSocketChannel {
    private final IOUringServerSocketChannelConfig config;
    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    public IOUringServerSocketChannel() {
        super(Socket.newSocketStream().intValue());
        this.config = new IOUringServerSocketChannelConfig(this);
    }

    @Override
    public IOUringServerSocketChannelConfig config() {
        return config;
    }

    @Override
    Channel newChildChannel(int fd) throws Exception {
        return new IOUringSocketChannel(this, new LinuxSocket(fd));
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        socket.listen(config.getBacklog());
        active = true;
    }

    @Override
    public FileDescriptor fd() {
        return super.fd();
    }
}
