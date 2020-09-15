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
import io.netty.channel.unix.NativeInetAddress;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class IOUringServerSocketChannel extends AbstractIOUringServerChannel implements ServerSocketChannel {
    private final IOUringServerSocketChannelConfig config;

    public IOUringServerSocketChannel() {
        super(LinuxSocket.newSocketStream(), false);
        this.config = new IOUringServerSocketChannelConfig(this);
    }

    @Override
    public IOUringServerSocketChannelConfig config() {
        return config;
    }

    @Override
    Channel newChildChannel(int fd, byte[] array, int offset, int len) {
        return new IOUringSocketChannel(this, new LinuxSocket(fd), NativeInetAddress.address(array, offset, len));
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
}
