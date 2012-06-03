/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ChannelBuffer;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * The default {@link ChannelPipeline} implementation.  It is usually created
 * by a {@link Channel} implementation when the {@link Channel} is created.
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    final Channel channel;
    private final Channel.Unsafe unsafe;
    private final ChannelBufferHolder<Object> directOutbound;

    private volatile DefaultChannelHandlerContext head;
    private volatile DefaultChannelHandlerContext tail;
    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);
    private boolean firedChannelActive;
    private boolean fireInboundBufferUpdatedOnActivation;

    final Map<EventExecutor, EventExecutor> childExecutors =
            new IdentityHashMap<EventExecutor, EventExecutor>();

    public DefaultChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
        unsafe = channel.unsafe();
        directOutbound = unsafe.directOutbound();
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
    public synchronized ChannelPipeline addFirst(EventExecutor executor, String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(executor, name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldHead = head;
            DefaultChannelHandlerContext newHead =
                    new DefaultChannelHandlerContext(this, executor, null, oldHead, name, handler);

            callBeforeAdd(newHead);

            oldHead.prev = newHead;
            head = newHead;
            name2ctx.put(name, newHead);

            callAfterAdd(newHead);
        }

        return this;
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public synchronized ChannelPipeline addLast(EventExecutor executor, String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(executor, name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldTail = tail;
            DefaultChannelHandlerContext newTail =
                    new DefaultChannelHandlerContext(this, executor, oldTail, null, name, handler);

            callBeforeAdd(newTail);

            oldTail.next = newTail;
            tail = newTail;
            name2ctx.put(name, newTail);

            callAfterAdd(newTail);
        }

        return this;
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public synchronized ChannelPipeline addBefore(EventExecutor executor, String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == head) {
            addFirst(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, executor, ctx.prev, ctx, name, handler);

            callBeforeAdd(newCtx);

            ctx.prev.next = newCtx;
            ctx.prev = newCtx;
            name2ctx.put(name, newCtx);

            callAfterAdd(newCtx);
        }

        return this;
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }

    @Override
    public synchronized ChannelPipeline addAfter(EventExecutor executor, String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == tail) {
            addLast(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, executor, ctx, ctx.next, name, handler);

            callBeforeAdd(newCtx);

            ctx.next.prev = newCtx;
            ctx.next = newCtx;
            name2ctx.put(name, newCtx);

            callAfterAdd(newCtx);
        }

        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public ChannelPipeline addFirst(EventExecutor executor, ChannelHandler... handlers) {
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
    public ChannelPipeline addLast(EventExecutor executor, ChannelHandler... handlers) {
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
    public synchronized void remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
    }

    @Override
    public synchronized ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    @Override
    public synchronized <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).handler();
    }

    private DefaultChannelHandlerContext remove(DefaultChannelHandlerContext ctx) {
        if (head == tail) {
            head = tail = null;
            name2ctx.clear();
        } else if (ctx == head) {
            removeFirst();
        } else if (ctx == tail) {
            removeLast();
        } else {
            callBeforeRemove(ctx);

            DefaultChannelHandlerContext prev = ctx.prev;
            DefaultChannelHandlerContext next = ctx.next;
            prev.next = next;
            next.prev = prev;
            name2ctx.remove(ctx.name());

            callAfterRemove(ctx);
        }
        return ctx;
    }

    @Override
    public synchronized ChannelHandler removeFirst() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultChannelHandlerContext oldHead = head;
        if (oldHead == null) {
            throw new NoSuchElementException();
        }

        callBeforeRemove(oldHead);

        if (oldHead.next == null) {
            head = tail = null;
            name2ctx.clear();
        } else {
            oldHead.next.prev = null;
            head = oldHead.next;
            name2ctx.remove(oldHead.name());
        }

        callAfterRemove(oldHead);

        return oldHead.handler();
    }

    @Override
    public synchronized ChannelHandler removeLast() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultChannelHandlerContext oldTail = tail;
        if (oldTail == null) {
            throw new NoSuchElementException();
        }

        callBeforeRemove(oldTail);

        if (oldTail.prev == null) {
            head = tail = null;
            name2ctx.clear();
        } else {
            oldTail.prev.next = null;
            tail = oldTail.prev;
            name2ctx.remove(oldTail.name());
        }

        callBeforeRemove(oldTail);

        return oldTail.handler();
    }

    @Override
    public synchronized void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
    }

    @Override
    public synchronized ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    private ChannelHandler replace(DefaultChannelHandlerContext ctx, String newName, ChannelHandler newHandler) {
        if (ctx == head) {
            removeFirst();
            addFirst(newName, newHandler);
        } else if (ctx == tail) {
            removeLast();
            addLast(newName, newHandler);
        } else {
            boolean sameName = ctx.name().equals(newName);
            if (!sameName) {
                checkDuplicateName(newName);
            }

            DefaultChannelHandlerContext prev = ctx.prev;
            DefaultChannelHandlerContext next = ctx.next;
            DefaultChannelHandlerContext newCtx =
                    new DefaultChannelHandlerContext(this, ctx.executor, prev, next, newName, newHandler);

            callBeforeRemove(ctx);
            callBeforeAdd(newCtx);

            prev.next = newCtx;
            next.prev = newCtx;

            if (!sameName) {
                name2ctx.remove(ctx.name());
            }
            name2ctx.put(newName, newCtx);

            ChannelHandlerLifeCycleException removeException = null;
            ChannelHandlerLifeCycleException addException = null;
            boolean removed = false;
            try {
                callAfterRemove(ctx);
                removed = true;
            } catch (ChannelHandlerLifeCycleException e) {
                removeException = e;
            }

            boolean added = false;
            try {
                callAfterAdd(newCtx);
                added = true;
            } catch (ChannelHandlerLifeCycleException e) {
                addException = e;
            }

            if (!removed && !added) {
                logger.warn(removeException.getMessage(), removeException);
                logger.warn(addException.getMessage(), addException);
                throw new ChannelHandlerLifeCycleException(
                        "Both " + ctx.handler().getClass().getName() +
                        ".afterRemove() and " + newCtx.handler().getClass().getName() +
                        ".afterAdd() failed; see logs.");
            } else if (!removed) {
                throw removeException;
            } else if (!added) {
                throw addException;
            }
        }

        return ctx.handler();
    }

    private static void callBeforeAdd(ChannelHandlerContext ctx) {
        ChannelHandler handler = ctx.handler();
        if (handler instanceof AbstractChannelHandler) {
            AbstractChannelHandler h = (AbstractChannelHandler) handler;
            if (!h.isSharable() && h.added) {
                throw new ChannelHandlerLifeCycleException(
                        "Only a @Sharable handler can be added or removed multiple times.");
            }
            h.added = true;
        }
        try {
            handler.beforeAdd(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
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
                throw new ChannelHandlerLifeCycleException(
                        ctx.handler().getClass().getName() +
                        ".afterAdd() has thrown an exception; removed.", t);
            } else {
                throw new ChannelHandlerLifeCycleException(
                        ctx.handler().getClass().getName() +
                        ".afterAdd() has thrown an exception; also failed to remove.", t);
            }
        }
    }

    private static void callBeforeRemove(ChannelHandlerContext ctx) {
        try {
            ctx.handler().beforeRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    ctx.handler().getClass().getName() +
                    ".beforeRemove() has thrown an exception; not removing.", t);
        }
    }

    private static void callAfterRemove(ChannelHandlerContext ctx) {
        try {
            ctx.handler().afterRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    ctx.handler().getClass().getName() +
                    ".afterRemove() has thrown an exception.", t);
        }
    }

    @Override
    public synchronized ChannelHandler first() {
        DefaultChannelHandlerContext head = this.head;
        if (head == null) {
            return null;
        }
        return head.handler();
    }

    @Override
    public synchronized ChannelHandler last() {
        DefaultChannelHandlerContext tail = this.tail;
        if (tail == null) {
            return null;
        }
        return tail.handler();
    }

    @Override
    public synchronized ChannelHandler get(String name) {
        DefaultChannelHandlerContext ctx = name2ctx.get(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @Override
    public synchronized <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public synchronized ChannelHandlerContext context(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return name2ctx.get(name);
    }

    @Override
    public synchronized ChannelHandlerContext context(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (ctx.handler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    @Override
    public synchronized ChannelHandlerContext context(
            Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }

        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    @Override
    public List<String> names() {
        List<String> list = new ArrayList<String>();
        if (name2ctx.isEmpty()) {
            return list;
        }

        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            list.add(ctx.name());
            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return list;
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        if (name2ctx.isEmpty()) {
            return map;
        }

        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            map.put(ctx.name(), ctx.handler());
            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return map;
    }

    /**
     * Returns the {@link String} representation of this pipeline.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append('{');
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            buf.append('(');
            buf.append(ctx.name());
            buf.append(" = ");
            buf.append(ctx.handler().getClass().getName());
            buf.append(')');
            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
            buf.append(", ");
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public Queue<Object> inboundMessageBuffer() {
        if (channel.type() != ChannelType.MESSAGE) {
            throw new NoSuchBufferException(
                    "The first inbound buffer of this channel must be a message buffer.");
        }
        return nextInboundMessageBuffer(head);
    }

    @Override
    public ChannelBuffer inboundByteBuffer() {
        if (channel.type() != ChannelType.STREAM) {
            throw new NoSuchBufferException(
                    "The first inbound buffer of this channel must be a byte buffer.");
        }
        return nextInboundByteBuffer(head);
    }

    @Override
    public Queue<Object> outboundMessageBuffer() {
        return nextOutboundMessageBuffer(tail);
    }

    @Override
    public ChannelBuffer outboundByteBuffer() {
        return nextOutboundByteBuffer(tail);
    }

    static boolean hasNextInboundByteBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                return false;
            }
            ChannelBufferHolder<Object> in = ctx.in;
            if (in != null && !in.isBypass() && in.hasByteBuffer()) {
                return true;
            }
            ctx = ctx.next;
        }
    }

    static boolean hasNextInboundMessageBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                return false;
            }
            ChannelBufferHolder<Object> in = ctx.inbound();
            if (in != null && !in.isBypass() && in.hasMessageBuffer()) {
                return true;
            }
            ctx = ctx.next;
        }
    }

    static ChannelBuffer nextInboundByteBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                throw new NoSuchBufferException();
            }
            ChannelBufferHolder<Object> in = ctx.in;
            if (in != null && !in.isBypass() && in.hasByteBuffer()) {
                return in.byteBuffer();
            }
            ctx = ctx.next;
        }
    }

    static Queue<Object> nextInboundMessageBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                throw new NoSuchBufferException();
            }
            ChannelBufferHolder<Object> in = ctx.inbound();
            if (in != null && !in.isBypass() && in.hasMessageBuffer()) {
                return in.messageBuffer();
            }
            ctx = ctx.next;
        }
    }

    boolean hasNextOutboundByteBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                if (directOutbound.hasByteBuffer()) {
                    return true;
                } else {
                    return false;
                }
            }

            ChannelBufferHolder<Object> out = ctx.outbound();
            if (out != null && !out.isBypass() && out.hasByteBuffer()) {
                return true;
            }
            ctx = ctx.prev;
        }
    }

    boolean hasNextOutboundMessageBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                if (directOutbound.hasMessageBuffer()) {
                    return true;
                } else {
                    return false;
                }
            }

            ChannelBufferHolder<Object> out = ctx.outbound();
            if (out != null && !out.isBypass() && out.hasMessageBuffer()) {
                return true;
            }
            ctx = ctx.prev;
        }
    }

    ChannelBuffer nextOutboundByteBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                if (directOutbound.hasByteBuffer()) {
                    return directOutbound.byteBuffer();
                } else {
                    throw new NoSuchBufferException();
                }
            }

            ChannelBufferHolder<Object> out = ctx.outbound();
            if (out != null && !out.isBypass() && out.hasByteBuffer()) {
                return out.byteBuffer();
            }
            ctx = ctx.prev;
        }
    }

    Queue<Object> nextOutboundMessageBuffer(DefaultChannelHandlerContext ctx) {
        for (;;) {
            if (ctx == null) {
                if (directOutbound.hasMessageBuffer()) {
                    return directOutbound.messageBuffer();
                } else {
                    throw new NoSuchBufferException();
                }
            }

            ChannelBufferHolder<Object> out = ctx.outbound();
            if (out != null && !out.isBypass() && out.hasMessageBuffer()) {
                return out.messageBuffer();
            }
            ctx = ctx.prev;
        }
    }

    @Override
    public void fireChannelRegistered() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireChannelRegistered(ctx);
        }
    }

    static void fireChannelRegistered(DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            ctx.fireChannelRegisteredTask.run();
        } else {
            executor.execute(ctx.fireChannelRegisteredTask);
        }
    }

    @Override
    public void fireChannelUnregistered() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireChannelUnregistered(ctx);
        }
    }

    static void fireChannelUnregistered(DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            ctx.fireChannelUnregisteredTask.run();
        } else {
            executor.execute(ctx.fireChannelUnregisteredTask);
        }
    }

    @Override
    public void fireChannelActive() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            firedChannelActive = true;
            fireChannelActive(ctx);
            if (fireInboundBufferUpdatedOnActivation) {
                fireInboundBufferUpdatedOnActivation = false;
                fireInboundBufferUpdated(ctx);
            }
        }
    }

    static void fireChannelActive(DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            ctx.fireChannelActiveTask.run();
        } else {
            executor.execute(ctx.fireChannelActiveTask);
        }
    }

    @Override
    public void fireChannelInactive() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            // Some implementations such as EmbeddedChannel can trigger inboundBufferUpdated()
            // after deactivation, so it's safe not to revert the firedChannelActive flag here.
            // Also, all known transports never get re-activated.
            //firedChannelActive = false;
            fireChannelInactive(ctx);
        }
    }

    static void fireChannelInactive(DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            ctx.fireChannelInactiveTask.run();
        } else {
            executor.execute(ctx.fireChannelInactiveTask);
        }
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireExceptionCaught(ctx, cause);
        } else {
            logTerminalException(cause);
        }
    }

    static void logTerminalException(Throwable cause) {
        logger.warn(
                "An exceptionCaught() event was fired, and it reached at the end of the " +
                "pipeline.  It usually means the last inbound handler in the pipeline did not " +
                "handle the exception.", cause);
    }

    void fireExceptionCaught(final DefaultChannelHandlerContext ctx, final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).exceptionCaught(ctx, cause);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "An exception was thrown by a user handler's " +
                            "exceptionCaught() method while handling the following exception:", cause);
                }
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    fireExceptionCaught(ctx, cause);
                }
            });
        }
    }

    @Override
    public void fireUserEventTriggered(Object event) {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireUserEventTriggered(ctx, event);
        }
    }

    void fireUserEventTriggered(final DefaultChannelHandlerContext ctx, final Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            try {
                ((ChannelInboundHandler<Object>) ctx.handler()).userEventTriggered(ctx, event);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    fireUserEventTriggered(ctx, event);
                }
            });
        }
    }

    @Override
    public void fireInboundBufferUpdated() {
        if (!firedChannelActive) {
            fireInboundBufferUpdatedOnActivation = true;
            return;
        }
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireInboundBufferUpdated(ctx);
        }
    }

    static void fireInboundBufferUpdated(DefaultChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            ctx.fireInboundBufferUpdatedTask.run();
        } else {
            executor.execute(ctx.fireInboundBufferUpdatedTask);
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, channel.newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, channel.newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, channel.newFuture());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(channel.newFuture());
    }

    @Override
    public ChannelFuture close() {
        return close(channel.newFuture());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(channel.newFuture());
    }

    @Override
    public ChannelFuture flush() {
        return flush(channel.newFuture());
    }

    @Override
    public ChannelFuture write(Object message) {
        return write(message, channel.newFuture());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return bind(firstOutboundContext(), localAddress, future);
    }

    ChannelFuture bind(final DefaultChannelHandlerContext ctx, final SocketAddress localAddress, final ChannelFuture future) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validateFuture(future);

        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                try {
                    ((ChannelOutboundHandler<Object>) ctx.handler()).bind(ctx, localAddress, future);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        bind(ctx, localAddress, future);
                    }
                });
            }
        } else {
            unsafe.bind(localAddress, future);
        }
        return future;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
        return connect(remoteAddress, null, future);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        return connect(firstOutboundContext(), remoteAddress, localAddress, future);
    }

    ChannelFuture connect(final DefaultChannelHandlerContext ctx, final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validateFuture(future);

        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                try {
                    ((ChannelOutboundHandler<Object>) ctx.handler()).connect(ctx, remoteAddress, localAddress, future);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(ctx, remoteAddress, localAddress, future);
                    }
                });
            }
        } else {
            unsafe.connect(remoteAddress,  localAddress, future);
        }

        return future;
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return disconnect(firstOutboundContext(), future);
    }

    ChannelFuture disconnect(final DefaultChannelHandlerContext ctx, final ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                try {
                    ((ChannelOutboundHandler<Object>) ctx.handler()).disconnect(ctx, future);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        disconnect(ctx, future);
                    }
                });
            }
        } else {
            unsafe.disconnect(future);
        }

        return future;
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return close(firstOutboundContext(), future);
    }

    ChannelFuture close(final DefaultChannelHandlerContext ctx, final ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                try {
                    ((ChannelOutboundHandler<Object>) ctx.handler()).close(ctx, future);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        close(ctx, future);
                    }
                });
            }
        } else {
            unsafe.close(future);
        }

        return future;
    }

    @Override
    public ChannelFuture deregister(final ChannelFuture future) {
        return deregister(firstOutboundContext(), future);
    }

    ChannelFuture deregister(final DefaultChannelHandlerContext ctx, final ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                try {
                    ((ChannelOutboundHandler<Object>) ctx.handler()).deregister(ctx, future);
                } catch (Throwable t) {
                    notifyHandlerException(t);
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        deregister(ctx, future);
                    }
                });
            }
        } else {
            unsafe.deregister(future);
        }

        return future;
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return flush(firstOutboundContext(), future);
    }

    ChannelFuture flush(final DefaultChannelHandlerContext ctx, final ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                flush0(ctx, future);
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(ctx, future);
                    }
                });
            }
        } else {
            unsafe.flush(future);
        }

        return future;
    }

    private void flush0(final DefaultChannelHandlerContext ctx, ChannelFuture future) {
        try {
            ((ChannelOutboundHandler<Object>) ctx.handler()).flush(ctx, future);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            ChannelBufferHolder<Object> outbound = ctx.outbound();
            if (!outbound.isBypass() && outbound.isEmpty() && outbound.hasByteBuffer()) {
                outbound.byteBuffer().discardReadBytes();
            }
        }
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        return write(tail, message, future);
    }

    ChannelFuture write(DefaultChannelHandlerContext ctx, final Object message, final ChannelFuture future) {
        if (message == null) {
            throw new NullPointerException("message");
        }
        validateFuture(future);

        EventExecutor executor;
        ChannelBufferHolder<Object> out;
        boolean msgBuf = false;
        for (;;) {
            if (ctx == null) {
                executor = channel.eventLoop();
                out = directOutbound;
                if (out.hasByteBuffer()) {
                    if(!(message instanceof ChannelBuffer)) {
                        throw new NoSuchBufferException();
                    }
                } else {
                    msgBuf = true;
                }
                break;
            }

            if (ctx.canHandleOutbound()) {
                out = ctx.outbound();
                if (out.hasMessageBuffer()) {
                    msgBuf = true;
                    executor = ctx.executor();
                    break;
                } else if (message instanceof ChannelBuffer) {
                    executor = ctx.executor();
                    break;
                }
            }

            ctx = ctx.prev;
        }

        if (executor.inEventLoop()) {
            if (msgBuf) {
                out.messageBuffer().add(message);
            } else {
                ChannelBuffer buf = (ChannelBuffer) message;
                out.byteBuffer().writeBytes(buf, buf.readerIndex(), buf.readableBytes());
            }
            if (ctx != null) {
                flush0(ctx, future);
            } else {
                unsafe.flush(future);
            }
            return future;
        } else {
            final DefaultChannelHandlerContext ctx0 = ctx;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    write(ctx0, message, future);
                }
            });
        }

        return future;
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

    private DefaultChannelHandlerContext firstInboundContext() {
        return nextInboundContext(head);
    }

    private DefaultChannelHandlerContext firstOutboundContext() {
        return nextOutboundContext(tail);
    }

    static DefaultChannelHandlerContext nextInboundContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleInbound()) {
            realCtx = realCtx.next;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    static DefaultChannelHandlerContext nextOutboundContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleOutbound()) {
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
        if (cause == null) {
            return false;
        }

        StackTraceElement[] trace = cause.getStackTrace();
        if (trace != null) {
            for (StackTraceElement t: trace) {
                if ("exceptionCaught".equals(t.getMethodName())) {
                    return true;
                }
            }
        }

        return inExceptionCaught(cause.getCause());
    }

    private void init(EventExecutor executor, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx =
                new DefaultChannelHandlerContext(this, executor, null, null, name, handler);
        callBeforeAdd(ctx);
        head = tail = ctx;
        name2ctx.clear();
        name2ctx.put(name, ctx);
        callAfterAdd(ctx);
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
}
