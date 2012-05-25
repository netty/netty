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
package io.netty.channel.socket.oio;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.Channel;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OioServerSocketChannel extends AbstractServerChannel
                                    implements ServerSocketChannel {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(OioServerSocketChannel.class);

    private static ServerSocket newServerSocket() {
        try {
            return new ServerSocket();
        } catch (IOException e) {
            throw new ChannelException("failed to create a server socket", e);
        }
    }

    final ServerSocket socket;
    final Lock shutdownLock = new ReentrantLock();
    private final ServerSocketChannelConfig config;

    public OioServerSocketChannel() {
        this(newServerSocket());
    }

    public OioServerSocketChannel(ServerSocket socket) {
        this(null, socket);
    }

    public OioServerSocketChannel(Integer id, ServerSocket socket) {
        super(id);
        if (socket == null) {
            throw new NullPointerException("socket");
        }

        boolean success = false;
        try {
            socket.setSoTimeout(1000);
            success = true;
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to set the server socket timeout.", e);
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (IOException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Failed to close a partially initialized socket.", e);
                    }
                }
            }
        }

        this.socket = socket;
        config = new DefaultServerSocketChannelConfig(socket);
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public boolean isActive() {
        return isOpen() && socket.isBound();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof OioChildEventLoop;
    }

    @Override
    protected Channel javaChannel() {
        return null;
    }

    @Override
    protected SocketAddress localAddress0() {
        return socket.getLocalSocketAddress();
    }

    @Override
    protected void doRegister() throws Exception {
        // NOOP
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
    }

    @Override
    protected void doClose() throws Exception {
        socket.close();
    }

    @Override
    protected void doDeregister() throws Exception {
        // NOOP
    }

    @Override
    protected int doReadMessages(Queue<Object> buf) throws Exception {
        if (socket.isClosed()) {
            return -1;
        }

        Socket s = null;
        try {
            s = socket.accept();
            if (s != null) {
                buf.add(new OioSocketChannel(this, null, s));
                return 1;
            }
        } catch (SocketTimeoutException e) {
            // Expected
        } catch (Throwable t) {
            logger.warn("Failed to create a new channel from an accepted socket.", t);
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a socket.", t2);
                }
            }
        }

        return 0;
    }
}
