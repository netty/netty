/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static io.netty.channel.epoll.LinuxSocket.newSocketStream;
import static io.netty.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;
import static io.netty.channel.unix.NativeInetAddress.address;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollServerSocketChannel extends AbstractEpollServerChannel implements ServerSocketChannel {

    private final EpollServerSocketChannelConfig config;
    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    public EpollServerSocketChannel() {
        this((SocketProtocolFamily) null);
    }

    /**
     * @deprecated  use {@link EpollServerSocketChannel#EpollServerSocketChannel(SocketProtocolFamily)}
     */
    @Deprecated
    public EpollServerSocketChannel(InternetProtocolFamily protocol) {
        super(newSocketStream(protocol), false);
        config = new EpollServerSocketChannelConfig(this);
    }

    public EpollServerSocketChannel(SocketProtocolFamily protocol) {
        super(newSocketStream(protocol), false);
        config = new EpollServerSocketChannelConfig(this);
    }

    public EpollServerSocketChannel(int fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(new LinuxSocket(fd));
    }

    EpollServerSocketChannel(LinuxSocket fd) {
        super(fd);
        config = new EpollServerSocketChannelConfig(this);
    }

    EpollServerSocketChannel(LinuxSocket fd, boolean active) {
        super(fd, active);
        config = new EpollServerSocketChannelConfig(this);
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        final int tcpFastopen;
        if (IS_SUPPORTING_TCP_FASTOPEN_SERVER && (tcpFastopen = config.getTcpFastopen()) > 0) {
            socket.setTcpFastOpen(tcpFastopen);
        }
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
    public EpollServerSocketChannelConfig config() {
        return config;
    }

    @Override
    protected Channel newChildChannel(int fd, byte[] address, int offset, int len) throws Exception {
        return new EpollSocketChannel(this, new LinuxSocket(fd), address(address, offset, len));
    }

    Collection<InetAddress> tcpMd5SigAddresses() {
        return tcpMd5SigAddresses;
    }

    void setTcpMd5Sig(Map<InetAddress, byte[]> keys) throws IOException {
        // Add synchronized as newTcpMp5Sigs might do multiple operations on the socket itself.
        synchronized (this) {
            tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
        }
    }
}
