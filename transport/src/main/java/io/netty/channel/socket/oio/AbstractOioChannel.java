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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Abstract base class for {@link Channel} implementations that use Old-Blocking-IO
 */
public abstract class AbstractOioChannel extends AbstractChannel {

    protected static final int SO_TIMEOUT = 1000;

    private boolean readInProgress;

    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            readInProgress = false;
            doRead();
        }
    };

    /**
     * @see AbstractChannel#AbstractChannel(Channel, Integer)
     */
    protected AbstractOioChannel(Channel parent, Integer id) {
        super(parent, id);
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
    protected AbstractUnsafe newUnsafe() {
        return new DefaultOioUnsafe();
    }

    private final class DefaultOioUnsafe extends AbstractUnsafe {
        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelPromise promise) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(promise)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();
                    doConnect(remoteAddress, localAddress);
                    promise.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline().fireChannelActive();
                    }
                } catch (Throwable t) {
                    promise.setFailure(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, promise);
                    }
                });
            }
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof OioEventLoop;
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    /**
     * Connect to the remote peer using the given localAddress if one is specified or {@code null} otherwise.
     */
    protected abstract void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    @Override
    protected void doBeginRead() throws Exception {
        if (readInProgress) {
            return;
        }

        readInProgress = true;
        eventLoop().execute(readTask);
    }

    protected abstract void doRead();
}
