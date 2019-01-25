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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.PromiseNotificationUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

abstract class AbstractChannelHandlerContext extends DefaultAttributeMap
        implements ChannelHandlerContext, ResourceLeakHint {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannelHandlerContext.class);
    AbstractChannelHandlerContext next;
    AbstractChannelHandlerContext prev;

    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractChannelHandlerContext.class, "handlerState");
    /**
     * {@link ChannelHandler#handlerAdded(ChannelHandlerContext)} was called.
     */
    private static final int ADD_COMPLETE = 1;

    /**
     * {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int REMOVE_COMPLETE = 2;
    /**
     * Neither {@link ChannelHandler#handlerAdded(ChannelHandlerContext)}
     * nor {@link ChannelHandler#handlerRemoved(ChannelHandlerContext)} was called.
     */
    private static final int INIT = 0;

    // Using to mask which methods must be called for a ChannelHandler.
    private static final int MASK_EXCEPTION_CAUGHT = 1;
    private static final int MASK_CHANNEL_REGISTERED = 1 << 1;
    private static final int MASK_CHANNEL_UNREGISTERED = 1 << 2;
    private static final int MASK_CHANNEL_ACTIVE = 1 << 3;
    private static final int MASK_CHANNEL_INACTIVE = 1 << 4;
    private static final int MASK_CHANNEL_READ = 1 << 5;
    private static final int MASK_CHANNEL_READ_COMPLETE = 1 << 6;
    private static final int MASK_USER_EVENT_TRIGGERED = 1 << 7;
    private static final int MASK_CHANNEL_WRITABILITY_CHANGED = 1 << 8;
    private static final int MASK_BIND = 1 << 9;
    private static final int MASK_CONNECT = 1 << 10;
    private static final int MASK_DISCONNECT = 1 << 11;
    private static final int MASK_CLOSE = 1 << 12;
    private static final int MASK_REGISTER = 1 << 13;
    private static final int MASK_DEREGISTER = 1 << 14;
    private static final int MASK_READ = 1 << 15;
    private static final int MASK_WRITE = 1 << 16;
    private static final int MASK_FLUSH = 1 << 17;

    private static final int MASK_ALL_INBOUND = MASK_CHANNEL_REGISTERED |
            MASK_CHANNEL_UNREGISTERED | MASK_CHANNEL_ACTIVE | MASK_CHANNEL_INACTIVE | MASK_CHANNEL_READ |
            MASK_CHANNEL_READ_COMPLETE | MASK_USER_EVENT_TRIGGERED | MASK_CHANNEL_WRITABILITY_CHANGED;
    private static final int MASK_ALL_OUTBOUND = MASK_BIND | MASK_CONNECT | MASK_DISCONNECT |
            MASK_CLOSE | MASK_REGISTER | MASK_DEREGISTER | MASK_READ | MASK_WRITE | MASK_FLUSH;

    private static final FastThreadLocal<Map<Class<? extends ChannelHandler>, Integer>> MASKS =
            new FastThreadLocal<Map<Class<? extends ChannelHandler>, Integer>>() {
                @Override
                protected Map<Class<? extends ChannelHandler>, Integer> initialValue() {
                    return new WeakHashMap<>(32);
                }
            };

    private final int executionMask;

    private final DefaultChannelPipeline pipeline;
    private final String name;

    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
    private Tasks invokeTasks;

    private volatile int handlerState = INIT;

    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, String name,
                                  Class<? extends ChannelHandler> handlerClass) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.pipeline = pipeline;
        this.executionMask = mask(handlerClass);
    }

    /**
     * Return the {@code executionMask}.
     */
    private static int mask(Class<? extends ChannelHandler> clazz) {
        // Try to obtain the mask from the cache first. If this fails calculate it and put it in the cache for fast
        // lookup in the future.
        Map<Class<? extends ChannelHandler>, Integer> cache = MASKS.get();
        Integer mask = cache.get(clazz);
        if (mask == null) {
            mask = mask0(clazz);
            cache.put(clazz, mask);
        }
        return mask;
    }

    /**
     * Calculate the {@code executionMask}.
     */
    private static int mask0(Class<? extends ChannelHandler> handlerType) {
        int mask = MASK_EXCEPTION_CAUGHT;
        try {
            if (isSkippable(handlerType, "exceptionCaught", ChannelHandlerContext.class, Throwable.class)) {
                mask &= ~MASK_EXCEPTION_CAUGHT;
            }

            if (ChannelInboundHandler.class.isAssignableFrom(handlerType)) {
                mask |= MASK_ALL_INBOUND;

                if (isSkippable(handlerType, "channelRegistered", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_REGISTERED;
                }
                if (isSkippable(handlerType, "channelUnregistered", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_UNREGISTERED;
                }
                if (isSkippable(handlerType, "channelActive", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_ACTIVE;
                }
                if (isSkippable(handlerType, "channelInactive", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_INACTIVE;
                }
                if (isSkippable(handlerType, "channelRead", ChannelHandlerContext.class, Object.class)) {
                    mask &= ~MASK_CHANNEL_READ;
                }
                if (isSkippable(handlerType, "channelReadComplete", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_READ_COMPLETE;
                }
                if (isSkippable(handlerType, "channelWritabilityChanged", ChannelHandlerContext.class)) {
                    mask &= ~MASK_CHANNEL_WRITABILITY_CHANGED;
                }
                if (isSkippable(handlerType, "userEventTriggered", ChannelHandlerContext.class, Object.class)) {
                    mask &= ~MASK_USER_EVENT_TRIGGERED;
                }
            }
            if (ChannelOutboundHandler.class.isAssignableFrom(handlerType)) {
                mask |= MASK_ALL_OUTBOUND;

                if (isSkippable(handlerType, "bind", ChannelHandlerContext.class,
                        SocketAddress.class, ChannelPromise.class)) {
                    mask &= ~MASK_BIND;
                }
                if (isSkippable(handlerType, "connect", ChannelHandlerContext.class, SocketAddress.class,
                        SocketAddress.class, ChannelPromise.class)) {
                    mask &= ~MASK_CONNECT;
                }
                if (isSkippable(handlerType, "disconnect", ChannelHandlerContext.class, ChannelPromise.class)) {
                    mask &= ~MASK_DISCONNECT;
                }
                if (isSkippable(handlerType, "close", ChannelHandlerContext.class, ChannelPromise.class)) {
                    mask &= ~MASK_CLOSE;
                }
                if (isSkippable(handlerType, "register", ChannelHandlerContext.class, ChannelPromise.class)) {
                    mask &= ~MASK_REGISTER;
                }
                if (isSkippable(handlerType, "deregister", ChannelHandlerContext.class, ChannelPromise.class)) {
                    mask &= ~MASK_DEREGISTER;
                }
                if (isSkippable(handlerType, "read", ChannelHandlerContext.class)) {
                    mask &= ~MASK_READ;
                }
                if (isSkippable(handlerType, "write", ChannelHandlerContext.class,
                        Object.class, ChannelPromise.class)) {
                    mask &= ~MASK_WRITE;
                }
                if (isSkippable(handlerType, "flush", ChannelHandlerContext.class)) {
                    mask &= ~MASK_FLUSH;
                }
            }
        } catch (Exception e) {
            // Should never reach here.
            PlatformDependent.throwException(e);
        }

        return mask;
    }

    @SuppressWarnings("rawtypes")
    private static boolean isSkippable(
            final Class<?> handlerType, final String methodName, final Class<?>... paramTypes) throws Exception {

        return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () ->
                handlerType.getMethod(methodName, paramTypes).isAnnotationPresent(ChannelHandler.Skip.class));
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
        findContextInbound(MASK_CHANNEL_REGISTERED).invokeChannelRegistered();
    }

    final void invokeChannelRegistered() {
        try {
            ((ChannelInboundHandler) handler()).channelRegistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
        findContextInbound(MASK_CHANNEL_UNREGISTERED).invokeChannelUnregistered();
    }

    final void invokeChannelUnregistered() {
        try {
            ((ChannelInboundHandler) handler()).channelUnregistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
        findContextInbound(MASK_CHANNEL_ACTIVE).invokeChannelActive();
    }

    final void invokeChannelActive() {
        try {
            ((ChannelInboundHandler) handler()).channelActive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
        findContextInbound(MASK_CHANNEL_INACTIVE).invokeChannelInactive();
    }

    final void invokeChannelInactive() {
        try {
            ((ChannelInboundHandler) handler()).channelInactive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        ObjectUtil.checkNotNull(cause, "cause");
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
        findContextInbound(MASK_EXCEPTION_CAUGHT).invokeExceptionCaught(cause);
    }

    final void invokeExceptionCaught(final Throwable cause) {
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
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        ObjectUtil.checkNotNull(event, "event");
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeUserEventTriggered(event);
        } else {
            executor.execute(() -> findAndInvokeUserEventTriggered(event));
        }
        return this;
    }

    private void findAndInvokeUserEventTriggered(Object event) {
        findContextInbound(MASK_USER_EVENT_TRIGGERED).invokeUserEventTriggered(event);
    }

    final void invokeUserEventTriggered(Object event) {
        try {
            ((ChannelInboundHandler) handler()).userEventTriggered(this, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        ObjectUtil.checkNotNull(msg, "msg");
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
        findContextInbound(MASK_CHANNEL_READ).invokeChannelRead(msg);
    }

    final void invokeChannelRead(Object msg) {
        final Object m = pipeline.touch(ObjectUtil.checkNotNull(msg, "msg"), this);
        try {
            ((ChannelInboundHandler) handler()).channelRead(this, m);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelReadComplete();
        } else {
            Tasks tasks = invokeTasks;
            if (tasks == null) {
                invokeTasks = tasks = new Tasks(this);
            }
            executor.execute(tasks.invokeChannelReadCompleteTask);
        }
        return this;
    }

    private void findAndInvokeChannelReadComplete() {
        findContextInbound(MASK_CHANNEL_READ_COMPLETE).invokeChannelReadComplete();
    }

    final void invokeChannelReadComplete() {
        try {
            ((ChannelInboundHandler) handler()).channelReadComplete(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeChannelWritabilityChanged();
        } else {
            Tasks tasks = invokeTasks;
            if (tasks == null) {
                invokeTasks = tasks = new Tasks(this);
            }
            executor.execute(tasks.invokeChannelWritableStateChangedTask);
        }
        return this;
    }

    private void findAndInvokeChannelWritabilityChanged() {
        findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED).invokeChannelWritabilityChanged();
    }

    final void invokeChannelWritabilityChanged() {
        try {
            ((ChannelInboundHandler) handler()).channelWritabilityChanged(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
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
        findContextOutbound(MASK_BIND).invokeBind(localAddress, promise);
    }

    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).bind(this, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {

        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
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
        findContextOutbound(MASK_CONNECT).invokeConnect(remoteAddress, localAddress, promise);
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            if (!channel().metadata().hasDisconnect()) {
                findAndInvokeClose(promise);
            } else {
                findAndInvokeDisconnect(promise);
            }
        } else {
            safeExecute(executor, () -> {
                // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
                // So far, UDP/IP is the only transport that has such behavior.
                if (!channel().metadata().hasDisconnect()) {
                    findAndInvokeClose(promise);
                } else {
                    findAndInvokeDisconnect(promise);
                }
            }, promise, null);
        }
        return promise;
    }

    private void findAndInvokeDisconnect(ChannelPromise promise) {
        findContextOutbound(MASK_DISCONNECT).invokeDisconnect(promise);
    }

    private void invokeDisconnect(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).disconnect(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
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
        findContextOutbound(MASK_CLOSE).invokeClose(promise);
    }

    private void invokeClose(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).close(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
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
        findContextOutbound(MASK_REGISTER).invokeRegister(promise);
    }

    private void invokeRegister(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).register(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
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
        findContextOutbound(MASK_DEREGISTER).invokeDeregister(promise);
    }

    private void invokeDeregister(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).deregister(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelHandlerContext read() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeRead();
        } else {
            Tasks tasks = invokeTasks;
            if (tasks == null) {
                invokeTasks = tasks = new Tasks(this);
            }
            executor.execute(tasks.invokeReadTask);
        }
        return this;
    }

    private void findAndInvokeRead() {
        findContextOutbound(MASK_READ).invokeRead();
    }

    private void invokeRead() {
        try {
            ((ChannelOutboundHandler) handler()).read(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
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
        final Object m = pipeline.touch(msg, this);
        try {
            ((ChannelOutboundHandler) handler()).write(this, m, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeFlush();
        } else {
            Tasks tasks = invokeTasks;
            if (tasks == null) {
                invokeTasks = tasks = new Tasks(this);
            }
            safeExecute(executor, tasks.invokeFlushTask, channel().voidPromise(), null);
        }

        return this;
    }

    private void findAndInvokeFlush() {
        findContextOutbound(MASK_FLUSH).invokeFlush();
    }

    private void invokeFlush() {
        try {
            ((ChannelOutboundHandler) handler()).flush(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        write(msg, true, promise);
        return promise;
    }

    private void invokeWriteAndFlush(Object msg, ChannelPromise promise) {
        invokeWrite(msg, promise);
        invokeFlush();
    }

    private void write(Object msg, boolean flush, ChannelPromise promise) {
        ObjectUtil.checkNotNull(msg, "msg");
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
            final AbstractChannelHandlerContext next = findContextOutbound(flush ?
                    (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
            if (flush) {
                next.invokeWriteAndFlush(msg, promise);
            } else {
                next.invokeWrite(msg, promise);
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
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel(), executor());
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

    private boolean isNotValidPromise(ChannelPromise promise, boolean allowVoidPromise) {
        if (promise == null) {
            throw new NullPointerException("promise");
        }

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

    private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel().voidPromise();
    }

    private void setRemoved() {
        handlerState = REMOVE_COMPLETE;
    }

    final boolean setAddComplete() {
        // Ensure we never update when the handlerState is REMOVE_COMPLETE already.
        // oldState is usually ADD_PENDING but can also be REMOVE_COMPLETE when an EventExecutor is used that is not
        // exposing ordering guarantees.
        return HANDLER_STATE_UPDATER.getAndSet(this, ADD_COMPLETE) != REMOVE_COMPLETE;
    }

    final void callHandlerAdded() throws Exception {
        // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
        // any pipeline events ctx.handler() will miss them because the state will not allow it.
        if (setAddComplete()) {
            handler().handlerAdded(this);
        }
    }

    final void callHandlerRemoved() throws Exception {
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
                promise.setFailure(cause);
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
        private AbstractChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;

        @SuppressWarnings("unchecked")
        private AbstractWriteTask(Recycler.Handle<? extends AbstractWriteTask> handle) {
            this.handle = (Recycler.Handle<AbstractWriteTask>) handle;
        }

        protected static void init(AbstractWriteTask task, AbstractChannelHandlerContext ctx,
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

        protected abstract AbstractChannelHandlerContext findContext(AbstractChannelHandlerContext ctx);
        @Override
        public final void run() {
            try {
                decrementPendingOutboundBytes();
                AbstractChannelHandlerContext next = findContext(ctx);
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

        protected void write(AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
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
                AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        @Override
        protected AbstractChannelHandlerContext findContext(AbstractChannelHandlerContext ctx) {
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
                AbstractChannelHandlerContext ctx, Object msg,  ChannelPromise promise) {
            WriteAndFlushTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        private WriteAndFlushTask(Recycler.Handle<WriteAndFlushTask> handle) {
            super(handle);
        }

        @Override
        protected AbstractChannelHandlerContext findContext(AbstractChannelHandlerContext ctx) {
            return ctx.findContextOutbound(MASK_WRITE | MASK_FLUSH);
        }

        @Override
        public void write(AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            super.write(ctx, msg, promise);
            ctx.invokeFlush();
        }
    }

    private static final class Tasks {
        private final Runnable invokeChannelReadCompleteTask;
        private final Runnable invokeReadTask;
        private final Runnable invokeChannelWritableStateChangedTask;
        private final Runnable invokeFlushTask;

        Tasks(AbstractChannelHandlerContext ctx) {
            invokeChannelReadCompleteTask = ctx::findAndInvokeChannelReadComplete;
            invokeReadTask = ctx::findAndInvokeRead;
            invokeChannelWritableStateChangedTask = ctx::invokeChannelWritabilityChanged;
            invokeFlushTask = ctx::findAndInvokeFlush;
        }
    }
}
