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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.concurrent.SingleThreadEventExecutor;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A {@link ServerChannel} for the local transport which allows in VM communication.
 */
public class LocalServerChannel extends AbstractServerChannel {

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final Queue<Object> inboundBuffer = new ArrayDeque<Object>();
    private final Runnable shutdownHook = new Runnable() {
        @Override
        public void run() {
            unsafe().close(unsafe().voidPromise());
        }
    };

    private volatile int state; // 0 - open, 1 - active, 2 - closed
    private volatile LocalAddress localAddress;
    private volatile boolean acceptInProgress;

    public LocalServerChannel() {
        config().setAllocator(new PreferHeapByteBufAllocator(config.getAllocator()));
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
    protected void doRegister() throws Exception {
        ((SingleThreadEventExecutor) eventLoop()).addShutdownHook(shutdownHook);
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress = LocalChannelRegistry.register(this, this.localAddress, localAddress);
        state = 1;
    }

    @Override
    protected void doClose() throws Exception {
        if (state <= 1) {
            // Update all internal state before the closeFuture is notified.
            if (localAddress != null) {
                LocalChannelRegistry.unregister(localAddress);
                localAddress = null;
            }
            state = 2;
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        ((SingleThreadEventExecutor) eventLoop()).removeShutdownHook(shutdownHook);
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (acceptInProgress) {
            return;
        }

        Queue<Object> inboundBuffer = this.inboundBuffer;
        if (inboundBuffer.isEmpty()) {
            acceptInProgress = true;
            return;
        }

        ChannelPipeline pipeline = pipeline();
        for (;;) {
            Object m = inboundBuffer.poll();
            if (m == null) {
                break;
            }
            pipeline.fireChannelRead(m);
        }
        pipeline.fireChannelReadComplete();
    }

    LocalChannel serve(final LocalChannel peer) {
        final LocalChannel child = new LocalChannel(this, peer);
        if (eventLoop().inEventLoop()) {
            serve0(child);
        } else {
            eventLoop().execute(new Runnable() {
              @Override
              public void run() {
                serve0(child);
              }
            });
        }
        return child;
    }

    private void serve0(final LocalChannel child) {
        inboundBuffer.add(child);
        if (acceptInProgress) {
            acceptInProgress = false;
            ChannelPipeline pipeline = pipeline();
            for (;;) {
                Object m = inboundBuffer.poll();
                if (m == null) {
                    break;
                }
                pipeline.fireChannelRead(m);
            }
            pipeline.fireChannelReadComplete();
        }
    }
}
