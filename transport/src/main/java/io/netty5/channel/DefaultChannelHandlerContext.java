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
package io.netty5.channel;

import io.netty5.util.Resource;
import io.netty5.util.ResourceLeakHint;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.ObjectPool;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.netty5.channel.ChannelHandlerMask.MASK_BIND;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_ACTIVE;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_INACTIVE;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_INBOUND_EVENT;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_READ;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_READ_COMPLETE;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_REGISTERED;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_SHUTDOWN;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_UNREGISTERED;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_WRITABILITY_CHANGED;
import static io.netty5.channel.ChannelHandlerMask.MASK_CLOSE;
import static io.netty5.channel.ChannelHandlerMask.MASK_CONNECT;
import static io.netty5.channel.ChannelHandlerMask.MASK_DEREGISTER;
import static io.netty5.channel.ChannelHandlerMask.MASK_DISCONNECT;
import static io.netty5.channel.ChannelHandlerMask.MASK_FLUSH;
import static io.netty5.channel.ChannelHandlerMask.MASK_PENDING_OUTBOUND_BYTES;
import static io.netty5.channel.ChannelHandlerMask.MASK_READ;
import static io.netty5.channel.ChannelHandlerMask.MASK_REGISTER;
import static io.netty5.channel.ChannelHandlerMask.MASK_SEND_OUTBOUND_EVENT;
import static io.netty5.channel.ChannelHandlerMask.MASK_SHUTDOWN;
import static io.netty5.channel.ChannelHandlerMask.MASK_WRITE;
import static io.netty5.channel.ChannelHandlerMask.mask;
import static java.util.Objects.requireNonNull;

