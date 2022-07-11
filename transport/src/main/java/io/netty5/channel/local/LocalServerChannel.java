/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.local;

import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.AbstractServerChannel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.ServerChannelRecvBufferAllocator;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A {@link ServerChannel} for the local transport which allows in VM communication.
 */
public class LocalServerChannel extends AbstractServerChannel<LocalChannel, LocalAddress, LocalAddress>
        implements LocalChannelUnsafe {

    private final Queue<Object> inboundBuffer = new ArrayDeque<>();
    private volatile int state; // 0 - open, 1 - active, 2 - closed
    private volatile LocalAddress localAddress;
    private volatile boolean acceptInProgress;

    public LocalServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, childEventLoopGroup, LocalChannel.class);
        setOption(ChannelOption.BUFFER_ALLOCATOR, DefaultBufferAllocators.onHeapAllocator());
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
    protected LocalAddress localAddress0() {
        return localAddress;
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
    protected void doBeginRead() throws Exception {
        if (acceptInProgress) {
            return;
        }

        Queue<Object> inboundBuffer = this.inboundBuffer;
        if (inboundBuffer.isEmpty()) {
            acceptInProgress = true;
            return;
        }

        readInbound();
    }

    LocalChannel serve(final LocalChannel peer) {
        final LocalChannel child = newLocalChannel(peer);
        if (executor().inEventLoop()) {
            serve0(child);
        } else {
            executor().execute(() -> serve0(child));
        }
        return child;
    }

    private void readInbound() {
        RecvBufferAllocator.Handle handle = recvBufAllocHandle();
        handle.reset();
        ChannelPipeline pipeline = pipeline();
        do {
            Object m = inboundBuffer.poll();
            if (m == null) {
                break;
            }
            pipeline.fireChannelRead(m);
        } while (handle.continueReading(isAutoRead()) && !isShutdown(ChannelShutdownDirection.Inbound));

        pipeline.fireChannelReadComplete();
        readIfIsAutoRead();
    }

    /**
     * A factory method for {@link LocalChannel}s. Users may override it
     * to create custom instances of {@link LocalChannel}s.
     */
    protected LocalChannel newLocalChannel(LocalChannel peer) {
        return new LocalChannel(this, childEventLoopGroup().next(), peer);
    }

    private void serve0(final LocalChannel child) {
        inboundBuffer.add(child);
        if (acceptInProgress) {
            acceptInProgress = false;

            readInbound();
        }
    }

    @Override
    public void registerTransportNow() {
    }

    @Override
    public void deregisterTransportNow() {
    }

    @Override
    public void closeTransportNow() {
        closeTransport(newPromise());
    }
}
