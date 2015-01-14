/*
 * Copyright 2015 The Netty Project
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
import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;

import java.net.SocketAddress;

public final class EpollDomainSocketChannel extends AbstractEpollStreamChannel {
    private final ChannelConfig config = new DefaultChannelConfig(this);

    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;

    EpollDomainSocketChannel(Channel parent, int fd) {
        super(parent, fd);
    }

    public EpollDomainSocketChannel() {
        super(Native.socketDomainFd());
    }

    @Override
    protected DomainSocketAddress localAddress0() {
        return local;
    }

    @Override
    protected DomainSocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        Native.bind(fd, localAddress);
        local = (DomainSocketAddress) localAddress;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            local = (DomainSocketAddress) localAddress;
            remote = (DomainSocketAddress) remoteAddress;
            return true;
        }
        return false;
    }

    @Override
    public DomainSocketAddress remoteAddress() {
        return (DomainSocketAddress) super.remoteAddress();
    }

    @Override
    public DomainSocketAddress localAddress() {
        return (DomainSocketAddress) super.localAddress();
    }
}
