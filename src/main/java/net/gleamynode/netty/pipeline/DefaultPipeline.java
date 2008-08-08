/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.pipeline;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultPipeline<E> extends AbstractPipeline<E> {

    static final Logger logger = Logger.getLogger(DefaultPipeline.class.getName());

    static <E> void checkPipeArray(Pipe<E>... pipes) {
        if (pipes == null) {
            throw new NullPointerException("pipes");
        }
        for (int i = 0; i < pipes.length; i ++) {
            if (pipes[i] == null) {
                throw new NullPointerException("pipes[" + i + "]");
            }
        }
    }

    private final PipelineSink<E> discardingSink = new PipelineSink<E>() {
        public void elementSunk(Pipeline<E> pipeline, E element) {
            logger.warning("No sink is set; discarding: " + element);
        }

        public void exceptionCaught(Pipeline<E> pipeline,
                E element, PipelineException cause) throws Exception {
            throw cause;
        }
    };

    volatile PipelineSink<E> sink;
    volatile DefaultPipeContext head;
    private volatile DefaultPipeContext tail;
    private final Map<String, DefaultPipeContext> name2ctx =
        new HashMap<String, DefaultPipeContext>(4);

    public DefaultPipeline() {
        super();
    }

    public DefaultPipeline(Pipe<E>... pipes) {
        checkPipeArray(pipes);
        for (Pipe<E> p: pipes) {
            addLast(p);
        }
    }

    public PipelineSink<E> getSink() {
        PipelineSink<E> sink = this.sink;
        if (sink == null) {
            return discardingSink;
        }
        return sink;
    }

    public void setSink(PipelineSink<E> sink) {
        if (sink == null) {
            throw new NullPointerException("sink");
        }
        if (this.sink != null) {
            throw new IllegalStateException("sink is already set.");
        }
        this.sink = sink;
    }

    public synchronized void addFirst(Pipe<E> pipe) {
        if (name2ctx.isEmpty()) {
            init(pipe);
        } else {
            checkDuplicateName(pipe);
            DefaultPipeContext oldHead = head;
            DefaultPipeContext newHead = new DefaultPipeContext(null, oldHead, pipe);
            oldHead.prev = newHead;
            head = newHead;
            name2ctx.put(pipe.getName(), newHead);
        }
    }

    public synchronized void addLast(Pipe<E> pipe) {
        if (name2ctx.isEmpty()) {
            init(pipe);
        } else {
            checkDuplicateName(pipe);
            DefaultPipeContext oldTail = tail;
            DefaultPipeContext newTail = new DefaultPipeContext(oldTail, null, pipe);
            oldTail.next = newTail;
            tail = newTail;
            name2ctx.put(pipe.getName(), newTail);
        }
    }

    public synchronized void addBefore(Pipe<E> basePipe, Pipe<E> pipe) {
        DefaultPipeContext ctx = (DefaultPipeContext) getContext(basePipe);
        if (ctx == null) {
            throw new NoSuchElementException("basePipe: " + basePipe.getName());
        }

        if (ctx == head) {
            addFirst(pipe);
        } else {
            checkDuplicateName(pipe);
            DefaultPipeContext newCtx = new DefaultPipeContext(ctx.prev, ctx, pipe);
            ctx.prev.next = newCtx;
            ctx.prev = newCtx;
            name2ctx.put(pipe.getName(), newCtx);
        }
    }

    public synchronized void addAfter(Pipe<E> basePipe, Pipe<E> pipe) {
        DefaultPipeContext ctx = (DefaultPipeContext) getContext(basePipe);
        if (ctx == null) {
            throw new NoSuchElementException("basePipe: " + basePipe.getName());
        }

        if (ctx == tail) {
            addLast(pipe);
        } else {
            checkDuplicateName(pipe);
            DefaultPipeContext newCtx = new DefaultPipeContext(ctx, ctx.next, pipe);
            ctx.next.prev = newCtx;
            ctx.next = newCtx;
            name2ctx.put(pipe.getName(), newCtx);
        }
    }

    public synchronized void remove(Pipe<E> pipe) {
        DefaultPipeContext ctx = (DefaultPipeContext) getContext(pipe);
        if (ctx == null) {
            throw new NoSuchElementException("pipe: " + pipe.getName());
        }
        if (head == tail) {
            head = tail = null;
            name2ctx.clear();
        } else if (ctx == head) {
            removeFirst();
        } else if (ctx == tail) {
            removeLast();
        } else {
            DefaultPipeContext prev = ctx.prev;
            DefaultPipeContext next = ctx.next;
            prev.next = next;
            next.prev = prev;
            name2ctx.remove(ctx.pipe.getName());
        }
    }

    public synchronized Pipe<E> removeFirst() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultPipeContext oldHead = head;
        oldHead.next.prev = null;
        head = oldHead.next;
        name2ctx.remove(oldHead.pipe.getName());
        return oldHead.pipe;
    }

    public synchronized Pipe<E> removeLast() {
        if (name2ctx.isEmpty()) {
            throw new NoSuchElementException();
        }

        DefaultPipeContext oldTail = tail;
        oldTail.prev.next = null;
        tail = oldTail.prev;
        name2ctx.remove(oldTail.pipe.getName());
        return oldTail.pipe;
    }

    public synchronized void replace(Pipe<E> oldPipe, Pipe<E> newPipe) {
        DefaultPipeContext ctx = (DefaultPipeContext) getContext(oldPipe);
        if (ctx == null) {
            throw new NoSuchElementException("oldPipe: " + oldPipe.getName());
        }

        if (head == tail) {
            init(newPipe);
        } else if (ctx == head) {
            removeFirst();
            addFirst(newPipe);
        } else if (ctx == tail) {
            removeLast();
            addLast(newPipe);
        } else {
            boolean sameName = oldPipe.getName().equals(newPipe.getName());
            if (!sameName) {
                checkDuplicateName(newPipe);
            }
            DefaultPipeContext prev = ctx.prev;
            DefaultPipeContext next = ctx.next;
            DefaultPipeContext newCtx = new DefaultPipeContext(prev, next, newPipe);
            prev.next = newCtx;
            next.prev = newCtx;
            if (!sameName) {
                name2ctx.remove(ctx.pipe.getName());
                name2ctx.put(newPipe.getName(), newCtx);
            }
        }
    }

    public synchronized Pipe<E> getFirst() {
        return head.getPipe();
    }

    public synchronized Pipe<E> getLast() {
        return tail.getPipe();
    }

    public synchronized Pipe<E> get(String name) {
        DefaultPipeContext ctx = name2ctx.get(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.pipe;
        }
    }

    public synchronized Pipe<E> get(Class<? extends PipeHandler<E>> handlerType) {
        if (name2ctx.isEmpty()) {
            return null;
        }
        DefaultPipeContext ctx = head;
        for (;;) {
            if (handlerType.isAssignableFrom(ctx.pipe.getHandler().getClass())) {
                return ctx.pipe;
            }

            ctx = ctx.next;
            if (ctx == null) {
                break;
            }
        }

        return null;
    }

    public synchronized PipeContext<E> getContext(Pipe<E> pipe) {
        if (pipe == null) {
            throw new NullPointerException("pipe");
        }

        return name2ctx.get(pipe.getName());
    }

    public void sendUpstream(E element) {
        DefaultPipeContext head = getActualUpstreamContext(this.head);
        if (head == null) {
            logger.warning(
                    "The pipeline contains no upstream handlers; discarding: " + element);
            return;
        }

        sendUpstream(head, element);
    }

    void sendUpstream(DefaultPipeContext ctx, E element) {
        try {
            ((UpstreamHandler<E>) ctx.pipe.getHandler()).handleUpstream(ctx, element);
        } catch (Throwable t) {
            notifyException(element, t);
        }
    }

    public void sendDownstream(E element) {
        DefaultPipeContext tail = getActualDownstreamContext(this.tail);
        if (tail == null) {
            try {
                getSink().elementSunk(this, element);
                return;
            } catch (Throwable t) {
                notifyException(element, t);
            }
        }

        sendDownstream(tail, element);
    }

    void sendDownstream(DefaultPipeContext ctx, E element) {
        try {
            ((DownstreamHandler<E>) ctx.pipe.getHandler()).handleDownstream(ctx, element);
        } catch (Throwable t) {
            notifyException(element, t);
        }
    }

    DefaultPipeContext getActualUpstreamContext(DefaultPipeContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultPipeContext realCtx = ctx;
        while (!realCtx.pipe.canHandleUpstream()) {
            realCtx = realCtx.next;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    DefaultPipeContext getActualDownstreamContext(DefaultPipeContext ctx) {
        if (ctx == null) {
            return null;
        }

        DefaultPipeContext realCtx = ctx;
        while (!realCtx.pipe.canHandleDownstream()) {
            realCtx = realCtx.prev;
            if (realCtx == null) {
                return null;
            }
        }

        return realCtx;
    }

    void notifyException(E element, Throwable t) {
        PipelineException e;
        if (t instanceof PipelineException) {
            e = (PipelineException) t;
        } else {
            e = new PipelineException(t);
        }

        try {
            sink.exceptionCaught(this, element, e);
        } catch (Exception e1) {
            logger.log(
                    Level.WARNING,
                    "An exception was thrown by an exception handler.", e1);
        }
    }

    public Iterator<Pipe<E>> iterator() {
        return new Iterator<Pipe<E>>() {
            private DefaultPipeContext ctx;
            private boolean endOfPipeline;

            public boolean hasNext() {
                if (ctx == null) {
                    return !endOfPipeline && head != null;
                } else {
                    return true;
                }
            }

            public Pipe<E> next() {
                if (endOfPipeline) {
                    throw new NoSuchElementException();
                }
                if (ctx == null) {
                    ctx = head;
                    if (ctx == null) {
                        throw new NoSuchElementException();
                    }
                }

                Pipe<E> next = ctx.pipe;
                ctx = ctx.next;
                if (ctx == null) {
                    endOfPipeline = true;
                }
                return next;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void init(Pipe<E> pipe) {
        DefaultPipeContext ctx = new DefaultPipeContext(null, null, pipe);
        head = tail = ctx;
        name2ctx.clear();
        name2ctx.put(pipe.getName(), ctx);
    }

    private void checkDuplicateName(Pipe<E> pipe) {
        if (name2ctx.containsKey(pipe.getName())) {
            throw new IllegalArgumentException("Duplicate pipe name.");
        }
    }

    private class DefaultPipeContext implements PipeContext<E> {
        volatile DefaultPipeContext next;
        volatile DefaultPipeContext prev;
        final Pipe<E> pipe;

        public DefaultPipeContext(
                DefaultPipeContext prev, DefaultPipeContext next,
                Pipe<E> pipe) {

            if (pipe == null) {
                throw new NullPointerException("pipe");
            }

            this.prev = prev;
            this.next = next;
            this.pipe = pipe;
        }

        public Pipe<E> getPipe() {
            return pipe;
        }

        public Pipeline<E> getPipeline() {
            return DefaultPipeline.this;
        }

        public void sendDownstream(E element) {
            DefaultPipeContext prev = getActualDownstreamContext(this.prev);
            if (prev == null) {
                try {
                    getSink().elementSunk(DefaultPipeline.this, element);
                } catch (Throwable t) {
                    notifyException(element, t);
                }
            } else {
                DefaultPipeline.this.sendDownstream(prev, element);
            }
        }

        public void sendUpstream(E element) {
            DefaultPipeContext next = getActualUpstreamContext(this.next);
            if (next != null) {
                DefaultPipeline.this.sendUpstream(next, element);
            }
        }
    }
}