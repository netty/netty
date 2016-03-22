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
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.Socket.newSocketStream;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollServerSocketChannel extends AbstractEpollServerChannel implements ServerSocketChannel {

    private final EpollServerSocketChannelConfig config;
    private volatile InetSocketAddress local;
    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    public EpollServerSocketChannel() {
        super(newSocketStream(), false);
        config = new EpollServerSocketChannelConfig(this);
    }

    /**
     * @deprecated Use {@link #EpollServerSocketChannel(Socket, boolean)}.
     * Creates a new {@link EpollServerSocketChannel} from an existing {@link FileDescriptor}.
     */
    @Deprecated
    public EpollServerSocketChannel(FileDescriptor fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(new Socket(fd.intValue()));
    }

    /**
     * @deprecated Use {@link #EpollServerSocketChannel(Socket, boolean)}.
     * Creates a new {@link EpollServerSocketChannel} from an existing {@link Socket}.
     */
    @Deprecated
    public EpollServerSocketChannel(Socket fd) {
        super(fd);
        // As we create an EpollServerSocketChannel from a FileDescriptor we should try to obtain the remote and local
        // address from it. This is needed as the FileDescriptor may be bound already.
        local = fd.localAddress();
        config = new EpollServerSocketChannelConfig(this);
    }

    public EpollServerSocketChannel(Socket fd, boolean active) {
        super(fd, active);
        // As we create an EpollServerSocketChannel from a FileDescriptor we should try to obtain the remote and local
        // address from it. This is needed as the FileDescriptor may be bound already.
        local = fd.localAddress();
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
        fd().bind(addr);
        local = fd().localAddress();
        if (Native.IS_SUPPORTING_TCP_FASTOPEN && config.getTcpFastopen() > 0) {
            Native.setTcpFastopen(fd().intValue(), config.getTcpFastopen());
        }
        fd().listen(config.getBacklog());
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
    protected Channel newChildChannel(int fd, byte[] address, int offset, int len) throws Exception {
        return new EpollSocketChannel(this, new Socket(fd), address(address, offset, len));
    }

    Collection<InetAddress> tcpMd5SigAddresses() {
        return tcpMd5SigAddresses;
    }

    void setTcpMd5Sig(Map<InetAddress, byte[]> keys) throws IOException {
        tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
    }
}
