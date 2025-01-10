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
package io.netty5.channel.embedded;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.ResourceSupport;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelId;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.IoHandle;
import io.netty5.channel.MaxMessagesWriteHandleFactory;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Ticker;
import io.netty5.util.internal.RecyclableArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty5.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

/**
 * Base class for {@link Channel} implementations that are used in an embedded fashion.
 */
public class EmbeddedChannel extends AbstractChannel<Channel, SocketAddress, SocketAddress> {

    private static final SocketAddress LOCAL_ADDRESS = new EmbeddedSocketAddress();
    private static final SocketAddress REMOTE_ADDRESS = new EmbeddedSocketAddress();

    private static final ChannelHandler[] EMPTY_HANDLERS = new ChannelHandler[0];
    private enum State { OPEN, ACTIVE, CLOSED }

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedChannel.class);

    private final FutureListener<Void> recordExceptionListener = this::recordException;

    private Queue<Object> inboundMessages;
    private Queue<Object> outboundMessages;
    private Throwable lastException;
    private State state;
    private boolean inputShutdown;
    private boolean outputShutdown;
    private int executingStackCnt;
    private boolean cancelRemainingScheduledTasks;

    private final EmbeddedIoHandle handle = new EmbeddedIoHandle() {
        @Override
        protected void setActive() {
            EmbeddedChannel.this.setActive();
        }
    };

    /**
     * Create a new instance with an {@link EmbeddedChannelId} and an empty pipeline.
     */
    public EmbeddedChannel() {
        this(EMPTY_HANDLERS);
    }

    /**
     * Create a new instance with an {@link EmbeddedChannelId} and an empty pipeline.
     *
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     */
    public EmbeddedChannel(Ticker ticker) {
        this(ticker, EMPTY_HANDLERS);
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
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Ticker ticker, ChannelHandler... handlers) {
        this(ticker, EmbeddedChannelId.INSTANCE, handlers);
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
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Ticker ticker, ChannelId channelId, ChannelHandler... handlers) {
        this(ticker, channelId, false, handlers);
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
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Ticker ticker, ChannelId channelId, boolean hasDisconnect, ChannelHandler... handlers) {
        this(ticker, channelId, true, hasDisconnect, handlers);
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
        this(Ticker.systemTicker(), null, channelId, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Ticker ticker, ChannelId channelId, boolean register, boolean hasDisconnect,
                           ChannelHandler... handlers) {
        this(ticker, null, channelId, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param parent the parent {@link Channel} of this {@link EmbeddedChannel}.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Channel parent, ChannelId channelId, boolean register, boolean hasDisconnect,
                           final ChannelHandler... handlers) {
        this(Ticker.systemTicker(), parent, channelId, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param ticker the {@link Ticker} that will be used for scheduling tasks.
     * @param parent the parent {@link Channel} of this {@link EmbeddedChannel}.
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param register {@code true} if this {@link Channel} is registered to the {@link EventLoop} in the
     *                 constructor. If {@code false} the user will need to call {@link #register()}.
     * @param hasDisconnect {@code false} if this {@link Channel} will delegate {@link #disconnect()}
     *                      to {@link #close()}, {@code true} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be added to the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Ticker ticker, Channel parent, ChannelId channelId, boolean register, boolean hasDisconnect,
                           final ChannelHandler... handlers) {
        super(parent, new EmbeddedEventLoop(ticker), hasDisconnect, new AdaptiveReadHandleFactory(),
                new MaxMessagesWriteHandleFactory(Integer.MAX_VALUE), channelId, EmbeddedIoHandle.class);
        setup(register, handlers);
    }

    private void setup(boolean register, final ChannelHandler... handlers) {
        requireNonNull(handlers, "handlers");
        ChannelPipeline p = pipeline();
        p.addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                for (ChannelHandler h : handlers) {
                    if (h == null) {
                        break;
                    }
                    pipeline.addLast(h);
                }
            }
        });
        if (register) {
            Future<Void> future = register();
            assert future.isDone();
        }
    }

    @Override
    protected IoHandle ioHandle() {
        return handle;
    }

    @Override
    public Future<Void> register() {
        Future<Void> future;
        executingStackCnt++;
        try {
            future = super.register();
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        assert future.isDone();
        Throwable cause = future.cause();
        if (cause != null) {
            throwException(cause);
        }
        return future;
    }

    @Override
    protected final DefaultChannelPipeline newChannelPipeline() {
        return new EmbeddedChannelPipeline(this);
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
            inboundMessages = new ArrayDeque<>();
        }
        return inboundMessages;
    }

    /**
     * Returns the {@link Queue} which holds all the {@link Object}s that were written by this {@link Channel}.
     */
    public Queue<Object> outboundMessages() {
        if (outboundMessages == null) {
            outboundMessages = new ArrayDeque<>();
        }
        return outboundMessages;
    }

    /**
     * Return received data from this {@link Channel}
     */
    @SuppressWarnings("unchecked")
    public <T> T readInbound() {
        T message = (T) poll(inboundMessages);
        if (message != null) {
            Resource.touch(message, "Caller of readInbound() will handle the message from this point");
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
            Resource.touch(message, "Caller of readOutbound() will handle the message from this point.");
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

            flushInbound(false);
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
        executingStackCnt++;
        try {
            if (checkOpen(true)) {
                pipeline().fireChannelRead(msg);
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return checkException0();
    }

    /**
     * Flushes the inbound of this {@link Channel}. This method is conceptually equivalent to {@link #flush()}.
     *
     * @see #flushOutbound()
     */
    public EmbeddedChannel flushInbound() {
        flushInbound(true);
        return this;
    }

    private void flushInbound(boolean recordException) {
        executingStackCnt++;
        try {
            if (checkOpen(recordException)) {
                pipeline().fireChannelReadComplete();
                embeddedEventLoop().execute(this::readIfIsAutoRead);
                runPendingTasks();
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
      checkException();
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
        executingStackCnt++;
        try {
            if (checkOpen(true)) {
                return write(msg);
            }
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return checkException0();
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
        checkException();
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
        Exception closeFailed = null;
        if (isNotEmpty(queue)) {
            for (;;) {
                Object msg = queue.poll();
                if (msg == null) {
                    break;
                }
                try {
                    Resource.dispose(msg);
                } catch (Exception e) {
                    if (closeFailed == null) {
                        closeFailed = e;
                    } else {
                        closeFailed.addSuppressed(e);
                    }
                }
            }
            if (closeFailed != null) {
                throwException(closeFailed);
            }
            return true;
        }
        return false;
    }

    @Override
    public final Future<Void> close() {
        Future<Void> future;
        executingStackCnt++;
        try {
            // We need to call runPendingTasks() before calling super.close() as there may be something in the queue
            // that needs to be run before the actual close takes place.
            runPendingTasks();
            future = super.close();
            cancelRemainingScheduledTasks = true;
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
        return future;
    }

    @Override
    public final Future<Void> disconnect() {
        Future<Void> future;
        try {
            future = super.disconnect();
            if (!isSupportingDisconnect()) {
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
    public Channel read(ReadBufferAllocator readBufferAllocator) {
        executingStackCnt++;
        try {
            return super.read(readBufferAllocator);
        } finally {
            executingStackCnt--;
            maybeRunPendingTasks();
        }
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
    public Future<Void> writeAndFlush(Object msg) {
        executingStackCnt++;
        try {
            return super.writeAndFlush(msg);
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
                embeddedEventLoop().cancelScheduled();
            }
        }
    }

    /**
     * Run all tasks (which also includes scheduled tasks) that are pending in the {@link EventLoop}
     * for this {@link Channel}
     */
    public void runPendingTasks() {
        EmbeddedEventLoop embeddedEventLoop = (EmbeddedEventLoop) executor();
        try {
            embeddedEventLoop.runTasks();
        } catch (Exception e) {
            recordException(e);
        }

        runScheduledPendingTasks();
    }

    /**
     * Run all pending scheduled tasks in the {@link EventLoop} for this {@link Channel} and return the
     * {@code nanoseconds} when the next scheduled task is ready to run. If no other task was scheduled it will return
     * {@code -1}.
     */
    public long runScheduledPendingTasks() {
        EmbeddedEventLoop embeddedEventLoop = (EmbeddedEventLoop) executor();

        try {
            return embeddedEventLoop.runScheduledTasks();
        } catch (Exception e) {
            recordException(e);
            return embeddedEventLoop.nextScheduledTask();
        } finally {
            // A scheduled task may put something on the taskQueue so lets run it.
            embeddedEventLoop.runTasks();
        }
    }

    /**
     * Check whether this channel has any pending tasks that would be executed by a call to {@link #runPendingTasks()}.
     * This includes normal tasks, and scheduled tasks where the deadline has expired. If this method returns
     * {@code false}, a call to {@link #runPendingTasks()} would do nothing.
     *
     * @return {@code true} if there are any pending tasks, {@code false} otherwise.
     */
    public boolean hasPendingTasks() {
        return embeddedEventLoop().hasPendingNormalTasks() ||
                embeddedEventLoop().nextScheduledTask() == 0;
    }

    private void recordException(Future<?> future) {
        if (future.isFailed()) {
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

    private EmbeddedEventLoop embeddedEventLoop() {
        return (EmbeddedEventLoop) executor();
    }

    /**
     * Checks for the presence of an {@link Exception}.
     */
    private Future<Void> checkException0() {
        try {
            checkException();
        } catch (Throwable cause) {
            return newFailedFuture(cause);
        }
        return newSucceededFuture();
    }

    /**
     * Check if there was any {@link Throwable} received and if so rethrow it.
     */
    public void checkException() {
        Throwable t = lastException;
        if (t != null) {
            lastException = null;

            throwException(t);
        }
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

    void setActive() {
        state = State.ACTIVE;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        // NOOP
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        switch (direction) {
            case Inbound:
                inputShutdown = true;
                break;
            case Outbound:
                outputShutdown = true;
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Inbound:
                return inputShutdown;
            case Outbound:
                return outputShutdown;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        if (!isSupportingDisconnect()) {
            doClose();
        }
    }

    @Override
    protected void doClose() throws Exception {
        state = State.CLOSED;
    }

    @Override
    protected void doRead(boolean wasReadPendingAlready) throws Exception {
        // NOOP
    }

    @Override
    protected boolean doReadNow(ReadSink readSink) {
        return false;
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) throws Exception {
        Object msg = writeSink.currentFlushedMessage();
        if (msg instanceof ResourceSupport<?, ?>) {
            // Prevent the close in ChannelOutboundBuffer.remove() from ending the lifecycle of this message.
            // This allows tests to examine the message.
            handleOutboundMessage(InternalBufferUtils.acquire((ResourceSupport<?, ?>) msg));
        } else if (msg instanceof Resource<?>) {
            // Resource life-cycle otherwise normally ends in ChannelOutboundBuffer.remove(), but using send()
            // here allows the close() in remove() to become a no-op.
            // Since message isn't a subclass of ResourceSupport, we can't rely on its internal reference counting.
            handleOutboundMessage(((Resource<?>) msg).send().receive());
        } else {
            handleOutboundMessage(ReferenceCountUtil.retain(msg));
        }
        writeSink.complete(0, 0, 1, true);
    }

    /**
     * Called for each outbound message.
     *
     * @see #doWriteNow(WriteSink)
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

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        return true;
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
        return true;
    }

    private final class EmbeddedChannelPipeline extends DefaultAbstractChannelPipeline {
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

        @Override
        protected void runAfterTransportOperation() {
            super.runAfterTransportOperation();
            if (!((EmbeddedEventLoop) executor()).running) {
                runPendingTasks();
            }
        }
    }
}
