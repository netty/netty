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

    private volatile boolean readPending;

    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            if (!isReadPending() && !config().isAutoRead()) {
                // ChannelConfig.setAutoRead(false) was called in the meantime so just return
                return;
            }

            setReadPending(false);
            doRead();
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
        if (isReadPending()) {
            return;
        }

        setReadPending(true);
        eventLoop().execute(readTask);
    }

    protected abstract void doRead();

    protected boolean isReadPending() {
        return readPending;
    }

    protected void setReadPending(boolean readPending) {
        this.readPending = readPending;
    }
}
