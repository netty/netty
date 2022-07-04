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
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.netty5.channel.ChannelHandlerMask.MASK_BIND;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_ACTIVE;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_INACTIVE;
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
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_EXCEPTION_CAUGHT;
import static io.netty5.channel.ChannelHandlerMask.MASK_FLUSH;
import static io.netty5.channel.ChannelHandlerMask.MASK_READ;
import static io.netty5.channel.ChannelHandlerMask.MASK_REGISTER;
import static io.netty5.channel.ChannelHandlerMask.MASK_SHUTDOWN;
import static io.netty5.channel.ChannelHandlerMask.MASK_CHANNEL_INBOUND_EVENT;
import static io.netty5.channel.ChannelHandlerMask.MASK_SEND_OUTBOUND_EVENT;
import static io.netty5.channel.ChannelHandlerMask.MASK_WRITE;
import static io.netty5.channel.ChannelHandlerMask.mask;
import static java.util.Objects.requireNonNull;

final class DefaultChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelHandlerContext.class);

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
    private final DefaultChannelHandlerContextAwareEventExecutor executor;
    private long currentPendingBytes;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
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
        // Wrap the executor so we are sure that the pending bytes will be updated correctly in all cases.
        this.executor = new DefaultChannelHandlerContextAwareEventExecutor(pipeline.executor(), this);
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
        return executor;
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

    private EventExecutor wrappedExecutor() {
        return executor.wrappedExecutor();
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelRegistered();
        } else {
            executor.execute(this::findAndInvokeChannelRegistered);
        }
        return this;
    }

    private void findAndInvokeChannelRegistered() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_REGISTERED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelRegistered();
    }

    void invokeChannelRegistered() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelRegistered(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelUnregistered();
        } else {
            executor.execute(this::findAndInvokeChannelUnregistered);
        }
        return this;
    }

    private void findAndInvokeChannelUnregistered() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_UNREGISTERED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelUnregistered();
    }

    void invokeChannelUnregistered() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelUnregistered(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelActive();
        } else {
            executor.execute(this::findAndInvokeChannelActive);
        }
        return this;
    }

    private void findAndInvokeChannelActive() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_ACTIVE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelActive();
    }

    void invokeChannelActive() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelActive(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelInactive();
        } else {
            executor.execute(this::findAndInvokeChannelInactive);
        }
        return this;
    }

    private void findAndInvokeChannelInactive() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_INACTIVE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelInactive();
    }

    void invokeChannelInactive() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelInactive(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelShutdown(ChannelShutdownDirection direction) {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelShutdown(direction);
        } else {
            executor.execute(() -> findAndInvokeChannelShutdown(direction));
        }
        return this;
    }

    private void findAndInvokeChannelShutdown(ChannelShutdownDirection direction) {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_SHUTDOWN);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelShutdown(direction);
    }

    void invokeChannelShutdown(ChannelShutdownDirection direction) {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelShutdown(this, direction);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelExceptionCaught(Throwable cause) {
        requireNonNull(cause, "cause");
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
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
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelInboundEvent(event);
        } else {
            executor.execute(() -> findAndInvokeChannelInboundEvent(event));
        }
        return this;
    }

    private void findAndInvokeChannelInboundEvent(Object event) {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_INBOUND_EVENT);
        if (ctx == null) {
            Resource.dispose(event);
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelInboundEvent(event);
    }

    void invokeChannelInboundEvent(Object event) {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelInboundEvent(this, event);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        requireNonNull(msg, "msg");
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelRead(msg);
        } else {
            try {
                executor.execute(() -> findAndInvokeChannelRead(msg));
            } catch (Throwable cause) {
                Resource.dispose(msg);
                throw cause;
            }
        }
        return this;
    }

    private void findAndInvokeChannelRead(Object msg) {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ);
        if (ctx == null) {
            Resource.dispose(msg);
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelRead(msg);
    }

    void invokeChannelRead(Object msg) {
        final Object m = pipeline.touch(requireNonNull(msg, "msg"), this);
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelRead(this, m);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelReadComplete();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelReadCompleteTask);
        }
        return this;
    }

    private void findAndInvokeChannelReadComplete() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ_COMPLETE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelReadComplete();
    }

    void invokeChannelReadComplete() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelReadComplete(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelWritabilityChanged();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelWritableStateChangedTask);
        }
        return this;
    }

    private void findAndInvokeChannelWritabilityChanged() {
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelWritabilityChanged();
    }

    void invokeChannelWritabilityChanged() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().channelWritabilityChanged(this);
        } catch (Throwable t) {
            invokeChannelExceptionCaught(t);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> bind(SocketAddress localAddress) {
        requireNonNull(localAddress, "localAddress");

        EventExecutor executor = wrappedExecutor();
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
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
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
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().connect(this, remoteAddress, localAddress);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> disconnect() {
        if (!channel().metadata().hasDisconnect()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            return close();
        }

        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().disconnect(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> close() {
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().close(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, true);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> shutdown(ChannelShutdownDirection direction) {
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().shutdown(this, direction);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, true);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> register() {
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().deregister(this);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext read() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeRead();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeReadTask);
        }
        return this;
    }

    private void findAndInvokeRead() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_READ);
        if (ctx != null) {
            ctx.invokeRead();
        }
    }

    private void invokeRead() {
        try {
            saveCurrentPendingBytesIfNeeded();
            handler().read(this);
        } catch (Throwable t) {
            handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public Future<Void> write(Object msg) {
        return write(msg, false);
    }

    private Future<Void> invokeWrite(Object msg) {
        final Object m = pipeline.touch(msg, this);
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().write(this, m);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            findAndInvokeFlush();
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

    private void findAndInvokeFlush() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_FLUSH);
        if (ctx != null) {
            ctx.invokeFlush();
        }
    }

    private void invokeFlush() {
        try {
            saveCurrentPendingBytesIfNeeded();
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

        EventExecutor executor = wrappedExecutor();
        if (executor.inEventLoop()) {
            final DefaultChannelHandlerContext next = findContextOutbound(flush ?
                    (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
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
            if (!safeExecute(executor, task, promise, msg)) {
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
        EventExecutor executor = wrappedExecutor();
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
        try {
            saveCurrentPendingBytesIfNeeded();
            return handler().sendOutboundEvent(this, event);
        } catch (Throwable t) {
            return handleOutboundHandlerException(t, false);
        } finally {
            updatePendingBytesIfNeeded();
        }
    }

    private Future<Void> handleOutboundHandlerException(Throwable cause, boolean closeDidThrow) {
        String msg = handler() + " threw an exception while handling an outbound event. This is most likely a bug";

        logger.warn("{}. This is most likely a bug, closing the channel.", msg, cause);
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

    @Override
    public Promise<Void> newPromise() {
        return executor.newPromise();
    }

    @Override
    public Future<Void> newSucceededFuture() {
        return executor.newSucceededFuture(null);
    }

    @Override
    public Future<Void> newFailedFuture(Throwable cause) {
        return executor.newFailedFuture(cause);
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
        }
    }

    void callHandlerRemoved() throws Exception {
        try {
            // Only call handlerRemoved(...) if we called handlerAdded(...) before.
            if (handlerState == ADD_COMPLETE) {
                handlerState = REMOVE_STARTED;
                handler().handlerRemoved(this);
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

        protected static void init(AbstractWriteTask task, DefaultChannelHandlerContext ctx,
                                   Object msg, Promise<Void> promise) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                ctx.pipeline.incrementPendingOutboundBytes(task.size);
            } else {
                task.size = 0;
            }
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
            init(task, ctx, msg, promise);
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
            init(task, ctx, msg, promise);
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
            invokeChannelReadCompleteTask = ctx::findAndInvokeChannelReadComplete;
            invokeReadTask = ctx::findAndInvokeRead;
            invokeChannelWritableStateChangedTask = ctx::invokeChannelWritabilityChanged;
            invokeFlushTask = ctx::findAndInvokeFlush;
        }
    }

    private void saveCurrentPendingBytesIfNeeded() {
        // We only save the current pending bytes if not already done before.
        // This is important as otherwise we might run into issues in case of reentrancy.
        if (currentPendingBytes == -1) {
            long pending = handler().pendingOutboundBytes(this);
            if (pending < 0) {
                pipeline.closeTransport(newPromise());
                throw new IllegalStateException(StringUtil.simpleClassName(handler.getClass()) +
                        ".pendingOutboundBytes(ChannelHandlerContext) returned a negative value: " + pending +
                        ". Force closed transport.");
            }
            currentPendingBytes = pending;
        }
    }

    private void updatePendingBytesIfNeeded() {
        long current = currentPendingBytes;
        if (current == -1) {
            return;
        }
        this.currentPendingBytes = -1;
        long newPendingBytes = handler().pendingOutboundBytes(this);
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

    private static final class DefaultChannelHandlerContextAwareEventExecutor implements EventExecutor {

        private final EventExecutor executor;
        private final DefaultChannelHandlerContext ctx;

        DefaultChannelHandlerContextAwareEventExecutor(EventExecutor executor, DefaultChannelHandlerContext ctx) {
            this.executor = executor;
            this.ctx = ctx;
        }

        EventExecutor wrappedExecutor() {
            return executor;
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
            return executor.submit(new DefaultHandlerContextRunnable(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return executor.submit(new DefaultHandlerContextRunnable(task), result);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return executor.submit(new DefaultHandlerContextCallable<>(task));
        }

        @Override
        public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
            return executor.schedule(new DefaultHandlerContextRunnable(task), delay, unit);
        }

        @Override
        public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            return executor.schedule(new DefaultHandlerContextCallable<>(task), delay, unit);
        }

        @Override
        public Future<Void> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
            return executor.scheduleAtFixedRate(
                    new DefaultHandlerContextRunnable(task), initialDelay, period, unit);
        }

        @Override
        public Future<Void> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
            return executor.scheduleWithFixedDelay(
                    new DefaultHandlerContextRunnable(task), initialDelay, delay, unit);
        }

        @Override
        public void execute(Runnable task) {
            executor.execute(new DefaultHandlerContextRunnable(task));
        }

        private final class DefaultHandlerContextCallable<V> implements Callable<V> {

            private final Callable<V> task;

            DefaultHandlerContextCallable(Callable<V> task) {
                this.task = task;
            }

            @Override
            public V call() throws Exception {
                try {
                    ctx.saveCurrentPendingBytesIfNeeded();
                    return task.call();
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        }

        private final class DefaultHandlerContextRunnable implements Runnable {
            private final Runnable task;
            DefaultHandlerContextRunnable(Runnable task) {
                this.task = task;
            }

            @Override
            public void run() {
                try {
                    ctx.saveCurrentPendingBytesIfNeeded();
                    task.run();
                } finally {
                    ctx.updatePendingBytesIfNeeded();
                }
            }
        }
    }
}
