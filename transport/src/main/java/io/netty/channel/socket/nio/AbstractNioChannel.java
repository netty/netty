/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public abstract class AbstractNioChannel extends AbstractChannel {

    private final SelectableChannel ch;

    private volatile InetSocketAddress localAddress;
    private volatile InetSocketAddress remoteAddress;

    private volatile SelectionKey selectionKey;

    protected AbstractNioChannel(Integer id, Channel parent, SelectableChannel ch) {
        super(id, parent);
        this.ch = ch;
    }

    protected AbstractNioChannel(Channel parent, SelectableChannel ch)  {
        super(parent);
        this.ch = ch;
    }

    @Override
    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    public InetSocketAddress localAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) unsafe().localAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    (InetSocketAddress) unsafe().remoteAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public abstract NioChannelConfig config();

    @Override
    protected void doRegister(ChannelFuture future) {
        if (!(eventLoop() instanceof SelectorEventLoop)) {
            throw new ChannelException("unsupported event loop: " + eventLoop().getClass().getName());
        }

        SelectorEventLoop loop = (SelectorEventLoop) eventLoop();
        try {
            selectionKey = javaChannel().register(loop.selector, javaChannel().validOps() & ~SelectionKey.OP_WRITE, this);
        } catch (Exception e) {
            throw new ChannelException("failed to register a channel", e);
        }
    }
}
