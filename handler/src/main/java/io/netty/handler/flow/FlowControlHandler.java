/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.flow;

import java.util.ArrayDeque;
import java.util.Queue;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.Recycler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The {@link FlowControlHandler} ensures that only one message per {@code read()} is sent downstream.
 * <p>
 * Classes such as {@link ByteToMessageDecoder} or {@link MessageToByteEncoder} are free to emit as
 * many events as they like for any given input. A channel's auto reading configuration doesn't usually
 * apply in these scenarios. This is causing problems in downstream {@link ChannelHandler}s that would
 * like to hold subsequent events while they're processing one event. It's a common problem with the
 * {@code HttpObjectDecoder} that will very often fire an {@code HttpRequest} that is immediately followed
 * by a {@code LastHttpContent} event.
 *
 * <pre>{@code
 * ChannelPipeline pipeline = ...;
 *
 * pipeline.addLast(new HttpServerCodec());
 * pipeline.addLast(new FlowControlHandler());
 *
 * pipeline.addLast(new MyExampleHandler());
 *
 * class MyExampleHandler extends ChannelInboundHandlerAdapter {
 *   @Override
 *   public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *     if (msg instanceof HttpRequest) {
 *       ctx.channel().config().setAutoRead(false);
 *
 *       // The FlowControlHandler will hold any subsequent events that
 *       // were emitted by HttpObjectDecoder until auto reading is turned
 *       // back on or Channel#read() is being called.
 *     }
 *   }
 * }
 * }</pre>
 *
 * @see ChannelConfig#setAutoRead(boolean)
 */
