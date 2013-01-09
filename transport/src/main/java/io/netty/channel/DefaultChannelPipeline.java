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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Freeable;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;

import static io.netty.channel.DefaultChannelHandlerContext.*;

/**
 * The default {@link ChannelPipeline} implementation.  It is usually created
 * by a {@link Channel} implementation when the {@link Channel} is created.
 */
final class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    final Channel channel;
    private final Channel.Unsafe unsafe;

    final DefaultChannelHandlerContext head;
    final DefaultChannelHandlerContext tail;

    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);
    private boolean firedChannelActive;
    private boolean fireInboundBufferUpdatedOnActivation;

    final Map<EventExecutorGroup, EventExecutor> childExecutors =
            new IdentityHashMap<EventExecutorGroup, EventExecutor>();

    private static final TailHandler TAIL_HANDLER = new TailHandler();

    public DefaultChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;

        tail = new DefaultChannelHandlerContext(
                this, null, null, null, generateName(TAIL_HANDLER), TAIL_HANDLER);

        HeadHandler headHandler;
        switch (channel.metadata().bufferType()) {
        case BYTE:
            headHandler = new ByteHeadHandler();
            break;
        case MESSAGE:
            headHandler = new MessageHeadHandler();
            break;
        default:
            throw new Error("unknown buffer type: " + channel.metadata().bufferType());
        }

        head = new DefaultChannelHandlerContext(
                this, null, null, tail, generateName(headHandler), headHandler);
        tail.prev = head;

        unsafe = channel.unsafe();
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, final String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext newCtx;

        synchronized (this) {
            checkDuplicateName(name);
            newCtx = new DefaultChannelHandlerContext(this, group, null, null, name, handler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                addFirst0(name, newCtx);
                return this;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        newCtx.executeOnEventLoop(new Runnable() {
            @Override
            public void run() {
                synchronized (DefaultChannelPipeline.this) {
                    checkDuplicateName(name);
                    addFirst0(name, newCtx);
                }
            }
        });

        return this;
    }

    private void addFirst0(String name, DefaultChannelHandlerContext newCtx) {
        callBeforeAdd(newCtx);

        DefaultChannelHandlerContext nextCtx = head.next;
        head.next = newCtx;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        nextCtx.prev = newCtx;

        name2ctx.put(name, newCtx);

        callAfterAdd(newCtx);
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, final String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext newCtx;

        synchronized (this) {
            checkDuplicateName(name);

            newCtx = new DefaultChannelHandlerContext(this, group, null, null, name, handler);
            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                addLast0(name, newCtx);
                return this;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        newCtx.executeOnEventLoop(new Runnable() {
            @Override
            public void run() {
                synchronized (DefaultChannelPipeline.this) {
                    checkDuplicateName(name);
                    addLast0(name, newCtx);
                }
            }
        });

        return this;
    }

    private void addLast0(
            final String name, DefaultChannelHandlerContext newCtx) {
        callBeforeAdd(newCtx);

        DefaultChannelHandlerContext prev = tail.prev;
        prev.next = newCtx;
        newCtx.prev = prev;
        newCtx.next = tail;
        tail.prev = newCtx;

        name2ctx.put(name, newCtx);

        callAfterAdd(newCtx);
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public ChannelPipeline addBefore(
            EventExecutorGroup group, String baseName, final String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext ctx;
        final DefaultChannelHandlerContext newCtx;

        synchronized (this) {
            ctx = getContextOrDie(baseName);
            checkDuplicateName(name);
            newCtx = new DefaultChannelHandlerContext(this, group, ctx.prev, ctx, name, handler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                addBefore0(name, ctx, newCtx);
                return this;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        newCtx.executeOnEventLoop(new Runnable() {
                @Override
                public void run() {
                    synchronized (DefaultChannelPipeline.this) {
                        checkDuplicateName(name);
                        addBefore0(name, ctx, newCtx);
                    }
                }
            });

        return this;
    }

    private void addBefore0(final String name, DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
        callBeforeAdd(newCtx);

        ctx.prev.next = newCtx;
        ctx.prev = newCtx;
        name2ctx.put(name, newCtx);

        callAfterAdd(newCtx);
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }

    @Override
    public ChannelPipeline addAfter(
            EventExecutorGroup group, String baseName, final String name, ChannelHandler handler) {
        final DefaultChannelHandlerContext ctx;
        final DefaultChannelHandlerContext newCtx;

        synchronized (this) {
            ctx = getContextOrDie(baseName);
            checkDuplicateName(name);
            newCtx = new DefaultChannelHandlerContext(this, group, null, null, name, handler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                addAfter0(name, ctx, newCtx);
                return this;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        newCtx.executeOnEventLoop(new Runnable() {
            @Override
            public void run() {
                synchronized (DefaultChannelPipeline.this) {
                    checkDuplicateName(name);
                    addAfter0(name, ctx, newCtx);
                }
            }
        });

        return this;
    }

    private void addAfter0(final String name, DefaultChannelHandlerContext ctx, DefaultChannelHandlerContext newCtx) {
        checkDuplicateName(name);

        callBeforeAdd(newCtx);

        newCtx.prev = ctx;
        newCtx.next = ctx.next;
        ctx.next.prev = newCtx;
        ctx.next = newCtx;

        name2ctx.put(name, newCtx);

        callAfterAdd(newCtx);
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        if (handlers.length == 0 || handlers[0] == null) {
            return this;
        }

        int size;
        for (size = 1; size < handlers.length; size ++) {
            if (handlers[size] == null) {
                break;
            }
        }

        for (int i = size - 1; i >= 0; i --) {
            ChannelHandler h = handlers[i];
            addFirst(executor, generateName(h), h);
        }

        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, generateName(h), h);
        }

        return this;
    }

    private static String generateName(ChannelHandler handler) {
        String type = handler.getClass().getSimpleName();
        StringBuilder buf = new StringBuilder(type.length() + 10);
        buf.append(type);
        buf.append("-0");
        buf.append(Long.toHexString(System.identityHashCode(handler) & 0xFFFFFFFFL | 0x100000000L));
        buf.setCharAt(buf.length() - 9, 'x');
        return buf.toString();
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).handler();
    }

    private DefaultChannelHandlerContext remove(final DefaultChannelHandlerContext ctx) {
        assert ctx != head && ctx != tail;

        DefaultChannelHandlerContext context;
        Future<?> future;

        synchronized (this) {
            if (!ctx.channel().isRegistered() || ctx.executor().inEventLoop()) {
                remove0(ctx);
                return ctx;
            } else {
               future = ctx.executor().submit(new Runnable() {
                   @Override
                   public void run() {
                       synchronized (DefaultChannelPipeline.this) {
                           remove0(ctx);
                       }
                   }
               });
               context = ctx;
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        waitForFuture(future);

        return context;
    }

    private void remove0(DefaultChannelHandlerContext ctx) {
        callBeforeRemove(ctx);

        DefaultChannelHandlerContext prev = ctx.prev;
        DefaultChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
        name2ctx.remove(ctx.name());

        callAfterRemove(ctx);
    }

    @Override
    public ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next).handler();
    }

    @Override
    public ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev).handler();
    }

    @Override
    public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
        return this;
    }

    @Override
    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    private ChannelHandler replace(
            final DefaultChannelHandlerContext ctx, final String newName, ChannelHandler newHandler) {

        assert ctx != head && ctx != tail;

        Future<?> future;
        synchronized (this) {
            boolean sameName = ctx.name().equals(newName);
            if (!sameName) {
                checkDuplicateName(newName);
            }

            final DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, ctx.executor, null, null, newName, newHandler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                replace0(ctx, newName, newCtx);
                return ctx.handler();
            } else {
                future = newCtx.executor().submit(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (DefaultChannelPipeline.this) {
                            replace0(ctx, newName, newCtx);
                        }
                    }
                });
            }
        }

        // Run the following 'waiting' code outside of the above synchronized block
        // in order to avoid deadlock

        waitForFuture(future);

        return ctx.handler();
    }

    private void replace0(DefaultChannelHandlerContext ctx, String newName, DefaultChannelHandlerContext newCtx) {
        boolean sameName = ctx.name().equals(newName);

        DefaultChannelHandlerContext prev = ctx.prev;
        DefaultChannelHandlerContext next = ctx.next;

        callBeforeRemove(ctx);
        callBeforeAdd(newCtx);

        newCtx.prev = prev;
        newCtx.next = next;
        prev.next = newCtx;
        next.prev = newCtx;

        if (!sameName) {
            name2ctx.remove(ctx.name());
        }
        name2ctx.put(newName, newCtx);

        ChannelPipelineException removeException = null;
        ChannelPipelineException addException = null;
        boolean removed = false;
        try {
            callAfterRemove(ctx);
            removed = true;
        } catch (ChannelPipelineException e) {
            removeException = e;
        }

        boolean added = false;
        try {
            callAfterAdd(newCtx);
            added = true;
        } catch (ChannelPipelineException e) {
            addException = e;
        }

        if (!removed && !added) {
            logger.warn(removeException.getMessage(), removeException);
            logger.warn(addException.getMessage(), addException);
            throw new ChannelPipelineException(
                    "Both " + ctx.handler().getClass().getName() +
                    ".afterRemove() and " + newCtx.handler().getClass().getName() +
                    ".afterAdd() failed; see logs.");
        } else if (!removed) {
            throw removeException;
        } else if (!added) {
            throw addException;
        }
    }

    private static void callBeforeAdd(ChannelHandlerContext ctx) {
        ChannelHandler handler = ctx.handler();
        if (handler instanceof ChannelStateHandlerAdapter) {
            ChannelStateHandlerAdapter h = (ChannelStateHandlerAdapter) handler;
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(
                        h.getClass().getName()  +
                        " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
        try {
            handler.beforeAdd(ctx);
        } catch (Throwable t) {
            throw new ChannelPipelineException(
                    handler.getClass().getName() +
                    ".beforeAdd() has thrown an exception; not adding.", t);
        }
    }

    private void callAfterAdd(ChannelHandlerContext ctx) {
        try {
            ctx.handler().afterAdd(ctx);
        } catch (Throwable t) {
            boolean removed = false;
            try {
                remove((DefaultChannelHandlerContext) ctx);
                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }

            if (removed) {
                throw new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".afterAdd() has thrown an exception; removed.", t);
            } else {
                throw new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                        ".afterAdd() has thrown an exception; also failed to remove.", t);
            }
        }
    }

    private static void callBeforeRemove(ChannelHandlerContext ctx) {
        try {
            ctx.handler().beforeRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelPipelineException(
                    ctx.handler().getClass().getName() +
                    ".beforeRemove() has thrown an exception; not removing.", t);
        }
    }

    private static void callAfterRemove(ChannelHandlerContext ctx) {
        try {
            ctx.handler().afterRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelPipelineException(
                    ctx.handler().getClass().getName() +
                    ".afterRemove() has thrown an exception.", t);
        }
    }

    @Override
    public ChannelHandler first() {
        DefaultChannelHandlerContext first = head.next;
        if (first == null) {
            return null;
        }
        return first.handler();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        return head.next;
    }

    @Override
    public ChannelHandler last() {
        DefaultChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last.handler();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        DefaultChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last;
    }

    @Override
    public ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public ChannelHandlerContext context(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        synchronized (this) {
            return name2ctx.get(name);
        }
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        DefaultChannelHandlerContext ctx = head.next;
        for (;;) {

            if (ctx == null) {
                return null;
            }

            if (ctx.handler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
        }
    }

    @Override
    public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }

        DefaultChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                return null;
            }
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public List<String> names() {
        List<String> list = new ArrayList<String>();
        DefaultChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                return list;
            }
            list.add(ctx.name());
            ctx = ctx.next;
        }
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        DefaultChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == tail) {
                return map;
            }
            map.put(ctx.name(), ctx.handler());
            ctx = ctx.next;
        }
    }

    /**
     * Returns the {@link String} representation of this pipeline.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append('{');
        DefaultChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                break;
            }

            buf.append('(');
            buf.append(ctx.name());
            buf.append(" = ");
            buf.append(ctx.handler().getClass().getName());
            buf.append(')');

            ctx = ctx.next;
            if (ctx == tail) {
                break;
            }

            buf.append(", ");
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> inboundMessageBuffer() {
        return (MessageBuf<T>) head.nextInboundMessageBuffer();
    }

    @Override
    public ByteBuf inboundByteBuffer() {
        return head.nextInboundByteBuffer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> MessageBuf<T> outboundMessageBuffer() {
        return (MessageBuf<T>) findOutboundMessageBuffer(tail.prev);
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        return findOutboundByteBuffer(tail.prev);
    }

    ByteBuf findOutboundByteBuffer(DefaultChannelHandlerContext ctx) {
        final DefaultChannelHandlerContext initialCtx = ctx;
        final Thread currentThread = Thread.currentThread();
        for (;;) {
            if (ctx == null) {
                if (initialCtx != null && initialCtx.next != null) {
                    throw new NoSuchBufferException(String.format(
                            "the handler '%s' could not find a %s whose outbound buffer is %s.",
                            initialCtx.next.name(), ChannelOutboundHandler.class.getSimpleName(),
                            ByteBuf.class.getSimpleName()));
                } else {
                    throw new NoSuchBufferException(String.format(
                            "the pipeline does not contain a %s whose outbound buffer is %s.",
                            ChannelOutboundHandler.class.getSimpleName(),
                            ByteBuf.class.getSimpleName()));
                }
            }

            if (ctx.hasOutboundByteBuffer()) {
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.outboundByteBuffer();
                } else {
                    ByteBridge bridge = ctx.outByteBridge.get();
                    if (bridge == null) {
                        bridge = new ByteBridge(ctx);
                        if (!ctx.outByteBridge.compareAndSet(null, bridge)) {
                            bridge = ctx.outByteBridge.get();
                        }
                    }
                    return bridge.byteBuf;
                }
            }
            ctx = ctx.prev;
        }
    }

    MessageBuf<Object> findOutboundMessageBuffer(DefaultChannelHandlerContext ctx) {
        final DefaultChannelHandlerContext initialCtx = ctx;
        final Thread currentThread = Thread.currentThread();
        for (;;) {
            if (ctx == null) {
                if (initialCtx.next != null) {
                    throw new NoSuchBufferException(String.format(
                            "the handler '%s' could not find a %s whose outbound buffer is %s.",
                            initialCtx.next.name(), ChannelOutboundHandler.class.getSimpleName(),
                            MessageBuf.class.getSimpleName()));
                } else {
                    throw new NoSuchBufferException(String.format(
                            "the pipeline does not contain a %s whose outbound buffer is %s.",
                            ChannelOutboundHandler.class.getSimpleName(),
                            MessageBuf.class.getSimpleName()));
                }
            }

            if (ctx.hasOutboundMessageBuffer()) {
                if (ctx.executor().inEventLoop(currentThread)) {
                    return ctx.outboundMessageBuffer();
                } else {
                    MessageBridge bridge = ctx.outMsgBridge.get();
                    if (bridge == null) {
                        bridge = new MessageBridge();
                        if (!ctx.outMsgBridge.compareAndSet(null, bridge)) {
                            bridge = ctx.outMsgBridge.get();
                        }
                    }
                    return bridge.msgBuf;
                }
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public void fireChannelRegistered() {
        head.fireChannelRegistered();
    }

    @Override
    public void fireChannelUnregistered() {
        head.fireChannelUnregistered();

        // Free all buffers if channel is closed and unregistered.
        if (!channel.isOpen()) {
            head.callFreeInboundBuffer();
        }
    }

    @Override
    public void fireChannelActive() {
        firedChannelActive = true;
        head.fireChannelActive();

        if (channel.config().isAutoRead()) {
            channel.read();
        }

        if (fireInboundBufferUpdatedOnActivation) {
            fireInboundBufferUpdatedOnActivation = false;
            head.fireInboundBufferUpdated();
        }
    }

    @Override
    public void fireChannelInactive() {
        // Some implementations such as EmbeddedChannel can trigger inboundBufferUpdated()
        // after deactivation, so it's safe not to revert the firedChannelActive flag here.
        // Also, all known transports never get re-activated.
        //firedChannelActive = false;
        head.fireChannelInactive();
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        head.fireExceptionCaught(cause);
    }

    @Override
    public void fireUserEventTriggered(Object event) {
        head.fireUserEventTriggered(event);
    }

    @Override
    public void fireInboundBufferUpdated() {
        if (!firedChannelActive) {
            fireInboundBufferUpdatedOnActivation = true;
            return;
        }
        head.fireInboundBufferUpdated();
    }

    @Override
    public void fireInboundBufferSuspended() {
        head.fireInboundBufferSuspended();
        if (channel.config().isAutoRead()) {
            channel.read();
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, channel.newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, channel.newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, channel.newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(channel.newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(channel.newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(channel.newPromise());
    }

    @Override
    public ChannelFuture flush() {
        return flush(channel.newPromise());
    }

    @Override
    public ChannelFuture write(Object message) {
        return write(message, channel.newPromise());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return bind(lastContext(FLAG_OPERATION_HANDLER), localAddress, promise);
    }

    ChannelFuture bind(
            final DefaultChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validateFuture(promise);

        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelOperationHandler) ctx.handler()).bind(ctx, localAddress, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    bind(ctx, localAddress, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return connect(lastContext(FLAG_OPERATION_HANDLER), remoteAddress, localAddress, promise);
    }

    ChannelFuture connect(
            final DefaultChannelHandlerContext ctx, final SocketAddress remoteAddress,
            final SocketAddress localAddress, final ChannelPromise promise) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validateFuture(promise);

        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelOperationHandler) ctx.handler()).connect(ctx, remoteAddress, localAddress, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    connect(ctx, remoteAddress, localAddress, promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return disconnect(lastContext(FLAG_OPERATION_HANDLER), promise);
    }

    ChannelFuture disconnect(final DefaultChannelHandlerContext ctx, final ChannelPromise promise) {
        // Translate disconnect to close if the channel has no notion of disconnect-reconnect.
        // So far, UDP/IP is the only transport that has such behavior.
        if (!ctx.channel().metadata().hasDisconnect()) {
            return close(ctx, promise);
        }

        validateFuture(promise);
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelOperationHandler) ctx.handler()).disconnect(ctx, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    disconnect(ctx, promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return close(lastContext(FLAG_OPERATION_HANDLER), promise);
    }

    ChannelFuture close(final DefaultChannelHandlerContext ctx, final ChannelPromise promise) {
        validateFuture(promise);
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelOperationHandler) ctx.handler()).close(ctx, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    close(ctx, promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        return deregister(lastContext(FLAG_OPERATION_HANDLER), promise);
    }

    ChannelFuture deregister(final DefaultChannelHandlerContext ctx, final ChannelPromise promise) {
        validateFuture(promise);
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelOperationHandler) ctx.handler()).deregister(ctx, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    deregister(ctx, promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture sendFile(FileRegion region) {
        return sendFile(region, channel().newPromise());
    }

    @Override
    public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
        return sendFile(lastContext(FLAG_OPERATION_HANDLER), region, promise);
    }

    ChannelFuture sendFile(final DefaultChannelHandlerContext ctx, final FileRegion region,
                           final ChannelPromise promise) {
        if (region == null) {
            throw new NullPointerException("region");
        }
        validateFuture(promise);

        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ctx.flushBridge();
                ((ChannelOperationHandler) ctx.handler()).sendFile(ctx, region, promise);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    sendFile(ctx, region, promise);
                }
            });
        }

        return promise;
    }

    @Override
    public void read() {
        read(lastContext(FLAG_OPERATION_HANDLER));
    }

    void read(final DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            read0(ctx);
        } else {
            executor.execute(ctx.read0Task);
        }
    }

    void read0(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelOperationHandler) ctx.handler()).read(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture flush(ChannelPromise promise) {
        return flush(lastContext(FLAG_OPERATION_HANDLER), promise);
    }

    ChannelFuture flush(final DefaultChannelHandlerContext ctx, final ChannelPromise promise) {
        validateFuture(promise);
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            flush0(ctx, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    flush0(ctx, promise);
                }
            });
        }

        return promise;
    }

    private void flush0(final DefaultChannelHandlerContext ctx, ChannelPromise promise) {
        if (!channel.isRegistered() && !channel.isActive()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        ChannelOperationHandler handler = (ChannelOperationHandler) ctx.handler();
        try {
            ctx.flushBridge();
            handler.flush(ctx, promise);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            if (handler instanceof ChannelOutboundByteHandler) {
                try {
                    ((ChannelOutboundByteHandler) handler).discardOutboundReadBytes(ctx);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            }
        }
    }

    @Override
    public ChannelFuture write(Object message, ChannelPromise promise) {
        if (message instanceof FileRegion) {
            return sendFile((FileRegion) message, promise);
        }
        return write(tail.prev, message, promise);
    }

    ChannelFuture write(DefaultChannelHandlerContext ctx, final Object message, final ChannelPromise promise) {
        if (message == null) {
            throw new NullPointerException("message");
        }
        validateFuture(promise);

        final DefaultChannelHandlerContext initialCtx = ctx;
        EventExecutor executor;
        boolean msgBuf = false;
        for (;;) {
            if (ctx == null) {
                if (initialCtx.next != null) {
                    throw new NoSuchBufferException(String.format(
                            "the handler '%s' could not find a %s which accepts a %s, and " +
                            "the transport does not accept it as-is.",
                            initialCtx.next.name(),
                            ChannelOutboundHandler.class.getSimpleName(),
                            message.getClass().getSimpleName()));
                } else {
                    throw new NoSuchBufferException(String.format(
                            "the pipeline does not contain a %s which accepts a %s, and " +
                            "the transport does not accept it as-is.",
                            ChannelOutboundHandler.class.getSimpleName(),
                            message.getClass().getSimpleName()));
                }
            }

            if (ctx.hasOutboundMessageBuffer()) {
                msgBuf = true;
                executor = ctx.executor();
                break;
            }

            if (message instanceof ByteBuf && ctx.hasOutboundByteBuffer()) {
                executor = ctx.executor();
                break;
            }

            ctx = ctx.prev;
        }
        if (executor.inEventLoop()) {
            write0(ctx, message, promise, msgBuf);
            return promise;
        }

        final boolean msgBuf0 = msgBuf;
        final DefaultChannelHandlerContext ctx0 = ctx;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                write0(ctx0, message, promise, msgBuf0);
            }
        });

        return promise;
    }

    private void write0(DefaultChannelHandlerContext ctx, Object message, ChannelPromise promise, boolean msgBuf) {
        if (!channel.isRegistered() && !channel.isActive()) {
            promise.setFailure(new ClosedChannelException());
            return;
        }

        if (msgBuf) {
            ctx.outboundMessageBuffer().add(message);
        } else {
            ByteBuf buf = (ByteBuf) message;
            ctx.outboundByteBuffer().writeBytes(buf, buf.readerIndex(), buf.readableBytes());
        }
        flush0(ctx, promise);
    }

    private void validateFuture(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (future.channel() != channel) {
            throw new IllegalArgumentException(String.format(
                    "future.channel does not match: %s (expected: %s)", future.channel(), channel));
        }
        if (future.isDone()) {
            throw new IllegalArgumentException("future already done");
        }
        if (future instanceof ChannelFuture.Unsafe) {
            throw new IllegalArgumentException("internal use only future not allowed");
        }
    }

    DefaultChannelHandlerContext lastContext(int flag) {
        return findContextOutbound(tail.prev, flag);
    }

    static DefaultChannelHandlerContext findContextInbound(DefaultChannelHandlerContext ctx, int flag) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while ((realCtx.flags & flag) == 0) {
            realCtx = realCtx.next;
            if (realCtx == null) {
                return null;
            }
        }
        return realCtx;
    }

    static DefaultChannelHandlerContext findContextOutbound(DefaultChannelHandlerContext ctx, int flag) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while ((realCtx.flags & flag) == 0) {
            realCtx = realCtx.prev;
            if (realCtx == null) {
                return null;
            }
        }
        return realCtx;
    }

    protected void notifyHandlerException(Throwable cause) {
        if (!(cause instanceof ChannelPipelineException)) {
            cause = new ChannelPipelineException(cause);
        }

        if (inExceptionCaught(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler " +
                        "while handling an exceptionCaught event", cause);
            }
            return;
        }

        fireExceptionCaught(cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        for (;;) {
            if (cause == null) {
                return false;
            }

            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        }
    }

    private void checkDuplicateName(String name) {
        if (name2ctx.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(String name) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(name);
        if (ctx == null || ctx == head) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handler);
        if (ctx == null || ctx == head) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handlerType);
        if (ctx == null || ctx == head) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    private static final class TailHandler extends ChannelInboundMessageHandlerAdapter<Freeable> {
        public TailHandler() {
            super(Freeable.class);
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Freeable msg) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn("Freeable reached end-of-pipeline, call " + msg + ".free() to" +
                        " guard against resource leakage!");
            }
            msg.free();
        }
    }

    private abstract class HeadHandler implements ChannelOutboundHandler {
        @Override
        public final void beforeAdd(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public final void afterAdd(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public final void beforeRemove(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public final void afterRemove(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public final void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            unsafe.bind(localAddress, promise);
        }

        @Override
        public final void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public final void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.disconnect(promise);
        }

        @Override
        public final void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.close(promise);
        }

        @Override
        public final void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.deregister(promise);
        }

        @Override
        public final void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public final void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            unsafe.flush(promise);
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public final void sendFile(
                ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
            unsafe.sendFile(region, promise);
        }
    }

    private final class ByteHeadHandler extends HeadHandler implements ChannelOutboundByteHandler {
        @Override
        public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return ctx.alloc().ioBuffer();
        }

        @Override
        public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
            if (ctx.hasOutboundByteBuffer()) {
                ctx.outboundByteBuffer().discardSomeReadBytes();
            }
        }

        @Override
        public void freeOutboundBuffer(ChannelHandlerContext ctx) {
            ctx.outboundByteBuffer().free();
        }
    }

    private final class MessageHeadHandler extends HeadHandler implements ChannelOutboundMessageHandler<Object> {
        @Override
        public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @Override
        public void freeOutboundBuffer(ChannelHandlerContext ctx) {
            ctx.outboundMessageBuffer().free();
        }
    }
}
