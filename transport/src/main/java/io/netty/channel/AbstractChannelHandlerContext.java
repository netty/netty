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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PromiseNotificationUtil;
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

    private ChannelFuture succeededFuture;

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
    public ChannelHandlerContext fireChannelRegistered() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
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
        return this;
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
    public ChannelHandlerContext fireUserEventTriggered(final Object event) {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
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
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelShutdown(ChannelShutdownType type) {
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
        return this;
    }

    @Override
    public ChannelFuture register() {
        return register(newPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_REGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).register(next, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.register(promise);
            }
        } else {
            safeExecute(executor, () -> register(promise), promise, null, false);
        }

        return promise;
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(localAddress, "localAddress");
        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_BIND);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).bind(next, localAddress, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.bind(localAddress, promise);
            }
        } else {
            safeExecute(executor, () -> bind(localAddress, promise), promise, null, false);
        }
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(remoteAddress, "remoteAddress");

        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).connect(next, remoteAddress, localAddress, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.connect(remoteAddress, localAddress, promise);
            }
        } else {
            safeExecute(executor, () -> connect(remoteAddress, localAddress, promise), promise, null, false);
        }
        return promise;
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        if (!pipeline.hasDisconnect) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            return close(promise);
        }
        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DISCONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).disconnect(next, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.disconnect(promise);
            }
        } else {
            safeExecute(executor, () -> disconnect(promise), promise, null, false);
        }
        return promise;
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CLOSE);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).close(next, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.close(promise);
            }
        } else {
            safeExecute(executor, () -> close(promise), promise, null, false);
        }

        return promise;
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DEREGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).deregister(next, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.deregister(promise);
            }
        } else {
            safeExecute(executor, () -> deregister(promise), promise, null, false);
        }

        return promise;
    }

    @Override
    public ChannelFuture shutdown(ChannelShutdownType type, ChannelPromise promise) {
        ObjectUtil.checkNotNull(type, "type");

        if (isNotValidPromise(promise)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(MASK_SHUTDOWN);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (next.invokeHandler()) {
                try {
                    next.saveCurrentPendingBytesIfNeeded();
                    ((ChannelOutboundHandler) next.handler()).shutdown(next, type, promise);
                } catch (Throwable t) {
                    notifyOutboundHandlerException(t, promise);
                } finally {
                    next.updatePendingBytesIfNeeded();
                }
            } else {
                next.shutdown(type, promise);
            }
        } else {
            safeExecute(executor, () -> shutdown(type, promise), promise, null, false);
        }

        return promise;
    }

    @Override
    public ChannelHandlerContext read() {
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
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        ChannelPromise promise = newPromise();
        write(msg, false, promise);
        return promise;
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        write(msg, false, promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext flush() {
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
            safeExecute(executor, getContextTasks().flushTask, channel().newPromise(), null, false);
        }

        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        write(msg, true, promise);
        return promise;
    }

    void write(Object msg, boolean flush, ChannelPromise promise) {
        if (validateWrite(msg, promise)) {
            final AbstractChannelHandlerContext next = findContextOutbound(flush ?
                    MASK_WRITE | MASK_FLUSH : MASK_WRITE);
            final Object m = pipeline.touch(msg, next);
            EventExecutor executor = next.executor();
            if (executor.inEventLoop()) {
                if (next.invokeHandler()) {
                    try {
                        next.saveCurrentPendingBytesIfNeeded();
                        ((ChannelOutboundHandler) next.handler()).write(next, msg, promise);
                    } catch (Throwable t) {
                        notifyOutboundHandlerException(t, promise);
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
                    next.write(msg, flush, promise);
                }
            } else {
                final WriteTask task = WriteTask.newInstance(this, m, promise, flush);
                if (task != null) {
                    if (!safeExecute(executor, task, promise, m, !flush)) {
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

    private boolean validateWrite(Object msg, ChannelPromise promise) {
        ObjectUtil.checkNotNull(msg, "msg");
        try {
            if (isNotValidPromise(promise)) {
                ReferenceCountUtil.release(msg);
                return false; // cancelled
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }
        return true;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        // Only log if the given promise is not of type VoidChannelPromise as tryFailure(...) is expected to return
        // false.
        PromiseNotificationUtil.tryFailure(promise, cause, logger);
    }

    private void handleFatalOutboundHandlerException(Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn(
                    "An exception was thrown by an ChannelOutboundHandler" +
                            " which can't be handled, closing the channel.", cause);
        }
        close();
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        ChannelFuture succeededFuture = this.succeededFuture;
        if (succeededFuture == null) {
            this.succeededFuture = succeededFuture = new SucceededChannelFuture(channel(), executor());
        }
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel(), executor(), cause);
    }

    private boolean isNotValidPromise(ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");

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

        if (promise.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), channel()));
        }

        if (promise.getClass() == DefaultChannelPromise.class) {
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
            ChannelPromise promise, Object msg, boolean lazy) {
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
                promise.setFailure(cause);
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
                Object msg, ChannelPromise promise, boolean flush) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, promise, flush);

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                try {
                    task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                    ctx.pipeline.incrementPendingOutboundBytes(task.size);
                } catch (Throwable t) {
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(t);
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
        private ChannelPromise promise;
        private int size;
        private boolean flush;

        private WriteTask(Handle<WriteTask> handle) {
            this.handle = handle;
        }

        static void init(WriteTask task, AbstractChannelHandlerContext ctx,
                                   Object msg, ChannelPromise promise, boolean flush) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;
            task.flush = flush;
            task.size = 0;
        }

        @Override
        public void run() {
            try {
                decrementPendingOutboundBytes();
                ctx.write(msg, flush, promise);
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
            promise = null;
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
