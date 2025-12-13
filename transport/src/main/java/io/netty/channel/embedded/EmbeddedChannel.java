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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoHandle;
import io.netty.channel.IoRegistration;
import io.netty.channel.IoTransport;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.Ticker;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.RecyclableArrayList;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for {@link Channel} implementations that are used in an embedded fashion.
 */
public class EmbeddedChannel extends AbstractChannel {

    private static final SocketAddress LOCAL_ADDRESS = new EmbeddedSocketAddress();
    private static final SocketAddress REMOTE_ADDRESS = new EmbeddedSocketAddress();

    private static final ChannelHandler[] EMPTY_HANDLERS = new ChannelHandler[0];
    private static final EmbeddedIoHandle IO_HANDLE = new EmbeddedIoHandle();

    private enum State { OPEN, ACTIVE, CLOSED }

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EmbeddedChannel.class);

    private final FutureListener<Void> recordExceptionListener = this::recordException;

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
        this(builder());
    }

    /**
     * Create a new instance with the specified ID and an empty pipeline.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     */
    public EmbeddedChannel(ChannelId channelId) {
        this(builder().channelId(channelId));
    }

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelHandler... handlers) {
        this(builder().handlers(handlers));
    }

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean hasDisconnect, ChannelHandler... handlers) {
        this(builder().hasDisconnect(hasDisconnect).handlers(handlers));
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
        this(builder().register(register).hasDisconnect(hasDisconnect).handlers(handlers));
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, ChannelHandler... handlers) {
        this(builder().channelId(channelId).handlers(handlers));
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
        this(builder().channelId(channelId).hasDisconnect(hasDisconnect).handlers(handlers));
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
        this(builder().channelId(channelId).register(register).hasDisconnect(hasDisconnect).handlers(handlers));
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
        this(builder()
                .parent(parent)
                .channelId(channelId)
                .register(register)
                .hasDisconnect(hasDisconnect)
                .handlers(handlers));
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
        this(builder().channelId(channelId).hasDisconnect(hasDisconnect).config(config).handlers(handlers));
    }

    /**
     * Create a new instance with the configuration from the given builder. This method is {@code protected} for use by
     * subclasses; Otherwise, please use {@link Builder#build()}.
     *
     * @param builder The builder
     */
    protected EmbeddedChannel(Builder builder) {
        super(new EmbeddedEventLoop(builder.ticker == null ? new EmbeddedEventLoop.FreezableTicker() : builder.ticker),
                EmbeddedIoHandle.class, builder.parent, builder.channelId, builder.hasDisconnect);
        config = builder.config == null ? new DefaultChannelConfig(this) : builder.config;
        if (builder.handler == null) {
            setup(builder.register, builder.handlers);
        } else {
            setup(builder.register, builder.handler);
        }
    }

    private void setup(boolean register, final ChannelHandler... handlers) {
        ChannelPipeline p = pipeline();
        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
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
            register0();
        }
    }

    private void setup(boolean register, final ChannelHandler handler) {
        ChannelPipeline p = pipeline();
        p.addLast(handler);
        if (register) {
            register0();
        }
    }

    /**
     * Register this {@code Channel} on its {@link EventLoop}.
     */
    public void registerNow() throws Exception {
        register0();
    }

    private void register0() {
        Promise<Void> promise = newPromise();
        ioTransport().register(promise);
        assert promise.isDone();
        Throwable cause = promise.cause();
        if (cause != null) {
            PlatformDependent.throwException(cause);
        }
    }

    @Override
    protected final DefaultChannelPipeline newChannelPipeline() {
        return new EmbeddedChannelPipeline(this);
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

    @Override
    protected void doShutdown(ChannelShutdownType type, Promise<Void> promise) {
        promise.setFailure(new UnsupportedOperationException());
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

            flushInbound(false, newPromise()).syncUninterruptibly();
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
    public Future<Void> writeOneInbound(Object msg) {
        return writeOneInbound(msg, newPromise());
    }

    /**
     * Writes one message to the inbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link io.netty.channel.ChannelOutboundInvoker#write(Object, Promise)}.
     *
     * @see #writeOneOutbound(Object, Promise)
     */
    public Future<Void> writeOneInbound(Object msg, Promise<Void> promise) {
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
        flushInbound(true, newPromise());
        return this;
    }

    private Future<Void> flushInbound(boolean recordException, Promise<Void> promise) {
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
                    Future<Void> future = (Future<Void>) futures.get(i);
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
    public Future<Void> writeOneOutbound(Object msg) {
        return writeOneOutbound(msg, newPromise());
    }

    /**
     * Writes one message to the outbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link io.netty.channel.ChannelOutboundInvoker#write(Object, Promise)}.
     *
     * @see #writeOneInbound(Object, Promise)
     */
    public Future<Void> writeOneOutbound(Object msg, Promise<Void> promise) {
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
        checkException(newPromise()).syncUninterruptibly();
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
    public final Future<Void> close() {
        return close(newPromise());
    }

    @Override
    public final Future<Void> disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public final Future<Void> close(Promise<Void> promise) {
        // We need to call runPendingTasks() before calling super.close() as there may be something in the queue
        // that needs to be run before the actual close takes place.
        executingStackCnt++;
        Future<Void> future;
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
    public final Future<Void> disconnect(Promise<Void> promise) {
        executingStackCnt++;
        Future<Void> future;
        try {
            future = super.disconnect(promise);

            if (!hasDisconnect) {
                cancelRemainingScheduledTasks = true;
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return future;
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress) {
        executingStackCnt++;
        try {
            return super.bind(localAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, localAddress);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> deregister() {
        executingStackCnt++;
        try {
            return super.deregister();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public EmbeddedChannel flush() {
        executingStackCnt++;
        try {
            super.flush();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return this;
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress, Promise<Void> promise) {
        executingStackCnt++;
        try {
            return super.bind(localAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, Promise<Void> promise) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
        executingStackCnt++;
        try {
            return super.connect(remoteAddress, localAddress, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> deregister(Promise<Void> promise) {
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
            super.read();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return this;
    }

    @Override
    public Future<Void> write(Object msg) {
        executingStackCnt++;
        try {
            return super.write(msg);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> write(Object msg, Promise<Void> promise) {
        executingStackCnt++;
        try {
            return super.write(msg, promise);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        executingStackCnt++;
        try {
            return super.writeAndFlush(msg);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
    }

    @Override
    public Future<Void> writeAndFlush(Object msg, Promise<Void> promise) {
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

    private void recordException(Future<? extends Void> future) {
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

    private EmbeddedEventLoop.FreezableTicker freezableTicker() {
        Ticker ticker = executor().ticker();
        if (ticker instanceof EmbeddedEventLoop.FreezableTicker) {
            return (EmbeddedEventLoop.FreezableTicker) ticker;
        } else {
            throw new IllegalStateException(
                    "EmbeddedChannel constructed with custom ticker, time manipulation methods are unavailable.");
        }
    }

    /**
     * Advance the clock of the event loop of this channel by the given duration. Any scheduled tasks will execute
     * sooner by the given time (but {@link #runScheduledPendingTasks()} still needs to be called).
     */
    public void advanceTimeBy(long duration, TimeUnit unit) {
        freezableTicker().advance(duration, unit);
    }

    /**
     * Freeze the clock of this channel's event loop. Any scheduled tasks that are not already due will not run on
     * future {@link #runScheduledPendingTasks()} calls. While the event loop is frozen, it is still possible to
     * {@link #advanceTimeBy(long, TimeUnit) advance time} manually so that scheduled tasks execute.
     */
    public void freezeTime() {
        freezableTicker().freezeTime();
    }

    /**
     * Unfreeze an event loop that was {@link #freezeTime() frozen}. Time will continue at the point where
     * {@link #freezeTime()} stopped it: if a task was scheduled ten minutes in the future and {@link #freezeTime()}
     * was called, it will run ten minutes after this method is called again (assuming no
     * {@link #advanceTimeBy(long, TimeUnit)} calls, and assuming pending scheduled tasks are run at that time using
     * {@link #runScheduledPendingTasks()}).
     */
    public void unfreezeTime() {
        freezableTicker().unfreezeTime();
    }

    /**
     * Checks for the presence of an {@link Exception}.
     */
    private Future<Void> checkException(Promise<Void> promise) {
      Throwable t = lastException;
      if (t != null) {
          lastException = null;
          return promise.setFailure(t);
      }

      return promise.setSuccess(null);
    }

    /**
     * Check if there was any {@link Throwable} received and if so rethrow it.
     */
    public void checkException() {
      checkException(newPromise()).syncUninterruptibly();
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
        return (EmbeddedEventLoop) super.executor();
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
    protected SocketAddress localAddress0() {
        return isActive()? LOCAL_ADDRESS : null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return isActive()? REMOTE_ADDRESS : null;
    }

    @Override
    protected void doDeregister(Promise<Void> promise) {
        promise.setSuccess(null);
    }

    @Override
    protected void doRegister(Promise<Void> promise) {
        state = State.ACTIVE;
        promise.setSuccess(null);
    }

    @Override
    protected void doBind(SocketAddress localAddress, Promise<Void> promise) {
        promise.setSuccess(null);
    }

    @Override
    protected void doDisconnect(Promise<Void> promise) {
        if (!hasDisconnect) {
            doClose(promise);
        } else {
            promise.setSuccess(null);
        }
    }

    @Override
    protected void doClose(Promise<Void> promise)  {
        state = State.CLOSED;
        promise.setSuccess(null);
    }

    @Override
    protected void doBeginRead() throws Exception {
        // NOOP
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        Channel parent;
        ChannelId channelId = EmbeddedChannelId.INSTANCE;
        boolean register = true;
        boolean hasDisconnect;
        //you should use either handlers or handler variable, but not both.
        ChannelHandler[] handlers = EMPTY_HANDLERS;
        ChannelHandler handler;
        ChannelConfig config;
        Ticker ticker;

        private Builder() {
        }

        /**
         * The parent {@link Channel} of this {@link EmbeddedChannel}.
         *
         * @param parent the parent {@link Channel} of this {@link EmbeddedChannel}.
         * @return This builder
         */
        public Builder parent(Channel parent) {
            this.parent = parent;
            return this;
        }

        /**
         * The {@link ChannelId} that will be used to identify this channel.
         *
         * @param channelId the {@link ChannelId} that will be used to identify this channel
         * @return This builder
         */
        public Builder channelId(ChannelId channelId) {
            this.channelId = Objects.requireNonNull(channelId, "channelId");
            return this;
        }

        /**
         * {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the constructor. If
         * {@code false} the user will need to call {@link #register()}.
         *
         * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
         *                 constructor. If {@code false} the user will need to call {@link #register()}.
         * @return This builder
         */
        public Builder register(boolean register) {
            this.register = register;
            return this;
        }

        /**
         * {@code false} if this {@link Channel} will delegate {@link #disconnect()} to {@link #close()}, {@code true}
         * otherwise.
         *
         * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()} to
         *                      {@link #close()}, {@code true} otherwise
         * @return This builder
         */
        public Builder hasDisconnect(boolean hasDisconnect) {
            this.hasDisconnect = hasDisconnect;
            return this;
        }

        /**
         * The {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}.
         *
         * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
         * @return This builder
         */
        public Builder handlers(ChannelHandler... handlers) {
            this.handlers = Objects.requireNonNull(handlers, "handlers");
            this.handler = null;
            return this;
        }

        /**
         * The {@link ChannelHandler} which will be added to the {@link ChannelPipeline}.
         *
         * @param handler the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
         * @return This builder
         */
        public Builder handlers(ChannelHandler handler) {
            this.handler = Objects.requireNonNull(handler, "handler");
            this.handlers = null;
            return this;
        }

        /**
         * The {@link ChannelConfig} which will be returned by {@link #config()}.
         *
         * @param config the {@link ChannelConfig} which will be returned by {@link #config()}
         * @return This builder
         */
        public Builder config(ChannelConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        /**
         * Configure a custom ticker for this event loop.
         *
         * @param ticker The custom ticker
         * @return This builder
         */
        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        /**
         * Create the channel. If you wish to extend {@link EmbeddedChannel}, please use the
         * {@link #EmbeddedChannel(Builder)} constructor instead.
         *
         * @return The channel
         */
        public EmbeddedChannel build() {
            return new EmbeddedChannel(this);
        }
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
        promise.setSuccess(null);
    }

    static final class EmbeddedIoHandle implements IoHandle {
        @Override
        public void handle(IoRegistration registration, IoEvent ioEvent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // NOOP
        }
    }

    final class EmbeddedIoTransport implements IoTransport {
        private final IoTransport transport;
        private final FutureListener<Void> futureListener = f ->  maybeRunPendingTasks();

        EmbeddedIoTransport(IoTransport transport) {
            this.transport = transport;
        }

        @Override
        public void shutdown(ChannelShutdownType type, Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.shutdown(type, promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void register(Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.register(promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void bind(SocketAddress localAddress, Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.bind(localAddress, promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.connect(remoteAddress, localAddress, promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void disconnect(Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.disconnect(promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void close(Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.close(promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void deregister(Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.deregister(promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void read() {
            executingStackCnt++;
            try {
                transport.read();
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void write(Object msg, Promise<Void> promise) {
            executingStackCnt++;
            try {
                transport.write(msg, promise.addListener(futureListener));
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }

        @Override
        public void flush() {
            executingStackCnt++;
            try {
                transport.flush();
            } finally {
                executingStackCnt--;
                maybeRunPendingTasks();
            }
        }
    }

    private final class EmbeddedChannelPipeline extends DefaultChannelPipeline {
        EmbeddedChannelPipeline(EmbeddedChannel channel) {
            super(channel, channel.hasDisconnect, new EmbeddedIoTransport(channel.ioTransport()));
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
