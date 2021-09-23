/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelHandler} which uses a {@link Compressor} for compressing the written {@link ByteBuf}s.
 */
public class CompressionHandler implements ChannelHandler {

    private final Supplier<? extends Compressor> compressorSupplier;
    private final long closeWriteTimeout;
    private final TimeUnit closeWriteTimeoutUnit;
    private Compressor compressor;

    /**
     * Creates a new instance.
     *
     * @param compressorSupplier  the {@link Supplier} that is used to create the {@link Compressor}.
     */
    public CompressionHandler(Supplier<? extends Compressor> compressorSupplier) {
        this(compressorSupplier, 10, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param compressorSupplier    the {@link Supplier} that is used to create the {@link Compressor}.
     * @param closeWriteTimeout     the amount to wait before we will close even tho the write of the trailer was not
     *                              finished yet.
     * @param closeWriteTimeoutUnit the unit of the timeout.
     */
    public CompressionHandler(Supplier<? extends Compressor> compressorSupplier,
                              long closeWriteTimeout, TimeUnit closeWriteTimeoutUnit) {
        this.compressorSupplier = requireNonNull(compressorSupplier, "compressorSupplier");
        this.closeWriteTimeout = checkPositive(closeWriteTimeout, "closeWriteTimeout");
        this.closeWriteTimeoutUnit = requireNonNull(closeWriteTimeoutUnit, "closeWriteTimeoutUnit");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        compressor = compressorSupplier.get();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (compressor != null) {
            try {
                finish(ctx, false);
            } finally {
                closeCompressor();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (compressor != null) {
            closeCompressor();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (compressor == null || compressor.isFinished() || !(msg instanceof ByteBuf)) {
            return ctx.write(msg);
        }
        ByteBuf input = (ByteBuf) msg;
        try {
            ByteBuf buffer = compressor.compress(input, ctx.alloc());
            return ctx.write(buffer);
        } finally {
            input.release();
        }
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        return finish(ctx, true);
    }

    private Future<Void> finish(ChannelHandlerContext ctx, boolean closeCtx) {
        if (compressor == null || compressor.isFinished()) {
            return closeCtx ? ctx.close() : ctx.newSucceededFuture();
        }
        ByteBuf buffer = compressor.finish(ctx.alloc());
        if (!buffer.isReadable()) {
            buffer.release();
            return closeCtx ? ctx.close() : ctx.newSucceededFuture();
        }
        if (closeCtx) {
            Promise<Void> promise = ctx.newPromise();
            Future<Void> f = ctx.writeAndFlush(buffer).addListener(ctx, (c, ignore) -> c.close().cascadeTo(promise));
            if (!f.isDone()) {
                // Ensure the channel is closed even if the write operation completes in time.
                Future<?> sF =  ctx.executor().schedule(() -> ctx.close().cascadeTo(promise),
                        closeWriteTimeout, closeWriteTimeoutUnit);
                f.addListener(sF, (scheduledFuture, ignore) -> scheduledFuture.cancel());
            }
            return promise.asFuture();
        }
        return ctx.write(buffer);
    }

    private void closeCompressor() {
        compressor.close();
        compressor = null;
    }
}
