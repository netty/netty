/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

import static org.jboss.netty.channel.ChannelPipelineCoverage.*;

import java.lang.annotation.AnnotationFormatError;
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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class DefaultChannelPipeline implements ChannelPipeline {

    static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultChannelPipeline.class);

    private final ChannelSink discardingSink = new ChannelSink() {
        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) {
            logger.warn("Not attached yet; discarding: " + e);
        }

        public void exceptionCaught(ChannelPipeline pipeline,
                ChannelEvent e, ChannelPipelineException cause) throws Exception {
            throw cause;
        }
    };

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

    public synchronized void addFirst(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldHead = head;
            DefaultChannelHandlerContext newHead = new DefaultChannelHandlerContext(null, oldHead, name, handler);
            oldHead.prev = newHead;
            head = newHead;
            name2ctx.put(name, newHead);
        }
    }

    public synchronized void addLast(String name, ChannelHandler handler) {
        if (name2ctx.isEmpty()) {
            init(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext oldTail = tail;
            DefaultChannelHandlerContext newTail = new DefaultChannelHandlerContext(oldTail, null, name, handler);
            oldTail.next = newTail;
            tail = newTail;
            name2ctx.put(name, newTail);
        }
    }

    public synchronized void addBefore(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == head) {
            addFirst(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx.prev, ctx, name, handler);
            ctx.prev.next = newCtx;
            ctx.prev = newCtx;
            name2ctx.put(name, newCtx);
        }
    }

    public synchronized void addAfter(String baseName, String name, ChannelHandler handler) {
        DefaultChannelHandlerContext ctx = getContextOrDie(baseName);
        if (ctx == tail) {
            addLast(name, handler);
        } else {
            checkDuplicateName(name);
            DefaultChannelHandlerContext newCtx = new DefaultChannelHandlerContext(ctx, ctx.next, name, handler);
            ctx.next.prev = newCtx;
            ctx.next = newCtx;
            name2ctx.put(name, newCtx);
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
            DefaultChannelHandlerContext prev = ctx.prev;
            DefaultChannelHandlerContext next = ctx.next;
            prev.next = next;
            next.prev = prev;
            name2ctx.remove(ctx.getName());
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
        if (oldHead.next == null) {
            head = tail = null;
            name2ctx.clear();
        } else {
            oldHead.next.prev = null;
            head = oldHead.next;
            name2ctx.remove(oldHead.getName());
        }
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
        if (oldTail.prev == null) {
            head = tail = null;
            name2ctx.clear();
        } else {
            oldTail.prev.next = null;
            tail = oldTail.prev;
            name2ctx.remove(oldTail.getName());
        }
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
            prev.next = newCtx;
            next.prev = newCtx;
            if (!sameName) {
                name2ctx.remove(ctx.getName());
                name2ctx.put(newName, newCtx);
            }
        }
        return ctx.getHandler();
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

    void notifyHandlerException(ChannelEvent e, Throwable t) {
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
        head = tail = ctx;
        name2ctx.clear();
        name2ctx.put(name, ctx);
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

    private class DefaultChannelHandlerContext implements ChannelHandlerContext {
        volatile DefaultChannelHandlerContext next;
        volatile DefaultChannelHandlerContext prev;
        private final String name;
        private final ChannelHandler handler;
        private final boolean canHandleUpstream;
        private final boolean canHandleDownstream;

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


            ChannelPipelineCoverage coverage = handler.getClass().getAnnotation(ChannelPipelineCoverage.class);
            if (coverage == null) {
                logger.warn(
                        "Handler '" + handler.getClass().getName() +
                        "' doesn't have a '" +
                        ChannelPipelineCoverage.class.getSimpleName() +
                        "' annotation with its class declaration. " +
                        "It is recommended to add the annotation to tell if " +
                        "one handler instance can handle more than one pipeline " +
                        "(\"" + ALL + "\") or not (\"" + ONE + "\")");
            } else {
                String coverageValue = coverage.value();
                if (coverageValue == null) {
                    throw new AnnotationFormatError(
                            ChannelPipelineCoverage.class.getSimpleName() +
                            " annotation value is undefined for type: " +
                            handler.getClass().getName());
                }


                if (!coverageValue.equals(ALL) && !coverageValue.equals(ONE)) {
                    throw new AnnotationFormatError(
                            ChannelPipelineCoverage.class.getSimpleName() +
                            " annotation value: " + coverageValue +
                            " (must be either \"" + ALL + "\" or \"" + ONE + ")");
                }
            }

            this.prev = prev;
            this.next = next;
            this.name = name;
            this.handler = handler;
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
}