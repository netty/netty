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
package io.netty.channel.embedded;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

public abstract class AbstractEmbeddedChannel extends AbstractChannel {

    private final EmbeddedEventLoop loop = new EmbeddedEventLoop();
    private final ChannelConfig config = new DefaultChannelConfig();
    private final SocketAddress localAddress = new EmbeddedSocketAddress();
    private final SocketAddress remoteAddress = new EmbeddedSocketAddress();
    private final Queue<Object> lastInboundMessageBuffer = new ArrayDeque<Object>();
    private final ChannelBuffer lastInboundByteBuffer = ChannelBuffers.dynamicBuffer();
    private Throwable lastException;
    private int state; // 0 = OPEN, 1 = ACTIVE, 2 = CLOSED

    AbstractEmbeddedChannel(ChannelBufferHolder<?> outboundBuffer, ChannelHandler... handlers) {
        super(null, null, outboundBuffer);

        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        int nHandlers = 0;
        boolean hasBuffer = false;
        ChannelPipeline p = pipeline();
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            nHandlers ++;
            p.addLast(h);
            if (h instanceof ChannelInboundHandler || h instanceof ChannelOutboundHandler) {
                hasBuffer = true;
            }
        }

        if (nHandlers == 0) {
            throw new IllegalArgumentException("handlers is empty.");
        }

        if (!hasBuffer) {
            throw new IllegalArgumentException("handlers does not provide any buffers.");
        }

        p.addLast(new LastInboundMessageHandler(), new LastInboundStreamHandler());
        loop.register(this);
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

    public Queue<Object> lastInboundMessageBuffer() {
        return lastInboundMessageBuffer;
    }

    public ChannelBuffer lastInboundByteBuffer() {
        return lastInboundByteBuffer;
    }

    public Object readInbound() {
        if (lastInboundByteBuffer.readable()) {
            try {
                return lastInboundByteBuffer.readBytes(lastInboundByteBuffer.readableBytes());
            } finally {
                lastInboundByteBuffer.clear();
            }
        }
        return lastInboundMessageBuffer.poll();
    }

    public void checkException() {
        Throwable t = lastException;
        if (t == null) {
            return;
        }

        lastException = null;

        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }

        throw new ChannelException(t);
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

    private final class LastInboundMessageHandler extends ChannelInboundHandlerAdapter<Object> {
        @Override
        public ChannelBufferHolder<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ChannelBufferHolders.messageBuffer(lastInboundMessageBuffer);
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            // Do nothing.
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            lastException = cause;
        }
    }

    private final class LastInboundStreamHandler extends ChannelInboundHandlerAdapter<Byte> {
        @Override
        public ChannelBufferHolder<Byte> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ChannelBufferHolders.byteBuffer(lastInboundByteBuffer);
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            // No nothing
        }
    }
}
