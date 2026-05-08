/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.IovArray;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class KQueueSocketChannel extends AbstractKQueueStreamChannel implements SocketChannel {
    private final KQueueSocketChannelConfig config;

    public KQueueSocketChannel() {
        super(null, BsdSocket.newSocketStream(), false);
        config = new KQueueSocketChannelConfig(this);
    }

    /**
     * @deprecated use {@link KQueueDatagramChannel(SocketProtocolFamily)}
     */
    @Deprecated
    public KQueueSocketChannel(InternetProtocolFamily protocol) {
        super(null, BsdSocket.newSocketStream(protocol), false);
        config = new KQueueSocketChannelConfig(this);
    }

    public KQueueSocketChannel(SocketProtocolFamily protocol) {
        super(null, BsdSocket.newSocketStream(protocol), false);
        config = new KQueueSocketChannelConfig(this);
    }

    public KQueueSocketChannel(int fd) {
        super(new BsdSocket(fd));
        config = new KQueueSocketChannelConfig(this);
    }

    KQueueSocketChannel(Channel parent, BsdSocket fd, InetSocketAddress remoteAddress) {
        super(parent, fd, remoteAddress);
        config = new KQueueSocketChannelConfig(this);
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
    public KQueueSocketChannelConfig config() {
        return config;
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (config.isTcpFastOpenConnect()) {
            ChannelOutboundBuffer outbound = unsafe().outboundBuffer();
            outbound.addFlush();
            Object curr;
            if ((curr = outbound.current()) instanceof ByteBuf) {
                ByteBuf initialData = (ByteBuf) curr;
                // Don't bother with TCP FastOpen if we don't have any initial data to send anyway.
                if (initialData.isReadable()) {
                    IovArray iov = new IovArray(config.getAllocator().directBuffer());
                    try {
                        iov.add(initialData, initialData.readerIndex(), initialData.readableBytes());
                        int bytesSent = socket.connectx(
                                (InetSocketAddress) localAddress, (InetSocketAddress) remoteAddress, iov, true);
                        writeFilter(true);
                        outbound.removeBytes(Math.abs(bytesSent));
                        // The `connectx` method returns a negative number if connection is in-progress.
                        // So we should return `true` to indicate that connection was established, if it's positive.
                        return bytesSent > 0;
                    } finally {
                        iov.release();
                    }
                }
            }
        }
        return super.doConnect0(remoteAddress, localAddress);
    }
}
