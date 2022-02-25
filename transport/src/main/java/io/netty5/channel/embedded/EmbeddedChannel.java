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

import io.netty5.buffer.api.Resource;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelId;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultChannelConfig;
import io.netty5.channel.DefaultChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.RecyclableArrayList;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty5.util.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

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

    private final FutureListener<Void> recordExceptionListener = this::recordException;

    private final ChannelMetadata metadata;
    private final ChannelConfig config;

    private Queue<Object> inboundMessages;
    private Queue<Object> outboundMessages;
    private Throwable lastException;
    private State state;

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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(boolean register, boolean hasDisconnect, ChannelHandler... handlers) {
        this(EmbeddedChannelId.INSTANCE, register, hasDisconnect, handlers);
    }

    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     *
     * @param channelId the {@link ChannelId} that will be used to identify this channel
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(Channel parent, ChannelId channelId, boolean register, boolean hasDisconnect,
                           final ChannelHandler... handlers) {
        super(parent, new EmbeddedEventLoop(), channelId);
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
     *                      to {@link #close()}, {@link false} otherwise.
     * @param config the {@link ChannelConfig} which will be returned by {@link #config()}.
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedChannel(ChannelId channelId, boolean hasDisconnect, final ChannelConfig config,
                           final ChannelHandler... handlers) {
        super(null, new EmbeddedEventLoop(), channelId);
        metadata = metadata(hasDisconnect);
        this.config = requireNonNull(config, "config");
        setup(true, handlers);
    }

    private static ChannelMetadata metadata(boolean hasDisconnect) {
        return hasDisconnect ? METADATA_DISCONNECT : METADATA_NO_DISCONNECT;
    }

    private void setup(boolean register, final ChannelHandler... handlers) {
        requireNonNull(handlers, "handlers");
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
            Future<Void> future = register();
            assert future.isDone();
        }
    }

    @Override
    public Future<Void> register() {
        Future<Void> future = super.register();
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
            inboundMessages = new ArrayDeque<>();
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
            outboundMessages = new ArrayDeque<>();
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

        ChannelPipeline p = pipeline();
        for (Object m: msgs) {
            p.fireChannelRead(m);
        }

        flushInbound(false);
        return isNotEmpty(inboundMessages);
    }

    /**
     * Writes one message to the inbound of this {@link Channel} and does not flush it. This
     * method is conceptually equivalent to {@link #write(Object)}.
     *
     * @see #writeOneOutbound(Object)
     */
    public Future<Void> writeOneInbound(Object msg) {
        if (checkOpen(true)) {
            pipeline().fireChannelRead(msg);
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
      if (checkOpen(recordException)) {
          pipeline().fireChannelReadComplete();
          readIfIsAutoRead();
          runPendingTasks();
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

        RecyclableArrayList futures = RecyclableArrayList.newInstance(msgs.length);
        try {
            for (Object m: msgs) {
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
        if (checkOpen(true)) {
            return write(msg);
        }
        return checkException0();
    }

    /**
     * Flushes the outbound of this {@link Channel}. This method is conceptually equivalent to {@link #flush()}.
     *
     * @see #flushInbound()
     */
    public EmbeddedChannel flushOutbound() {
        if (checkOpen(true)) {
            flushOutbound0();
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
        Exception closeFailed = null;
        if (isNotEmpty(queue)) {
            for (;;) {
                Object msg = queue.poll();
                if (msg == null) {
                    break;
                }
                if (!ReferenceCountUtil.release(msg) && msg instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) msg).close();
                    } catch (Exception e) {
                        if (closeFailed == null) {
                            closeFailed = e;
                        } else {
                            closeFailed.addSuppressed(e);
                        }
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

    private void finishPendingTasks(boolean cancel) {
        runPendingTasks();
        if (cancel) {
            // Cancel all scheduled tasks that are left.
            ((EmbeddedEventLoop) executor()).cancelScheduled();
        }
    }

    @Override
    public final Future<Void> close() {
        // We need to call runPendingTasks() before calling super.close() as there may be something in the queue
        // that needs to be run before the actual close takes place.
        runPendingTasks();
        Future<Void> future = super.close();

        // Now finish everything else and cancel all scheduled tasks that were not ready set.
        finishPendingTasks(true);
        return future;
    }

    @Override
    public final Future<Void> disconnect() {
        Future<Void> future = super.disconnect();
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

            if (msg instanceof Resource<?>) {
                // Exfiltrate message for later inspection in tests.
                // Resource life-cycle otherwise normally ends in ChannelOutboundBuffer.remove(), but using send()
                // here allows the close() in remove() to become a no-op.
                handleOutboundMessage(((Resource<?>) msg).send().receive());
            } else {
                handleOutboundMessage(ReferenceCountUtil.retain(msg));
            }
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
            public RecvBufferAllocator.Handle recvBufAllocHandle() {
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

            private void mayRunPendingTasks() {
                if (!((EmbeddedEventLoop) executor()).running) {
                    runPendingTasks();
                }
            }

            @Override
            public void register(Promise<Void> promise) {
                EmbeddedUnsafe.this.register(promise);
                mayRunPendingTasks();
            }

            @Override
            public void bind(SocketAddress localAddress, Promise<Void> promise) {
                EmbeddedUnsafe.this.bind(localAddress, promise);
                mayRunPendingTasks();
            }

            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
                EmbeddedUnsafe.this.connect(remoteAddress, localAddress, promise);
                mayRunPendingTasks();
            }

            @Override
            public void disconnect(Promise<Void> promise) {
                EmbeddedUnsafe.this.disconnect(promise);
                mayRunPendingTasks();
            }

            @Override
            public void close(Promise<Void> promise) {
                EmbeddedUnsafe.this.close(promise);
                mayRunPendingTasks();
            }

            @Override
            public void closeForcibly() {
                EmbeddedUnsafe.this.closeForcibly();
                mayRunPendingTasks();
            }

            @Override
            public void deregister(Promise<Void> promise) {
                EmbeddedUnsafe.this.deregister(promise);
                mayRunPendingTasks();
            }

            @Override
            public void beginRead() {
                EmbeddedUnsafe.this.beginRead();
                mayRunPendingTasks();
            }

            @Override
            public void write(Object msg, Promise<Void> promise) {
                EmbeddedUnsafe.this.write(msg, promise);
                mayRunPendingTasks();
            }

            @Override
            public void flush() {
                EmbeddedUnsafe.this.flush();
                mayRunPendingTasks();
            }

            @Override
            public ChannelOutboundBuffer outboundBuffer() {
                return EmbeddedUnsafe.this.outboundBuffer();
            }
        };

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
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
