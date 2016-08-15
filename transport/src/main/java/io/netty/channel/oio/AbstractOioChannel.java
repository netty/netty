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
package io.netty.channel.oio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.ThreadPerChannelEventLoop;

import java.net.SocketAddress;

/**
 * Abstract base class for {@link Channel} implementations that use Old-Blocking-IO
 */
public abstract class AbstractOioChannel extends AbstractChannel {

    protected static final int SO_TIMEOUT = 1000;

    boolean readPending;
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            doRead();
        }
    };
    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            readPending = false;
        }
    };

    /**
     * @see AbstractChannel#AbstractChannel(Channel)
     */
    protected AbstractOioChannel(Channel parent) {
        super(parent);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultOioUnsafe();
    }

    private final class DefaultOioUnsafe extends AbstractUnsafe {
        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                boolean wasActive = isActive();
                doConnect(remoteAddress, localAddress);

                // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
                // We still need to ensure we call fireChannelActive() in this case.
                boolean active = isActive();

                safeSetSuccess(promise);
                if (!wasActive && active) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                safeSetFailure(promise, annotateConnectException(t, remoteAddress));
                closeIfClosed();
            }
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof ThreadPerChannelEventLoop;
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    @Override
    protected void doBeginRead() throws Exception {
        if (readPending) {
            return;
        }

        readPending = true;
        eventLoop().execute(readTask);
    }

    protected abstract void doRead();

    /**
     * @deprecated No longer supported.
     * No longer supported.
     */
    @Deprecated
    protected boolean isReadPending() {
        return readPending;
    }

    /**
     * @deprecated Use {@link #clearReadPending()} if appropriate instead.
     * No longer supported.
     */
    @Deprecated
    protected void setReadPending(final boolean readPending) {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                this.readPending = readPending;
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        AbstractOioChannel.this.readPending = readPending;
                    }
                });
            }
        } else {
            this.readPending = readPending;
        }
    }

    /**
     * Set read pending to {@code false}.
     */
    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                readPending = false;
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            // Best effort if we are not registered yet clear readPending. This happens during channel initialization.
            readPending = false;
        }
    }
}
