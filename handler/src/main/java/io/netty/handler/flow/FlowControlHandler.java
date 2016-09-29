/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The {@link FlowControlHandler} ensures that only one message per {@code read()} is sent downstream.
 *
 * Classes such as {@link ByteToMessageDecoder} or {@link MessageToByteEncoder} are free to emit as
 * many events as they like for any given input. A channel's auto reading configuration doesn't usually
 * apply in these scenarios. This is causing problems in downstream {@link ChannelHandler}s that would
 * like to hold subsequent events while they're processing one event. It's a common problem with the
 * {@code HttpObjectDecoder} that will very often fire a {@code HttpRequest} that is immediately followed
 * by a {@code LastHttpContent} event.
 *
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * pipeline.addLast(new HttpServerCodec());
 * pipeline.addLast(new {@link FlowControlHandler}());
 *
 * pipeline.addLast(new MyExampleHandler());
 *
 * class MyExampleHandler extends {@link ChannelInboundHandlerAdapter} {
 *   @Override
 *   public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     if (msg instanceof HttpRequest) {
 *       ctx.channel().config().setAutoRead(false);
 *
 *       // The FlowControlHandler will hold any subsequent events that
 *       // were emitted by HttpObjectDecoder until auto reading is turned
 *       // back on or Channel#read() is being called.
 *     }
 *   }
 * }
 * </pre>
 *
 * @see ChannelConfig#setAutoRead(boolean)
 */
public class FlowControlHandler extends ChannelDuplexHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(FlowControlHandler.class);

    private final boolean releaseMessages;

    private RecyclableArrayDeque queue;

    private ChannelConfig config;

    private boolean shouldConsume;

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
        return queue.isEmpty();
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        ctx.fireChannelInactive();
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (dequeue(ctx, 1) == 0) {
            // It seems no messages were consumed. We need to read() some
            // messages from upstream and once one arrives it need to be
            // relayed to downstream to keep the flow going.
            shouldConsume = true;
            ctx.read();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (queue == null) {
            queue = RecyclableArrayDeque.newInstance();
        }

        queue.offer(msg);

        // We just received one message. Do we need to relay it regardless
        // of the auto reading configuration? The answer is yes if this
        // method was called as a result of a prior read() call.
        int minConsume = shouldConsume ? 1 : 0;
        shouldConsume = false;

        dequeue(ctx, minConsume);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // Don't relay completion events from upstream as they
        // make no sense in this context. See dequeue() where
        // a new set of completion events is being produced.
    }

    /**
     * Dequeues one or many (or none) messages depending on the channel's auto
     * reading state and returns the number of messages that were consumed from
     * the internal queue.
     *
     * The {@code minConsume} argument is used to force {@code dequeue()} into
     * consuming that number of messages regardless of the channel's auto
     * reading configuration.
     *
     * @see #read(ChannelHandlerContext)
     * @see #channelRead(ChannelHandlerContext, Object)
     */
    private int dequeue(ChannelHandlerContext ctx, int minConsume) {
        if (queue != null) {

            int consumed = 0;

            Object msg;
            while ((consumed < minConsume) || config.isAutoRead()) {
                msg = queue.poll();
                if (msg == null) {
                    break;
                }

                ++consumed;
                ctx.fireChannelRead(msg);
            }

            // We're firing a completion event every time one (or more)
            // messages were consumed and the queue ended up being drained
            // to an empty state.
            if (queue.isEmpty() && consumed > 0) {
                ctx.fireChannelReadComplete();
            }

            return consumed;
        }

        return 0;
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

        private static final Recycler<RecyclableArrayDeque> RECYCLER = new Recycler<RecyclableArrayDeque>() {
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