public class FlowControlHandler extends ChannelDuplexHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(FlowControlHandler.class);

    private final boolean releaseMessages;

    private RecyclableArrayDeque queue;

    private ChannelConfig config;

    /**
     * Number of unsatisfied downstream {@code read()} calls. A downstream {@code read()} is considered unsatisfied
     * if auto-read is off and if it has not yet been paired with a {@code fireChannelRead} or
     * a cumulative {@code fireChannelReadComplete}.
     * <p>
     * A {@code read()} can be satisfied in three ways, whichever comes first:
     * <ul>
     *     <li>inside the {@code read()} call itself, by {@code dequeue()}ing a message</li>
     *     <li>in a {@code channelRead()}</li>
     *     <li>in a {@code channelReadComplete()}</li>
     * </ul>
     * A {@code read()} can be satisfied with auto-read on.
     * <p>
     * When one or more {@code read()} calls are unsatisfied, a downstream {@code channelReadComplete} is fired
     * only when either of the following happens:
     * <ul>
     *     <li>auto-read is off and {@code unsatisfiedReads} returns to zero after {@code dequeue()}ing, or</li>
     *     <li>an upstream {@code channelReadComplete} arrives</li>
     * </ul>
     */
    private int unsatisfiedReads;

    /**
     * {@code true} while a {@link #dequeue(ChannelHandlerContext)} loop is on the stack.
     */
    private boolean dequeuing;

    public FlowControlHandler() {
        this(true);
    }

    public FlowControlHandler(boolean releaseMessages) {
        this.releaseMessages = releaseMessages;
    }

    /**
     * Determine if the underlying {@link Queue} is empty. This method exists for
     * testing, debugging and inspection purposes and it is not Thread safe!
     */
    boolean isQueueEmpty() {
        return queue == null || queue.isEmpty();
    }

    /**
     * Releases all messages and destroys the {@link Queue}.
     */
    private void destroy() {
        if (queue != null) {

            if (!queue.isEmpty()) {
                logger.trace("Non-empty queue: {}", queue);

                if (releaseMessages) {
                    Object msg;
                    while ((msg = queue.poll()) != null) {
                        ReferenceCountUtil.safeRelease(msg);
                    }
                }
            }

            queue.recycle();
            queue = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        config = ctx.channel().config();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        if (!isQueueEmpty()) {
            unsatisfiedReads = queue.size();
            dequeue(ctx);
            ctx.fireChannelReadComplete();
        }
        destroy();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        ctx.fireChannelInactive();
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (!config.isAutoRead()) {
            unsatisfiedReads++;
        }

        boolean didSatisfyARead = dequeue(ctx);
        boolean isAutoRead = config.isAutoRead();
        if (!didSatisfyARead || isAutoRead) {
            assert unsatisfiedReads > 0 || isAutoRead;
            // We either could not satisfy the read or auto-read is on.
            // In both cases we need to delegate the read upstream.
            ctx.read();
        } else if (unsatisfiedReads == 0 && !dequeuing) {
            // Auto-read is off, and we have satisfied all reads.
            // As such, we can complete the current read cycle. && !dequeueing makes sure we are completing the
            // read cycle only once in the top-most read() call.
            ctx.fireChannelReadComplete();
        } else {
            // Auto-read is off, and either reads are still unsatisfied or we are nested in a dequeue.
            // Wait for the outermost call, an upstream channelRead() or a channelReadComplete().
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (queue == null) {
            queue = RecyclableArrayDeque.newInstance();
        }

        queue.offer(msg);

        if (dequeue(ctx)) {
            if (!config.isAutoRead() && unsatisfiedReads == 0 && !dequeuing) {
                ctx.fireChannelReadComplete();
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // Upstream closed the read cycle. Collapse every outstanding read() into a single downstream
        // channelReadComplete; spurious upstream completions with no pending read are dropped.
        if (config.isAutoRead() || unsatisfiedReads > 0) {
            unsatisfiedReads = 0;
            ctx.fireChannelReadComplete();
        }
    }

    /**
     * Dequeues messages while auto-read is enabled or downstream reads are unsatisfied, and updates
     * {@code unsatisfiedReads} accordingly.
     *
     * @see #read(ChannelHandlerContext)
     * @see #channelRead(ChannelHandlerContext, Object)
     */
    private boolean dequeue(ChannelHandlerContext ctx) {
        boolean didSatisfyARead = false;

        boolean wasDequeuing = dequeuing;
        dequeuing = true;
        try {
            // fireChannelRead(...) may call ctx.read() and so this method may be re-entered. Because of that
            // we need to check if queue was set to null in the meantime and, if so, break out of the loop.
            while (queue != null && (config.isAutoRead() || unsatisfiedReads > 0)) {
                Object msg = queue.poll();
                if (msg == null) {
                    break;
                }

                if (unsatisfiedReads > 0) {
                    unsatisfiedReads--;
                }
                ctx.fireChannelRead(msg);

                didSatisfyARead = true;
            }

            if (queue != null && queue.isEmpty()) {
                queue.recycle();
                queue = null;
            }

            return didSatisfyARead;
        } finally {
            dequeuing = wasDequeuing;
        }
    }

    /**
     * A recyclable {@link ArrayDeque}.
     */
    private static final class RecyclableArrayDeque extends ArrayDeque<Object> {

        private static final long serialVersionUID = 0L;

        /**
         * A value of {@code 2} should be a good choice for most scenarios.
         */
        private static final int DEFAULT_NUM_ELEMENTS = 2;

        private static final Recycler<RecyclableArrayDeque> RECYCLER =
                new Recycler<RecyclableArrayDeque>() {
                    @Override
                    protected RecyclableArrayDeque newObject(Handle<RecyclableArrayDeque> handle) {
                        return new RecyclableArrayDeque(DEFAULT_NUM_ELEMENTS, handle);
                    }
                };

        public static RecyclableArrayDeque newInstance() {
            return RECYCLER.get();
        }

        private final Handle<RecyclableArrayDeque> handle;

        private RecyclableArrayDeque(int numElements, Handle<RecyclableArrayDeque> handle) {
            super(numElements);
            this.handle = handle;
        }

        public void recycle() {
            clear();
            handle.recycle(this);
        }
    }
}
