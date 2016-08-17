/*
 * Copyright 2016 The Netty Project
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

/**
 * Netty is extremely flexible in providing a way to batch writes on the physical socket by providing a means to write
 * and flush writes separately. This works for the usecases when the application pattern is one that provides
 * predictable finite streams of data. In cases, when an infinite stream of data is to be written on a channel which
 * does not have any predictable length and duration of writes, a user has the following choices:
 * <ol>
 * <li>Flush every write. </li>
 * <li>Flush batches based on time and count (eg: Flush every 10 message or 1 second) . </li>
 * </ol>
 *
 * Neither of the above is ideal as the nature of stream is completely unpredictable.
 * Flushing every write is costly as flush is a system call.
 * Flushing batches is arbitrary and has to be deviced for each stream in an arbitrary way.  
 *
 * This handler addresses the above usecase by providing a way to batch writes and flush them at the following points:
 * <ol>
 * <li>At the end of the eventloop iteration.</li>
 * <li>If the channel becomes unwritable determined via {@link #channelWritabilityChanged(ChannelHandlerContext)}
 * callback.</li>
 * </ol>
 *
 * Any explicit flushes are honored as usual.
 *
 * <h2>Restrictions</h2>
 *
 * Due to limitation of existing APIs, {@link AutoFlushHandler} can only be used if the following conditions are met:
 *
 * <ol>
 *     <li>Channel is registered with an {@link EventLoop} that extends {@link SingleThreadEventLoop}.</li>
 *     <li>{@link ChannelPipeline} is an instance of {@link DefaultChannelPipeline}</li>
 *     <li>This handler is not executed by any executor apart from the channel's eventloop.</li>
 * </ol>
 *
 * If any of the above conditions are not met, this handler will fail on
 * {@link #channelRegistered(ChannelHandlerContext)}.
 *
 * <h2>Recommendations</h2>
 *
 * The {@link AutoFlushHandler} should be put as first {@link ChannelHandler} in the
 * {@link ChannelPipeline} to have the best effect.
 */
public class AutoFlushHandler extends ChannelDuplexHandler implements Runnable {
    private boolean flushPending;
    private boolean flushScheduled;
    private SingleThreadEventLoop eventLoop;
    private Channel channel;
    private DefaultChannelPipeline pipeline;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        ChannelPipeline pipeline = ctx.channel().pipeline();
        if (!(pipeline instanceof DefaultChannelPipeline)) {
            throw new IllegalStateException(
                    "Auto flush is only supported with " + DefaultChannelPipeline.class.getName());
        }
        this.pipeline = (DefaultChannelPipeline) pipeline;
        EventLoop eventLoop = ctx.channel().eventLoop();
        if (!(eventLoop instanceof SingleThreadEventLoop)) {
            throw new IllegalStateException(
                    "Auto flush is only supported for " + SingleThreadEventLoop.class.getName() + " eventloops.");
        }
        this.eventLoop = (SingleThreadEventLoop) eventLoop;
        if (this.eventLoop != ctx.executor()) {
            throw new IllegalStateException(AutoFlushHandler.class.getName() + " must run on the channel eventloop.");
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        flushPending = true;
        super.write(ctx, msg, promise);
        scheduleFlushIfRequired();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        flushIfPending(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // To ensure we not miss to flush anything, do it now.
        flushIfPending(ctx);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Try to flush one last time if flushes are pending before disconnect the channel.
        flushIfPending(ctx);
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Try to flush one last time if flushes are pending before close the channel.
        flushIfPending(ctx);
        ctx.close(promise);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isWritable()) {
            // The writability of the channel changed to false, so flush immediately to free up memory.
            flushIfPending(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        flushIfPending(ctx);
    }

    /**
     * Executed at the end of an eventloop iteration and flush the channel.
     */
    @Override
    public void run() {
        /**
         * Task is only scheduled if there are any writes pending and the pending status is cleared only after the flush
         * call reaches this handler. This makes sure that writes are not lost without a flush since the handler is
         * single-threaded.
         */
        flushScheduled = false;
        /**
         * Flush from the start of the pipeline and not from this handler onwards as many handlers would expect a flush
         * and calling flush from this handler onwards will most likely break that assumption because there is no
         * explicit flush from user and this handler is recommended to be at the head of the pipeline.
         */
        channel.pipeline().flush();
        /**
         * Reset the status on the pipeline so that a subsequent write will wake up the selector if required.
         */
        pipeline.postAutoFlush();
    }

    private void scheduleFlushIfRequired() {
        if (!flushScheduled) {
            eventLoop.executeAfterEventLoopIteration(this);
            flushScheduled = true;
        }
    }

    private void flushIfPending(ChannelHandlerContext ctx) {
        assert eventLoop != null && eventLoop.inEventLoop();

        if (flushPending) {
            /**
             * This flush call will actually flush the channel as this must be at the pipeline head, so clear the
             * pending status.
             */
            flushPending = false;
            ctx.flush();
        }
    }
}
