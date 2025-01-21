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
package io.netty.channel.embedded;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
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
import java.util.concurrent.TimeUnit;

/**
 * Base class for {@link Channel} implementations that are used in an embedded fashion.
 */
public class EmbeddedChannel extends AbstractChannel {

    private static final SocketAddress LOCAL_ADDRESS = new EmbeddedSocketAddress();
    private static final SocketAddress REMOTE_ADDRESS = new EmbeddedSocketAddress();

    private static final ChannelHandler[] EMPTY_HANDLERS = new ChannelHandler[0];
    private enum State { OPEN, ACTIVE, CLOSED }

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

    private Queue<Object> inboundMessages;
    private Queue<Object> outboundMessages;
    private Throwable lastException;
    private State state;
    private int executingStackCnt;
    private boolean cancelRemainingScheduledTasks;

    /**
     * Create a new instance with an {@link EmbeddedChannelId} and an empty pipeline.
     */
    public EmbeddedChannel() {
        this(EMPTY_HANDLERS);
    }

    /**
     * Create a new instance with the specified ID and an empty pipeline.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     */
    public EmbeddedChannel(ChannelId channelId) {
        this(channelId, EMPTY_HANDLERS);
    }

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelHandler... handlers) {
        this(EmbeddedChannelId.INSTANCE, handlers);
    }

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean hasDisconnect, ChannelHandler... handlers) {
        this(EmbeddedChannelId.INSTANCE, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean register, boolean hasDisconnect, ChannelHandler... handlers) {
        this(EmbeddedChannelId.INSTANCE, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, ChannelHandler... handlers) {
        this(channelId, false, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, boolean hasDisconnect, ChannelHandler... handlers) {
        this(channelId, true, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, boolean register, boolean hasDisconnect,
                           ChannelHandler... handlers) {
        this(null, channelId, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param parent    the parent {@link Channel} of this {@link EmbeddedChannel}.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Channel parent, ChannelId channelId, boolean register, boolean hasDisconnect,
                           final ChannelHandler... handlers) {
        super(parent, channelId);
        metadata = metadata(hasDisconnect);
        config = new DefaultChannelConfig(this);
        setup(register, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param config the {@link ChannelConfig} which will be returned by {@link #config()}.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, boolean hasDisconnect, final ChannelConfig config,
                           final ChannelHandler... handlers) {
        super(null, channelId);
        metadata = metadata(hasDisconnect);
        this.config = ObjectUtil.checkNotNull(config, "config");
        setup(true, handlers);
    }

    private static ChannelMetadata metadata(boolean hasDisconnect) {
        return hasDisconnect ? METADATA_DISCONNECT : METADATA_NO_DISCONNECT;
    }

    private void setup(boolean register, final ChannelHandler... handlers) {
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
        if (register) {
            ChannelFuture future = loop.register(this);
            assert future.isDone();
        }
    }

    /**
     * Register this {@code Channel} on its {@link EventLoop}.
     */
    public void register() throws Exception {
        ChannelFuture future = loop.register(this);
        assert future.isDone();
        Throwable cause = future.cause();
        if (cause != null) {
            PlatformDependent.throwException(cause);
        }
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
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.ACTIVE;
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
    @SuppressWarnings("unchecked")
    public <T> T readInbound() {
        T message = (T) poll(inboundMessages);
        if (message != null) {
            ReferenceCountUtil.touch(message, "Caller of readInbound() will handle the message from this point");
        }
        return message;
    }

    /**
     * Read data from the outbound. This may return {@code null} if nothing is readable.
     */
    @SuppressWarnings("unchecked")
    public <T> T readOutbound() {
        T message =  (T) poll(outboundMessages);
        if (message != null) {
            ReferenceCountUtil.touch(message, "Caller of readOutbound() will handle the message from this point.");
        }
        return message;
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

        executingStackCnt++;
        try {
            ChannelPipeline p = pipeline();
            for (Object m : msgs) {
                p.fireChannelRead(m);
            }

            flushInbound(false, voidPromise());
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return isNotEmpty(inboundMessages);
    }

    /**
     * Writes one message to the inbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link #write(Object)}.
     *
     * @see #writeOneOutbound(Object)
     */
    public ChannelFuture writeOneInbound(Object msg) {
        return writeOneInbound(msg, newPromise());
    }

    /**
     * Writes one message to the inbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link #write(Object, ChannelPromise)}.
     *
     * @see #writeOneOutbound(Object, ChannelPromise)
     */
    public ChannelFuture writeOneInbound(Object msg, ChannelPromise promise) {
        executingStackCnt++;
        try {
            if (checkOpen(true)) {
                pipeline().fireChannelRead(msg);
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return checkException(promise);
    }

    /**
     * Flushes the inbound of this {@link Channel}. This method is conceptually equivalent to {@link #flush()}.
     *
     * @see #flushOutbound()
     */
    public EmbeddedChannel flushInbound() {
        flushInbound(true, voidPromise());
        return this;
    }

    private ChannelFuture flushInbound(boolean recordException, ChannelPromise promise) {
        executingStackCnt++;
        try {
            if (checkOpen(recordException)) {
                pipeline().fireChannelReadComplete();
                runPendingTasks();
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }

      return checkException(promise);
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

        executingStackCnt++;
        RecyclableArrayList futures = RecyclableArrayList.newInstance(msgs.length);
        try {
            try {
                for (Object m : msgs) {
                    if (m == null) {
                        break;
                    }
                    futures.add(write(m));
                }

                flushOutbound0();

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
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
            checkException();
            return isNotEmpty(outboundMessages);
        } finally {
            futures.recycle();
        }
    }

    /**
     * Writes one message to the outbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link #write(Object)}.
     *
     * @see #writeOneInbound(Object)
     */
    public ChannelFuture writeOneOutbound(Object msg) {
        return writeOneOutbound(msg, newPromise());
    }

    /**
     * Writes one message to the outbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link #write(Object, ChannelPromise)}.
     *
     * @see #writeOneInbound(Object, ChannelPromise)
     */
    public ChannelFuture writeOneOutbound(Object msg, ChannelPromise promise) {
        executingStackCnt++;
        try {
            if (checkOpen(true)) {
                return write(msg, promise);
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }

        return checkException(promise);
    }

    /**
     * Flushes the outbound of this {@link Channel}. This method is conceptually equivalent to {@link #flush()}.
     *
     * @see #flushInbound()
     */
    public EmbeddedChannel flushOutbound() {
        executingStackCnt++;
        try {
            if (checkOpen(true)) {
                flushOutbound0();
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        checkException(voidPromise());
        return this;
    }

    private void flushOutbound0() {
        // We need to call runPendingTasks first as a ChannelOutboundHandler may used eventloop.execute(...) to
        // delay the write on the next eventloop run.
        runPendingTasks();

        flush();
    }

    /**
     * Mark this {@link Channel} as finished. Any further try to write data to it will fail.
     *
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    public boolean finish() {
        return finish(false);
    }

    /**
     * Mark this {@link Channel} as finished and release all pending message in the inbound and outbound buffer.
     * Any further try to write data to it will fail.
     *
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    public boolean finishAndReleaseAll() {
        return finish(true);
    }

    /**
     * Mark this {@link Channel} as finished. Any further try to write data to it will fail.
     *
     * @param releaseAll if {@code true} all pending message in the inbound and outbound buffer are released.
     * @return bufferReadable returns {@code true} if any of the used buffers has something left to read
     */
    private boolean finish(boolean releaseAll) {
        executingStackCnt++;
        try {
            close();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
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
        executingStackCnt++;
        ChannelFuture future;
        try {
            runPendingTasks();
            future = super.close(promise);

            cancelRemainingScheduledTasks = true;
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return future;
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        executingStackCnt++;
        ChannelFuture future;
        try {
            future = super.disconnect(promise);

            if (!metadata.hasDisconnect()) {
                cancelRemainingScheduledTasks = true;
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return future;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        executingStackCnt++;
        try {
            return super.bind(localAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, localAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture deregister() {
        executingStackCnt++;
        try {
            return super.deregister();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Channel flush() {
        executingStackCnt++;
        try {
            return super.flush();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.bind(localAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, localAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.deregister(promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Channel read() {
        executingStackCnt++;
        try {
            return super.read();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        executingStackCnt++;
        try {
            return super.write(msg);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.write(msg, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        executingStackCnt++;
        try {
            return super.writeAndFlush(msg);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        executingStackCnt++;
        try {
            return super.writeAndFlush(msg, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    private static boolean isNotEmpty(Queue<Object> queue) {
        return queue != null && !queue.isEmpty();
    }

    private static Object poll(Queue<Object> queue) {
        return queue != null ? queue.poll() : null;
    }

    private void maybeRunPendingTasks() {
        if (executingStackCnt == 0) {
            runPendingTasks();

            if (cancelRemainingScheduledTasks) {
                // Cancel all scheduled tasks that are left.
                embeddedEventLoop().cancelScheduledTasks();
            }
        }
    }

    /**
     * Run all tasks (which also includes scheduled tasks) that are pending in the {@link EventLoop}
     * for this {@link Channel}
     */
    public void runPendingTasks() {
        try {
            embeddedEventLoop().runTasks();
        } catch (Exception e) {
            recordException(e);
        }

        try {
            embeddedEventLoop().runScheduledTasks();
        } catch (Exception e) {
            recordException(e);
        }
    }

    /**
     * Check whether this channel has any pending tasks that would be executed by a call to {@link #runPendingTasks()}.
     * This includes normal tasks, and scheduled tasks where the deadline has expired. If this method returns
     * {@code false}, a call to {@link #runPendingTasks()} would do nothing.
     *
     * @return {@code true} if there are any pending tasks, {@code false} otherwise.
     */
    public boolean hasPendingTasks() {
        return embeddedEventLoop().hasPendingNormalTasks() ||
                embeddedEventLoop().nextScheduledTask() == 0;
    }

    /**
     * Run all pending scheduled tasks in the {@link EventLoop} for this {@link Channel} and return the
     * {@code nanoseconds} when the next scheduled task is ready to run. If no other task was scheduled it will return
     * {@code -1}.
     */
    public long runScheduledPendingTasks() {
        try {
            return embeddedEventLoop().runScheduledTasks();
        } catch (Exception e) {
            recordException(e);
            return embeddedEventLoop().nextScheduledTask();
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
     * Advance the clock of the event loop of this channel by the given duration. Any scheduled tasks will execute
     * sooner by the given time (but {@link #runScheduledPendingTasks()} still needs to be called).
     */
    public void advanceTimeBy(long duration, TimeUnit unit) {
        embeddedEventLoop().advanceTimeBy(unit.toNanos(duration));
    }

    /**
     * Freeze the clock of this channel's event loop. Any scheduled tasks that are not already due will not run on
     * future {@link #runScheduledPendingTasks()} calls. While the event loop is frozen, it is still possible to
     * {@link #advanceTimeBy(long, TimeUnit) advance time} manually so that scheduled tasks execute.
     */
    public void freezeTime() {
        embeddedEventLoop().freezeTime();
    }

    /**
     * Unfreeze an event loop that was {@link #freezeTime() frozen}. Time will continue at the point where
     * {@link #freezeTime()} stopped it: if a task was scheduled ten minutes in the future and {@link #freezeTime()}
     * was called, it will run ten minutes after this method is called again (assuming no
     * {@link #advanceTimeBy(long, TimeUnit)} calls, and assuming pending scheduled tasks are run at that time using
     * {@link #runScheduledPendingTasks()}).
     */
    public void unfreezeTime() {
        embeddedEventLoop().unfreezeTime();
    }

    /**
     * Checks for the presence of an {@link Exception}.
     */
    private ChannelFuture checkException(ChannelPromise promise) {
      Throwable t = lastException;
      if (t != null) {
        lastException = null;

        if (promise.isVoid()) {
            PlatformDependent.throwException(t);
        }

        return promise.setFailure(t);
      }

      return promise.setSuccess();
    }

    /**
     * Check if there was any {@link Throwable} received and if so rethrow it.
     */
    public void checkException() {
      checkException(voidPromise());
    }

    /**
     * Returns {@code true} if the {@link Channel} is open and records optionally
     * an {@link Exception} if it isn't.
     */
    private boolean checkOpen(boolean recordException) {
        if (!isOpen()) {
          if (recordException) {
              recordException(new ClosedChannelException());
          }
          return false;
      }

      return true;
    }

    private EmbeddedEventLoop embeddedEventLoop() {
        if (isRegistered()) {
            return (EmbeddedEventLoop) super.eventLoop();
        }

        return loop;
    }

    /**
     * Ensure the {@link Channel} is open and if not throw an exception.
     */
    protected final void ensureOpen() {
        if (!checkOpen(true)) {
            checkException();
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EmbeddedEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return isActive()? LOCAL_ADDRESS : null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return isActive()? REMOTE_ADDRESS : null;
    }

    @Override
    protected void doRegister() throws Exception {
        state = State.ACTIVE;
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
        state = State.CLOSED;
    }

    @Override
    protected void doBeginRead() throws Exception {
        // NOOP
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new EmbeddedUnsafe();
    }

    @Override
    public Unsafe unsafe() {
        return ((EmbeddedUnsafe) super.unsafe()).wrapped;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            ReferenceCountUtil.retain(msg);
            handleOutboundMessage(msg);
            in.remove();
        }
    }

    /**
     * Called for each outbound message.
     *
     * @see #doWrite(ChannelOutboundBuffer)
     */
    protected void handleOutboundMessage(Object msg) {
        outboundMessages().add(msg);
    }

    /**
     * Called for each inbound message.
     */
    protected void handleInboundMessage(Object msg) {
        inboundMessages().add(msg);
    }

    private final class EmbeddedUnsafe extends AbstractUnsafe {

        // Delegates to the EmbeddedUnsafe instance but ensures runPendingTasks() is called after each operation
        // that may change the state of the Channel and may schedule tasks for later execution.
        final Unsafe wrapped = new Unsafe() {
            @Override
            public RecvByteBufAllocator.Handle recvBufAllocHandle() {
                return EmbeddedUnsafe.this.recvBufAllocHandle();
            }

            @Override
            public SocketAddress localAddress() {
                return EmbeddedUnsafe.this.localAddress();
            }

            @Override
            public SocketAddress remoteAddress() {
                return EmbeddedUnsafe.this.remoteAddress();
            }

            @Override
            public void register(EventLoop eventLoop, ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.register(eventLoop, promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void bind(SocketAddress localAddress, ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.bind(localAddress, promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.connect(remoteAddress, localAddress, promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void disconnect(ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.disconnect(promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void close(ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.close(promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void closeForcibly() {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.closeForcibly();
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void deregister(ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.deregister(promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void beginRead() {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.beginRead();
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void write(Object msg, ChannelPromise promise) {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.write(msg, promise);
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public void flush() {
                executingStackCnt++;
                try {
                    EmbeddedUnsafe.this.flush();
                } finally {
                    executingStackCnt--;
                    maybeRunPendingTasks();
                }
            }

            @Override
            public ChannelPromise voidPromise() {
                return EmbeddedUnsafe.this.voidPromise();
            }

            @Override
            public ChannelOutboundBuffer outboundBuffer() {
                return EmbeddedUnsafe.this.outboundBuffer();
            }
        };

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            safeSetSuccess(promise);
        }
    }

    private final class EmbeddedChannelPipeline extends DefaultChannelPipeline {
        EmbeddedChannelPipeline(EmbeddedChannel channel) {
            super(channel);
        }

        @Override
        protected void onUnhandledInboundException(Throwable cause) {
            recordException(cause);
        }

        @Override
        protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
            handleInboundMessage(msg);
        }
    }
}
