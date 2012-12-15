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
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

abstract class AbstractOioChannel extends AbstractChannel {

    static final int SO_TIMEOUT = 1000;

    protected volatile boolean readSuspended;

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
    public OioUnsafe unsafe() {
        return (OioUnsafe) super.unsafe();
    }

    public interface OioUnsafe extends Unsafe {
        void read();
    }

    abstract class AbstractOioUnsafe extends AbstractUnsafe implements OioUnsafe {
        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();
                    doConnect(remoteAddress, localAddress);
                    future.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline().fireChannelActive();
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

        @Override
        public void suspendRead() {
            readSuspended = true;
        }

        @Override
        public void resumeRead() {
            readSuspended = false;
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof OioEventLoop;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        // NOOP
        return null;
    }

    @Override
    protected void doDeregister() throws Exception {
        // NOOP
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    protected abstract void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;
}
