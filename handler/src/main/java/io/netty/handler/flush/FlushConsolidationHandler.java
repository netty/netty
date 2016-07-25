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
package io.netty.handler.flush;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.ObjectUtil;

/**
 * {@link ChannelDuplexHandler} which consolidate {@link ChannelOutboundInvoker#flush()} operations (which also includes
 * {@link ChannelOutboundInvoker#writeAndFlush(Object)} and
 * {@link ChannelOutboundInvoker#writeAndFlush(Object, ChannelPromise)}).
 * <p>
 * Flush operations are general speaking expensive as these may trigger a syscall on the transport level. Thus it is
 * in most cases (where write latency can be traded with throughput) a good idea to try to minimize flush operations
 * as much as possible.
 * <p>
 * When {@link #flush(ChannelHandlerContext)} is called it will only pass it on to the next
 * {@link ChannelOutboundHandler} in the {@link ChannelPipeline} if no read loop is currently ongoing
 * as it will pick up any pending flushes when {@link #channelReadComplete(ChannelHandlerContext)} is trigged.
 * If {@code explicitFlushAfterFlushes} is reached the flush will also be forwarded as well.
 * <p>
 * If the {@link Channel} becomes non-writable it will also try to execute any pending flush operations.
 * <p>
 * The {@link FlushConsolidationHandler} should be put as first {@link ChannelHandler} in the
 * {@link ChannelPipeline} to have the best effect.
 */
public class FlushConsolidationHandler extends ChannelDuplexHandler {
    private final int explicitFlushAfterFlushes;
    private int flushPendingCount;
    private boolean readInprogess;

    /**
     * Create new instance which explicit flush after 256 pending flush operations latest.
     */
    public FlushConsolidationHandler() {
        this(256);
    }

    /**
     * Create new instance.
     *
     * @param explicitFlushAfterFlushes the number of flushes after which an explicit flush will be done.
     */
    public FlushConsolidationHandler(int explicitFlushAfterFlushes) {
        this.explicitFlushAfterFlushes = ObjectUtil.checkPositive(explicitFlushAfterFlushes,
                "explicitFlushAfterFlushes");
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (readInprogess) {
            // If there is still a read in compress we are sure we will see a channelReadComplete(...) call. Thus
            // we only need to flush if we reach the explicitFlushAfterFlushes limit.
            if (++flushPendingCount == explicitFlushAfterFlushes) {
                flushPendingCount = 0;
                ctx.flush();
            }
            return;
        }
        ctx.flush();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // This may be the last event in the read loop, so flush now!
        resetReadAndFlushIfNeeded(ctx);
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readInprogess = true;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // To ensure we not miss to flush anything, do it now.
        resetReadAndFlushIfNeeded(ctx);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Try to flush one last time if flushes are pending before disconnect the channel.
        resetReadAndFlushIfNeeded(ctx);
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Try to flush one last time if flushes are pending before close the channel.
        resetReadAndFlushIfNeeded(ctx);
        ctx.close(promise);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isWritable()) {
            // The writability of the channel changed to false, so flush all consolidated flushes now to free up memory.
            flushIfNeeded(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        flushIfNeeded(ctx);
    }

    private void resetReadAndFlushIfNeeded(ChannelHandlerContext ctx) {
        readInprogess = false;
        flushIfNeeded(ctx);
    }

    private void flushIfNeeded(ChannelHandlerContext ctx) {
        if (flushPendingCount > 0) {
            flushPendingCount = 0;
            ctx.flush();
        }
    }
}
