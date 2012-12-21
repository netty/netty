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
package io.netty.channel.socket.aio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations that use the new {@link AsynchronousChannel} which is part
 * of NIO.2.
 */
abstract class AbstractAioChannel extends AbstractChannel {

    protected volatile AsynchronousChannel ch;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    protected ChannelFuture connectFuture;
    protected ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    /**
     * Creates a new instance.
     *
     * @param id
     *        the unique non-negative integer ID of this channel.
     *        Specify {@code null} to auto-generate a unique negative integer
     *        ID.
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     * @param ch
     *        the {@link AsynchronousChannel} which will handle the IO or {@code null} if not created yet.
     */
    protected AbstractAioChannel(Channel parent, Integer id, AsynchronousChannel ch) {
        super(parent, id);
        this.ch = ch;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    /**
     * Return the underlying {@link AsynchronousChannel}. Be aware this should only be called after it was set as
     * otherwise it will throw an {@link IllegalStateException}.
     */
    protected AsynchronousChannel javaChannel() {
        if (ch == null) {
            throw new IllegalStateException("Try to access Channel before eventLoop was registered");
        }
        return ch;
    }

    @Override
    public boolean isOpen() {
        if (ch == null) {
            return true;
        }
        return ch.isOpen();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof AioEventLoop;
    }

    protected abstract class AbstractAioUnsafe extends AbstractUnsafe {

        @Override
        public void connect(final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    if (connectFuture != null) {
                        throw new IllegalStateException("connection attempt already made");
                    }
                    connectFuture = future;

                    doConnect(remoteAddress, localAddress, future);

                    // Schedule connect timeout.
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (connectTimeoutException == null) {
                                    connectTimeoutException = new ConnectException("connection timed out");
                                }
                                ChannelFuture connectFuture = AbstractAioChannel.this.connectFuture;
                                if (connectFuture != null &&
                                        connectFuture.setFailure(connectTimeoutException)) {
                                    pipeline().fireExceptionCaught(connectTimeoutException);
                                    close(voidFuture());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        protected final void connectFailed(Throwable t) {
            connectFuture.setFailure(t);
            pipeline().fireExceptionCaught(t);
            closeIfClosed();
        }

        protected final void connectSuccess() {
            assert eventLoop().inEventLoop();
            assert connectFuture != null;
            try {
                boolean wasActive = isActive();
                connectFuture.setSuccess();
                if (!wasActive && isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                connectFuture.setFailure(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectFuture = null;
            }
        }
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelFuture connectFuture);

}
