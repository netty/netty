/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.aio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations that use the new {@link AsynchronousChannel} which is part of
 * NIO.2.
 */
public abstract class AbstractAioChannel extends AbstractChannel {

    protected volatile AsynchronousChannel ch;

    /**
     * The future of the current connection attempt.  If not null, subsequent connection attempts will fail.
     */
    protected ChannelPromise connectPromise;
    protected ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    /**
     * Creates a new instance.
     *
     * @param parent the parent of this channel. {@code null} if there's no parent.
     * @param ch the {@link AsynchronousChannel} which will handle the IO or {@code null} if not created yet.
     */
    protected AbstractAioChannel(Channel parent, AsynchronousChannel ch) {
        super(parent);
        this.ch = ch;
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

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultAioUnsafe();
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract void doConnect(SocketAddress remoteAddress,
                                      SocketAddress localAddress, ChannelPromise connectPromise);

    protected final class DefaultAioUnsafe extends AbstractUnsafe {

        @Override
        public void connect(final SocketAddress remoteAddress,
                            final SocketAddress localAddress, final ChannelPromise promise) {

            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    throw new IllegalStateException("connection attempt already made");
                }
                connectPromise = promise;
                requestedRemoteAddress = remoteAddress;

                doConnect(remoteAddress, localAddress, promise);

                // Schedule connect timeout.
                int connectTimeoutMillis = config().getConnectTimeoutMillis();
                if (connectTimeoutMillis > 0) {
                    connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            ChannelPromise connectFuture = connectPromise;
                            ConnectTimeoutException cause =
                                    new ConnectTimeoutException("connection timed out: " + remoteAddress);
                            if (connectFuture != null && connectFuture.tryFailure(cause)) {
                                close(voidPromise());
                            }
                        }
                    }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                }
            } catch (Throwable t) {

                if (t instanceof ConnectException) {
                    Throwable newT = new ConnectException(t.getMessage() + ": " + remoteAddress);
                    newT.setStackTrace(t.getStackTrace());
                    t = newT;
                }
                promise.setFailure(t);
                closeIfClosed();
            }
        }

        public void connectFailed(Throwable t) {
            if (t instanceof ConnectException) {
                Throwable newT = new ConnectException(t.getMessage() + ": " + requestedRemoteAddress);
                newT.setStackTrace(t.getStackTrace());
                t = newT;
            }
            connectPromise.setFailure(t);
            closeIfClosed();
        }

        public void connectSuccess() {
            assert eventLoop().inEventLoop();
            assert connectPromise != null;
            try {
                connectPromise.setSuccess();
                pipeline().fireChannelActive();
            } catch (Throwable t) {
                connectPromise.setFailure(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectPromise = null;
            }
        }
    }
}
