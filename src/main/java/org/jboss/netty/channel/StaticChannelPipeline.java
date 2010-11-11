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

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.ConversionUtil;

/**
 * A {@link ChannelPipeline} that might perform better at the cost of
 * disabled dynamic insertion and removal of {@link ChannelHandler}s.
 * An attempt to insert, remove, or replace a handler in this pipeline will
 * trigger an {@link UnsupportedOperationException}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2267 $, $Date: 2010-05-06 16:00:52 +0900 (Thu, 06 May 2010) $
 *
 */
public class StaticChannelPipeline implements ChannelPipeline {

    // FIXME Code duplication with DefaultChannelPipeline
    static final InternalLogger logger = InternalLoggerFactory.getInstance(StaticChannelPipeline.class);

    private volatile Channel channel;
    private volatile ChannelSink sink;
    private final StaticChannelHandlerContext[] contexts;
    private final int lastIndex;
    private final Map<String, StaticChannelHandlerContext> name2ctx =
        new HashMap<String, StaticChannelHandlerContext>(4);

    /**
     * Creates a new pipeline from the specified handlers.
     * The names of the specified handlers are generated automatically;
     * the first handler's name is {@code "0"}, the second handler's name is
     * {@code "1"}, the third handler's name is {@code "2"}, and so on.
     */
    public StaticChannelPipeline(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        if (handlers.length == 0) {
            throw new IllegalArgumentException("no handlers specified");
        }

        // Get the number of first non-null handlers.
        StaticChannelHandlerContext[] contexts =
            new StaticChannelHandlerContext[handlers.length];
        int nContexts;
        for (nContexts = 0; nContexts < contexts.length; nContexts ++) {
            ChannelHandler h = handlers[nContexts];
            if (h == null) {
                break;
            }
        }

        if (nContexts == contexts.length) {
            this.contexts = contexts;
            lastIndex = contexts.length - 1;
        } else {
            this.contexts = contexts =
                new StaticChannelHandlerContext[nContexts];
            lastIndex = nContexts - 1;
        }

        // Initialize the first non-null handlers only.
        for (int i = 0; i < nContexts; i ++) {
            ChannelHandler h = handlers[i];
            String name = ConversionUtil.toString(i);
            StaticChannelHandlerContext ctx =
                new StaticChannelHandlerContext(i, name, h);
            contexts[i] = ctx;
            name2ctx.put(name, ctx);
        }

        for (ChannelHandlerContext ctx: contexts) {
            callBeforeAdd(ctx);
            callAfterAdd(ctx);
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelSink getSink() {
        ChannelSink sink = this.sink;
        if (sink == null) {
            return DefaultChannelPipeline.discardingSink;
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

    public void addFirst(String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public void addLast(String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public void addBefore(String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public void addAfter(String baseName, String name, ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public void remove(ChannelHandler handler) {
        throw new UnsupportedOperationException();
    }

    public ChannelHandler remove(String name) {
        throw new UnsupportedOperationException();
    }

    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        throw new UnsupportedOperationException();
    }

    public ChannelHandler removeFirst() {
        throw new UnsupportedOperationException();
    }

    public ChannelHandler removeLast() {
        throw new UnsupportedOperationException();
    }

    public void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
    }

    public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
    }

    public <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        throw new UnsupportedOperationException();
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
                callBeforeRemove(ctx);
                callAfterRemove(ctx);
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

    public ChannelHandler getFirst() {
        return contexts[0].getHandler();
    }

    public ChannelHandler getLast() {
        return contexts[contexts.length - 1].getHandler();
    }

    public ChannelHandler get(String name) {
        StaticChannelHandlerContext ctx = name2ctx.get(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.getHandler();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = getContext(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.getHandler();
        }
    }

    public ChannelHandlerContext getContext(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return name2ctx.get(name);
    }

    public ChannelHandlerContext getContext(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        for (StaticChannelHandlerContext ctx: contexts) {
            if (ctx.getHandler() == handler) {
                return ctx;
            }
        }
        return null;
    }

    public ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }
        for (StaticChannelHandlerContext ctx: contexts) {
            if (handlerType.isAssignableFrom(ctx.getHandler().getClass())) {
                return ctx;
            }
        }
        return null;
    }

    public Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        for (StaticChannelHandlerContext ctx: contexts) {
            map.put(ctx.getName(), ctx.getHandler());
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

        for (StaticChannelHandlerContext ctx: contexts) {
            buf.append('(');
            buf.append(ctx.getName());
            buf.append(" = ");
            buf.append(ctx.getHandler().getClass().getName());
            buf.append(')');
            buf.append(", ");
        }
        buf.replace(buf.length() - 2, buf.length(), "}");
        return buf.toString();
    }

    public void sendUpstream(ChannelEvent e) {
        StaticChannelHandlerContext head = getActualUpstreamContext(0);
        if (head == null) {
            logger.warn(
                    "The pipeline contains no upstream handlers; discarding: " + e);
            return;
        }

        sendUpstream(head, e);
    }

    void sendUpstream(StaticChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelUpstreamHandler) ctx.getHandler()).handleUpstream(ctx, e);
        } catch (Throwable t) {
            notifyHandlerException(e, t);
        }
    }

    public void sendDownstream(ChannelEvent e) {
        StaticChannelHandlerContext tail = getActualDownstreamContext(lastIndex);
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

    void sendDownstream(StaticChannelHandlerContext ctx, ChannelEvent e) {
        try {
            ((ChannelDownstreamHandler) ctx.getHandler()).handleDownstream(ctx, e);
        } catch (Throwable t) {
            notifyHandlerException(e, t);
        }
    }

    StaticChannelHandlerContext getActualUpstreamContext(int index) {
        for (int i = index; i < contexts.length; i ++) {
            StaticChannelHandlerContext ctx = contexts[i];
            if (ctx.canHandleUpstream()) {
                return ctx;
            }
        }
        return null;
    }

    StaticChannelHandlerContext getActualDownstreamContext(int index) {
        for (int i = index; i >= 0; i --) {
            StaticChannelHandlerContext ctx = contexts[i];
            if (ctx.canHandleDownstream()) {
                return ctx;
            }
        }
        return null;
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

    private final class StaticChannelHandlerContext implements ChannelHandlerContext {
        private final int index;
        private final String name;
        private final ChannelHandler handler;
        private final boolean canHandleUpstream;
        private final boolean canHandleDownstream;
        private volatile Object attachment;

        StaticChannelHandlerContext(
                int index, String name, ChannelHandler handler) {

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

            this.index = index;
            this.name = name;
            this.handler = handler;
        }

        public Channel getChannel() {
            return getPipeline().getChannel();
        }

        public ChannelPipeline getPipeline() {
            return StaticChannelPipeline.this;
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
            StaticChannelHandlerContext prev = getActualDownstreamContext(index - 1);
            if (prev == null) {
                try {
                    getSink().eventSunk(StaticChannelPipeline.this, e);
                } catch (Throwable t) {
                    notifyHandlerException(e, t);
                }
            } else {
                StaticChannelPipeline.this.sendDownstream(prev, e);
            }
        }

        public void sendUpstream(ChannelEvent e) {
            StaticChannelHandlerContext next = getActualUpstreamContext(index + 1);
            if (next != null) {
                StaticChannelPipeline.this.sendUpstream(next, e);
            }
        }
    }
}
