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
package io.netty.channel.socket.nio2;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractAsyncChannel extends AbstractChannel {

    protected volatile AsynchronousChannel ch;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    protected ChannelFuture connectFuture;
    protected ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    protected AbstractAsyncChannel(Channel parent, Integer id) {
        super(parent, id);
    }


    @Override
    public InetSocketAddress localAddress() {
        if (ch == null) {
            return null;
        }
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        if (ch == null) {
            return null;
        }
        return (InetSocketAddress) super.remoteAddress();
    }

    protected AsynchronousChannel javaChannel() {
        return ch;
    }

    @Override
    public ChannelConfig config() {
        // TODO: Fix me
        return null;
    }

    @Override
    public boolean isOpen() {
        return ch == null || ch.isOpen();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        // TODO: Fix me
        return true;
    }


    @Override
    protected void doDeregister() throws Exception {
        throw new UnsupportedOperationException("Deregistration is not supported by AbstractAsyncChannel");
    }

    @Override
    protected AsyncUnsafe newUnsafe() {
        return new AsyncUnsafe();
    }

    protected class AsyncUnsafe extends AbstractUnsafe {

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
                                ChannelFuture connectFuture = AbstractAsyncChannel.this.connectFuture;
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
                    pipeline().fireExceptionCaught(t);
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
                pipeline().fireExceptionCaught(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectFuture = null;
            }
        }
    }
    protected abstract void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelFuture connectFuture);

}
