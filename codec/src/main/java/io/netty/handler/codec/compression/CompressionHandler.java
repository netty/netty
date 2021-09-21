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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link ChannelHandler} which uses a {@link Compressor} for compressing the written {@link ByteBuf}s.
 */
public class CompressionHandler implements ChannelHandler {

    private final Supplier<? extends Compressor> compressorSupplier;
    private Compressor compressor;

    /**
     * Creates a new instance.
     *
     * @param compressorSupplier  the {@link Supplier} that is used to create the {@link Compressor}.
     */
    public CompressionHandler(Supplier<? extends Compressor> compressorSupplier) {
        this.compressorSupplier = Objects.requireNonNull(compressorSupplier, "compressorSupplier");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        compressor = compressorSupplier.get();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (compressor != null) {
            finish(ctx, false);
            compressor.close();
            compressor = null;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (compressor != null) {
            compressor.close();
            compressor = null;
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
            if (closeCtx) {
                return ctx.close();
            }
            return ctx.newSucceededFuture();
        }
        ByteBuf buffer = compressor.finish(ctx.alloc());
        if (!buffer.isReadable()) {
            buffer.release();
            if (closeCtx) {
                return ctx.close();
            }
            return ctx.newSucceededFuture();
        }
        if (closeCtx) {
            Promise<Void> promise = ctx.newPromise();
            Future<Void> f = ctx.writeAndFlush(buffer).addListener(ctx, (c, ignore) -> c.close().cascadeTo(promise));
            if (!f.isDone()) {
                // Ensure the channel is closed even if the write operation completes in time.
                Future<?> sF =  ctx.executor().schedule(() -> ctx.close().cascadeTo(promise),
                        10, TimeUnit.SECONDS); // FIXME: Magic number
                f.addListener(sF, (scheduledFuture, ignore) -> scheduledFuture.cancel());
            }
            return promise.asFuture();
        }
        return ctx.write(buffer);
    }
}
