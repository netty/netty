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
import io.netty.util.DefaultAttributeMap;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.SocketAddress;

import static io.netty.channel.DefaultChannelPipeline.*;

final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelHandlerContext {

    volatile DefaultChannelHandlerContext next;
    volatile DefaultChannelHandlerContext prev;

    private final Channel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private final ChannelHandler handler;
    private boolean removed;

    // Will be set to null if no child executor should be used, otherwise it will be set to the
    // child executor.
    final EventExecutor executor;
    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    private Runnable invokeChannelReadSuspendedTask;
    private Runnable invokeRead0Task;
    private Runnable invokeChannelWritableStateChangedTask;

    @SuppressWarnings("unchecked")
    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutorGroup group, String name, ChannelHandler handler) {

        if (name == null) {
            throw new NullPointerException("name");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.handler = handler;

        if (group != null) {
            // Pin one of the child executors once and remember it so that the same child executor
            // is used to fire events for the same channel.
            EventExecutor childExecutor = pipeline.childExecutors.get(group);
            if (childExecutor == null) {
                childExecutor = group.next();
                pipeline.childExecutors.put(group, childExecutor);
            }
            executor = childExecutor;
        } else {
            executor = null;
        }
    }

    void freeInbound() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            freeInbound0();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    freeInbound0();
                }
            });
        }
    }

    private void freeInbound0() {
        if (next != null) {
            DefaultChannelHandlerContext nextCtx = findContextInbound();
            nextCtx.freeInbound();
        } else {
            // Freed all inbound buffers. Remove all handlers from the pipeline one by one from tail (exclusive)
            // to head (inclusive) to trigger handlerRemoved(). If the removed handler has an outbound buffer, free it,
            // too.  Note that the tail handler is excluded because it's neither an outbound buffer and it doesn't
            // do anything in handlerRemoved().
            pipeline.tail.prev.teardown();
        }
    }

    void teardownAll() {
        pipeline.tail.prev.teardown();
    }

    /** Invocation initiated by {@link #freeInbound0()} after freeing all inbound buffers. */
    private void teardown() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            teardown0();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    teardown0();
                }
            });
        }
    }

    private void teardown0() {
        DefaultChannelHandlerContext prev = this.prev;
        if (prev != null) {
            synchronized (pipeline) {
                pipeline.remove0(this);
            }
            prev.teardown();
        }
    }

    @Override
    public Channel channel() {
        return channel;
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
        if (executor == null) {
            return channel().eventLoop();
        } else {
            return executor;
        }
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRegistered();
                }
            });
        }
        return this;
    }

    private void invokeChannelRegistered() {
        try {
            ((ChannelInboundHandler) handler()).channelRegistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelUnregistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelUnregistered();
                }
            });
        }
        return this;
    }

    private void invokeChannelUnregistered() {
        try {
            ((ChannelInboundHandler) handler()).channelUnregistered(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelActive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelActive();
                }
            });
        }
        return this;
    }

    private void invokeChannelActive() {
        try {
            ((ChannelInboundHandler) handler()).channelActive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelInactive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelInactive();
                }
            });
        }
        return this;
    }

    private void invokeChannelInactive() {
        try {
            ((ChannelInboundHandler) handler()).channelInactive(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        next.invokeExceptionCaught(cause);
        return this;
    }

    private void invokeExceptionCaught(final Throwable cause) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeExceptionCaught0(cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeExceptionCaught0(cause);
                    }
                });
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
    }

    private void invokeExceptionCaught0(Throwable cause) {
        ChannelHandler handler = handler();
        try {
            handler.exceptionCaught(this, cause);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler's " +
                        "exceptionCaught() method while handling the following exception:", cause);
            }
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeUserEventTriggered(event);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeUserEventTriggered(event);
                }
            });
        }
        return this;
    }

    private void invokeUserEventTriggered(Object event) {
        ChannelInboundHandler handler = (ChannelInboundHandler) handler();
        try {
            handler.userEventTriggered(this, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireMessageReceived(Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }
        return fireMessageReceived(MessageList.newInstance(msg));
    }

    @Override
    public ChannelHandlerContext fireMessageReceived(final MessageList<?> msgs) {
        if (msgs == null) {
            throw new NullPointerException("msgs");
        }

        if (msgs.isEmpty()) {
            msgs.recycle();
            return this;
        }

        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeMessageReceived(msgs);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeMessageReceived(msgs);
                }
            });
        }
        return this;
    }

    private void invokeMessageReceived(MessageList<?> msgs) {
        ChannelInboundHandler handler = (ChannelInboundHandler) handler();
        try {
            handler.messageReceived(this, msgs.cast());
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadSuspended() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelReadSuspended();
        } else {
            Runnable task = next.invokeChannelReadSuspendedTask;
            if (task == null) {
                next.invokeChannelReadSuspendedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelReadSuspended();
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void invokeChannelReadSuspended() {
        try {
            ((ChannelInboundHandler) handler()).channelReadSuspended(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        final DefaultChannelHandlerContext next = findContextInbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeChannelWritabilityChanged();
        } else {
            Runnable task = next.invokeChannelWritableStateChangedTask;
            if (task == null) {
                next.invokeChannelWritableStateChangedTask = task = new Runnable() {
                    @Override
                    public void run() {
                        next.invokeChannelReadSuspended();
                    }
                };
            }
            executor.execute(task);
        }
        return this;
    }

    private void invokeChannelWritabilityChanged() {
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
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(MessageList<?> msgs) {
        return write(msgs, newPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validatePromise(promise, false);
        return findContextOutbound().invokeBind(localAddress, promise);
    }

    private ChannelFuture invokeBind(final SocketAddress localAddress, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeBind0(localAddress, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeBind0(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private void invokeBind0(SocketAddress localAddress, ChannelPromise promise) {
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
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validatePromise(promise, false);
        return findContextOutbound().invokeConnect(remoteAddress, localAddress, promise);
    }

    private ChannelFuture invokeConnect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeConnect0(remoteAddress, localAddress, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeConnect0(remoteAddress, localAddress, promise);
                }
            });
        }

        return promise;
    }

    private void invokeConnect0(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).connect(this, remoteAddress, localAddress, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        validatePromise(promise, false);

        // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
        // So far, UDP/IP is the only transport that has such behavior.
        if (!channel().metadata().hasDisconnect()) {
            return findContextOutbound().invokeClose(promise);
        }

        return findContextOutbound().invokeDisconnect(promise);
    }

    private ChannelFuture invokeDisconnect(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeDisconnect0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDisconnect0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeDisconnect0(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).disconnect(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        validatePromise(promise, false);
        return findContextOutbound().invokeClose(promise);
    }

    private ChannelFuture invokeClose(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeClose0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeClose0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeClose0(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).close(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        validatePromise(promise, false);
        return findContextOutbound().invokeDeregister(promise);
    }

    private ChannelFuture invokeDeregister(final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeDeregister0(promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDeregister0(promise);
                }
            });
        }

        return promise;
    }

    private void invokeDeregister0(ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).deregister(this, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public void read() {
        findContextOutbound().invokeRead();
    }

    private void invokeRead() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeRead0();
        } else {
            Runnable task = invokeRead0Task;
            if (task == null) {
                invokeRead0Task = task = new Runnable() {
                    @Override
                    public void run() {
                        invokeRead0();
                    }
                };
            }
            executor.execute(task);
        }
    }

    private void invokeRead0() {
        try {
            ((ChannelOutboundHandler) handler()).read(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture write(Object msg, final ChannelPromise promise) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }
        return write(MessageList.newInstance(msg), promise);
    }

    @Override
    public ChannelFuture write(MessageList<?> msgs, ChannelPromise promise) {
        if (msgs == null) {
            throw new NullPointerException("msgs");
        }
        validatePromise(promise, true);

        return findContextOutbound().invokeWrite(msgs, promise);
    }

    private ChannelFuture invokeWrite(final MessageList<?> msgs, final ChannelPromise promise) {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            invokeWrite0(msgs, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeWrite0(msgs, promise);
                }
            });
        }

        return promise;
    }

    private void invokeWrite0(MessageList<?> msgs, ChannelPromise promise) {
        ChannelOutboundHandler handler = (ChannelOutboundHandler) handler();
        try {
            handler.write(this, msgs.cast(), promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        // only try to fail the promise if its not a VoidChannelPromise, as
        // the VoidChannelPromise would also fire the cause through the pipeline
        if (promise instanceof VoidChannelPromise) {
            return;
        }

        if (!promise.tryFailure(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to fail the promise because it's done already: {}", promise, cause);
            }
        }
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

    private void validatePromise(ChannelFuture promise, boolean allowUnsafe) {
        if (promise == null) {
            throw new NullPointerException("promise");
        }
        if (promise.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), channel()));
        }
        if (promise.isDone()) {
            throw new IllegalArgumentException("future already done");
        }
        if (!allowUnsafe && promise instanceof VoidChannelPromise) {
            throw new IllegalArgumentException("VoidChannelPromise not allowed for this operation");
        }
        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException("AbstractChannel.CloseFuture may not send through the pipeline");
        }
    }

    private DefaultChannelHandlerContext findContextInbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!(ctx.handler() instanceof ChannelInboundHandler));
        return ctx;
    }

    private DefaultChannelHandlerContext findContextOutbound() {
        DefaultChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!(ctx.handler() instanceof ChannelOutboundHandler));
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel.voidPromise();
    }

    void setRemoved() {
        removed = true;
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }
}
