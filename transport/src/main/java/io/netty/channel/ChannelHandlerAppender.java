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
import java.util.Arrays;
import java.util.Collection;

/**
 * A {@link ChannelHandler} that appends the specified {@link ChannelHandler}s right next to itself.
 * By default, it removes itself from the {@link ChannelPipeline} once the specified {@link ChannelHandler}s
 * are added. Optionally, you can keep it in the {@link ChannelPipeline} by specifying a {@code boolean}
 * parameter at construction time.
 */
public class ChannelHandlerAppender extends ChannelHandlerAdapter {

    private final boolean selfRemoval;
    private ChannelHandler[] handlers;

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link #init(ChannelHandler...)} before adding this handler into a {@link ChannelPipeline}.
     */
    protected ChannelHandlerAppender() {
        this(true);
    }

    /**
     * Creates a new uninitialized instance. A class that extends this handler must invoke
     * {@link ##init(ChannelHandler...)} before adding this handler into a {@link ChannelPipeline}.
     *
     * @param selfRemoval {@code true} to remove itself from the {@link ChannelPipeline} after appending
     *                    the {@link ChannelHandler}s specified via {@link #init(ChannelHandler...)}.
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
        init(handlers);
    }

    /**
     * Creates a new instance that appends the specified {@link ChannelHandler}s right next to itself.
     *
     * @param selfRemoval {@code true} to remove itself from the {@link ChannelPipeline} after appending
     *                    the specified {@link ChannelHandler}s
     */
    public ChannelHandlerAppender(boolean selfRemoval, ChannelHandler... handlers) {
        this.selfRemoval = selfRemoval;
        init(handlers);
    }

    /**
     * Initializes this handler with the specified handlers.
     *
     * @throws IllegalStateException if initialized already
     */
    @SuppressWarnings("unchecked")
    protected final void init(Iterable<? extends ChannelHandler> handlers) {
        checkUninitialized();
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        Collection<ChannelHandler> c;
        if (handlers instanceof Collection) {
            c = (Collection<ChannelHandler>) handlers;
        } else {
            c = new ArrayList<ChannelHandler>(4);
            for (ChannelHandler h: handlers) {
                if (h == null) {
                    break;
                }
                c.add(h);
            }
        }

        this.handlers = c.toArray(new ChannelHandler[c.size()]);
    }

    /**
     * Initializes this handler with the specified handlers.
     *
     * @throws IllegalStateException if initialized already
     */
    protected final void init(ChannelHandler... handlers) {
        checkUninitialized();
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        int cnt = 0;
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            cnt++;
        }

        this.handlers = Arrays.copyOf(handlers, cnt);
    }

    private void checkUninitialized() {
        if (handlers != null) {
            throw new IllegalStateException("initialized already");
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T extends ChannelHandler> T handlerAt(int index) {
        return (T) handlers[index];
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (handlers == null) {
            throw new IllegalStateException(
                    "must be initialized before being added to a " + ChannelPipeline.class.getSimpleName());
        }

        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) ctx.pipeline();

        String name = ctx.name();
        try {
            for (ChannelHandler h: handlers) {
                String oldName = name;
                name = pipeline.generateName(h);
                pipeline.addAfter(ctx.invoker(), oldName, name, h);
            }
        } finally {
            if (selfRemoval) {
                pipeline.remove(this);
            }
        }
    }
}
