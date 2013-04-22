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

import io.netty.buffer.Buf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelStateHandlerAdapter;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

/**
 * Base class for {@link Channel} implementations that are used in an embedded fashion.
 *
 * @param <O>  the type of data that can be written to this {@link Channel}
 */
public abstract class AbstractEmbeddedChannel<O> extends AbstractChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractEmbeddedChannel.class);

    private final EmbeddedEventLoop loop = new EmbeddedEventLoop();
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final SocketAddress localAddress = new EmbeddedSocketAddress();
    private final SocketAddress remoteAddress = new EmbeddedSocketAddress();
    private final MessageBuf<Object> lastInboundMessageBuffer = Unpooled.messageBuffer();
    private final ByteBuf lastInboundByteBuffer = Unpooled.buffer();
    protected final Object lastOutboundBuffer;
    private Throwable lastException;
    private int state; // 0 = OPEN, 1 = ACTIVE, 2 = CLOSED

    /**
     * Create a new instance
     *
     * @param lastOutboundBuffer    the last outbound buffer which will hold all the written data
     * @param handlers              the @link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    AbstractEmbeddedChannel(Object lastOutboundBuffer, ChannelHandler... handlers) {
        super(null, null);

        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        this.lastOutboundBuffer = lastOutboundBuffer;

        int nHandlers = 0;
        boolean hasBuffer = false;
        ChannelPipeline p = pipeline();
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            nHandlers ++;
            ChannelHandlerContext ctx = p.addLast(h).context(h);

            if (ctx.hasInboundByteBuffer() || ctx.hasOutboundByteBuffer()
                    || ctx.hasInboundMessageBuffer() || ctx.hasOutboundMessageBuffer()) {
                hasBuffer = true;
            }
        }

        if (nHandlers == 0) {
            throw new IllegalArgumentException("handlers is empty.");
        }

        if (!hasBuffer) {
            throw new IllegalArgumentException("handlers does not provide any buffers.");
        }

        p.addLast(new LastInboundMessageHandler(), new LastInboundByteHandler());
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

    /**
     * Return the last inbound {@link MessageBuf} which will hold all the {@link Object}s that where received
     * by this {@link Channel}
     */
    public MessageBuf<Object> lastInboundMessageBuffer() {
        return lastInboundMessageBuffer;
    }

    /**
     * Return the last inbound {@link ByteBuf} which will hold all the bytes that where received
     * by this {@link Channel}
     */
    public ByteBuf lastInboundByteBuffer() {
        return lastInboundByteBuffer;
    }

    /**
     * Return received data from this {@link Channel}
     */
    public Object readInbound() {
        if (lastInboundByteBuffer.isReadable()) {
            try {
                return lastInboundByteBuffer.readBytes(lastInboundByteBuffer.readableBytes());
            } finally {
                lastInboundByteBuffer.clear();
            }
        }
        return lastInboundMessageBuffer.poll();
    }

    /**
     * Run all tasks that are pending in the {@link EventLoop} for this {@link Channel}
     */
    public void runPendingTasks() {
        try {
            loop.runTasks();
        } catch (Exception e) {
            recordException(e);
        }
    }

    private void recordException(Throwable cause) {
        if (lastException == null) {
            lastException = cause;
        } else {
            logger.warn(
                    "More than one exception was raised. " +
                            "Will report only the first one and log others.", cause);
        }
    }

    /**
     * Check if there was any {@link Throwable} received and if so rethrow it.
     */
    public void checkException() {
        Throwable t = lastException;
        if (t == null) {
            return;
        }

        lastException = null;

        PlatformDependent.throwException(t);
    }

    protected final void ensureOpen() {
        if (!isOpen()) {
            recordException(new ClosedChannelException());
            checkException();
        }
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
    protected Runnable doDeregister() throws Exception {
        return null;
    }

    @Override
    protected void doBeginRead() throws Exception {
        // NOOP
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultUnsafe();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    /**
     * Read data froum the outbound. This may return {@code null} if nothing is readable.
     */
    public abstract O readOutbound();

    /**
     * Return the inbound buffer in which inbound messages are stored.
     */
    public abstract Buf inboundBuffer();

    /**
     * Return the last outbound buffer in which all the written outbound messages are stored.
     */
    public abstract Buf lastOutboundBuffer();

    /**
     * Mark this {@link Channel} as finished. Any futher try to write data to it will fail.
     *
     *
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    public boolean finish() {
        close();
        runPendingTasks();
        checkException();
        return lastInboundByteBuffer().isReadable() || !lastInboundMessageBuffer().isEmpty() ||
                hasReadableOutboundBuffer();
    }

    /**
     * Write data to the inbound of this {@link Channel}.
     *
     * @param data              data that should be written
     * @return bufferReadable   returns {@code true} if the write operation did add something to the the inbound buffer
     */
    public boolean writeInbound(O data) {
        ensureOpen();
        writeInbound0(data);
        pipeline().fireInboundBufferUpdated();
        runPendingTasks();
        checkException();
        return lastInboundByteBuffer().isReadable() || !lastInboundMessageBuffer().isEmpty();
    }

    /**
     * Write data to the outbound of this {@link Channel}.
     *
     * @param data              data that should be written
     * @return bufferReadable   returns {@code true} if the write operation did add something to the the outbound buffer
     */
    public boolean writeOutbound(Object data) {
        ensureOpen();
        ChannelFuture future = write(data);
        assert future.isDone();
        if (future.cause() != null) {
            recordException(future.cause());
        }
        runPendingTasks();
        checkException();
        return hasReadableOutboundBuffer();
    }

    /**
     * Returns {@code true} if the outbound buffer hold some data which can be read
     */
    protected abstract boolean hasReadableOutboundBuffer();

    /**
     * Add the data to the inbound buffer.
     */
    protected abstract void writeInbound0(O data);

    private class DefaultUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress,
                SocketAddress localAddress, ChannelPromise promise) {
            promise.setSuccess();
        }
    }

    private final class LastInboundMessageHandler extends ChannelStateHandlerAdapter
            implements ChannelInboundMessageHandler<Object> {
        @Override
        public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return lastInboundMessageBuffer;
        }

        @Override
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            // Do NOT free the buffer.
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            // Do nothing.
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            recordException(cause);
        }
    }

    private final class LastInboundByteHandler extends ChannelStateHandlerAdapter
            implements ChannelInboundByteHandler {
        @Override
        public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return lastInboundByteBuffer;
        }

        @Override
        public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
            // nothing
        }

        @Override
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            // Do NOT free the buffer.
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            // No nothing
        }
    }
}
