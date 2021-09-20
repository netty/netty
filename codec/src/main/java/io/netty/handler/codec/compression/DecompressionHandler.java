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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ByteToMessageDecoder} that uses a {@link Decompressor} for decompressing incoming {@link ByteBuf}s.
 */
public class DecompressionHandler extends ByteToMessageDecoder {

    private final Supplier<? extends Decompressor> decompressorSupplier;
    private Decompressor decompressor;

    /**
     * Creates a new instance.
     *
     * @param decompressorSupplier  the {@link Supplier} that is used to create the {@link Decompressor}.
     */
    public DecompressionHandler(Supplier<? extends Decompressor> decompressorSupplier) {
        this.decompressorSupplier = Objects.requireNonNull(decompressorSupplier, "decompressorSupplier");
    }

    @Override
    protected final void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded0(ctx);
        decompressor = decompressorSupplier.get();
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (shouldDiscard()) {
            in.skipBytes(in.readableBytes());
            return;
        }

        for (;;) {
            int idx = in.readerIndex();
            ByteBuf decompressed = decompressor.decompress(in, ctx.alloc());
            if (decompressed != null) {
                ctx.fireChannelRead(decompressed);
            } else if (idx == in.readerIndex()) {
                return;
            }
        }
    }

    @Override
    protected final void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved0(ctx);
        closeDecompressor();
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        closeDecompressor();
    }

    private void closeDecompressor() {
        if (decompressor != null) {
            decompressor.close();
            decompressor = null;
        }
    }

    private boolean shouldDiscard() {
        return decompressor == null || decompressor.isFinished();
    }
}
