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

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

import static io.netty.channel.ChannelHandlerMask.*;

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

    final ChannelOutboundInvokerCallback voidCallback;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // There is no need to make this volatile as at worse it will just create a few more instances then needed.
    private Tasks invokeTasks;
    private int handlerState = INIT;

    DefaultChannelHandlerContext next;
    DefaultChannelHandlerContext prev;

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, String name,
                                 ChannelHandler handler) {
        this.name = requireNonNull(name, "name");
        this.pipeline = pipeline;
        this.executionMask = mask(handler.getClass());
        this.handler = handler;
        this.voidCallback = VoidChannelOutboundInvokerCallback.newInstance(this);
    }

    private static void failRemoved(DefaultChannelHandlerContext ctx, ChannelOutboundInvokerCallback callback) {
        callback.onError(newRemovedException(ctx, null));
    }

    private void notifyHandlerRemovedAlready() {
        notifyHandlerRemovedAlready(null);
    }

    private void notifyHandlerRemovedAlready(Throwable cause) {
        pipeline().fireExceptionCaught(newRemovedException(this, cause));
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_REGISTERED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelRegistered();
    }

    void invokeChannelRegistered() {
        try {
            handler().channelRegistered(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_UNREGISTERED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelUnregistered();
    }

    void invokeChannelUnregistered() {
        try {
            handler().channelUnregistered(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_ACTIVE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelActive();
    }

    void invokeChannelActive() {
        try {
            handler().channelActive(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_INACTIVE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelInactive();
    }

    void invokeChannelInactive() {
        try {
            handler().channelInactive(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_EXCEPTION_CAUGHT);
        if (ctx == null) {
            notifyHandlerRemovedAlready(cause);
            return;
        }
        ctx.invokeExceptionCaught(cause);
    }

    void invokeExceptionCaught(final Throwable cause) {
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_USER_EVENT_TRIGGERED);
        if (ctx == null) {
            ReferenceCountUtil.release(event);
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeUserEventTriggered(event);
    }

    void invokeUserEventTriggered(Object event) {
        try {
            handler().userEventTriggered(this, event);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ);
        if (ctx == null) {
            ReferenceCountUtil.release(msg);
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelRead(msg);
    }

    void invokeChannelRead(Object msg) {
        final Object m = pipeline.touch(requireNonNull(msg, "msg"), this);
        try {
            handler().channelRead(this, m);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_READ_COMPLETE);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelReadComplete();
    }

    void invokeChannelReadComplete() {
        try {
            handler().channelReadComplete(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
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
        DefaultChannelHandlerContext ctx = findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED);
        if (ctx == null) {
            notifyHandlerRemovedAlready();
            return;
        }
        ctx.invokeChannelWritabilityChanged();
    }

    void invokeChannelWritabilityChanged() {
        try {
            handler().channelWritabilityChanged(this);
        } catch (Throwable t) {
            invokeExceptionCaught(t);
        }
    }

    @Override
    public ChannelHandlerContext bind(final SocketAddress localAddress,
                                      final ChannelOutboundInvokerCallback callback) {
        requireNonNull(localAddress, "localAddress");
        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeBind(localAddress, callback);
        } else {
            safeExecute(executor, () -> findAndInvokeBind(localAddress, callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeBind(SocketAddress localAddress, ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_BIND);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeBind(localAddress, callback);
    }

    private void invokeBind(SocketAddress localAddress, ChannelOutboundInvokerCallback callback) {
        try {
            handler().bind(this, localAddress, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress,
            final ChannelOutboundInvokerCallback callback) {
        requireNonNull(remoteAddress, "remoteAddress");
        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeConnect(remoteAddress, localAddress, callback);
        } else {
            safeExecute(executor, () ->
                    findAndInvokeConnect(remoteAddress, localAddress, callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeConnect(SocketAddress remoteAddress, SocketAddress localAddress,
                                      ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_CONNECT);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeConnect(remoteAddress, localAddress, callback);
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress,
                               ChannelOutboundInvokerCallback callback) {
        try {
            handler().connect(this, remoteAddress, localAddress, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext disconnect(final ChannelOutboundInvokerCallback callback) {
        if (!channel().metadata().hasDisconnect()) {
            // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
            // So far, UDP/IP is the only transport that has such behavior.
            return close(callback);
        }

        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeDisconnect(callback);
        } else {
            safeExecute(executor, () -> findAndInvokeDisconnect(callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeDisconnect(ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_DISCONNECT);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeDisconnect(callback);
    }

    private void invokeDisconnect(ChannelOutboundInvokerCallback callback) {
        try {
            handler().disconnect(this, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext close(final ChannelOutboundInvokerCallback callback) {
        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeClose(callback);
        } else {
            safeExecute(executor, () -> findAndInvokeClose(callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeClose(ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_CLOSE);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeClose(callback);
    }

    private void invokeClose(ChannelOutboundInvokerCallback callback) {
        try {
            handler().close(this, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext register(final ChannelOutboundInvokerCallback callback) {
        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeRegister(callback);
        } else {
            safeExecute(executor, () -> findAndInvokeRegister(callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeRegister(ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_REGISTER);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeRegister(callback);
    }

    private void invokeRegister(ChannelOutboundInvokerCallback callback) {
        try {
            handler().register(this, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext deregister(final ChannelOutboundInvokerCallback callback) {
        if (isNotValidCallback(channel(), callback)) {
            // cancelled
            return this;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeDeregister(callback);
        } else {
            safeExecute(executor, () -> findAndInvokeDeregister(callback), callback, null);
        }
        return this;
    }

    private void findAndInvokeDeregister(ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_DEREGISTER);
        if (ctx == null) {
            failRemoved(this, callback);
            return;
        }
        ctx.invokeDeregister(callback);
    }

    private void invokeDeregister(ChannelOutboundInvokerCallback callback) {
        try {
            handler().deregister(this, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext read() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeRead();
        } else {
            Tasks tasks = invokeTasks();
            safeExecute(executor, tasks.invokeReadTask, voidCallback(), null);
        }
        return this;
    }

    private void findAndInvokeRead() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_READ);
        if (ctx == null) {
            failRemoved(this, voidCallback());
            return;
        }
        ctx.invokeRead();
    }

    private void invokeRead() {
        try {
            handler().read(this);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext write(final Object msg, final ChannelOutboundInvokerCallback callback) {
        requireNonNull(msg, "msg");
        try {
            if (isNotValidCallback(channel(), callback)) {
                ReferenceCountUtil.release(msg);
                // cancelled
                return this;
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeWrite(msg, callback);
        } else {
            final WriteTask writeTask = WriteTask.newInstance(this, msg, callback);
            if (!safeExecute(executor, writeTask, callback, msg)) {
                writeTask.cancel();
            }
        }
        return this;
    }

    private void findAndInvokeWrite(Object msg, ChannelOutboundInvokerCallback callback) {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_WRITE);
        if (ctx == null) {
            ReferenceCountUtil.release(msg);
            failRemoved(this, callback);
            return;
        }
        ctx.invokeWrite(msg, callback);
    }

    private void invokeWrite(Object msg, ChannelOutboundInvokerCallback callback) {
        final Object m = pipeline.touch(msg, this);
        try {
            handler().write(this, m, callback);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext flush() {

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            findAndInvokeFlush();
        } else {
            Tasks tasks = invokeTasks();
            safeExecute(executor, tasks.invokeFlushTask, voidCallback(), null);
        }

        return this;
    }

    private void findAndInvokeFlush() {
        DefaultChannelHandlerContext ctx = findContextOutbound(MASK_FLUSH);
        if (ctx == null) {
            failRemoved(this, voidCallback());
            return;
        }
        ctx.invokeFlush();
    }

    private void invokeFlush() {
        try {
            handler().flush(this);
        } catch (Throwable t) {
            handleOutboundHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext writeAndFlush(Object msg, ChannelOutboundInvokerCallback callback) {
        write(msg, callback);
        flush();
        return this;
    }

    private void handleOutboundHandlerException(Throwable cause) {
        logger.warn("{} threw an exception while handling an outbound event." +
                " This is most likely a bug, closing the channel.", handler(), cause);
        close();
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
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
        }
    }

    @Override
    public boolean isRemoved() {
        return handlerState == REMOVE_COMPLETE;
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

        this.prev = null;
        this.next = null;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    @Override
    public ChannelOutboundInvokerCallback voidCallback() {
        return voidCallback;
    }

    private static boolean safeExecute(EventExecutor executor, Runnable runnable,
                                       ChannelOutboundInvokerCallback callback, Object msg) {
        try {
            executor.execute(runnable);
            return true;
        } catch (Throwable cause) {
            try {
                if (msg != null) {
                    ReferenceCountUtil.release(msg);
                }
            } finally {
                if (callback != null) {
                    callback.onError(cause);
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

    private static boolean isNotValidCallback(Channel channel, ChannelOutboundInvokerCallback callback) {
        requireNonNull(channel, "channel");
        requireNonNull(callback, "callback");

        if (callback instanceof ChannelPromise) {
            ChannelPromise promise = (ChannelPromise) callback;
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

            if (promise.channel() != channel) {
                throw new IllegalArgumentException(String.format(
                        "promise.channel does not match: %s (expected: %s)", promise.channel(), channel));
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
        return false;
    }

    private static final boolean ESTIMATE_TASK_SIZE_ON_SUBMIT =
            SystemPropertyUtil.getBoolean("io.netty.transport.estimateSizeOnSubmit", true);

    // Assuming a 64-bit JVM, 16 bytes object header, 4 reference fields and one int field, plus alignment
    private static final int WRITE_TASK_OVERHEAD =
            SystemPropertyUtil.getInt("io.netty.transport.writeTaskSizeOverhead", 48);

    static final class WriteTask implements SingleThreadEventLoop.NonWakeupRunnable {

        private final ObjectPool.Handle<WriteTask> handle;
        private DefaultChannelHandlerContext ctx;
        private Object msg;
        private ChannelOutboundInvokerCallback writeListener;
        private int size;

        private WriteTask(ObjectPool.Handle<WriteTask> handle) {
            this.handle = handle;
        }

        private static final ObjectPool<WriteTask> RECYCLER = ObjectPool.newPool(WriteTask::new);

        static WriteTask newInstance(
                DefaultChannelHandlerContext ctx, Object msg, ChannelOutboundInvokerCallback writeListener) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, writeListener);
            return task;
        }

        private static void init(WriteTask task, DefaultChannelHandlerContext ctx,
                                   Object msg, ChannelOutboundInvokerCallback writeListener) {
            task.ctx = ctx;
            task.msg = msg;
            task.writeListener = writeListener;

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                ctx.pipeline.incrementPendingOutboundBytes(task.size);
            } else {
                task.size = 0;
            }
        }

        @Override
        public void run() {
            try {
                decrementPendingOutboundBytes();
                ctx.findAndInvokeWrite(msg, writeListener);
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
            writeListener = null;
            handle.recycle(this);
        }
    }

    private final class Tasks {
        private final Runnable invokeChannelReadCompleteTask;
        private final Runnable invokeReadTask;
        private final Runnable invokeChannelWritableStateChangedTask;
        private final Runnable invokeFlushTask;

        Tasks(DefaultChannelHandlerContext ctx) {
            invokeChannelReadCompleteTask = ctx::findAndInvokeChannelReadComplete;
            invokeReadTask = () -> ctx.findAndInvokeRead();
            invokeChannelWritableStateChangedTask = ctx::invokeChannelWritabilityChanged;
            invokeFlushTask = () -> ctx.findAndInvokeFlush();
        }
    }
}
