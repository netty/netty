/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.OneTimeTask;

import java.util.concurrent.TimeUnit;

/**
 * Compresses a {@link ByteBuf} using one of compression algorithms.
 */
public abstract class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    /**
     * Default delay time for close. See method {@link #closeDelay(int)}.
     */
    public static final int DEFAULT_CLOSE_DELAY = 10;

    private volatile int closeDelay;

    private final CompressionFormat format;

    /**
     * Used to interact with its {@link ChannelPipeline} and other handlers.
     */
    private volatile ChannelHandlerContext ctx;

    /**
     * Default constructor for {@link CompressionEncoder} which will try to use a direct
     * {@link ByteBuf} as a target for the encoded messages.
     */
    protected CompressionEncoder(CompressionFormat format) {
        this(format, true);
    }

    /**
     * Creates an instance with specified preference of a target {@link ByteBuf} for the encoded messages.
     * It uses if compression algorithm needs a byte array as a target.
     *
     * @param preferDirect {@code true} if a direct {@link ByteBuf} should be tried to be used as a target
     *                     for the encoded messages. If {@code false} is used it will allocate a heap
     *                     {@link ByteBuf}, which is backed by an byte array.
     */
    protected CompressionEncoder(CompressionFormat format, boolean preferDirect) {
        super(preferDirect);
        ObjectUtil.checkNotNull(format, "format");
        this.format = format;
        this.closeDelay = DEFAULT_CLOSE_DELAY;
    }

    /**
     * Returns a format of current compression algorithm.
     */
    public final CompressionFormat format() {
        return format;
    }

    /**
     * Set amount of time to be delayed before the ensuring that the channel is closed
     * after the call of {@link #close(ChannelHandlerContext, ChannelPromise)}.
     *
     * The default value is {@link #DEFAULT_CLOSE_DELAY}.
     *
     * @param delay time in seconds.
     */
    public final void closeDelay(int delay) {
        closeDelay = delay;
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream has been reached.
     */
    public abstract boolean isClosed();

    /**
     * Close this {@link CompressionEncoder} and so finish the encoding.
     *
     * The returned {@link ChannelFuture} will be notified once the operation completes.
     *
     * Note: for some {@link CompressionFormat}'s which does not support close operation
     * this method could return failed {@link ChannelFuture}.
     */
    public ChannelFuture close() {
        return close(ctx().newPromise());
    }

    /**
     * Close this {@link CompressionEncoder} and so finish the encoding.
     * The given {@link ChannelFuture} will be notified once the operation
     * completes and will also be returned.
     *
     * Note: for some {@link CompressionFormat}'s which does not support close operation
     * this method could return failed {@link ChannelFuture}.
     */
    public ChannelFuture close(final ChannelPromise promise) {
        final ChannelHandlerContext ctx = ctx();
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            return finishEncode(ctx, promise);
        } else {
            final ChannelPromise p = ctx.newPromise();
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    ChannelFuture f = finishEncode(ctx(), p);
                    f.addListener(new ChannelPromiseNotifier(promise));
                }
            });
            return p;
        }
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        ChannelFuture f = finishEncode(ctx, ctx.newPromise());
        if (f.isDone()) {
            ctx.close(promise);
        } else {
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    ctx.close(promise);
                }
            });
            // Ensure the channel is closed even if the write operation completes in time
            ctx.executor().schedule(new OneTimeTask() {
                @Override
                public void run() {
                    if (!promise.isDone()) {
                        ctx.close(promise);
                    }
                }
            }, closeDelay, TimeUnit.SECONDS);
        }
    }

    /**
     * Finishes current encoding.
     *
     * Note: for some {@link CompressionFormat}'s which does not support finish operation
     * this method could fail specified {@link ChannelPromise}.
     */
    protected abstract ChannelFuture finishEncode(ChannelHandlerContext ctx, ChannelPromise promise);

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * Returns current {@link ChannelHandlerContext}.
     */
    protected final ChannelHandlerContext ctx() {
        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline");
        }
        return ctx;
    }
}
