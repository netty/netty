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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A {@link ChannelHandler} that appends the specified {@link ChannelHandler}s right after itself.
 * By default, it does <strong>NOT</strong> remove itself from the {@link ChannelPipeline} once the specified
 * {@link ChannelHandler}s are added. Optionally, you can remove it from the {@link ChannelPipeline} by specifying
 * a {@code boolean} parameter at construction time.
 */
public class ChannelHandlerAppender extends ChannelInboundHandlerAdapter {
    final List<Map.Entry<String, ChannelHandler>> handlers = new ArrayList<Map.Entry<String, ChannelHandler>>();

    private final boolean selfRemoval;
    private volatile boolean added;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #add(ChannelHandler...)} before adding this handler into a {@link ChannelPipeline}.
     */
    protected ChannelHandlerAppender() {
        this(false);
    }

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #add(ChannelHandler...)} before adding this handler into a {@link ChannelPipeline}.
     *
     * @param selfRemoval {@code true} to remove itself from the {@link ChannelPipeline} after appending
     *                    the {@link ChannelHandler}s specified via {@link #add(ChannelHandler...)}.
     */
    protected ChannelHandlerAppender(boolean selfRemoval) {
        this.selfRemoval = selfRemoval;
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     */
    public ChannelHandlerAppender(Iterable<? extends ChannelHandler> handlers) {
        this(false, handlers);
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     */
    public ChannelHandlerAppender(ChannelHandler... handlers) {
        this(false, handlers);
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     *
     * @param selfRemoval {@code true} to remove itself from the {@link ChannelPipeline} after appending
     *                    the specified {@link ChannelHandler}s
     */
    public ChannelHandlerAppender(boolean selfRemoval, Iterable<? extends ChannelHandler> handlers) {
        this.selfRemoval = selfRemoval;
        add(handlers);
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     *
     * @param selfRemoval {@code true} to remove itself from the {@link ChannelPipeline} after appending
     *                    the specified {@link ChannelHandler}s
     */
    public ChannelHandlerAppender(boolean selfRemoval, ChannelHandler... handlers) {
        this.selfRemoval = selfRemoval;
        add(handlers);
    }

    /**
     * Adds the specified handler to the list of the appended handlers.
     *
     * @param name the name of the appended handler. {@code null} to auto-generate
     * @param handler the handler to append
     *
     * @throws IllegalStateException if {@link ChannelHandlerAppender} has been added to the pipeline already
     */
    protected final ChannelHandlerAppender add(String name, ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }

        if (added) {
            throw new IllegalStateException("added to the pipeline already");
        }

        handlers.add(new AbstractMap.SimpleImmutableEntry<String, ChannelHandler>(name, handler));
        return this;
    }

    /**
     * Adds the specified handler to the list of the appended handlers with the auto-generated handler name.
     *
     * @param handler the handler to append
     *
     * @throws IllegalStateException if {@link ChannelHandlerAppender} has been added to the pipeline already
     */
    protected final ChannelHandlerAppender add(ChannelHandler handler) {
        return add(null, handler);
    }

    /**
     * Adds the specified handlers to the list of the appended handlers. The handlers' names are auto-generated.
     *
     * @throws IllegalStateException if {@link ChannelHandlerAppender} has been added to the pipeline already
     */
    protected final ChannelHandlerAppender add(Iterable<? extends ChannelHandler> handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            add(h);
        }

        return this;
    }

    /**
     * Adds the specified handlers to the list of the appended handlers. The handlers' names are auto-generated.
     *
     * @throws IllegalStateException if {@link ChannelHandlerAppender} has been added to the pipeline already
     */
    protected final ChannelHandlerAppender add(ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }

            add(h);
        }

        return this;
    }

    /**
     * Returns the {@code index}-th appended handler.
     */
    @SuppressWarnings("unchecked")
    protected final <T extends ChannelHandler> T handlerAt(int index) {
        return (T) handlers.get(index).getValue();
    }

    /**
     * Return the numbers of appended handlers.
     */
    final int numHandlers() {
        return handlers.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        added = true;

        AbstractChannelHandlerContext dctx = (AbstractChannelHandlerContext) ctx;
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) dctx.pipeline();
        try {
            pipeline.appendHandlers(this);
        } finally {
            if (selfRemoval) {
                pipeline.remove(this);
            }
        }
        handlerAdded0(ctx);
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (!selfRemoval) {
            removeAppendHandlers(ctx.pipeline());
        }
        handlerRemoved0(ctx);
    }

    /**
     * Remove all added {@link ChannelHandler} and itself (if needed) from the {@link ChannelPipeline}.
     */
    protected final void remove(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeAppendHandlers(pipeline);
        if (!selfRemoval) {
            pipeline.remove(this);
        }
    }

    private void removeAppendHandlers(ChannelPipeline pipeline) {
        for (int i = 0; i < handlers.size(); i++) {
            Map.Entry<String, ChannelHandler> h = handlers.get(i);
            ChannelHandler handler = h.getValue();
            ChannelHandlerContext hCtx = pipeline.context(handler);
            if (hCtx != null) {
                try {
                    pipeline.remove(h.getValue());
                } catch (NoSuchElementException ignore) {
                    // A user may have removed the handler in the meantime so just ignore this if it happens.
                }
            }
        }
    }

    /**
     * Called after the {@link ChannelHandlerAdapter} was added to the pipeline. At this point all handlers provided
     * in {@link #add(ChannelHandler...)} have been added to the pipeline.
     */
    protected void handlerAdded0(@SuppressWarnings("unused") ChannelHandlerContext ctx) throws Exception { }

    /**
     * Called after the {@link ChannelHandlerAdapter} was removed from the pipeline. At this point all handlers
     * provided in {@link #add(ChannelHandler...)} have been removed from the pipeline.
     */
    protected void handlerRemoved0(@SuppressWarnings("unused") ChannelHandlerContext ctx) throws Exception { }
}
