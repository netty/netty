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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.unix.FileDescriptor;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class IOUringSocketChannel extends AbstractIOUringChannel implements SocketChannel {
    private final IOUringSocketChannelConfig config;

    IOUringSocketChannel(final Channel parent, final LinuxSocket fd) {
        super(parent, fd);
        this.config = new IOUringSocketChannelConfig(this);
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected AbstractUringUnsafe newUnsafe() {
        return new AbstractUringUnsafe() {

            @Override
            public void uringEventExecution() {
                final ChannelConfig config = config();

                final ByteBufAllocator allocator = config.getAllocator();
                final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
                allocHandle.reset(config);

                ByteBuf byteBuf = allocHandle.allocate(allocator);
                doReadBytes(byteBuf);
            }
        };
    }

    @Override
    public IOUringSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isInputShutdown() {
        return false;
    }

    @Override
    public ChannelFuture shutdownInput() {
        return null;
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise promise) {
        return null;
    }

    @Override
    public boolean isOutputShutdown() {
        return false;
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return null;
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        return null;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public ChannelFuture shutdown() {
        return null;
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        return null;
    }

    @Override
    public FileDescriptor fd() {
        return super.fd();
    }

    @Override
    protected SocketAddress localAddress0() {
        return super.localAddress0();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return super.remoteAddress0();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }
}
