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
package io.netty.channel;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PromiseNotificationUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.channel.ChannelHandlerMask.*;

final class DefaultChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelHandlerContext.class);

    private static final AtomicIntegerFieldUpdater<DefaultChannelHandlerContext> HANDLER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(DefaultChannelHandlerContext.class, "handlerState");

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
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int REMOVE_COMPLETE = 2;

    private final int executionMask;
    private final DefaultChannelPipeline pipeline;
    private final ChannelHandler handler;
    private final String name;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
    private Tasks invokeTasks;

    DefaultChannelHandlerContext next;
    DefaultChannelHandlerContext prev;
    private volatile int handlerState = INIT;

    // Keeps track of processing different events
    private short outboundOperations;
    private short inboundOperations;

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name,
                                 ChannelHandler handler) {
        this.name = requireNonNull(name, "name");
        this.pipeline = pipeline;
        this.executionMask = mask(handler.getClass());
        this.handler = handler;
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
        return pipeline().executor();
    }

    @Override
    public ChannelHandler handler() {
        return handler;
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
    public String name() {
        return name;
    }

    private boolean isProcessInboundDirectly() {
        assert inboundOperations >= 0;
        return inboundOperations == 0;
    }

    private boolean isProcessOutboundDirectly() {
        assert outboundOperations >= 0;
        return outboundOperations == 0;
    }

    private void incrementOutboundOperations() {
        assert outboundOperations >= 0;
        outboundOperations++;
    }

    private void decrementOutboundOperations() {
        assert outboundOperations > 0;
        outboundOperations--;
    }

    private void incrementInboundOperations() {
        assert inboundOperations >= 0;
        inboundOperations++;
    }

    private void decrementInboundOperations() {
        assert inboundOperations > 0;
        inboundOperations--;
    }

    private static void executeInboundReentrance(DefaultChannelHandlerContext context, Runnable task) {
        context.incrementInboundOperations();
        try {
            context.executor().execute(task);
        } catch (Throwable cause) {
            context.decrementInboundOperations();
            throw cause;
        }
    }

    private static void executeOutboundReentrance(
            DefaultChannelHandlerContext context, Runnable task, ChannelPromise promise, Object msg) {
        context.incrementOutboundOperations();
        if (!safeExecute(context.executor(), task, promise, msg)) {
            context.decrementOutboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelRegistered();
        } else {
            executor.execute(this::findAndInvokeChannelRegistered);
        }
        return this;
    }

    private void findAndInvokeChannelRegistered() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_REGISTERED);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelRegistered();
        } else {
            executeInboundReentrance(context, context::invokeChannelRegistered0);
        }
    }

    void invokeChannelRegistered() {
        incrementInboundOperations();
        invokeChannelRegistered0();
    }

    private void invokeChannelRegistered0() {
        try {
            handler().channelRegistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelUnregistered();
        } else {
            executor.execute(this::findAndInvokeChannelUnregistered);
        }
        return this;
    }

    private void findAndInvokeChannelUnregistered() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_UNREGISTERED);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelUnregistered();
        } else {
            executeInboundReentrance(context, context::invokeChannelUnregistered0);
        }
    }

    void invokeChannelUnregistered() {
        incrementInboundOperations();
        invokeChannelUnregistered0();
    }

    private void invokeChannelUnregistered0() {
        try {
            handler().channelUnregistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelActive();
        } else {
            executor.execute(this::findAndInvokeChannelActive);
        }
        return this;
    }

    private void findAndInvokeChannelActive() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_ACTIVE);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelActive();
        } else {
            executeInboundReentrance(context, context::invokeChannelActive0);
        }
    }

    void invokeChannelActive() {
        incrementInboundOperations();
        invokeChannelActive0();
    }

    private void invokeChannelActive0() {
        try {
            handler().channelActive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelInactive();
        } else {
            executor.execute(this::findAndInvokeChannelInactive);
        }
        return this;
    }

    private void findAndInvokeChannelInactive() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_INACTIVE);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelInactive();
        } else {
            executeInboundReentrance(context, context::invokeChannelInactive0);
        }
    }

    void invokeChannelInactive() {
        incrementInboundOperations();
        invokeChannelInactive0();
    }

    private void invokeChannelInactive0() {
        try {
            handler().channelInactive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        requireNonNull(cause, "cause");
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeExceptionCaught(cause);
        } else {
            try {
                executor.execute(() -> findAndInvokeExceptionCaught(cause));
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
        return this;
    }

    private void findAndInvokeExceptionCaught(Throwable cause) {
        DefaultChannelHandlerContext context = findContextInbound(MASK_EXCEPTION_CAUGHT);
        if (context.isProcessInboundDirectly()) {
            context.invokeExceptionCaught(cause);
        } else {
            executeInboundReentrance(context, () -> context.invokeExceptionCaught0(cause));
        }
    }

    void invokeExceptionCaught(final Throwable cause) {
        incrementInboundOperations();
        invokeExceptionCaught0(cause);
    }

    private void invokeExceptionCaught0(final Throwable cause) {
        try {
            handler().exceptionCaught(this, cause);
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
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        requireNonNull(event, "event");
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeUserEventTriggered(event);
        } else {
            executor.execute(() -> findAndInvokeUserEventTriggered(event));
        }
        return this;
    }

    private void findAndInvokeUserEventTriggered(Object event) {
        DefaultChannelHandlerContext context = findContextInbound(MASK_USER_EVENT_TRIGGERED);
        if (context.isProcessInboundDirectly()) {
            context.invokeUserEventTriggered(event);
        } else {
            executeInboundReentrance(context, () -> context.invokeUserEventTriggered0(event));
        }
    }

    void invokeUserEventTriggered(Object event) {
        incrementInboundOperations();
        invokeUserEventTriggered0(event);
    }

    private void invokeUserEventTriggered0(Object event) {
        try {
            handler().userEventTriggered(this, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        requireNonNull(msg, "msg");
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelRead(msg);
        } else {
            try {
                executor.execute(() -> findAndInvokeChannelRead(msg));
            } catch (Throwable cause) {
                ReferenceCountUtil.release(msg);
                throw cause;
            }
        }
        return this;
    }

    private void findAndInvokeChannelRead(Object msg) {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_READ);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelRead(msg);
        } else {
            executeInboundReentrance(context, () -> context.invokeChannelRead0(msg));
        }
    }

    void invokeChannelRead(Object msg) {
        incrementInboundOperations();
        invokeChannelRead0(msg);
    }

    private void invokeChannelRead0(Object msg) {
        final Object m = pipeline.touch(requireNonNull(msg, "msg"), this);
        try {
            handler().channelRead(this, m);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelReadComplete();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelReadCompleteTask);
        }
        return this;
    }

    private void findAndInvokeChannelReadComplete() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_READ_COMPLETE);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelReadComplete();
        } else {
            executeInboundReentrance(context, context::invokeChannelReadComplete0);
        }
    }

    void invokeChannelReadComplete() {
        incrementInboundOperations();
        invokeChannelReadComplete0();
    }

    private void invokeChannelReadComplete0() {
        try {
            handler().channelReadComplete(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelWritabilityChanged();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeChannelWritableStateChangedTask);
        }
        return this;
    }

    private void findAndInvokeChannelWritabilityChanged() {
        DefaultChannelHandlerContext context = findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED);
        if (context.isProcessInboundDirectly()) {
            context.invokeChannelWritabilityChanged();
        } else {
            executeInboundReentrance(context, context::invokeChannelWritabilityChanged0);
        }
    }

    void invokeChannelWritabilityChanged() {
        incrementInboundOperations();
        invokeChannelWritabilityChanged0();
    }

    private void invokeChannelWritabilityChanged0() {
        try {
            handler().channelWritabilityChanged(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            decrementInboundOperations();
        }
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
    public ChannelFuture register() {
        return register(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        requireNonNull(localAddress, "localAddress");
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeBind(localAddress, promise);
        } else {
            safeExecute(executor, () -> findAndInvokeBind(localAddress, promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeBind(SocketAddress localAddress, ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_BIND);
        if (context.isProcessOutboundDirectly()) {
            context.invokeBind(localAddress, promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeBind0(localAddress, promise), promise, null);
        }
    }

    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        incrementOutboundOperations();
        invokeBind0(localAddress, promise);
    }

    private void invokeBind0(SocketAddress localAddress, ChannelPromise promise) {
        try {
            handler().bind(this, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        requireNonNull(remoteAddress, "remoteAddress");
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeConnect(remoteAddress, localAddress, promise);
        } else {
            safeExecute(executor, () -> findAndInvokeConnect(remoteAddress, localAddress, promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_CONNECT);
        if (context.isProcessOutboundDirectly()) {
            context.invokeConnect(remoteAddress, localAddress, promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeConnect0(remoteAddress, localAddress, promise),
                    promise, null);
        }
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        incrementOutboundOperations();
        invokeConnect0(remoteAddress, localAddress, promise);
    }

    private void invokeConnect0(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        try {
            handler().connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        if (!channel().metadata().hasDisconnect()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            return close(promise);
        }

        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeDisconnect(promise);
        } else {
            safeExecute(executor, () -> findAndInvokeDisconnect(promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeDisconnect(ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_DISCONNECT);
        if (context.isProcessOutboundDirectly()) {
            context.invokeDisconnect(promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeDisconnect0(promise), promise, null);
        }
    }

    private void invokeDisconnect(ChannelPromise promise) {
        incrementOutboundOperations();
        invokeDisconnect0(promise);
    }

    private void invokeDisconnect0(ChannelPromise promise) {
        try {
            handler().disconnect(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeClose(promise);
        } else {
            safeExecute(executor, () -> findAndInvokeClose(promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeClose(ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_CLOSE);
        if (context.isProcessOutboundDirectly()) {
            context.invokeClose(promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeClose0(promise), promise, null);
        }
    }

    private void invokeClose(ChannelPromise promise) {
        incrementOutboundOperations();
        invokeClose0(promise);
    }

    private void invokeClose0(ChannelPromise promise) {
        try {
            handler().close(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeRegister(promise);
        } else {
            safeExecute(executor, () -> findAndInvokeRegister(promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeRegister(ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_REGISTER);
        if (context.isProcessOutboundDirectly()) {
            context.invokeRegister(promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeRegister0(promise), promise, null);
        }
    }

    private void invokeRegister(ChannelPromise promise) {
        incrementOutboundOperations();
        invokeRegister0(promise);
    }

    private void invokeRegister0(ChannelPromise promise) {
        try {
            handler().register(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeDeregister(promise);
        } else {
            safeExecute(executor, () -> findAndInvokeDeregister(promise), promise, null);
        }
        return promise;
    }

    private void findAndInvokeDeregister(ChannelPromise promise) {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_DEREGISTER);
        if (context.isProcessOutboundDirectly()) {
            context.invokeDeregister(promise);
        } else {
            executeOutboundReentrance(context, () -> context.invokeDeregister0(promise), promise, null);
        }
    }

    private void invokeDeregister(ChannelPromise promise) {
        incrementOutboundOperations();
        invokeDeregister0(promise);
    }

    private void invokeDeregister0(ChannelPromise promise) {
        try {
            handler().deregister(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext read() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeRead();
        } else {
            Tasks tasks = invokeTasks();
            executor.execute(tasks.invokeReadTask);
        }
        return this;
    }

    private void findAndInvokeRead() {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_READ);
        if (context.isProcessOutboundDirectly()) {
            context.invokeRead();
        } else {
            executeOutboundReentrance(context, context::invokeRead0, null, null);
        }
    }

    private void invokeRead() {
        incrementOutboundOperations();
        invokeRead0();
    }

    private void invokeRead0() {
        try {
            handler().read(this);
        } catch (Throwable t) {
            invokeExceptionCaughtFromOutbound(t);
        } finally {
            decrementOutboundOperations();
        }
    }

    private void invokeExceptionCaughtFromOutbound(Throwable t) {
        if ((executionMask & MASK_EXCEPTION_CAUGHT) != 0) {
            notifyHandlerException(t);
        } else {
            DefaultChannelHandlerContext context = findContextInbound(MASK_EXCEPTION_CAUGHT);
            if (context.isProcessInboundDirectly()) {
                context.invokeExceptionCaught(t);
            } else {
                executeInboundReentrance(context, () -> context.invokeExceptionCaught0(t));
            }
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        write(msg, false, promise);

        return promise;
    }

    private void invokeWrite(Object msg, ChannelPromise promise) {
        incrementOutboundOperations();
        invokeWrite0(msg, promise);
    }

    private void invokeWrite0(Object msg, ChannelPromise promise) {
        final Object m = pipeline.touch(msg, this);
        try {
            handler().write(this, m, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeFlush();
        } else {
            Tasks tasks = invokeTasks();
            safeExecute(executor, tasks.invokeFlushTask, channel().voidPromise(), null);
        }

        return this;
    }

    private void findAndInvokeFlush() {
        DefaultChannelHandlerContext context = findContextOutbound(MASK_FLUSH);
        if (context.isProcessOutboundDirectly()) {
            context.invokeFlush();
        } else {
            executeOutboundReentrance(context, context::invokeFlush0, null, null);
        }
    }

    private void invokeFlush() {
        incrementOutboundOperations();
        invokeFlush0();
    }

    private void invokeFlush0() {
        try {
            handler().flush(this);
        } catch (Throwable t) {
            invokeExceptionCaughtFromOutbound(t);
        } finally {
            decrementOutboundOperations();
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        write(msg, true, promise);
        return promise;
    }

    private void write(Object msg, boolean flush, ChannelPromise promise) {
        requireNonNull(msg, "msg");
        try {
            if (isNotValidPromise(promise, true)) {
                ReferenceCountUtil.release(msg);
                // cancelled
                return;
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            final DefaultChannelHandlerContext next = findContextOutbound(flush ?
                    (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
            if (flush) {
                if (next.isProcessOutboundDirectly()) {
                    next.invokeWrite(msg, promise);
                    next.invokeFlush();
                } else {
                    executeOutboundReentrance(next, () -> next.invokeWrite0(msg, promise), promise, msg);
                    executeOutboundReentrance(next, next::invokeFlush0, null, null);
                }
            } else {
                if (next.isProcessOutboundDirectly()) {
                    next.invokeWrite(msg, promise);
                } else {
                    executeOutboundReentrance(next, () -> next.invokeWrite0(msg, promise), promise, msg);
                }
            }
        } else {
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
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        // Only log if the given promise is not of type VoidChannelPromise as tryFailure(...) is expected to return
        // false.
        PromiseNotificationUtil.tryFailure(promise, cause, promise instanceof VoidChannelPromise ? null : logger);
    }

    private void notifyHandlerException(Throwable cause) {
        if (inExceptionCaught(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler " +
                                "while handling an exceptionCaught event", cause);
            }
            return;
        }
        invokeExceptionCaught(cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        do {
            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if (t == null) {
                        break;
                    }
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        } while (cause != null);

        return false;
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return pipeline().newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    private boolean isNotValidPromise(ChannelPromise promise, boolean allowVoidPromise) {
        requireNonNull(promise, "promise");

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

        if (!allowVoidPromise && promise instanceof VoidChannelPromise) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(VoidChannelPromise.class) + " not allowed for this operation");
        }

        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(AbstractChannel.CloseFuture.class) + " not allowed in a pipeline");
        }
        return false;
    }

    private DefaultChannelHandlerContext findContextInbound(int mask) {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while ((ctx.executionMask & mask) == 0 && ctx.isProcessInboundDirectly());
        return ctx;
    }

    private DefaultChannelHandlerContext findContextOutbound(int mask) {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while ((ctx.executionMask & mask) == 0 && ctx.isProcessOutboundDirectly());
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel().voidPromise();
    }

    private void setRemoved() {
        handlerState = REMOVE_COMPLETE;
    }

    boolean setAddComplete() {
        // Ensure we never update when the handlerState is REMOVE_COMPLETE already.
        // oldState is usually ADD_PENDING but can also be REMOVE_COMPLETE when an EventExecutor is used that is not
        // exposing ordering guarantees.
        return HANDLER_STATE_UPDATER.getAndSet(this, ADD_COMPLETE) != REMOVE_COMPLETE;
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
                handler().handlerRemoved(this);
            }
        } finally {
            // Mark the handler as removed in any case.
            setRemoved();
        }
    }

    @Override
    public boolean isRemoved() {
        return handlerState == REMOVE_COMPLETE;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    private static boolean safeExecute(EventExecutor executor, Runnable runnable, ChannelPromise promise, Object msg) {
        try {
            executor.execute(runnable);
            return true;
        } catch (Throwable cause) {
            try {
                if (promise != null) {
                    promise.setFailure(cause);
                }
            } finally {
                if (msg != null) {
                    ReferenceCountUtil.release(msg);
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
            SystemPropertyUtil.getBoolean("io.netty.transport.estimateSizeOnSubmit", true);

    // Assuming a 64-bit JVM, 16 bytes object header, 3 reference fields and one int field, plus alignment
    private static final int WRITE_TASK_OVERHEAD =
            SystemPropertyUtil.getInt("io.netty.transport.writeTaskSizeOverhead", 48);

    abstract static class AbstractWriteTask implements Runnable {

        private final Recycler.Handle<AbstractWriteTask> handle;
        private DefaultChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;

        @SuppressWarnings("unchecked")
        private AbstractWriteTask(Recycler.Handle<? extends AbstractWriteTask> handle) {
            this.handle = (Recycler.Handle<AbstractWriteTask>) handle;
        }

        protected static void init(AbstractWriteTask task, DefaultChannelHandlerContext ctx,
                                   Object msg, ChannelPromise promise) {
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
                DefaultChannelHandlerContext next = findContext(ctx);
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

        protected void write(DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ctx.invokeWrite(msg, promise);
        }
    }

    static final class WriteTask extends AbstractWriteTask implements SingleThreadEventLoop.NonWakeupRunnable {

        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle<WriteTask> handle) {
                return new WriteTask(handle);
            }
        };

        static WriteTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        @Override
        protected DefaultChannelHandlerContext findContext(DefaultChannelHandlerContext ctx) {
            return ctx.findContextOutbound(MASK_WRITE);
        }

        private WriteTask(Recycler.Handle<WriteTask> handle) {
            super(handle);
        }
    }

    static final class WriteAndFlushTask extends AbstractWriteTask {

        private static final Recycler<WriteAndFlushTask> RECYCLER = new Recycler<WriteAndFlushTask>() {
            @Override
            protected WriteAndFlushTask newObject(Handle<WriteAndFlushTask> handle) {
                return new WriteAndFlushTask(handle);
            }
        };

        static WriteAndFlushTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            WriteAndFlushTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        private WriteAndFlushTask(Recycler.Handle<WriteAndFlushTask> handle) {
            super(handle);
        }

        @Override
        protected DefaultChannelHandlerContext findContext(DefaultChannelHandlerContext ctx) {
            return ctx.findContextOutbound(MASK_WRITE | MASK_FLUSH);
        }

        @Override
        public void write(DefaultChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
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
}
