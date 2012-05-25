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

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.Queue;

public class NioServerSocketChannel extends AbstractServerChannel
                             implements io.netty.channel.socket.ServerSocketChannel {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioServerSocketChannel.class);

    private final ServerSocketChannel socket;
    private final ServerSocketChannelConfig config;
    private volatile SelectionKey selectionKey;

    public NioServerSocketChannel() {
        super(null);

        try {
            socket = ServerSocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }

        try {
            socket.configureBlocking(false);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }

        config = new DefaultServerSocketChannelConfig(socket.socket());
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return javaChannel().socket().isBound();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected java.nio.channels.ServerSocketChannel javaChannel() {
        return socket;
    }

    @Override
    protected SocketAddress localAddress0() {
        return socket.socket().getLocalSocketAddress();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadSelectorEventLoop;
    }

    @Override
    protected void doRegister() throws Exception {
        SingleThreadSelectorEventLoop loop = (SingleThreadSelectorEventLoop) eventLoop();
        selectionKey = javaChannel().register(
                loop.selector, isActive()? SelectionKey.OP_ACCEPT : 0, this);
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().socket().bind(localAddress);
        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_ACCEPT);
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected void doDeregister() throws Exception {
        selectionKey.cancel();
    }

    @Override
    protected int doReadMessages(Queue<Object> buf) throws Exception {
        java.nio.channels.SocketChannel ch = javaChannel().accept();
        if (ch == null) {
            return 0;
        }
        buf.add(new NioSocketChannel(this, null, ch));
        return 1;
    }
}
