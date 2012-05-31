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
import io.netty.util.DefaultAttributeMap;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final Channel channel;
    private volatile DefaultChannelHandlerContext head;
    private volatile DefaultChannelHandlerContext tail;
    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);
    private boolean firedChannelActive;
    private boolean fireInboundBufferUpdatedOnActivation;

    public DefaultChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public synchronized ChannelPipeline addFirst(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldHead = head;
            DefaultChannelHandlerContext newHead = new DefaultChannelHandlerContext(null, oldHead, name, handler);

            callBeforeAdd(newHead);

            oldHead.prev = newHead;
            head = newHead;
            name2ctx.put(name, newHead);

            callAfterAdd(newHead);
        }

        return this;
    }

    @Override
    public synchronized ChannelPipeline addLast(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldTail = tail;
            DefaultChannelHandlerContext newTail = new DefaultChannelHandlerContext(oldTail, null, name, handler);

            callBeforeAdd(newTail);

            oldTail.next = newTail;
            tail = newTail;
            name2ctx.put(name, newTail);

            callAfterAdd(newTail);
        }

        return this;
    }

    @Override
    public synchronized ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == head) {
            addFirst(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx.prev, ctx, name, handler);

            callBeforeAdd(newCtx);

            ctx.prev.next = newCtx;
            ctx.prev = newCtx;
            name2ctx.put(name, newCtx);

            callAfterAdd(newCtx);
        }

        return this;
    }

    @Override
    public synchronized ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == tail) {
            addLast(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx, ctx.next, name, handler);

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
            addFirst(generateName(h), h);
        }

        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(generateName(h), h);
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
    @SuppressWarnings("unchecked")
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
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(prev, next, newName, newHandler);

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
        try {
            ctx.handler().beforeAdd(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    ctx.handler().getClass().getName() +
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
    public ChannelBufferHolder<Object> inbound() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            return ctx.inbound();
        }
        return null;
    }

    @Override
    public ChannelBufferHolder<Object> outbound() {
        DefaultChannelHandlerContext ctx = firstOutboundContext();
        if (ctx != null) {
            return ctx.outbound();
        }
        return channel().unsafe().directOutbound();
    }

    @Override
    public void fireChannelRegistered() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireChannelRegistered(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private void fireChannelRegistered(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).channelRegistered(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public void fireChannelUnregistered() {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireChannelUnregistered(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private void fireChannelUnregistered(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).channelUnregistered(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
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

    @SuppressWarnings("unchecked")
    private void fireChannelActive(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).channelActive(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
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

    @SuppressWarnings("unchecked")
    private void fireChannelInactive(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).channelInactive(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
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

    private static void logTerminalException(Throwable cause) {
        logger.warn(
                "An exceptionCaught() event was fired, and it reached at the end of the " +
                "pipeline.  It usually means the last inbound handler in the pipeline did not " +
                "handle the exception.", cause);
    }

    @SuppressWarnings("unchecked")
    private void fireExceptionCaught(DefaultChannelHandlerContext ctx, Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

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
    }

    @Override
    public void fireUserEventTriggered(Object event) {
        DefaultChannelHandlerContext ctx = firstInboundContext();
        if (ctx != null) {
            fireUserEventTriggered(ctx, event);
        }
    }

    @SuppressWarnings("unchecked")
    private void fireUserEventTriggered(DefaultChannelHandlerContext ctx, Object event) {
        if (event == null) {
            throw new NullPointerException("event");
        }

        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).userEventTriggered(ctx, event);
        } catch (Throwable t) {
            notifyHandlerException(t);
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

    @SuppressWarnings("unchecked")
    private void fireInboundBufferUpdated(DefaultChannelHandlerContext ctx) {
        try {
            ((ChannelInboundHandler<Object>) ctx.handler()).inboundBufferUpdated(ctx);
        } catch (Throwable t) {
            notifyHandlerException(t);
        } finally {
            ChannelBufferHolder<Object> inbound = ctx.inbound();
            if (!inbound.isBypass() && inbound.isEmpty() && inbound.hasByteBuffer()) {
                inbound.byteBuffer().discardReadBytes();
            }
        }
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, channel().newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, channel().newFuture());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, channel().newFuture());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(channel().newFuture());
    }

    @Override
    public ChannelFuture close() {
        return close(channel().newFuture());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(channel().newFuture());
    }

    @Override
    public ChannelFuture flush() {
        return flush(channel().newFuture());
    }

    @Override
    public ChannelFuture write(Object message) {
        return write(message, channel().newFuture());
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
        return bind(firstOutboundContext(), localAddress, future);
    }

    @SuppressWarnings("unchecked")
    private ChannelFuture bind(DefaultChannelHandlerContext ctx, SocketAddress localAddress, ChannelFuture future) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        validateFuture(future);

        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).bind(ctx, localAddress, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().bind(localAddress, future);
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

    @SuppressWarnings("unchecked")
    private ChannelFuture connect(DefaultChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validateFuture(future);

        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).connect(ctx, remoteAddress, localAddress, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().connect(remoteAddress,  localAddress, future);
        }

        return future;
    }

    @Override
    public ChannelFuture disconnect(ChannelFuture future) {
        return disconnect(firstOutboundContext(), future);
    }

    @SuppressWarnings("unchecked")
    private ChannelFuture disconnect(DefaultChannelHandlerContext ctx, ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).disconnect(ctx, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().disconnect(future);
        }

        return future;
    }

    @Override
    public ChannelFuture close(ChannelFuture future) {
        return close(firstOutboundContext(), future);
    }

    @SuppressWarnings("unchecked")
    private ChannelFuture close(DefaultChannelHandlerContext ctx, ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).close(ctx, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().close(future);
        }

        return future;
    }

    @Override
    public ChannelFuture deregister(final ChannelFuture future) {
        return deregister(firstOutboundContext(), future);
    }

    @SuppressWarnings("unchecked")
    private ChannelFuture deregister(DefaultChannelHandlerContext ctx, ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).deregister(ctx, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().deregister(future);
        }

        return future;
    }

    @Override
    public ChannelFuture flush(ChannelFuture future) {
        return flush(firstOutboundContext(), future);
    }

    @SuppressWarnings("unchecked")
    private ChannelFuture flush(DefaultChannelHandlerContext ctx, ChannelFuture future) {
        validateFuture(future);
        if (ctx != null) {
            try {
                ((ChannelOutboundHandler<Object>) ctx.handler()).flush(ctx, future);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            channel().unsafe().flush(future);
        }

        return future;
    }

    @Override
    public ChannelFuture write(Object message, ChannelFuture future) {
        if (message == null) {
            throw new NullPointerException("message");
        }
        validateFuture(future);

        ChannelBufferHolder<Object> out = outbound();
        if (out.hasMessageBuffer()) {
            out.messageBuffer().add(message);
        } else if (message instanceof ChannelBuffer) {
            ChannelBuffer m = (ChannelBuffer) message;
            out.byteBuffer().writeBytes(m, m.readerIndex(), m.readableBytes());
        } else {
            throw new IllegalArgumentException(
                    "cannot write a message whose type is not " +
                    ChannelBuffer.class.getSimpleName() + ": " + message.getClass().getName());
        }

        return flush(future);
    }

    private void validateFuture(ChannelFuture future) {
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (future.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "future.channel does not match: %s (expected: %s)", future.channel(), channel()));
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

    private static DefaultChannelHandlerContext nextInboundContext(DefaultChannelHandlerContext ctx) {
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

    private static DefaultChannelHandlerContext nextOutboundContext(DefaultChannelHandlerContext ctx) {
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

    private void init(String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = new DefaultChannelHandlerContext(null, null, name, handler);
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

    private final class DefaultChannelHandlerContext extends DefaultAttributeMap implements ChannelInboundHandlerContext<Object>, ChannelOutboundHandlerContext<Object> {
        volatile DefaultChannelHandlerContext next;
        volatile DefaultChannelHandlerContext prev;
        private final String name;
        private final ChannelHandler handler;
        private final boolean canHandleInbound;
        private final boolean canHandleOutbound;
        private final ChannelBufferHolder<Object> in;
        private final ChannelBufferHolder<Object> out;

        @SuppressWarnings("unchecked")
        DefaultChannelHandlerContext(
                DefaultChannelHandlerContext prev, DefaultChannelHandlerContext next,
                String name, ChannelHandler handler) {

            if (name == null) {
                throw new NullPointerException("name");
            }
            if (handler == null) {
                throw new NullPointerException("handler");
            }
            canHandleInbound = handler instanceof ChannelInboundHandler;
            canHandleOutbound = handler instanceof ChannelOutboundHandler;

            if (!canHandleInbound && !canHandleOutbound) {
                throw new IllegalArgumentException(
                        "handler must be either " +
                        ChannelInboundHandler.class.getName() + " or " +
                        ChannelOutboundHandler.class.getName() + '.');
            }

            this.prev = prev;
            this.next = next;
            this.name = name;
            this.handler = handler;

            if (canHandleInbound) {
                try {
                    in = ((ChannelInboundHandler<Object>) handler).newInboundBuffer(this);
                } catch (Exception e) {
                    throw new ChannelPipelineException("A user handler failed to create a new inbound buffer.", e);
                }
            } else {
                in = null;
            }
            if (canHandleOutbound) {
                try {
                    out = ((ChannelOutboundHandler<Object>) handler).newOutboundBuffer(this);
                } catch (Exception e) {
                    throw new ChannelPipelineException("A user handler failed to create a new outbound buffer.", e);
                } finally {
                    if (in != null) {
                        // TODO Release the inbound buffer once pooling is implemented.
                    }
                }
            } else {
                out = null;
            }
        }

        @Override
        public Channel channel() {
            return DefaultChannelPipeline.this.channel();
        }

        @Override
        public ChannelPipeline pipeline() {
            return DefaultChannelPipeline.this;
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
        public boolean canHandleInbound() {
            return canHandleInbound;
        }

        @Override
        public boolean canHandleOutbound() {
            return canHandleOutbound;
        }

        @Override
        public ChannelBufferHolder<Object> inbound() {
            return in;
        }

        @Override
        public ChannelBufferHolder<Object> outbound() {
            return out;
        }

        @Override
        public ChannelBuffer nextInboundByteBuffer() {
            DefaultChannelHandlerContext ctx = this;
            for (;;) {
                ctx = nextInboundContext(ctx.next);
                if (ctx == null) {
                    throw NoSuchBufferException.INSTANCE;
                }
                ChannelBufferHolder<Object> nextIn = ctx.inbound();
                if (nextIn.hasByteBuffer()) {
                    return nextIn.byteBuffer();
                }
            }
        }

        @Override
        public Queue<Object> nextInboundMessageBuffer() {
            DefaultChannelHandlerContext ctx = this;
            for (;;) {
                ctx = nextInboundContext(ctx.next);
                if (ctx == null) {
                    throw NoSuchBufferException.INSTANCE;
                }
                ChannelBufferHolder<Object> nextIn = ctx.inbound();
                if (nextIn.hasMessageBuffer()) {
                    return nextIn.messageBuffer();
                }
            }
        }

        @Override
        public ChannelBuffer nextOutboundByteBuffer() {
            DefaultChannelHandlerContext ctx = this;
            for (;;) {
                ctx = nextOutboundContext(ctx.prev);
                if (ctx == null) {
                    ChannelBufferHolder<Object> lastOut = channel().unsafe().directOutbound();
                    if (lastOut.hasByteBuffer()) {
                        return lastOut.byteBuffer();
                    } else {
                        throw NoSuchBufferException.INSTANCE;
                    }
                }
                ChannelBufferHolder<Object> nextOut = ctx.outbound();
                if (nextOut.hasByteBuffer()) {
                    return nextOut.byteBuffer();
                }
            }
        }

        @Override
        public Queue<Object> nextOutboundMessageBuffer() {
            DefaultChannelHandlerContext ctx = this;
            for (;;) {
                ctx = nextOutboundContext(ctx.prev);
                if (ctx == null) {
                    ChannelBufferHolder<Object> lastOut = channel().unsafe().directOutbound();
                    if (lastOut.hasMessageBuffer()) {
                        return lastOut.messageBuffer();
                    } else {
                        throw NoSuchBufferException.INSTANCE;
                    }
                }
                ChannelBufferHolder<Object> nextOut = ctx.outbound();
                if (nextOut.hasMessageBuffer()) {
                    return nextOut.messageBuffer();
                }
            }
        }

        @Override
        public void fireChannelRegistered() {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireChannelRegistered(next);
            }
        }

        @Override
        public void fireChannelUnregistered() {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireChannelUnregistered(next);
            }
        }

        @Override
        public void fireChannelActive() {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireChannelActive(next);
            }
        }

        @Override
        public void fireChannelInactive() {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireChannelInactive(next);
            }
        }

        @Override
        public void fireExceptionCaught(Throwable cause) {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireExceptionCaught(next, cause);
            } else {
                logTerminalException(cause);
            }
        }

        @Override
        public void fireUserEventTriggered(Object event) {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireUserEventTriggered(next, event);
            }
        }

        @Override
        public void fireInboundBufferUpdated() {
            DefaultChannelHandlerContext next = nextInboundContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.fireInboundBufferUpdated(next);
            }
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return bind(localAddress, newFuture());
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return connect(remoteAddress, newFuture());
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return connect(remoteAddress, localAddress, newFuture());
        }

        @Override
        public ChannelFuture disconnect() {
            return disconnect(newFuture());
        }

        @Override
        public ChannelFuture close() {
            return close(newFuture());
        }

        @Override
        public ChannelFuture deregister() {
            return deregister(newFuture());
        }

        @Override
        public ChannelFuture flush() {
            return flush(newFuture());
        }

        @Override
        public ChannelFuture write(Object message) {
            return write(message, newFuture());
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelFuture future) {
            return DefaultChannelPipeline.this.bind(nextOutboundContext(prev), localAddress, future);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelFuture future) {
            return connect(remoteAddress, null, future);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
            return DefaultChannelPipeline.this.connect(nextOutboundContext(prev), remoteAddress, localAddress, future);
        }

        @Override
        public ChannelFuture disconnect(ChannelFuture future) {
            return DefaultChannelPipeline.this.disconnect(nextOutboundContext(prev), future);
        }

        @Override
        public ChannelFuture close(ChannelFuture future) {
            return DefaultChannelPipeline.this.close(nextOutboundContext(prev), future);
        }

        @Override
        public ChannelFuture deregister(ChannelFuture future) {
            return DefaultChannelPipeline.this.deregister(nextOutboundContext(prev), future);
        }

        @Override
        public ChannelFuture flush(ChannelFuture future) {
            return DefaultChannelPipeline.this.flush(nextOutboundContext(prev), future);
        }

        @Override
        public ChannelFuture write(Object message, ChannelFuture future) {
            if (message instanceof ChannelBuffer) {
                ChannelBuffer m = (ChannelBuffer) message;
                nextOutboundByteBuffer().writeBytes(m, m.readerIndex(), m.readableBytes());
            } else {
                nextOutboundMessageBuffer().add(message);
            }
            return flush(future);
        }

        @Override
        public ChannelFuture newFuture() {
            return channel().newFuture();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return channel().newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return channel().newFailedFuture(cause);
        }
    }
}
