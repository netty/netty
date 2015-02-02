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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollSocketChannel extends AbstractEpollStreamChannel implements SocketChannel {

    private final EpollSocketChannelConfig config;

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;

    EpollSocketChannel(Channel parent, int fd) {
        super(parent, fd);
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        flags |= Native.EPOLLRDHUP;
        config = new EpollSocketChannelConfig(this);
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        remote = Native.remoteAddress(fd);
        local = Native.localAddress(fd);
    }

    public EpollSocketChannel() {
        super(Native.socketStreamFd());
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        flags |= Native.EPOLLRDHUP;
        config = new EpollSocketChannelConfig(this);
    }

    /**
     * Returns the {@code TCP_INFO} for the current socket. See <a href="http://linux.die.net/man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo() {
        return tcpInfo(new EpollTcpInfo());
    }

    /**
     * Updates and returns the {@code TCP_INFO} for the current socket.
     * See <a href="http://linux.die.net/man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo(EpollTcpInfo info) {
        Native.tcpInfo(fd().intValue(), info);
        return info;
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
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        if (remote == null) {
            // Remote address not know, try to get it now.
            InetSocketAddress address = Native.remoteAddress(fd().intValue());
            if (address != null) {
                remote = address;
            }
            return address;
        }
        return remote;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) local;
        int fd = fd().intValue();
        Native.bind(fd, localAddress);
        this.local = Native.localAddress(fd);
    }

    @Override
    public EpollSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown0();
    }

    @Override
    public boolean isOutputShutdown() {
        return isOutputShutdown0();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        return shutdownOutput0(promise);
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            checkResolvable((InetSocketAddress) localAddress);
        }
        checkResolvable((InetSocketAddress) remoteAddress);
        if (super.doConnect(remoteAddress, localAddress)) {
            int fd = fd().intValue();
            local = Native.localAddress(fd);
            remote = Native.remoteAddress(fd);
            return true;
        }
        return false;
    }
}
