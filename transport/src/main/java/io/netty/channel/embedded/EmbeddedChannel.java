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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
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

    private static final ChannelMetadata METADATA_NO_DISCONNECT = new ChannelMetadata(false);
    private static final ChannelMetadata METADATA_DISCONNECT = new ChannelMetadata(true);

    private final EmbeddedEventLoop loop = new EmbeddedEventLoop();
    private final ChannelFutureListener recordExceptionListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            recordException(future);
        }
    };

    private final ChannelMetadata metadata;
    private final ChannelConfig config;
    private final SocketAddress localAddress = new EmbeddedSocketAddress();
    private final SocketAddress remoteAddress = new EmbeddedSocketAddress();

    private Queue<Object> inboundMessages;
    private Queue<Object> outboundMessages;
    private Throwable lastException;
    private int state; // 0 = OPEN, 1 = ACTIVE, 2 = CLOSED

    /**
     * Create a new instance
     *
     * @param handlers the @link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(final ChannelHandler... handlers) {
        this(false, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean hasDisconnect, final ChannelHandler... handlers) {
        super(null);
        metadata = metadata(hasDisconnect);
        config = new DefaultChannelConfig(this);
        setup(handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@link false} otherwise.
     * @param config the {@link ChannelConfig} which will be returned by {@link #config()}.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean hasDisconnect, final ChannelConfig config,
                           final ChannelHandler... handlers) {
        super(null);
        metadata = metadata(hasDisconnect);
        this.config = ObjectUtil.checkNotNull(config, "config");
        setup(handlers);
    }

    private static ChannelMetadata metadata(boolean hasDisconnect) {
        return hasDisconnect ? METADATA_DISCONNECT : METADATA_NO_DISCONNECT;
    }

    private void setup(final ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");
        ChannelPipeline p = pipeline();
        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                for (ChannelHandler h: handlers) {
                    if (h == null) {
                        break;
                    }
                    pipeline.addLast(h);
                }
            }
        });

        ChannelFuture future = loop.register(this);
        assert future.isDone();
    }

    @Override
    protected final DefaultChannelPipeline newChannelPipeline() {
        return new EmbeddedChannelPipeline(this);
    }

    @Override
    public ChannelMetadata metadata() {
        return metadata;
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
        if (inboundMessages == null) {
            inboundMessages = new ArrayDeque<Object>();
        }
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
        if (outboundMessages == null) {
            outboundMessages = new ArrayDeque<Object>();
        }
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
        return poll(inboundMessages);
    }

    /**
     * Read data from the outbound. This may return {@code null} if nothing is readable.
     */
    public Object readOutbound() {
        return poll(outboundMessages);
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
            return isNotEmpty(inboundMessages);
        }

        ChannelPipeline p = pipeline();
        for (Object m: msgs) {
            p.fireChannelRead(m);
        }
        p.fireChannelReadComplete();
        runPendingTasks();
        checkException();
        return isNotEmpty(inboundMessages);
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
            return isNotEmpty(outboundMessages);
        }

        RecyclableArrayList futures = RecyclableArrayList.newInstance(msgs.length);
        try {
            for (Object m: msgs) {
                if (m == null) {
                    break;
                }
                futures.add(write(m));
            }
            // We need to call runPendingTasks first as a ChannelOutboundHandler may used eventloop.execute(...) to
            // delay the write on the next eventloop run.
            runPendingTasks();
            flush();

            int size = futures.size();
            for (int i = 0; i < size; i++) {
                ChannelFuture future = (ChannelFuture) futures.get(i);
                if (future.isDone()) {
                    recordException(future);
                } else {
                    // The write may be delayed to run later by runPendingTasks()
                    future.addListener(recordExceptionListener);
                }
            }

            checkException();
            return isNotEmpty(outboundMessages);
        } finally {
            futures.recycle();
        }
    }

    /**
     * Mark this {@link Channel} as finished. Any futher try to write data to it will fail.
     *
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    public boolean finish() {
        return finish(false);
    }

    /**
     * Mark this {@link Channel} as finished and release all pending message in the inbound and outbound buffer.
     * Any futher try to write data to it will fail.
     *
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    public boolean finishAndReleaseAll() {
        return finish(true);
    }

    /**
     * Mark this {@link Channel} as finished. Any futher try to write data to it will fail.
     *
     * @param releaseAll if {@code true} all pending message in the inbound and outbound buffer are released.
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    private boolean finish(boolean releaseAll) {
        close();
        try {
            checkException();
            return isNotEmpty(inboundMessages) || isNotEmpty(outboundMessages);
        } finally {
            if (releaseAll) {
                releaseAll(inboundMessages);
                releaseAll(outboundMessages);
            }
        }
    }

    /**
     * Release all buffered inbound messages and return {@code true} if any were in the inbound buffer, {@code false}
     * otherwise.
     */
    public boolean releaseInbound() {
        return releaseAll(inboundMessages);
    }

    /**
     * Release all buffered outbound messages and return {@code true} if any were in the outbound buffer, {@code false}
     * otherwise.
     */
    public boolean releaseOutbound() {
        return releaseAll(outboundMessages);
    }

    private static boolean releaseAll(Queue<Object> queue) {
        if (isNotEmpty(queue)) {
            for (;;) {
                Object msg = queue.poll();
                if (msg == null) {
                    break;
                }
                ReferenceCountUtil.release(msg);
            }
            return true;
        }
        return false;
    }

    private void finishPendingTasks(boolean cancel) {
        runPendingTasks();
        if (cancel) {
            // Cancel all scheduled tasks that are left.
            loop.cancelScheduledTasks();
        }
    }

    @Override
    public final ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public final ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        // We need to call runPendingTasks() before calling super.close() as there may be something in the queue
        // that needs to be run before the actual close takes place.
        runPendingTasks();
        ChannelFuture future = super.close(promise);

        // Now finish everything else and cancel all scheduled tasks that were not ready set.
        finishPendingTasks(true);
        return future;
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        ChannelFuture future = super.disconnect(promise);
        finishPendingTasks(!metadata.hasDisconnect());
        return future;
    }

    private static boolean isNotEmpty(Queue<Object> queue) {
        return queue != null && !queue.isEmpty();
    }

    private static Object poll(Queue<Object> queue) {
        return queue != null ? queue.poll() : null;
    }

    /**
     * Run all tasks (which also includes scheduled tasks) that are pending in the {@link EventLoop}
     * for this {@link Channel}
     */
    public void runPendingTasks() {
        try {
            loop.runTasks();
        } catch (Exception e) {
            recordException(e);
        }

        try {
            loop.runScheduledTasks();
        } catch (Exception e) {
            recordException(e);
        }
    }

    /**
     * Run all pending scheduled tasks in the {@link EventLoop} for this {@link Channel} and return the
     * {@code nanoseconds} when the next scheduled task is ready to run. If no other task was scheduled it will return
     * {@code -1}.
     */
    public long runScheduledPendingTasks() {
        try {
            return loop.runScheduledTasks();
        } catch (Exception e) {
            recordException(e);
            return loop.nextScheduledTask();
        }
    }

    private void recordException(ChannelFuture future) {
        if (!future.isSuccess()) {
            recordException(future.cause());
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
     * Ensure the {@link Channel} is open and if not throw an exception.
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
        if (!metadata.hasDisconnect()) {
            doClose();
        }
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
            outboundMessages().add(msg);
            in.remove();
        }
    }

    private class DefaultUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            safeSetSuccess(promise);
        }
    }

    private final class EmbeddedChannelPipeline extends DefaultChannelPipeline {
        public EmbeddedChannelPipeline(EmbeddedChannel channel) {
            super(channel);
        }

        @Override
        protected void onUnhandledInboundException(Throwable cause) {
            recordException(cause);
        }

        @Override
        protected void onUnhandledInboundMessage(Object msg) {
            inboundMessages().add(msg);
        }
    }
}
