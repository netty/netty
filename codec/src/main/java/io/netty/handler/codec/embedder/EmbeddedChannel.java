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
package io.netty.handler.codec.embedder;

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelType;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;

import java.net.SocketAddress;
import java.util.Queue;

class EmbeddedChannel extends AbstractChannel {

    private final ChannelConfig config = new DefaultChannelConfig();
    private final SocketAddress localAddress = new EmbeddedSocketAddress();
    private final SocketAddress remoteAddress = new EmbeddedSocketAddress();
    private final Queue<Object> productQueue;
    private int state; // 0 = OPEN, 1 = ACTIVE, 2 = CLOSED

    EmbeddedChannel(Queue<Object> productQueue) {
        super(null, null, ChannelBufferHolders.messageBuffer());
        this.productQueue = productQueue;
    }

    @Override
    public ChannelType type() {
        return ChannelType.MESSAGE;
    }

    @Override
    public ChannelConfig config() {
        return config;
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
        return loop instanceof EmbeddedEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return isActive()? localAddress : null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return isActive()? remoteAddress : null;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        state = 1;
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        // NOOP
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        state = 2;
    }

    @Override
    protected void doDeregister() throws Exception {
        // NOOP
    }

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        Queue<Object> msgBuf = buf.messageBuffer();
        if (!msgBuf.isEmpty()) {
            productQueue.addAll(msgBuf);
            msgBuf.clear();
        }
    }

    @Override
    protected Unsafe newUnsafe() {
        return new DefaultUnsafe();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    private class DefaultUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress,
                SocketAddress localAddress, ChannelFuture future) {
            future.setSuccess();
        }
    }
}
