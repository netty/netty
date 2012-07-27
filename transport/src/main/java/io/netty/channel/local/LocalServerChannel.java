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
package io.netty.channel.local;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadEventExecutor;
import io.netty.channel.SingleThreadEventLoop;

import java.net.SocketAddress;

/**
 * A {@link ServerChannel} for the local transport.
 */
public class LocalServerChannel extends AbstractServerChannel {

    private final ChannelConfig config = new DefaultChannelConfig();
    private final Runnable shutdownHook = new Runnable() {
        @Override
        public void run() {
            unsafe().close(unsafe().voidFuture());
        }
    };

    private volatile int state; // 0 - open, 1 - active, 2 - closed
    private volatile LocalAddress localAddress;

    public LocalServerChannel() {
        this(null);
    }

    public LocalServerChannel(Integer id) {
        super(id);
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public LocalAddress localAddress() {
        return (LocalAddress) super.localAddress();
    }

    @Override
    public LocalAddress remoteAddress() {
        return (LocalAddress) super.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return state < 2;
    }

    @Override
    public boolean isActive() {
        return state == 1;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        ((SingleThreadEventExecutor) eventLoop()).addShutdownHook(shutdownHook);
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress = LocalChannelRegistry.register(this, this.localAddress, localAddress);
        state = 1;
    }

    @Override
    protected void doPreClose() throws Exception {
        if (state > 1) {
            // Closed already.
            return;
        }

        // Update all internal state before the closeFuture is notified.
        LocalChannelRegistry.unregister(localAddress);
        localAddress = null;
        state = 2;
    }

    @Override
    protected void doClose() throws Exception {
        // All internal state was updated already at doPreClose().
    }

    @Override
    protected void doDeregister() throws Exception {
        ((SingleThreadEventExecutor) eventLoop()).removeShutdownHook(shutdownHook);
    }

    LocalChannel serve(final LocalChannel peer) {
        LocalChannel child = new LocalChannel(this, peer);
        serve0(child);
        return child;
    }

    private void serve0(final LocalChannel child) {
        if (eventLoop().inEventLoop()) {
            pipeline().inboundMessageBuffer().add(child);
            pipeline().fireInboundBufferUpdated();
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    serve0(child);
                }
            });
        }
    }

    @Override
    protected Unsafe newUnsafe() {
        return new AbstractServerChannel.DefaultServerUnsafe() {

            @Override
            public void suspendRead() {
                // TODO: Implement me
            }

            @Override
            public void resumeRead() {
                // TODO: Implement me
            }
        };

    }
}
