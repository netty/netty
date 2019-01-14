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
package io.netty.channel.kqueue;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static io.netty.channel.kqueue.BsdSocket.newSocketStream;
import static io.netty.channel.unix.NativeInetAddress.address;

@UnstableApi
public final class KQueueServerSocketChannel extends AbstractKQueueServerChannel implements ServerSocketChannel {
    private final KQueueServerSocketChannelConfig config;

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, childEventLoopGroup, newSocketStream(), false);
        config = new KQueueServerSocketChannelConfig(this);
    }

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(eventLoop, childEventLoopGroup, new BsdSocket(fd));
    }

    KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd) {
        super(eventLoop, childEventLoopGroup, fd);
        config = new KQueueServerSocketChannelConfig(this);
    }

    KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd, boolean active) {
        super(eventLoop, childEventLoopGroup, fd, active);
        config = new KQueueServerSocketChannelConfig(this);
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof KQueueEventLoop;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);

        // TODO(scott): tcp fast open here!
        socket.listen(config.getBacklog());
        active = true;
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
    public KQueueServerSocketChannelConfig config() {
        return config;
    }

    @Override
    protected Channel newChildChannel(int fd, byte[] address, int offset, int len) throws Exception {
        return new KQueueSocketChannel(this, childEventLoopGroup().next(),
                                       new BsdSocket(fd), address(address, offset, len));
    }
}
