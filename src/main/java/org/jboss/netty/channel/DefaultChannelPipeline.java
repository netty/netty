/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The default {@link ChannelPipeline} implementation.  It is recommended
 * to use {@link Channels#pipeline()} to create a new {@link ChannelPipeline}
 * instance rather than calling the constructor directly.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2119 $, $Date: 2010-02-01 20:46:09 +0900 (Mon, 01 Feb 2010) $
 *
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);
    static final ChannelSink discardingSink = new DiscardingChannelSink();

    private volatile Channel channel;
    private volatile ChannelSink sink;
    private volatile DefaultChannelHandlerContext head;
    private volatile DefaultChannelHandlerContext tail;
    private final Map<String, DefaultChannelHandlerContext> name2ctx =
        new HashMap<String, DefaultChannelHandlerContext>(4);

    /**
     * Creates a new empty pipeline.
     */
    public DefaultChannelPipeline() {
        super();
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelSink getSink() {
        ChannelSink sink = this.sink;
        if (sink == null) {
            return discardingSink;
        }
        return sink;
    }

    public void attach(Channel channel, ChannelSink sink) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (sink == null) {
            throw new NullPointerException("sink");
        }
        if (this.channel != null || this.sink != null) {
            throw new IllegalStateException("attached already");
        }
        this.channel = channel;
        this.sink = sink;
    }

    public boolean isAttached() {
        return sink != null;
    }

    public synchronized void addFirst(String name, ChannelHandler handler) {
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
    }

    public synchronized void addLast(String name, ChannelHandler handler) {
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
    }

    public synchronized void addBefore(String baseName, String name, ChannelHandler handler) {
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
    }

    public synchronized void addAfter(String baseName, String name, ChannelHandler handler) {
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
    }

    public synchronized void remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
    }

    public synchronized ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).getHandler();
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).getHandler();
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
            name2ctx.remove(ctx.getName());

            callAfterRemove(ctx);
        }
        return ctx;
    }

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
            name2ctx.remove(oldHead.getName());
        }

        callAfterRemove(oldHead);

        return oldHead.getHandler();
    }

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
            name2ctx.remove(oldTail.getName());
        }

        callBeforeRemove(oldTail);

        return oldTail.getHandler();
    }

    public synchronized void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
    }

    public synchronized ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

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
            boolean sameName = ctx.getName().equals(newName);
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
                name2ctx.remove(ctx.getName());
                name2ctx.put(newName, newCtx);
            }

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
                        "Both " + ctx.getHandler().getClass().getName() +
                        ".afterRemove() and " + newCtx.getHandler().getClass().getName() +
                        ".afterAdd() failed; see logs.");
            } else if (!removed) {
                throw removeException;
            } else if (!added) {
                throw addException;
            }
        }

        return ctx.getHandler();
    }

    private void callBeforeAdd(ChannelHandlerContext ctx) {
        if (!(ctx.getHandler() instanceof LifeCycleAwareChannelHandler)) {
            return;
        }

        LifeCycleAwareChannelHandler h =
            (LifeCycleAwareChannelHandler) ctx.getHandler();

        try {
            h.beforeAdd(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    h.getClass().getName() +
                    ".beforeAdd() has thrown an exception; not adding.", t);
        }
    }

    private void callAfterAdd(ChannelHandlerContext ctx) {
        if (!(ctx.getHandler() instanceof LifeCycleAwareChannelHandler)) {
            return;
        }

        LifeCycleAwareChannelHandler h =
            (LifeCycleAwareChannelHandler) ctx.getHandler();

        try {
            h.afterAdd(ctx);
        } catch (Throwable t) {
            boolean removed = false;
            try {
                remove((DefaultChannelHandlerContext) ctx);
                removed = true;
            } catch (Throwable t2) {
                logger.warn("Failed to remove a handler: " + ctx.getName(), t2);
            }

            if (removed) {
                throw new ChannelHandlerLifeCycleException(
                        h.getClass().getName() +
                        ".afterAdd() has thrown an exception; removed.", t);
            } else {
                throw new ChannelHandlerLifeCycleException(
                        h.getClass().getName() +
                        ".afterAdd() has thrown an exception; also failed to remove.", t);
            }
        }
    }

    private void callBeforeRemove(ChannelHandlerContext ctx) {
        if (!(ctx.getHandler() instanceof LifeCycleAwareChannelHandler)) {
            return;
        }

        LifeCycleAwareChannelHandler h =
            (LifeCycleAwareChannelHandler) ctx.getHandler();

        try {
            h.beforeRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    h.getClass().getName() +
                    ".beforeRemove() has thrown an exception; not removing.", t);
        }
    }

    private void callAfterRemove(ChannelHandlerContext ctx) {
        if (!(ctx.getHandler() instanceof LifeCycleAwareChannelHandler)) {
            return;
        }

        LifeCycleAwareChannelHandler h =
            (LifeCycleAwareChannelHandler) ctx.getHandler();

        try {
            h.afterRemove(ctx);
        } catch (Throwable t) {
            throw new ChannelHandlerLifeCycleException(
                    h.getClass().getName() +
                    ".afterRemove() has thrown an exception.", t);
        }
    }

    public synchronized ChannelHandler getFirst() {
        DefaultChannelHandlerContext head = this.head;
        if (head == null) {
            return null;
        }
        return head.getHandler();
    }

    public synchronized ChannelHandler getLast() {
        DefaultChannelHandlerContext tail = this.tail;
        if (tail == null) {
            return null;
        }
        return tail.getHandler();
    }

    public synchronized ChannelHandler get(String name) {
        DefaultChannelHandlerContext ctx = name2ctx.get(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.getHandler();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = getContext(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.getHandler();
        }
    }

    public synchronized ChannelHandlerContext getContext(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return name2ctx.get(name);
    }

    public synchronized ChannelHandlerContext getContext(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (ctx.getHandler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    public synchronized ChannelHandlerContext getContext(
            Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }

        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            if (handlerType.isAssignableFrom(ctx.getHandler().getClass())) {
                return ctx;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }
        return null;
    }

    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        if (name2ctx.isEmpty()) {
            return map;
        }

        DefaultChannelHandlerContext ctx = head;
        for (;;) {
            map.put(ctx.getName(), ctx.getHandler());
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
            buf.append(ctx.getName());
            buf.append(" = ");
            buf.append(ctx.getHandler().getClass().getName());
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

    public void sendUpstream(ChannelEvent e) {
        DefaultChannelHandlerContext head = getActualUpstreamContext(this.head);
        if (head == null) {
            logger.warn(
                    "The pipeline contains no upstream handlers; discarding: " + e);
            return;
        }

        sendUpstream(head, e);
    }

    void sendUpstream(DefaultChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelUpstreamHandler) ctx.getHandler()).handleUpstream(ctx, e);
        } catch (Throwable t) {
            notifyHandlerException(e, t);
        }
    }

    public void sendDownstream(ChannelEvent e) {
        DefaultChannelHandlerContext tail = getActualDownstreamContext(this.tail);
        if (tail == null) {
            try {
                getSink().eventSunk(this, e);
                return;
            } catch (Throwable t) {
                notifyHandlerException(e, t);
                return;
            }
        }

        sendDownstream(tail, e);
    }

    void sendDownstream(DefaultChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelDownstreamHandler) ctx.getHandler()).handleDownstream(ctx, e);
        } catch (Throwable t) {
            notifyHandlerException(e, t);
        }
    }

    DefaultChannelHandlerContext getActualUpstreamContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleUpstream()) {
            realCtx = realCtx.next;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    DefaultChannelHandlerContext getActualDownstreamContext(DefaultChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultChannelHandlerContext realCtx = ctx;
        while (!realCtx.canHandleDownstream()) {
            realCtx = realCtx.prev;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    protected void notifyHandlerException(ChannelEvent e, Throwable t) {
        if (e instanceof ExceptionEvent) {
            logger.warn(
                    "An exception was thrown by a user handler " +
                    "while handling an exception event (" + e + ")", t);
            return;
        }

        ChannelPipelineException pe;
        if (t instanceof ChannelPipelineException) {
            pe = (ChannelPipelineException) t;
        } else {
            pe = new ChannelPipelineException(t);
        }

        try {
            sink.exceptionCaught(this, e, pe);
        } catch (Exception e1) {
            logger.warn("An exception was thrown by an exception handler.", e1);
        }
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
            throw new IllegalArgumentException("Duplicate handler name.");
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(String name) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private DefaultChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        DefaultChannelHandlerContext ctx = (DefaultChannelHandlerContext) getContext(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    private final class DefaultChannelHandlerContext implements ChannelHandlerContext {
        volatile DefaultChannelHandlerContext next;
        volatile DefaultChannelHandlerContext prev;
        private final String name;
        private final ChannelHandler handler;
        private final boolean canHandleUpstream;
        private final boolean canHandleDownstream;
        private volatile Object attachment;

        DefaultChannelHandlerContext(
                DefaultChannelHandlerContext prev, DefaultChannelHandlerContext next,
                String name, ChannelHandler handler) {

            if (name == null) {
                throw new NullPointerException("name");
            }
            if (handler == null) {
                throw new NullPointerException("handler");
            }
            canHandleUpstream = handler instanceof ChannelUpstreamHandler;
            canHandleDownstream = handler instanceof ChannelDownstreamHandler;


            if (!canHandleUpstream && !canHandleDownstream) {
                throw new IllegalArgumentException(
                        "handler must be either " +
                        ChannelUpstreamHandler.class.getName() + " or " +
                        ChannelDownstreamHandler.class.getName() + '.');
            }

            this.prev = prev;
            this.next = next;
            this.name = name;
            this.handler = handler;
        }

        public Channel getChannel() {
            return getPipeline().getChannel();
        }

        public ChannelPipeline getPipeline() {
            return DefaultChannelPipeline.this;
        }

        public boolean canHandleDownstream() {
            return canHandleDownstream;
        }

        public boolean canHandleUpstream() {
            return canHandleUpstream;
        }

        public ChannelHandler getHandler() {
            return handler;
        }

        public String getName() {
            return name;
        }

        public Object getAttachment() {
            return attachment;
        }

        public void setAttachment(Object attachment) {
            this.attachment = attachment;
        }

        public void sendDownstream(ChannelEvent e) {
            DefaultChannelHandlerContext prev = getActualDownstreamContext(this.prev);
            if (prev == null) {
                try {
                    getSink().eventSunk(DefaultChannelPipeline.this, e);
                } catch (Throwable t) {
                    notifyHandlerException(e, t);
                }
            } else {
                DefaultChannelPipeline.this.sendDownstream(prev, e);
            }
        }

        public void sendUpstream(ChannelEvent e) {
            DefaultChannelHandlerContext next = getActualUpstreamContext(this.next);
            if (next != null) {
                DefaultChannelPipeline.this.sendUpstream(next, e);
            }
        }
    }

    private static final class DiscardingChannelSink implements ChannelSink {
        DiscardingChannelSink() {
            super();
        }

        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            logger.warn("Not attached yet; discarding: " + e);
        }

        public void exceptionCaught(ChannelPipeline pipeline,
                ChannelEvent e, ChannelPipelineException cause) throws Exception {
            throw cause;
        }
    }
}
