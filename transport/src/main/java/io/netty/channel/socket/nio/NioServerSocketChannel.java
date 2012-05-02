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
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

final class NioServerSocketChannel extends AbstractServerChannel
                             implements io.netty.channel.socket.ServerSocketChannel {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioServerSocketChannel.class);

    private final ServerSocketChannel socket;
    private final ServerSocketChannelConfig config;
    private volatile InetSocketAddress localAddress;
    private volatile SelectionKey selectionKey;

    public NioServerSocketChannel() {
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
        // TODO Auto-generated method stub
        return false;
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
    protected void doRegister(ChannelFuture future) {
        if (!(eventLoop() instanceof SelectorEventLoop)) {
            throw new ChannelException("unsupported event loop: " + eventLoop().getClass().getName());
        }

        SelectorEventLoop loop = (SelectorEventLoop) eventLoop();
        try {
            selectionKey = javaChannel().register(loop.selector, javaChannel().validOps(), this);
        } catch (Exception e) {
            throw new ChannelException("failed to register a channel", e);
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelFuture future) {
        try {
            javaChannel().socket().bind(localAddress);
            future.setSuccess();
            pipeline().fireChannelActive();
        } catch (Exception e) {
            future.setFailure(e);
        }
    }

    @Override
    protected void doClose(ChannelFuture future) {
        try {
            javaChannel().close();
        } catch (Exception e) {
            logger.warn("Failed to close a channel.", e);
        }

        future.setSuccess();
        pipeline().fireChannelInactive();

        if (isRegistered()) {
            deregister(null);
        }
    }

    @Override
    protected void doDeregister(ChannelFuture future) {
        try {
            selectionKey.cancel();
            future.setSuccess();
            pipeline().fireChannelUnregistered();
        } catch (Exception e) {
            future.setFailure(e);
        }
    }

    @Override
    protected int doRead() {
        int acceptedConns = 0;
        for (;;) {
            try {
                java.nio.channels.SocketChannel ch = javaChannel().accept();
                if (ch == null) {
                    break;
                }
                pipeline().nextIn().messageBuffer().add(new NioSocketChannel(this, ch));
            } catch (ChannelException e) {
                pipeline().fireExceptionCaught(e);
            } catch (Exception e) {
                pipeline().fireExceptionCaught(new ChannelException("failed to accept a connection", e));
            }
        }
        return acceptedConns;
    }
}