final class DefaultChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final Logger logger = LoggerFactory.getLogger(DefaultChannelHandlerContext.class);

    /**
     * Neither {@link ChannelHandler#handlerAdded(ChannelHandlerContext)}
     * nor {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int INIT = 0;

    /**
     * {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} was called.
     */
    private static final int ADD_COMPLETE = 1;

    /**
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} is about to be called.
     */
    private static final int REMOVE_STARTED = 2;

    /**
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int REMOVE_COMPLETE = 3;

    private final int executionMask;
    private final DefaultChannelPipeline pipeline;
    private final ChannelHandler handler;
    private final String name;

    // Is null if the ChannelHandler not implements pendingOutboundBytes(...).
    private final DefaultChannelHandlerContextAwareEventExecutor executor;
    private final EventExecutor pipelineExecutor;
    private long currentPendingBytes;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances than needed.
    private Tasks invokeTasks;
    private int handlerState = INIT;

    private volatile boolean removed;

    DefaultChannelHandlerContext next;
    DefaultChannelHandlerContext prev;

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name,
                                 ChannelHandler handler) {
        this.name = requireNonNull(name, "name");
        this.pipeline = pipeline;
        executionMask = mask(handler.getClass());
        this.handler = handler;
        // Wrap the executor if the ChannelHandler implements pendingOutboundBytes(ChannelHandlerContext) so we are
        // sure that the pending bytes will be updated correctly in all cases. Otherwise, we don't need any special
        // wrapping and so can save some work (which is true most of the time).
        executor = handlesPendingOutboundBytes(executionMask) ?
                new DefaultChannelHandlerContextAwareEventExecutor(pipeline.executor(), this) : null;
        pipelineExecutor = pipeline.executor();
    }

    private static boolean handlesPendingOutboundBytes(int mask) {
        return (mask & MASK_PENDING_OUTBOUND_BYTES) != 0;
    }

    private static Future<Void> failRemoved(DefaultChannelHandlerContext ctx) {
        return ctx.newFailedFuture(newRemovedException(ctx, null));
    }

    private void notifyHandlerRemovedAlready() {
        notifyHandlerRemovedAlready(null);
    }

    private void notifyHandlerRemovedAlready(Throwable cause) {
        pipeline().fireChannelExceptionCaught(newRemovedException(this, cause));
    }

    private static ChannelPipelineException newRemovedException(ChannelHandlerContext ctx, Throwable cause) {
        return new ChannelPipelineException("Context " + ctx + " already removed", cause);
    }

    private Tasks invokeTasks() {
        Tasks tasks = invokeTasks;
        if (tasks == null) {
            invokeTasks = tasks = new Tasks(this);
        }
        return tasks;
    }

    @Override
    public EventExecutor executor() {
        return executor == null ? pipeline().executor() : executor;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public String name() {
        return name;
    }

    private EventExecutor originalExecutor() {
        return pipelineExecutor;
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_REGISTERED);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else {
                try {
                    if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                        ctx.handler().channelRegistered(ctx);
                    }
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            executor.execute(this::fireChannelRegistered);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_UNREGISTERED);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelUnregistered(ctx);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            executor.execute(this::fireChannelUnregistered);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_ACTIVE);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelActive(ctx);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            executor.execute(this::fireChannelActive);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_INACTIVE);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelInactive(ctx);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            executor.execute(this::fireChannelInactive);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelShutdown(ChannelShutdownDirection direction) {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_SHUTDOWN);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelShutdown(ctx, direction);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            executor.execute(() -> fireChannelShutdown(direction));
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelExceptionCaught(Throwable cause) {
        requireNonNull(cause, "cause");
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelExceptionCaught(cause);
        } else {
            try {
                executor.execute(() -> findAndInvokeChannelExceptionCaught(cause));
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
        return this;
    }

    private void findAndInvokeChannelExceptionCaught(Throwable cause) {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_EXCEPTION_CAUGHT);
        if (ctx == null) {
            notifyHandlerRemovedAlready(cause);
            return;
        }
        ctx.invokeChannelExceptionCaught(cause);
    }

    void invokeChannelExceptionCaught(final Throwable cause) {
        if (!saveCurrentPendingBytesIfNeededInbound()) {
            return;
        }
        try {
            handler().channelExceptionCaught(this, cause);
        } catch (Throwable error) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "An exception {}" +
                                "was thrown by a user handler's exceptionCaught() " +
                                "method while handling the following exception:",
                        ThrowableUtil.stackTraceToString(error), cause);
            } else if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception '{}' [enable DEBUG level for full stacktrace] " +
                                "was thrown by a user handler's exceptionCaught() " +
                                "method while handling the following exception:", error, cause);
            }
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInboundEvent(Object event) {
        requireNonNull(event, "event");
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_INBOUND_EVENT);
            if (ctx == null) {
                Resource.dispose(event);
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelInboundEvent(ctx, event);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            } else {
                Resource.dispose(event);
            }
        } else {
            executor.execute(() -> fireChannelInboundEvent(event));
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        requireNonNull(msg, "msg");
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ);
            if (ctx == null) {
                Resource.dispose(msg);
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                final Object m = ctx.pipeline.touch(requireNonNull(msg, "msg"), ctx);
                try {
                    ctx.handler().channelRead(ctx, m);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            } else {
                Resource.dispose(msg);
            }
        } else {
            try {
                executor.execute(() -> fireChannelRead(msg));
            } catch (Throwable cause) {
                Resource.dispose(msg);
                throw cause;
            }
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ_COMPLETE);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelReadComplete(ctx);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelReadCompleteTask);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED);
            if (ctx == null) {
                notifyHandlerRemovedAlready();
            } else if (ctx.saveCurrentPendingBytesIfNeededInbound()) {
                try {
                    ctx.handler().channelWritabilityChanged(ctx);
                } catch (Throwable t) {
                    ctx.invokeChannelExceptionCaught(t);
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelWritableStateChangedTask);
        }
        return this;
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress) {
        requireNonNull(localAddress, "localAddress");

        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeBind(localAddress);
        }

        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeBind(localAddress).cascadeTo(promise), promise, null);
        return promise.asFuture();
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, null);
    }

    @Override
    public Future<Void> deregister() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeDeregister();
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeDeregister().cascadeTo(promise), promise, null);
        return promise.asFuture();
    }
    private Future<Void> findAndInvokeBind(SocketAddress localAddress) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_BIND);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeBind(localAddress);
    }

    private Future<Void> invokeBind(SocketAddress localAddress) {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().bind(this, localAddress);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress) {
        requireNonNull(remoteAddress, "remoteAddress");
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeConnect(remoteAddress, localAddress);
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () ->
                findAndInvokeConnect(remoteAddress, localAddress).cascadeTo(promise), promise, null);

        return promise.asFuture();
    }

    private Future<Void> findAndInvokeConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_CONNECT);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeConnect(remoteAddress, localAddress);
    }

    private Future<Void> invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().connect(this, remoteAddress, localAddress);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> disconnect() {
        if (!pipeline.isTransportSupportingDisconnect()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            return close();
        }

        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeDisconnect();
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeDisconnect().cascadeTo(promise), promise, null);
        return promise.asFuture();
    }

    private Future<Void> findAndInvokeDisconnect() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_DISCONNECT);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeDisconnect();
    }

    private Future<Void> invokeDisconnect() {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().disconnect(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> close() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeClose();
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeClose().cascadeTo(promise), promise, null);
        return promise.asFuture();
    }

    private Future<Void> findAndInvokeClose() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_CLOSE);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeClose();
    }

    private Future<Void> invokeClose() {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().close(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, true);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> shutdown(ChannelShutdownDirection direction) {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeShutdown(direction);
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeShutdown(direction).cascadeTo(promise), promise, null);
        return promise.asFuture();
    }

    private Future<Void> findAndInvokeShutdown(ChannelShutdownDirection direction) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_SHUTDOWN);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeShutdown(direction);
    }

    private Future<Void> invokeShutdown(ChannelShutdownDirection direction) {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().shutdown(this, direction);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, true);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> register() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeRegister();
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeRegister().cascadeTo(promise), promise, null);
        return promise.asFuture();
    }

    private Future<Void> findAndInvokeRegister() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_REGISTER);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeRegister();
    }

    private Future<Void> invokeRegister() {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().register(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    private Future<Void> findAndInvokeDeregister() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_DEREGISTER);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeDeregister();
    }

    private Future<Void> invokeDeregister() {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return failed;
        }

        try {
            return handler().deregister(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext read() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeRead(DefaultChannelPipeline.DEFAULT_READ_BUFFER_ALLOCATOR);
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeReadTask);
        }
        return this;
    }

    @Override
    public ChannelHandlerContext read(ReadBufferAllocator readBufferAllocator) {
        requireNonNull(readBufferAllocator, "readBufferAllocator");
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeRead(readBufferAllocator);
        } else {
            executor.execute(() -> read(readBufferAllocator));
        }
        return this;
    }

    private void findAndInvokeRead(ReadBufferAllocator allocator) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_READ);
        if (ctx != null) {
            Future<Void> failed = ctx.saveCurrentPendingBytesIfNeededOutbound();
            if (failed != null) {
                return;
            }

            try {
                ctx.handler().read(ctx, allocator);
            } catch (Throwable t) {
                ctx.handleOutboundHandlerException(t, false);
            } finally {
                ctx.updatePendingBytesIfNeeded();
            }
        }
    }

    @Override
    public Future<Void> write(Object msg) {
        return write(msg, false);
    }

    private Future<Void> invokeWrite(Object msg) {
        final Object m = pipeline.touch(msg, this);
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            Resource.dispose(m);
            return failed;
        }

        try {
            return handler().write(this, m);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            DefaultChannelHandlerContext ctx = findContextOutbound(MASK_FLUSH);
            if (ctx != null) {
                ctx.invokeFlush();
            }
        } else {
            Tasks tasks = invokeTasks();
            Promise<Void> promise = newPromise();
            promise.asFuture().addListener(channel(), ChannelFutureListeners.FIRE_EXCEPTION_ON_FAILURE);
            // If flush throws we want to at least propagate the exception through the ChannelPipeline
            // as otherwise the user will not be made aware of the failure at all.
            safeExecute(executor, tasks.invokeFlushTask, promise, null);
        }

        return this;
    }

    private void invokeFlush() {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            return;
        }

        try {
            handler().flush(this);
        } catch (Throwable t) {
            handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        return write(msg, true);
    }

    private Future<Void> invokeWriteAndFlush(Object msg) {
        Future<Void> f = invokeWrite(msg);
        invokeFlush();
        return f;
    }

    private Future<Void> write(Object msg, boolean flush) {
        requireNonNull(msg, "msg");

        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            final DefaultChannelHandlerContext next = findContextOutbound(flush ?
                    MASK_WRITE | MASK_FLUSH : MASK_WRITE);
            if (next == null) {
                Resource.dispose(msg);
                return failRemoved(this);
            }
            if (flush) {
                return next.invokeWriteAndFlush(msg);
            }
            return next.invokeWrite(msg);
        } else {
            Promise<Void> promise  = newPromise();
            final AbstractWriteTask task;
            if (flush) {
                task = WriteAndFlushTask.newInstance(this, msg, promise);
            }  else {
                task = WriteTask.newInstance(this, msg, promise);
            }
            if (task != null && !safeExecute(executor, task, promise, msg)) {
                // We failed to submit the AbstractWriteTask. We need to cancel it so we decrement the pending bytes
                // and put it back in the Recycler for re-use later.
                //
                // See https://github.com/netty/netty/issues/8343.
                task.cancel();
            }
            return promise.asFuture();
        }
    }

    @Override
    public Future<Void> sendOutboundEvent(Object event) {
        EventExecutor executor = originalExecutor();
        if (executor.inEventLoop()) {
            return findAndInvokeSendOutboundEvent(event);
        }
        Promise<Void> promise  = newPromise();
        safeExecute(executor, () -> findAndInvokeSendOutboundEvent(event).cascadeTo(promise), promise, event);
        return promise.asFuture();
    }

    private Future<Void> findAndInvokeSendOutboundEvent(Object event) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_SEND_OUTBOUND_EVENT);
        if (ctx == null) {
            return failRemoved(this);
        }
        return ctx.invokeSendOutboundEvent(event);
    }

    private Future<Void> invokeSendOutboundEvent(Object event) {
        Future<Void> failed = saveCurrentPendingBytesIfNeededOutbound();
        if (failed != null) {
            Resource.dispose(event);
            return failed;
        }

        try {
            return handler().sendOutboundEvent(this, event);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    private Future<Void> handleOutboundHandlerException(Throwable cause, boolean closeDidThrow) {
        String msg = handler() + " threw an exception while handling an outbound event. This is most likely a bug";

        logger.warn("{}. Closing the channel.", msg, cause);
        if (closeDidThrow) {
            // Close itself did throw, just call close() directly and so have the next handler invoked. If we would
            // call close() on the Channel we would risk an infinite-loop.
            close();
        } else {
            // Let's close the channel. Calling close on the Channel ensure we start from the end of the pipeline
            // and so give all handlers the chance to do something during close.
            channel().close();
        }
        return newFailedFuture(new IllegalStateException(msg, cause));
    }

    private DefaultChannelHandlerContext findContextInbound(int mask) {
        DefaultChannelHandlerContext ctx = this;
        if (ctx.next == null) {
            return null;
        }
        do {
            ctx = ctx.next;
        } while ((ctx.executionMask & mask) == 0 || ctx.handlerState == REMOVE_STARTED);
        return ctx;
    }

    private DefaultChannelHandlerContext findContextOutbound(int mask) {
        DefaultChannelHandlerContext ctx = this;
        if (ctx.prev == null) {
            return null;
        }
        do {
            ctx = ctx.prev;
        } while ((ctx.executionMask & mask) == 0 || ctx.handlerState == REMOVE_STARTED);
        return ctx;
    }

    boolean setAddComplete() {
        // Ensure we never update when the handlerState is REMOVE_COMPLETE already.
        // oldState is usually ADD_PENDING but can also be REMOVE_COMPLETE when an EventExecutor is used that is not
        // exposing ordering guarantees.
        if (handlerState == INIT) {
            handlerState = ADD_COMPLETE;
            return true;
        }
        return false;
    }

    void callHandlerAdded() throws Exception {
        // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
        // any pipeline events ctx.handler() will miss them because the state will not allow it.
        if (setAddComplete()) {
            handler().handlerAdded(this);
            if (handlesPendingOutboundBytes(executionMask)) {
                long pending = pendingOutboundBytes();
                currentPendingBytes = Math.max(0, pending);
                if (pending > 0) {
                    pipeline.incrementPendingOutboundBytes(pending);
                }
            }
        }
    }

    void callHandlerRemoved() throws Exception {
        try {
            // Only call handlerRemoved(...) if we called handlerAdded(...) before.
            if (handlerState == ADD_COMPLETE) {
                handlerState = REMOVE_STARTED;
                try {
                    handler().handlerRemoved(this);
                } finally {
                    if (handlesPendingOutboundBytes(executionMask)) {
                        long pending = pendingOutboundBytes();
                        currentPendingBytes = Math.max(0, pending);
                        if (pending > 0) {
                            pipeline.decrementPendingOutboundBytes(pending);
                        }
                    }
                }
            }
        } finally {
            // Mark the handler as removed in any case.
            handlerState = REMOVE_COMPLETE;
            removed = true;
        }
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    void remove(boolean relink) {
        assert handlerState == REMOVE_COMPLETE;
        if (relink) {
            DefaultChannelHandlerContext prev = this.prev;
            DefaultChannelHandlerContext next = this.next;
            // prev and next may be null if the handler was never really added to the pipeline
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
        }

        prev = null;
        next = null;
    }

    static boolean safeExecute(EventExecutor executor, Runnable runnable, Promise<Void> promise, Object msg) {
        try {
            executor.execute(runnable);
            return true;
        } catch (Throwable cause) {
            try {
                if (msg != null) {
                    Resource.dispose(msg);
                }
            } finally {
                if (promise != null) {
                    promise.setFailure(cause);
                }
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

    private static final boolean ESTIMATE_TASK_SIZE_ON_SUBMIT =
            SystemPropertyUtil.getBoolean("io.netty5.transport.estimateSizeOnSubmit", true);

    // Assuming a 64-bit JVM, 16 bytes object header, 3 reference fields and one int field, plus alignment
    private static final int WRITE_TASK_OVERHEAD =
            SystemPropertyUtil.getInt("io.netty5.transport.writeTaskSizeOverhead", 48);

    abstract static class AbstractWriteTask implements Runnable {

        private final ObjectPool.Handle<AbstractWriteTask> handle;
        private DefaultChannelHandlerContext ctx;
        private Object msg;
        private Promise<Void> promise;
        private int size;

        @SuppressWarnings("unchecked")
        private AbstractWriteTask(ObjectPool.Handle<? extends AbstractWriteTask> handle) {
            this.handle = (ObjectPool.Handle<AbstractWriteTask>) handle;
        }

        /**
         * Init the given {@link AbstractWriteTask} if possible.
         *
         * @param task      the task.
         * @param ctx       the context.
         * @param msg       the message
         * @param promise   the promise that will be notified.
         * @return          {@code true} if the task could be init successfully, {@code false} otherwise. If the init
         *                  failed it will automatically release the msg, fail the promise and recycle the task itself.
         */
        protected static boolean init(AbstractWriteTask task, DefaultChannelHandlerContext ctx,
                                   Object msg, Promise<Void> promise) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                try {
                    ctx.pipeline.incrementPendingOutboundBytes(task.size);
                } catch (IllegalStateException e) {
                    task.recycle();
                    Resource.dispose(msg);
                    promise.setFailure(e);
                    return false;
                }
            } else {
                task.size = 0;
            }
            return true;
        }

        protected abstract DefaultChannelHandlerContext findContext(DefaultChannelHandlerContext ctx);
        @Override
        public final void run() {
            try {
                decrementPendingOutboundBytes();
                if (promise.isCancelled()) {
                    Resource.dispose(msg);
                    return;
                }
                DefaultChannelHandlerContext next = findContext(ctx);
                if (next == null) {
                    Resource.dispose(msg);
                    failRemoved(ctx).cascadeTo(promise);
                    return;
                }
                write(next, msg, promise);
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
                // Update the pending bytes
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

        protected void write(DefaultChannelHandlerContext ctx, Object msg, Promise<Void> promise) {
            ctx.invokeWrite(msg).cascadeTo(promise);
        }
    }

    static final class WriteTask extends AbstractWriteTask implements SingleThreadEventLoop.NonWakeupRunnable {

        private static final ObjectPool<WriteTask> RECYCLER = ObjectPool.newPool(WriteTask::new);

        static WriteTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, Promise<Void> promise) {
            WriteTask task = RECYCLER.get();
            if (!init(task, ctx, msg, promise)) {
                return null;
            }
            return task;
        }

        @Override
        protected DefaultChannelHandlerContext findContext(DefaultChannelHandlerContext ctx) {
            return ctx.findContextOutbound(MASK_WRITE);
        }

        private WriteTask(ObjectPool.Handle<WriteTask> handle) {
            super(handle);
        }
    }

    static final class WriteAndFlushTask extends AbstractWriteTask {

        private static final ObjectPool<WriteAndFlushTask> RECYCLER = ObjectPool.newPool(WriteAndFlushTask::new);

        static WriteAndFlushTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, Promise<Void> promise) {
            WriteAndFlushTask task = RECYCLER.get();
            if (!init(task, ctx, msg, promise)) {
                return null;
            }
            return task;
        }

        private WriteAndFlushTask(ObjectPool.Handle<WriteAndFlushTask> handle) {
            super(handle);
        }

        @Override
        protected DefaultChannelHandlerContext findContext(DefaultChannelHandlerContext ctx) {
            return ctx.findContextOutbound(MASK_WRITE | MASK_FLUSH);
        }

        @Override
        public void write(DefaultChannelHandlerContext ctx, Object msg, Promise<Void> promise) {
            super.write(ctx, msg, promise);
            ctx.invokeFlush();
        }
    }

    private static final class Tasks {
        private final Runnable invokeChannelReadCompleteTask;
        private final Runnable invokeReadTask;
        private final Runnable invokeChannelWritableStateChangedTask;
        private final Runnable invokeFlushTask;

        Tasks(DefaultChannelHandlerContext ctx) {
            invokeChannelReadCompleteTask = ctx::fireChannelReadComplete;
            invokeReadTask = ctx::read;
            invokeChannelWritableStateChangedTask = ctx::fireChannelWritabilityChanged;
            invokeFlushTask = ctx::flush;
        }
    }

    boolean saveCurrentPendingBytesIfNeededInbound() {
        IllegalStateException e = saveCurrentPendingBytesIfNeeded();
        if (e != null) {
            logger.error("Failed to save current pending bytes.", e);
            return false;
        }
        return true;
    }

    private Future<Void> saveCurrentPendingBytesIfNeededOutbound() {
        IllegalStateException e = saveCurrentPendingBytesIfNeeded();
        if (e != null) {
            logger.error("Failed to save current pending bytes.", e);
            return newFailedFuture(e);
        }
        return null;
    }

    private IllegalStateException saveCurrentPendingBytesIfNeeded() {
        return saveAndUpdatePendingBytes();
    }

    private long pendingOutboundBytes() {
        long pending = handler().pendingOutboundBytes(this);
        if (pending < 0) {
            pipeline.forceCloseTransport();
            String message = StringUtil.simpleClassName(handler.getClass()) +
                    ".pendingOutboundBytes(ChannelHandlerContext) returned a negative value: " +
                    pending + ". Force closed transport.";
            throw new IllegalStateException(message);
        }
        return pending;
    }

    private IllegalStateException saveAndUpdatePendingBytes() {
        if (!handlesPendingOutboundBytes(executionMask)) {
            assert currentPendingBytes == 0;
            return null;
        }
        long prev = currentPendingBytes;
        long delta = (currentPendingBytes = pendingOutboundBytes()) - prev;

        if (delta == 0) {
            // No changes
            return null;
        }
        try {
            if (delta > 0) {
                pipeline.incrementPendingOutboundBytes(delta);
            } else {
                pipeline.decrementPendingOutboundBytes(-delta);
            }
        } catch (IllegalStateException e) {
            return e;
        }
        return null;
    }

    void updatePendingBytesIfNeeded() {
        IllegalStateException exception = saveAndUpdatePendingBytes();
        if (exception != null) {
            logger.error("Failed to update pending bytes.", exception);
        }
    }

    private static final class DefaultChannelHandlerContextAwareEventExecutor implements EventExecutor {

        private final EventExecutor executor;
        private final DefaultChannelHandlerContext ctx;

        DefaultChannelHandlerContextAwareEventExecutor(EventExecutor executor, DefaultChannelHandlerContext ctx) {
            this.executor = executor;
            this.ctx = ctx;
        }

        @Override
        public boolean inEventLoop() {
            return executor.inEventLoop();
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return executor.inEventLoop(thread);
        }

        @Override
        public boolean isShuttingDown() {
            return executor.isShuttingDown();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        @Override
        public Future<Void> shutdownGracefully() {
            return executor.shutdownGracefully();
        }

        @Override
        public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return executor.shutdownGracefully(quietPeriod, timeout, unit);
        }

        @Override
        public Future<Void> terminationFuture() {
            return executor.terminationFuture();
        }

        @Override
        public Future<Void> submit(Runnable task) {
            return executor.submit(new DefaultHandlerContextRunnable(ctx, task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return executor.submit(new DefaultHandlerContextRunnable(ctx, task), result);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return executor.submit(new DefaultHandlerContextCallable<>(ctx, task));
        }

        @Override
        public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
            return executor.schedule(new DefaultHandlerContextRunnable(ctx, task), delay, unit);
        }

        @Override
        public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            return executor.schedule(new DefaultHandlerContextCallable<>(ctx, task), delay, unit);
        }

        @Override
        public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            return executor.scheduleAtFixedRate(
                    new DefaultHandlerContextRunnable(ctx, task), initialDelay, period, unit);
        }

        @Override
        public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
            return executor.scheduleWithFixedDelay(
                    new DefaultHandlerContextRunnable(ctx, task), initialDelay, delay, unit);
        }

        @Override
        public void execute(Runnable task) {
            executor.execute(new DefaultHandlerContextRunnable(ctx, task));
        }

        private static final class DefaultHandlerContextCallable<V> implements Callable<V> {

            private final DefaultChannelHandlerContext ctx;
            private final Callable<V> task;

            DefaultHandlerContextCallable(DefaultChannelHandlerContext ctx, Callable<V> task) {
                this.ctx = ctx;
                this.task = task;
            }

            @Override
            public V call() throws Exception {
                IllegalStateException e = ctx.saveCurrentPendingBytesIfNeeded();
                try {
                    V value = null;
                    try {
                        value = task.call();
                        return value;
                    } finally {
                        if (e != null) {
                            String valueStr = String.valueOf(value);
                            Resource.dispose(value);
                            logger.error("Failed to dispose {}.", valueStr, e);
                            throw e;
                        }
                    }
                } finally {
                    if (e == null) {
                        ctx.updatePendingBytesIfNeeded();
                    }
                }
            }
        }

        private static final class DefaultHandlerContextRunnable implements Runnable {
            private final DefaultChannelHandlerContext ctx;
            private final Runnable task;

            DefaultHandlerContextRunnable(DefaultChannelHandlerContext ctx, Runnable task) {
                this.ctx = ctx;
                this.task = task;
            }

            @Override
            public void run() {
                IllegalStateException e = ctx.saveCurrentPendingBytesIfNeeded();
                try {
                    task.run();
                    if (e != null) {
                        logger.error("Failed to save current pending bytes.", e);
                        throw e;
                    }
                } finally {
                    if (e == null) {
                        ctx.updatePendingBytesIfNeeded();
                    }
                }
            }
        }
    }
}
