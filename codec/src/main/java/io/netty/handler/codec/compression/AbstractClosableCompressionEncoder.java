/*
 * Copyright 2017 The Netty Project
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
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.TimeUnit;

/**
 * A skeletal {@link ClosableCompressionEncoder} implementation.
 */
public abstract class AbstractClosableCompressionEncoder extends AbstractCompressionEncoder
        implements ClosableCompressionEncoder {

    private static final long DEFAULT_LAST_WRITE_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_LAST_WRITE_TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

    private volatile long lastWriteTimeout;
    private volatile TimeUnit lastWriteTimeoutUnit;

    /**
     * Used to interact with its {@link ChannelPipeline} and other handlers.
     */
    private volatile ChannelHandlerContext ctx;

    /**
     * Default constructor for {@link AbstractClosableCompressionEncoder} which will try to use a direct
     * {@link ByteBuf} as a target for the encoded messages.
     */
    protected AbstractClosableCompressionEncoder(CompressionFormat format) {
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
    protected AbstractClosableCompressionEncoder(CompressionFormat format, boolean preferDirect) {
        super(format, preferDirect);
        lastWriteTimeout = DEFAULT_LAST_WRITE_TIMEOUT;
        lastWriteTimeoutUnit = DEFAULT_LAST_WRITE_TIMEOUT_UNIT;
    }

    @Override
    public final long lastWriteTimeoutMillis() {
        return lastWriteTimeoutUnit.toMillis(lastWriteTimeout);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * The default value is {@value #DEFAULT_LAST_WRITE_TIMEOUT}, unit is {@value #DEFAULT_LAST_WRITE_TIMEOUT_UNIT}.
     */
    @Override
    public final void setLastWriteTimeout(long timeout, TimeUnit unit) {
        lastWriteTimeout = timeout;
        lastWriteTimeoutUnit = unit;
    }

    @Override
    public abstract boolean isClosed();

    @Override
    public final ChannelFuture close() {
        return close(ctx().newPromise());
    }

    @Override
    public final ChannelFuture close(final ChannelPromise promise) {
        final ChannelHandlerContext ctx = ctx();
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            return finishEncode(ctx, promise);
        } else {
            final ChannelPromise p = ctx.newPromise();
            executor.execute(new Runnable() {
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
    public final void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
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
            ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    if (!promise.isDone()) {
                        ctx.close(promise);
                    }
                }
            }, lastWriteTimeout, lastWriteTimeoutUnit);
        }
    }

    /**
     * Finishes current encoding.
     *
     * Note: for some {@link CompressionFormat}'s which does not support finish operation
     * this method could fail specified {@link ChannelPromise}.
     */
    protected abstract ChannelFuture finishEncode(ChannelHandlerContext ctx, ChannelPromise promise);

    /**
     * Holds link to current {@link ChannelHandlerContext}.
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * Discards link to current {@link ChannelHandlerContext}.
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        finishEncode(ctx, ctx.newPromise());
        this.ctx = null;
    }

    /**
     * Returns current {@link ChannelHandlerContext}.
     */
    private ChannelHandlerContext ctx() {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline");
        }
        return ctx;
    }
}
