/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ServerSocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollServerSocketChannel extends AbstractEpollServerChannel implements ServerSocketChannel {

    private final EpollServerSocketChannelConfig config;
    private volatile InetSocketAddress local;

    public EpollServerSocketChannel() {
        super(Native.socketStreamFd());
        config = new EpollServerSocketChannelConfig(this);
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EpollEventLoop;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        InetSocketAddress addr = (InetSocketAddress) localAddress;
        checkResolvable(addr);
        int fd = fd().intValue();
        Native.bind(fd, addr);
        local = Native.localAddress(fd);
        Native.listen(fd, config.getBacklog());
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
    public EpollServerSocketChannelConfig config() {
        return config;
    }

    @Override
    protected InetSocketAddress localAddress0() {
        return local;
    }

    @Override
    protected Channel newChildChannel(int fd) throws Exception {
        return new EpollSocketChannel(this, fd);
    }
}
