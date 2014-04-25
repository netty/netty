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

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChannelHandler} that appends the specified {@link ChannelHandler}s right next to itself.
 * By default, it removes itself from the {@link ChannelPipeline} once the specified {@link ChannelHandler}s
 * are added. Optionally, you can keep it in the {@link ChannelPipeline} by specifying a {@code boolean}
 * parameter at construction time.
 */
public class ChannelHandlerAppender extends ChannelInboundHandlerAdapter {

    private static final class Entry {
        final String name;
        final ChannelHandler handler;

        Entry(String name, ChannelHandler handler) {
            this.name = name;
            this.handler = handler;
        }
    }

    private final boolean selfRemoval;
    private final List<Entry> handlers = new ArrayList<Entry>();
    private boolean added;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #add(ChannelHandler...)} before adding this handler into a {@link ChannelPipeline}.
     */
    protected ChannelHandlerAppender() {
        this(true);
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
        this(true, handlers);
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     */
    public ChannelHandlerAppender(ChannelHandler... handlers) {
        this(true, handlers);
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

        handlers.add(new Entry(name, handler));
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
    @SuppressWarnings("unchecked")
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
        return (T) handlers.get(index).handler;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        added = true;

        DefaultChannelHandlerContext dctx = (DefaultChannelHandlerContext) ctx;
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) dctx.pipeline();
        String name = dctx.name();
        try {
            for (Entry e: handlers) {
                String oldName = name;
                if (e.name == null) {
                    name = pipeline.generateName(e.handler);
                } else {
                    name = e.name;
                }

                // Note that we do not use dctx.invoker() because it raises an IllegalStateExxception
                // if the Channel is not registered yet.
                pipeline.addAfter(dctx.invoker, oldName, name, e.handler);
            }
        } finally {
            if (selfRemoval) {
                pipeline.remove(this);
            }
        }
    }
}
