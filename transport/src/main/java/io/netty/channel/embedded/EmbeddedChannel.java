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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.RecyclableArrayList;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Base class for {@link Channel} implementations that are used in an embedded fashion.
 */
public class EmbeddedChannel extends AbstractChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EmbeddedChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final EmbeddedEventLoop loop = new EmbeddedEventLoop();
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final SocketAddress localAddress = new EmbeddedSocketAddress();
    private final SocketAddress remoteAddress = new EmbeddedSocketAddress();
    private final Queue<Object> inboundMessages = new ArrayDeque<Object>();
    private final Queue<Object> outboundMessages = new ArrayDeque<Object>();
    private Throwable lastException;
    private int state; // 0 = OPEN, 1 = ACTIVE, 2 = CLOSED

    /**
     * Create a new instance
     *
     * @param handlers the @link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelHandler... handlers) {
        super(null);

        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        int nHandlers = 0;
        ChannelPipeline p = pipeline();
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            nHandlers ++;
            p.addLast(h);
        }

        if (nHandlers == 0) {
            throw new IllegalArgumentException("handlers is empty.");
        }

        loop.register(this);
        p.addLast(new LastInboundHandler());
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
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
     * Returns the {@link Queue} which holds all the {@link Object}s that were received by this {@link Channel}.
     */
    public Queue<Object> inboundMessages() {
        return inboundMessages;
    }

    /**
     * @deprecated use {@link #inboundMessages()}
     */
    @Deprecated
    public Queue<Object> lastInboundBuffer() {
        return inboundMessages();
    }

    /**
     * Returns the {@link Queue} which holds all the {@link Object}s that were written by this {@link Channel}.
     */
    public Queue<Object> outboundMessages() {
        return outboundMessages;
    }

    /**
     * @deprecated use {@link #outboundMessages()}
     */
    @Deprecated
    public Queue<Object> lastOutboundBuffer() {
        return outboundMessages();
    }

    /**
     * Return received data from this {@link Channel}
     */
    public Object readInbound() {
        return inboundMessages.poll();
    }

    /**
     * Read data froum the outbound. This may return {@code null} if nothing is readable.
     */
    public Object readOutbound() {
        return outboundMessages.poll();
    }

    /**
     * Write messages to the inbound of this {@link Channel}.
     *
     * @param msgs the messages to be written
     *
     * @return {@code true} if the write operation did add something to the inbound buffer
     */
    public boolean writeInbound(Object... msgs) {
        ensureOpen();
        if (msgs.length == 0) {
            return !inboundMessages.isEmpty();
        }

        ChannelPipeline p = pipeline();
        for (Object m: msgs) {
            p.fireChannelRead(m);
        }
        p.fireChannelReadComplete();
        runPendingTasks();
        checkException();
        return !inboundMessages.isEmpty();
    }

    /**
     * Write messages to the outbound of this {@link Channel}.
     *
     * @param msgs              the messages to be written
     * @return bufferReadable   returns {@code true} if the write operation did add something to the outbound buffer
     */
    public boolean writeOutbound(Object... msgs) {
        ensureOpen();
        if (msgs.length == 0) {
            return !outboundMessages.isEmpty();
        }

        RecyclableArrayList futures = RecyclableArrayList.newInstance(msgs.length);
        try {
            for (Object m: msgs) {
                if (m == null) {
                    break;
                }
                futures.add(write(m));
            }

            flush();

            int size = futures.size();
            for (int i = 0; i < size; i++) {
                ChannelFuture future = (ChannelFuture) futures.get(i);
                assert future.isDone();
                if (future.cause() != null) {
                    recordException(future.cause());
                }
            }

            runPendingTasks();
            checkException();
            return !outboundMessages.isEmpty();
        } finally {
            futures.recycle();
        }
    }

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
        return !inboundMessages.isEmpty() || !outboundMessages.isEmpty();
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

    /**
     * Ensure the {@link Channel} is open and of not throw an exception.
     */
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
    protected void doRegister() throws Exception {
        state = 1;
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
    protected void doBeginRead() throws Exception {
        // NOOP
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DefaultUnsafe();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            ReferenceCountUtil.retain(msg);
            outboundMessages.add(msg);
            in.remove();
        }
    }

    private class DefaultUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            safeSetSuccess(promise);
        }
    }

    private final class LastInboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            inboundMessages.add(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            recordException(cause);
        }
    }
}
