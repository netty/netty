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
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public final class IoUringServerSocketChannel extends AbstractIoUringServerChannel implements ServerSocketChannel {
    private final IoUringServerSocketChannelConfig config;

    public IoUringServerSocketChannel() {
        // We don't use a blocking fd for the server channel at the moment as
        // there is no support for IORING_CQE_F_SOCK_NONEMPTY and IORING_ACCEPT_DONTWAIT
        // at the moment. Once these land in the kernel we should check if we can use these and if so make
        // the fd blocking to get rid of POLLIN etc.
        // See:
        //
        //  - https://lore.kernel.org/netdev/20240509180627.204155-1-axboe@kernel.dk/
        //  - https://lore.kernel.org/io-uring/20240508142725.91273-1-axboe@kernel.dk/
        super(LinuxSocket.newSocketStream(), false);
        this.config = new IoUringServerSocketChannelConfig(this);
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    Channel newChildChannel(int fd, ByteBuffer acceptedAddressMemory) {
        IoUringIoHandler handler = registration().attachment();
        LinuxSocket socket = new LinuxSocket(fd);
        if (acceptedAddressMemory != null) {
            // We didnt use ACCEPT_MULTISHOT And so can depend on the addresses.
            final InetSocketAddress address;
            if (socket.isIpv6()) {
                byte[] ipv6Array = handler.inet6AddressArray();
                byte[] ipv4Array = handler.inet4AddressArray();
                address = SockaddrIn.getIPv6(acceptedAddressMemory, ipv6Array, ipv4Array);
            } else {
                byte[] addressArray = handler.inet4AddressArray();
                address = SockaddrIn.getIPv4(acceptedAddressMemory, addressArray);
            }
            return new IoUringSocketChannel(this, new LinuxSocket(fd), address);
        }
        return new IoUringSocketChannel(this, new LinuxSocket(fd));
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
        if (IoUring.isTcpFastOpenServerSideAvailable()) {
            Integer fastOpen = config().getOption(ChannelOption.TCP_FASTOPEN);
            if (fastOpen != null && fastOpen > 0) {
                socket.setTcpFastOpen(fastOpen);
            }
        }
        socket.listen(config.getBacklog());
        active = true;
    }
}
