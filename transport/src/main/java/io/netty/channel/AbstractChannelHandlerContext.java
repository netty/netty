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
package io.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.channel.ChannelHandlerMask.MASK_ALL_INBOUND;
import static io.netty.channel.ChannelHandlerMask.MASK_ALL_OUTBOUND;
import static io.netty.channel.ChannelHandlerMask.MASK_BIND;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_ACTIVE;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_INACTIVE;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_READ;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_REGISTERED;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_SHUTDOWN;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_UNREGISTERED;
import static io.netty.channel.ChannelHandlerMask.MASK_CHANNEL_WRITABILITY_CHANGED;
import static io.netty.channel.ChannelHandlerMask.MASK_CLOSE;
import static io.netty.channel.ChannelHandlerMask.MASK_CONNECT;
import static io.netty.channel.ChannelHandlerMask.MASK_DEREGISTER;
import static io.netty.channel.ChannelHandlerMask.MASK_DISCONNECT;
import static io.netty.channel.ChannelHandlerMask.MASK_EXCEPTION_CAUGHT;
import static io.netty.channel.ChannelHandlerMask.MASK_FLUSH;
import static io.netty.channel.ChannelHandlerMask.MASK_PENDING_OUTBOUND_BYTES;
import static io.netty.channel.ChannelHandlerMask.MASK_READ;
import static io.netty.channel.ChannelHandlerMask.MASK_REGISTER;
import static io.netty.channel.ChannelHandlerMask.MASK_SHUTDOWN;
import static io.netty.channel.ChannelHandlerMask.MASK_USER_EVENT_TRIGGERED;
import static io.netty.channel.ChannelHandlerMask.MASK_WRITE;
import static io.netty.channel.ChannelHandlerMask.mask;

abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannelHandlerContext.class);
    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractChannelHandlerContext.class, "handlerState");

    /**
     * {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} is about to be called.
     */
    private static final int ADD_PENDING = 1;
    /**
     * {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} was called.
     */
    private static final int ADD_COMPLETE = 2;
    /**
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int REMOVE_COMPLETE = 3;
    /**
     * Neither {@link ChannelHandler#handlerAdded(ChannelHandlerContext)}
     * nor {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int INIT = 0;

    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final int executionMask;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
    private Tasks contextTasks;

    private volatile int handlerState = INIT;
    private final EventExecutor executor;
    private long currentPendingBytes = -1;

    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline,
                                  String name, Class<? extends ChannelHandler> handlerClass) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.pipeline = pipeline;
        executionMask = mask(handlerClass);
        this.executor = pipeline.executor();
    }

    @Override
    public Channel channel() {
        return pipeline.channel();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public EventExecutor executor() {
        return executor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void fireChannelRegistered() {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_REGISTERED);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelRegistered(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelRegistered();
            }
        } else {
            next.executor().execute(this::fireChannelRegistered);
        }
    }

    @Override
    public void fireChannelUnregistered() {
        final AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_UNREGISTERED);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelUnregistered(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelUnregistered();
            }
        } else {
            next.executor().execute(this::fireChannelUnregistered);
        }
    }

    @Override
    public void fireChannelActive() {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_ACTIVE);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelActive(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelActive();
            }
        } else {
            next.executor().execute(this::fireChannelActive);
        }
    }

    @Override
    public void fireChannelInactive() {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_INACTIVE);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelInactive(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelInactive();
            }
        } else {
            next.executor().execute(this::fireChannelInactive);
        }
    }

    @Override
    public void fireExceptionCaught(final Throwable cause) {
        AbstractChannelHandlerContext next = findContextInbound(MASK_EXCEPTION_CAUGHT);
        ObjectUtil.checkNotNull(cause, "cause");
        if (next.executor().inEventLoop()) {
            next.invokeFireExceptionCaught(cause);
        } else {
            try {
                next.executor().execute(() -> fireExceptionCaught(cause));
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
    }

    private void invokeFireExceptionCaught(final Throwable cause) {
        if (invokeHandler()) {
            try {
                saveCurrentPendingBytesIfNeeded();
                ((ChannelInboundHandler) handler()).exceptionCaught(this, cause);
            } catch (Throwable error) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "An exception " +
                        "was thrown by a user handler's exceptionCaught() " +
                        "method while handling the following exception:", cause);
                } else if (logger.isWarnEnabled()) {
                    logger.warn(
                        "An exception '{}' [enable DEBUG level for full stacktrace] " +
                        "was thrown by a user handler's exceptionCaught() " +
                        "method while handling the following exception:", error, cause);
                }
            } finally {
                updatePendingBytesIfNeeded();
            }
        } else {
            fireExceptionCaught(cause);
        }
    }

    @Override
    public void fireUserEventTriggered(final Object event) {
        ObjectUtil.checkNotNull(event, "event");
        AbstractChannelHandlerContext next = findContextInbound(MASK_USER_EVENT_TRIGGERED);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).userEventTriggered(next, event);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                fireUserEventTriggered(event);
            }
        } else {
            next.executor().execute(() -> fireUserEventTriggered(event));
        }
    }

    @Override
    public void fireChannelRead(final Object msg) {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_READ);
        if (next.executor().inEventLoop()) {
            final Object m = pipeline.touch(msg, next);
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelRead(next, m);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelRead(m);
            }
        } else {
            next.executor().execute(() -> fireChannelRead(msg));
        }
    }

    @Override
    public void fireChannelReadComplete() {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_READ_COMPLETE);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelReadComplete(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelReadComplete();
            }
        } else {
            next.executor().execute(getContextTasks().fireChannelReadCompleteTask);
        }
    }

    @Override
    public void fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelWritabilityChanged(next);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelWritabilityChanged();
            }
        } else {
            next.executor().execute(getContextTasks().fireChannelWritabilityChangedTask);
        }
    }

    @Override
    public void fireChannelShutdown(ChannelShutdownType type) {
        AbstractChannelHandlerContext next = findContextInbound(MASK_CHANNEL_SHUTDOWN);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelInboundHandler) next.handler()).channelShutdown(next, type);
                } catch (Throwable t) {
                    next.invokeFireExceptionCaught(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.fireChannelShutdown(type);
            }
        } else {
            next.executor().execute(() -> fireChannelShutdown(type));
        }
    }

    /**
     * Check if the given {@link CompletionHandler} is using the same {@link EventExecutor} as this
     * {@link ChannelHandlerContext} and if not return a new {@link CompletionHandler} that runs on the same
     * {@link EventExecutor} as this {@link ChannelHandlerContext}. The result of the new
     * {@link CompletionHandler} is cascaded to the old {@link CompletionHandler}.
     *
     * This is done to ensure that {@link FutureListener}s that are added to the {@link Promise} by an
     * {@link ChannelOutboundHandler} are executed in the same thread as the handler itself. By doing so we can
     * ensure that there are not issues even if fields etc that are stored in the handler are modified by the listener.
     */
    private CompletionHandler<Void> ensureCompletionHandlerUseCorrectExecutor(CompletionHandler<Void> handler) {
        if  (handler instanceof Promise<?>) {
            Promise<Void> p = (Promise<Void>) handler;
            if (!p.executor().inEventLoop()) {
                Promise<Void> newPromise = newPromise();
                newPromise.addHandler(handler);
                return newPromise.toCompletionHandler();
            }
        }

        return handler;
    }

    @Override
    public void register(CompletionHandler<Void> handler) {
        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_REGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).register(next, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.register(handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> register(h), h, null, false);
        }
    }

    @Override
    public void bind(final SocketAddress localAddress, CompletionHandler<Void> handler) {
        ObjectUtil.checkNotNull(localAddress, "localAddress");
        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_BIND);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).bind(next, localAddress, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.bind(localAddress, handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> bind(localAddress, h), h, null, false);
        }
    }

    @Override
    public void connect(SocketAddress remoteAddress, CompletionHandler<Void> handler) {
        connect(remoteAddress, null, handler);
    }

    @Override
    public void connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, CompletionHandler<Void> handler) {
        ObjectUtil.checkNotNull(remoteAddress, "remoteAddress");

        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).connect(next, remoteAddress, localAddress, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.connect(remoteAddress, localAddress, handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> connect(remoteAddress, localAddress, h), h, null, false);
        }
    }

    @Override
    public void disconnect(CompletionHandler<Void> handler) {
        if (!pipeline.hasDisconnect) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            close(handler);
            return;
        }
        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DISCONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).disconnect(next, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.disconnect(handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> disconnect(h), h, null, false);
        }
    }

    @Override
    public void close(CompletionHandler<Void> handler) {
        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CLOSE);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).close(next, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.close(handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> close(h), h, null, false);
        }
    }

    @Override
    public void deregister(CompletionHandler<Void> handler) {
        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DEREGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).deregister(next, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.deregister(handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> deregister(h), h, null, false);
        }
    }

    @Override
    public void shutdown(ChannelShutdownType type, CompletionHandler<Void> handler) {
        ObjectUtil.checkNotNull(type, "type");

        if (isNotValidCompletionHandler(handler)) {
            // cancelled
            return;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_SHUTDOWN);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).shutdown(next, type, handler);
                } catch (Throwable t) {
                    handler.onFailure(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.shutdown(type, handler);
            }
        } else {
            final CompletionHandler<Void> h = handler;
            safeExecute(executor, () -> shutdown(type, h), h, null, false);
        }
    }

    @Override
    public void read() {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_READ);
        if (next.executor().inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).read(next);
                } catch (Throwable t) {
                    handleFatalOutboundHandlerException(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.read();
            }
        } else {
            next.executor().execute(getContextTasks().readTask);
        }
    }

    @Override
    public Future<Void> write(Object msg) {
        Promise<Void> promise = newPromise();
        write(msg, false, promise.toCompletionHandler());
        return promise;
    }

    @Override
    public void write(final Object msg, final CompletionHandler<Void> handler) {
        write(msg, false, handler);
    }

    @Override
    public void flush() {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_FLUSH);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).flush(next);
                } catch (Throwable t) {
                    handleFatalOutboundHandlerException(t);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.flush();
            }
        } else {
            safeExecute(executor, getContextTasks().flushTask, CompletionHandler.ignore(), null, false);
        }
    }

    @Override
    public void writeAndFlush(Object msg, CompletionHandler<Void> handler) {
        write(msg, true, handler);
    }

    void write(Object msg, boolean flush, CompletionHandler<Void> handler) {
        if (validateWrite(msg, handler)) {
            final AbstractChannelHandlerContext next = findContextOutbound(flush ?
                    MASK_WRITE | MASK_FLUSH : MASK_WRITE);
            final Object m = pipeline.touch(msg, next);
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                if (next.invokeHandler()) {
                    handler = ensureCompletionHandlerUseCorrectExecutor(handler);
                    try {
                        next.saveCurrentPendingBytesIfNeeded();
                        ((ChannelOutboundHandler) next.handler()).write(next, msg, handler);
                    } catch (Throwable t) {
                        handler.onFailure(t);
                    } finally {
                        next.updatePendingBytesIfNeeded();
                    }
                    if (flush) {
                        try {
                            next.saveCurrentPendingBytesIfNeeded();
                            ((ChannelOutboundHandler) next.handler()).flush(next);
                        } catch (Throwable t) {
                            handleFatalOutboundHandlerException(t);
                        } finally {
                            next.updatePendingBytesIfNeeded();
                        }
                    }
                } else {
                    next.write(msg, flush, handler);
                }
            } else {
                final WriteTask task = WriteTask.newInstance(this, m, handler, flush);
                if (task != null) {
                    if (!safeExecute(executor, task, handler, m, !flush)) {
                        // We failed to submit the WriteTask. We need to cancel it so we decrement the pending bytes
                        // and put it back in the Recycler for re-use later.
                        //
                        // See https://github.com/netty/netty/issues/8343.
                        task.cancel();
                    }
                }
            }
        }
    }

    private boolean validateWrite(Object msg, CompletionHandler<Void> handler) {
        ObjectUtil.checkNotNull(msg, "msg");
        try {
            if (isNotValidCompletionHandler(handler)) {
                ReferenceCountUtil.release(msg);
                return false; // cancelled
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }
        return true;
    }

    private void handleFatalOutboundHandlerException(Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn(
                    "An exception was thrown by an ChannelOutboundHandler" +
                            " which can't be handled, closing the channel.", cause);
        }
        close();
    }

    private boolean isNotValidCompletionHandler(CompletionHandler<Void> handler) {
        ObjectUtil.checkNotNull(handler, "handler");

        if (!(handler instanceof Promise<?>)) {
            return false;
        }
        Promise<?> promise = (Promise<?>) handler;
        if (promise.isDone()) {
            // Check if the promise was cancelled and if so signal that the processing of the operation
            // should not be performed.
            //
            // See https://github.com/netty/netty/issues/2349
            if (promise.isCancelled()) {
                return true;
            }
            throw new IllegalArgumentException("promise already done: " + promise);
        }

        if (promise.getClass() == DefaultPromise.class) {
            return false;
        }

        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(AbstractChannel.CloseFuture.class) + " not allowed in a pipeline");
        }
        return false;
    }

    private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        EventExecutor currentExecutor = executor();
        do {
            ctx = ctx.next;
        } while (skipContext(ctx, currentExecutor, mask, MASK_ALL_INBOUND));
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        EventExecutor currentExecutor = executor();
        do {
            ctx = ctx.prev;
        } while (skipContext(ctx, currentExecutor, mask, MASK_ALL_OUTBOUND));
        return ctx;
    }

    private static boolean skipContext(
            AbstractChannelHandlerContext ctx, EventExecutor currentExecutor, int mask, int onlyMask) {
        // Ensure we correctly handle MASK_EXCEPTION_CAUGHT which is not included in the MASK_EXCEPTION_CAUGHT
        return (ctx.executionMask & (onlyMask | mask)) == 0 ||
                // We can only skip if the EventExecutor is the same as otherwise we need to ensure we offload
                // everything to preserve ordering.
                //
                // See https://github.com/netty/netty/issues/10067
                (ctx.executor() == currentExecutor && (ctx.executionMask & mask) == 0);
    }

    final void setRemoved() {
        handlerState = REMOVE_COMPLETE;
    }

    final boolean setAddComplete() {
        for (;;) {
            int oldState = handlerState;
            if (oldState == REMOVE_COMPLETE) {
                return false;
            }
            // Ensure we never update when the handlerState is REMOVE_COMPLETE already.
            // oldState is usually ADD_PENDING but can also be REMOVE_COMPLETE when an EventExecutor is used that is not
            // exposing ordering guarantees.
            if (HANDLER_STATE_UPDATER.compareAndSet(this, oldState, ADD_COMPLETE)) {
                return true;
            }
        }
    }

    final void setAddPending() {
        boolean updated = HANDLER_STATE_UPDATER.compareAndSet(this, INIT, ADD_PENDING);
        assert updated; // This should always be true as it MUST be called before setAddComplete() or setRemoved().
    }

    final void callHandlerAdded() throws Exception {
        // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
        // any pipeline events ctx.handler() will miss them because the state will not allow it.
        if (setAddComplete()) {
            handler().handlerAdded(this);
        }
    }

    final void callHandlerRemoved(Throwable cause) throws Exception {
        try {
            // Only call handlerRemoved(...) if we called handlerAdded(...) before.
            if (handlerState == ADD_COMPLETE) {
                long pending = 0;
                try {
                    if (cause == null && (executionMask & MASK_PENDING_OUTBOUND_BYTES) != 0) {
                        pending = currentPendingBytes((ChannelOutboundHandler) handler());
                    }
                } finally {
                    try {
                        handler().handlerRemoved(this);
                    } finally {
                        if (pending > 0) {
                            pipeline.decrementPendingOutboundBytes(pending);
                        }
                    }
                }
            }
        } finally {
            // Mark the handler as removed in any case.
            setRemoved();
        }
    }

    /**
     * Makes best possible effort to detect if {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} was called
     * yet. If not return {@code false} and if called or could not detect return {@code true}.
     *
     * If this method returns {@code false} we will not invoke the {@link ChannelHandler} but just forward the event.
     * This is needed as {@link DefaultChannelPipeline} may already put the {@link ChannelHandler} in the linked-list
     * but not called {@link ChannelHandler#handlerAdded(ChannelHandlerContext)}.
     */
    boolean invokeHandler() {
        // Store in local variable to reduce volatile reads.
        int handlerState = this.handlerState;
        return handlerState == ADD_COMPLETE;
    }

    @Override
    public boolean isRemoved() {
        return handlerState == REMOVE_COMPLETE;
    }

    private static boolean safeExecute(EventExecutor executor, Runnable runnable,
            CompletionHandler<Void> handler, Object msg, boolean lazy) {
        try {
            if (lazy && executor instanceof AbstractEventExecutor) {
                ((AbstractEventExecutor) executor).lazyExecute(runnable);
            } else {
                executor.execute(runnable);
            }
            return true;
        } catch (Throwable cause) {
            try {
                if (msg != null) {
                    ReferenceCountUtil.release(msg);
                }
            } finally {
                handler.onFailure(cause);
            }
            return false;
        }
    }

    @Override
    public String toHintString() {
        return '\'' + name + "' will handle the message from this point.";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ChannelHandlerContext.class) + '(' + name + ", " + channel() + ')';
    }

    Tasks getContextTasks() {
        Tasks tasks = contextTasks;
        if (tasks == null) {
            contextTasks = tasks = new Tasks(this);
        }
        return tasks;
    }

    static final class WriteTask implements Runnable {
        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle<WriteTask> handle) {
                return new WriteTask(handle);
            }
        };

        static WriteTask newInstance(AbstractChannelHandlerContext ctx,
                Object msg, CompletionHandler<Void> handler, boolean flush) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, handler, flush);

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                try {
                    task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                    ctx.pipeline.incrementPendingOutboundBytes(task.size);
                } catch (Throwable t) {
                    ReferenceCountUtil.release(msg);
                    handler.onFailure(t);
                    task.recycle();
                    return null;
                }
            }

            return task;
        }

        private static final boolean ESTIMATE_TASK_SIZE_ON_SUBMIT =
                SystemPropertyUtil.getBoolean("io.netty.transport.estimateSizeOnSubmit", true);

        // Assuming compressed oops, 12 bytes obj header, 4 ref fields and one int field
        private static final int WRITE_TASK_OVERHEAD =
                SystemPropertyUtil.getInt("io.netty.transport.writeTaskSizeOverhead", 32);

        private final Handle<WriteTask> handle;
        private AbstractChannelHandlerContext ctx;
        private Object msg;
        private CompletionHandler<Void> handler;
        private int size;
        private boolean flush;

        private WriteTask(Handle<WriteTask> handle) {
            this.handle = handle;
        }

        static void init(WriteTask task, AbstractChannelHandlerContext ctx,
                                   Object msg, CompletionHandler<Void> handler, boolean flush) {
            task.ctx = ctx;
            task.msg = msg;
            task.handler = handler;
            task.flush = flush;
            task.size = 0;
        }

        @Override
        public void run() {
            try {
                decrementPendingOutboundBytes();
                ctx.write(msg, flush, handler);
            } finally {
                recycle();
            }
        }

        void cancel() {
            try {
                decrementPendingOutboundBytes();
            } finally {
                recycle();
            }
        }

        private void decrementPendingOutboundBytes() {
            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                ctx.pipeline.decrementPendingOutboundBytes(size);
            }
        }

        private void recycle() {
            // Set to null so the GC can collect them directly
            ctx = null;
            msg = null;
            handler = null;
            handle.recycle(this);
        }
    }

    static final class Tasks {
        private final Runnable fireChannelReadCompleteTask;
        private final Runnable readTask;
        private final Runnable fireChannelWritabilityChangedTask;
        private final Runnable flushTask;

        Tasks(AbstractChannelHandlerContext ctx) {
            fireChannelReadCompleteTask = ctx::fireChannelReadComplete;
            readTask = ctx::read;
            fireChannelWritabilityChangedTask = ctx::fireChannelWritabilityChanged;
            flushTask = ctx::flush;
        }
    }
    private long currentPendingBytes(ChannelOutboundHandler handler) {
        long pending = handler.pendingOutboundBytes(this);
        if (pending < 0) {
            pipeline.closeTransport();
            throw new IllegalStateException(StringUtil.simpleClassName(handler().getClass()) +
                    ".pendingOutboundBytes(ChannelHandlerContext) returned a negative value: " + pending +
                    ". Force closed transport.");
        }
        return pending;
    }

    private void saveCurrentPendingBytesIfNeeded() {
        // We only save the current pending bytes if not already done before.
        // This is important as otherwise we might run into issues in case of reentrancy.
        if (currentPendingBytes == -1 && (executionMask & MASK_PENDING_OUTBOUND_BYTES) != 0) {
            currentPendingBytes = currentPendingBytes((ChannelOutboundHandler) handler());
        }
    }

    private void updatePendingBytesIfNeeded() {
        if ((executionMask & MASK_PENDING_OUTBOUND_BYTES) == 0) {
            assert currentPendingBytes == -1;
            return;
        }
        long current = currentPendingBytes;
        if (current == -1) {
            return;
        }
        this.currentPendingBytes = -1;
        long newPendingBytes = currentPendingBytes((ChannelOutboundHandler) handler());
        long delta = current - newPendingBytes;
        if (delta == 0) {
            // No changes
            return;
        }
        if (delta > 0) {
            pipeline.decrementPendingOutboundBytes(delta);
        } else {
            pipeline.incrementPendingOutboundBytes(-delta);
        }
    }
}
