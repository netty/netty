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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.channel.oio.OioByteStreamChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.hander.codec.socks.SocksClientHandler;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksInitRequest;
import io.netty.handler.codec.socks.SocksInitResponseDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SocketChannel} which is using Old-Blocking-IO
 */
public class OioSocketChannel extends OioByteStreamChannel
        implements SocketChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSocketChannel.class);

    private final Socket socket;
    private final OioSocketChannelConfig config;
    private SocketAddress remoteAddress;

    /**
     * Create a new instance with an new {@link Socket}
     */
    public OioSocketChannel() {
        this(new Socket());
    }

    /**
     * Create a new instance from the given {@link Socket}
     *
     * @param socket the {@link Socket} which is used by this instance
     */
    public OioSocketChannel(Socket socket) {
        this(null, null, socket);
    }

    /**
     * Create a new instance from the given {@link Socket}
     *
     * @param parent the parent {@link Channel} which was used to create this instance. This can be null if the
     *               {@link} has no parent as it was created by your self.
     * @param id     the id which should be used for this instance or {@code null} if a new one should be generated
     * @param socket the {@link Socket} which is used by this instance
     */
    public OioSocketChannel(Channel parent, Integer id, Socket socket) {
        super(parent, id);
        this.socket = socket;
        config = new DefaultOioSocketChannelConfig(this, socket);

        boolean success = false;
        try {
            if (socket.isConnected()) {
                activate(socket.getInputStream(), socket.getOutputStream());
            }
            socket.setSoTimeout(SO_TIMEOUT);
            success = true;
        } catch (Exception e) {
            throw new ChannelException("failed to initialize a socket", e);
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a socket.", e);
                }
            }
        }
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public OioSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public boolean isActive() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public boolean isInputShutdown() {
        return super.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return socket.isOutputShutdown() || !isActive();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    protected int doReadBytes(ByteBuf buf) throws Exception {
        if (socket.isClosed()) {
            return -1;
        }
        try {
            return super.doReadBytes(buf);
        } catch (SocketTimeoutException e) {
            return 0;
        }
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise future) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            try {
                socket.shutdownOutput();
                future.setSuccess();
            } catch (Throwable t) {
                future.setFailure(t);
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput(future);
                }
            });
        }
        return future;
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
    protected SocketAddress localAddress0() {
        return socket.getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        if (config().proxy() != Proxy.NO_PROXY) {
            return this.remoteAddress;
        } else {
            return socket.getRemoteSocketAddress();
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress,
                             SocketAddress localAddress, ChannelPromise promise) throws Exception {
        if (localAddress != null) {
            socket.bind(localAddress);
        }
        switch (config().proxy().type()) {
            case DIRECT:
                doConnectDirect(remoteAddress, localAddress, promise);
                break;
            case HTTP:
                doConnectHttp(remoteAddress, localAddress, promise);
                break;
            case SOCKS:
                doConnectSocks(remoteAddress, localAddress, promise);
                break;
        }
    }

    private void doConnectDirect(SocketAddress remoteAddress,
                                 SocketAddress localAddress, ChannelPromise promise) throws Exception {
        boolean success = false;
        try {
            socket.connect(remoteAddress, config().getConnectTimeoutMillis());
            activate(socket.getInputStream(), socket.getOutputStream());
            success = true;
        } catch (SocketTimeoutException e) {
            ConnectTimeoutException cause = new ConnectTimeoutException("connection timed out: " + remoteAddress);
            cause.setStackTrace(e.getStackTrace());
            throw cause;
        } finally {
            if (!success) {
                doClose();
            } else {
                promise.setSuccess();
            }
        }
    }

    private void doConnectSocks(SocketAddress remoteAddress,
                                SocketAddress localAddress, final ChannelPromise promise) throws Exception {
        this.remoteAddress = remoteAddress;
        try {
            logger.debug("SOCKS proxy selected");
            socket.connect(config().proxy().address(), config().getConnectTimeoutMillis());
            ChannelPromise localPromise = newPromise();
            activate(socket.getInputStream(), socket.getOutputStream());
            localPromise.addListener(new GenericFutureListener<Future<Void>>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (future.isSuccess()) {
                        logger.debug("Socks proxy CONNECT method succeded!");
                        promise.setSuccess();
                    } else {
                        logger.debug("Socks proxy CONNECT method failed! Closing connection.");
                        doClose();
                    }
                }
            });
            pipeline().addLast(SocksInitResponseDecoder.getName(), new SocksInitResponseDecoder());
            pipeline().addLast(SocksMessageEncoder.getName(), new SocksMessageEncoder());
            pipeline().addLast(SocksClientHandler.getName(),
                    new SocksClientHandler(
                            (InetSocketAddress) remoteAddress,
                            config().proxyPasswordAuthentication(),
                            localPromise));
            List<SocksAuthScheme> authSchemes = new ArrayList<SocksAuthScheme>();
            authSchemes.add(SocksAuthScheme.NO_AUTH);
            if (config().proxyPasswordAuthentication() != null) {
                authSchemes.add(SocksAuthScheme.AUTH_PASSWORD);
            }
            pipeline().write(new SocksInitRequest(authSchemes));
        } catch (SocketTimeoutException e) {
            ConnectTimeoutException cause =
                    new ConnectTimeoutException("connection timed out: " + config().proxy().address());
            cause.setStackTrace(e.getStackTrace());
            throw cause;
        } finally {
            //NOOP
            logger.debug("reached finally block");
        }
    }

    private void doConnectHttp(SocketAddress remoteAddress,
                               SocketAddress localAddress, ChannelPromise promise) throws Exception {
        this.remoteAddress = remoteAddress;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        socket.close();
    }

    @Override
    protected boolean checkInputShutdown() {
        if (isInputShutdown()) {
            try {
                Thread.sleep(config().getSoTimeout());
            } catch (Throwable e) {
                // ignore
            }
            return true;
        }
        return false;
    }
}
