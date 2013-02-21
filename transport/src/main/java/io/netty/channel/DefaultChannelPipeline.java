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

import io.netty.buffer.Buf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.ReferenceCounted;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel.Unsafe;
import io.netty.util.internal.InternalLogger;
import io.netty.util.internal.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
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

    final DefaultChannelHandlerContext head;
    final DefaultChannelHandlerContext tail;

    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);
    private boolean firedChannelActive;
    private boolean fireInboundBufferUpdatedOnActivation;

    final Map<EventExecutorGroup, EventExecutor> childExecutors =
            new IdentityHashMap<EventExecutorGroup, EventExecutor>();

    private boolean inboundShutdown;
    private boolean outboundShutdown;

    public DefaultChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;

        TailHandler tailHandler = new TailHandler();
        tail = new DefaultChannelHandlerContext(this, generateName(tailHandler), tailHandler);

        HeadHandler headHandler;
        switch (channel.metadata().bufferType()) {
        case BYTE:
            headHandler = new ByteHeadHandler(channel.unsafe());
            break;
        case MESSAGE:
            headHandler = new MessageHeadHandler(channel.unsafe());
            break;
        default:
            throw new Error("unknown buffer type: " + channel.metadata().bufferType());
        }

        head = new DefaultChannelHandlerContext(this, generateName(headHandler), headHandler);

        head.next = tail;
        tail.prev = head;
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
            newCtx = new DefaultChannelHandlerContext(this, group, name, handler);

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
        DefaultChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;

        callBeforeAdd(newCtx);

        head.next = newCtx;
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

            newCtx = new DefaultChannelHandlerContext(this, group, name, handler);
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
        DefaultChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;

        callBeforeAdd(newCtx);

        prev.next = newCtx;
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
            newCtx = new DefaultChannelHandlerContext(this, group, name, handler);

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

        newCtx.prev = ctx.prev;
        newCtx.next = ctx;

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
            newCtx = new DefaultChannelHandlerContext(this, group, name, handler);

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

        newCtx.prev = ctx;
        newCtx.next = ctx.next;

        callBeforeAdd(newCtx);

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
        remove(getContextOrDie(handler), false);
        return this;
    }

    @Override
    public ChannelPipeline removeAndForward(ChannelHandler handler) {
        remove(getContextOrDie(handler), true);
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        return remove(getContextOrDie(name), false).handler();
    }

    @Override
    public ChannelHandler removeAndForward(String name) {
        return remove(getContextOrDie(name), true).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType), false).handler();
    }

    private DefaultChannelHandlerContext remove(final DefaultChannelHandlerContext ctx, final boolean forward) {
        assert ctx != head && ctx != tail;

        DefaultChannelHandlerContext context;
        Future<?> future;

        synchronized (this) {
            if (!ctx.channel().isRegistered() || ctx.executor().inEventLoop()) {
                remove0(ctx, forward);
                return ctx;
            } else {
               future = ctx.executor().submit(new Runnable() {
                   @Override
                   public void run() {
                       synchronized (DefaultChannelPipeline.this) {
                           remove0(ctx, forward);
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

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T removeAndForward(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType), true).handler();
    }

    private void remove0(DefaultChannelHandlerContext ctx, boolean forward) {
        callBeforeRemove(ctx);

        DefaultChannelHandlerContext prev = ctx.prev;
        DefaultChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
        name2ctx.remove(ctx.name());

        callAfterRemove(ctx, forward);
    }

    @Override
    public ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next, false).handler();
    }

    @Override
    public ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev, false).handler();
    }

    @Override
    public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler, false);
        return this;
    }

    @Override
    public ChannelPipeline replaceAndForward(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler, true);
        return this;
    }

    @Override
    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler, false);
    }

    @Override
    public ChannelHandler replaceAndForward(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler, false);
    }

    private ChannelHandler replace(
            final DefaultChannelHandlerContext ctx, final String newName,
            ChannelHandler newHandler, final boolean forward) {

        assert ctx != head && ctx != tail;

        Future<?> future;
        synchronized (this) {
            boolean sameName = ctx.name().equals(newName);
            if (!sameName) {
                checkDuplicateName(newName);
            }

            final DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, ctx.executor, newName, newHandler);

            if (!newCtx.channel().isRegistered() || newCtx.executor().inEventLoop()) {
                replace0(ctx, newName, newCtx, forward);

                return ctx.handler();
            } else {
                future = newCtx.executor().submit(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (DefaultChannelPipeline.this) {
                            replace0(ctx, newName, newCtx, forward);
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

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ChannelHandler> T replaceAndForward(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler, true);
    }

    private void replace0(DefaultChannelHandlerContext ctx, String newName,
                          DefaultChannelHandlerContext newCtx, boolean forward) {
        boolean sameName = ctx.name().equals(newName);

        DefaultChannelHandlerContext prev = ctx.prev;
        DefaultChannelHandlerContext next = ctx.next;
        newCtx.prev = prev;
        newCtx.next = next;

        callBeforeRemove(ctx);
        callBeforeAdd(newCtx);

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
            callAfterRemove(ctx, forward);
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
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
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
                remove((DefaultChannelHandlerContext) ctx, false);
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

    private void callAfterRemove(final DefaultChannelHandlerContext ctx, boolean forward) {
        final ChannelHandler handler = ctx.handler();

        // Notify the complete removal.
        try {
            handler.afterRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelPipelineException(
                    ctx.handler().getClass().getName() +
                    ".afterRemove() has thrown an exception.", t);
        }

        if (forward) {
            ctx.forwardBufferContent();
        } else {
            ctx.clearBuffer();
        }

        ctx.removed = true;

        // Free all buffers before completing removal.
        if (!channel.isRegistered()) {
            ctx.freeHandlerBuffersAfterRemoval();
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
            if (ctx == tail) {
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
        return (MessageBuf<T>) tail.nextOutboundMessageBuffer();
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        return tail.nextOutboundByteBuffer();
    }

    boolean isInboundShutdown() {
        return inboundShutdown;
    }

    boolean isOutboundShutdown() {
        return outboundShutdown;
    }

    void shutdownInbound() {
        inboundShutdown = true;
    }

    void shutdownOutbound() {
        outboundShutdown = true;
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        head.fireChannelRegistered();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        head.fireChannelUnregistered();

        // Free all buffers if channel is closed and unregistered.
        if (!channel.isOpen()) {
            head.invokeFreeInboundBuffer();
        }
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        firedChannelActive = true;
        head.fireChannelActive();

        if (channel.config().isAutoRead()) {
            channel.read();
        }

        if (fireInboundBufferUpdatedOnActivation) {
            fireInboundBufferUpdatedOnActivation = false;
            head.fireInboundBufferUpdated();
        }
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        // Some implementations such as EmbeddedChannel can trigger inboundBufferUpdated()
        // after deactivation, so it's safe not to revert the firedChannelActive flag here.
        // Also, all known transports never get re-activated.
        //firedChannelActive = false;
        head.fireChannelInactive();
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        head.fireExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        head.fireUserEventTriggered(event);
        return this;
    }

    @Override
    public ChannelPipeline fireInboundBufferUpdated() {
        if (!firedChannelActive) {
            fireInboundBufferUpdatedOnActivation = true;
            return this;
        }
        head.fireInboundBufferUpdated();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadSuspended() {
        head.fireChannelReadSuspended();
        if (channel.config().isAutoRead()) {
            read();
        }
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return tail.close();
    }

    @Override
    public ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public ChannelFuture flush() {
        return tail.flush();
    }

    @Override
    public ChannelFuture write(Object message) {
        return tail.write(message);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }

    @Override
    public void read() {
        tail.read();
    }

    @Override
    public ChannelFuture flush(ChannelPromise promise) {
        return tail.flush(promise);
    }

    @Override
    public ChannelFuture sendFile(FileRegion region) {
        return tail.sendFile(region);
    }

    @Override
    public ChannelFuture sendFile(FileRegion region, ChannelPromise promise) {
        return tail.sendFile(region, promise);
    }

    @Override
    public ChannelFuture write(Object message, ChannelPromise promise) {
        return tail.write(message, promise);
    }

    void notifyHandlerException(Throwable cause) {
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
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) context(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    // A special catch-all handler that handles both bytes and messages.
    static final class TailHandler implements ChannelInboundHandler {

        final ByteBuf byteSink = Unpooled.buffer(0);
        final MessageBuf<Object> msgSink = Unpooled.messageBuffer(0);

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void channelReadSuspended(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void beforeAdd(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void afterAdd(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void beforeRemove(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void afterRemove(ChannelHandlerContext ctx) throws Exception { }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception { }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the end of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.", cause);
        }

        @Override
        public Buf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            throw new Error();
        }

        @Override
        public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            byteSink.release();
            msgSink.release();
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
            int byteSinkSize = byteSink.readableBytes();
            if (byteSinkSize != 0) {
                byteSink.clear();
                logger.warn(
                        "Discarded {} inbound byte(s) that reached at the end of the pipeline. " +
                        "Please check your pipeline configuration.", byteSinkSize);
            }

            int msgSinkSize = msgSink.size();
            if (msgSinkSize != 0) {
                MessageBuf<Object> in = msgSink;
                for (;;) {
                    Object m = in.poll();
                    if (m == null) {
                        break;
                    }

                    if (m instanceof ReferenceCounted) {
                        ((ReferenceCounted) m).release();
                    }
                    logger.debug(
                            "Discarded inbound message {} that reached at the end of the pipeline. " +
                                    "Please check your pipeline configuration.", m);
                }
                logger.warn(
                        "Discarded {} inbound message(s) that reached at the end of the pipeline. " +
                        "Please check your pipeline configuration.", msgSinkSize);
            }
        }
    }

    abstract static class HeadHandler implements ChannelOutboundHandler {

        protected final Unsafe unsafe;
        ByteBuf byteSink;
        MessageBuf<Object> msgSink;

        protected HeadHandler(Unsafe unsafe) {
            this.unsafe = unsafe;
        }

        void init(ChannelHandlerContext ctx) {
            switch (ctx.channel().metadata().bufferType()) {
            case BYTE:
                byteSink = ctx.alloc().ioBuffer();
                msgSink = Unpooled.messageBuffer(0);
                break;
            case MESSAGE:
                byteSink = Unpooled.buffer(0);
                msgSink = Unpooled.messageBuffer();
                break;
            default:
                throw new Error();
            }
        }

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

        @Override
        public final Buf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            throw new Error();
        }

        @Override
        public final void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
            msgSink.release();
            byteSink.release();
        }
    }

    private static final class ByteHeadHandler extends HeadHandler {

        private ByteHeadHandler(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            int discardedMessages = 0;
            MessageBuf<Object> in = msgSink;
            for (;;) {
                Object m = in.poll();
                if (m == null) {
                    break;
                }

                if (m instanceof ByteBuf) {
                    ByteBuf src = (ByteBuf) m;
                    byteSink.writeBytes(src, src.readerIndex(), src.readableBytes());
                } else {
                    logger.debug(
                            "Discarded outbound message {} that reached at the end of the pipeline. " +
                                    "Please check your pipeline configuration.", m);
                    discardedMessages ++;
                }

                if (m instanceof ReferenceCounted) {
                    ((ReferenceCounted) m).release();
                }
            }

            if (discardedMessages != 0) {
                logger.warn(
                        "Discarded {} outbound message(s) that reached at the end of the pipeline. " +
                        "Please check your pipeline configuration.", discardedMessages);
            }

            unsafe.flush(promise);
        }
    }

    private static final class MessageHeadHandler extends HeadHandler {

        private MessageHeadHandler(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            int byteSinkSize = byteSink.readableBytes();
            if (byteSinkSize != 0) {
                byteSink.clear();
                logger.warn(
                        "Discarded {} outbound byte(s) that reached at the end of the pipeline. " +
                                "Please check your pipeline configuration.", byteSinkSize);
            }
            unsafe.flush(promise);
        }
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }

    @Override
    public boolean isWritable() {
        return tail.isWritable();
    }
}
