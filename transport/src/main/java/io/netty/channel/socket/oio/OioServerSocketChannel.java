/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.oio;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.oio.AbstractOioMessageChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link ServerSocketChannel} which accepts new connections and create the {@link OioSocketChannel}'s for them.
 * 采用oid的方式,作为服务器端,接受新连接,并且在服务器端创建一个OioSocketChannel引用
 * This implementation use Old-Blocking-IO.
 */
public class OioServerSocketChannel extends AbstractOioMessageChannel
                                    implements ServerSocketChannel {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(OioServerSocketChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static ServerSocket newServerSocket() {
        try {
            return new ServerSocket();
        } catch (IOException e) {
            throw new ChannelException("failed to create a server socket", e);
        }
    }

    final ServerSocket socket;//服务器端对象
    final Lock shutdownLock = new ReentrantLock();
    private final OioServerSocketChannelConfig config;

    /**
     * Create a new instance with an new {@link Socket}
     * 创建一个服务端实例对象
     */
    public OioServerSocketChannel() {
        this(newServerSocket());
    }

    /**
     * Create a new instance from the given {@link ServerSocket}
     *
     * @param socket    the {@link ServerSocket} which is used by this instance
     */
    public OioServerSocketChannel(ServerSocket socket) {
        super(null);
        if (socket == null) {
            throw new NullPointerException("socket");
        }

        boolean success = false;
        try {
            socket.setSoTimeout(SO_TIMEOUT);
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
        config = new DefaultOioServerSocketChannelConfig(this, socket);
    }

    //服务器本地ip
    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public OioServerSocketChannelConfig config() {
        return config;
    }

    //服务器本地是不需要远程服务的,因此返回null
    @Override
    public InetSocketAddress remoteAddress() {
        return null;
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
    protected SocketAddress localAddress0() {
        return SocketUtils.localSocketAddress(socket);
    }

    //服务器绑定ip和端口
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress, config.getBacklog());
    }

    //关闭服务器
    @Override
    protected void doClose() throws Exception {
        socket.close();
    }

    /**
     * 读取信息,将信息转换成对象,返回到参数的list中---返回后说明已经有一个连接成功的socket了,即该类接受的信息就是连接成功的socket
     * @param buf
     * @return
     * @throws Exception
     * 返回值  -1表示服务器已经关闭了    0表示接受socket失败  1表示接受socket成功
     */
    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        if (socket.isClosed()) {
            return -1;
        }

        try {
            Socket s = socket.accept();//阻塞接受一个连接
            try {
                buf.add(new OioSocketChannel(this, s));//为接受者创建一个socket客户端
                return 1;
            } catch (Throwable t) {
                logger.warn("Failed to create a new channel from an accepted socket.", t);
                try {
                    s.close();
                } catch (Throwable t2) {
                    logger.warn("Failed to close a socket.", t2);
                }
            }
        } catch (SocketTimeoutException e) {
            // Expected
        }
        return 0;
    }

    //服务器不需要参与写信息,写信息由服务器产生的socket来写
    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    //服务器不需要去连接
    @Override
    protected void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void setReadPending(boolean readPending) {
        super.setReadPending(readPending);
    }
}
