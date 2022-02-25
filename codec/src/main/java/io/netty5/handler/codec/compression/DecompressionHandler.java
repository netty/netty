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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.ByteBuf;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoder;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ByteToMessageDecoder} that uses a {@link Decompressor} for decompressing incoming {@link ByteBuf}s.
 */
public final class DecompressionHandler extends ByteToMessageDecoder {

    private final Supplier<? extends Decompressor> decompressorSupplier;
    private final boolean discardBytesAfterFinished;
    private Decompressor decompressor;

    /**
     * Creates a new instance.
     *
     * @param decompressorSupplier  the {@link Supplier} that is used to create the {@link Decompressor}.
     */
    public DecompressionHandler(Supplier<? extends Decompressor> decompressorSupplier) {
        this(decompressorSupplier, true);
    }

    /**
     * Creates a new instance.
     *
     * @param decompressorSupplier          the {@link Supplier} that is used to create the {@link Decompressor}.
     * @param discardBytesAfterFinished     {@code true} if the bytes should be discarded after the {@link Compressor}
     *                                      finished the compression of the whole stream.
     */
    public DecompressionHandler(Supplier<? extends Decompressor> decompressorSupplier,
                                boolean discardBytesAfterFinished) {
        this.decompressorSupplier = Objects.requireNonNull(decompressorSupplier, "decompressorSupplier");
        this.discardBytesAfterFinished = discardBytesAfterFinished;
    }

    @Override
    protected void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded0(ctx);
        decompressor = decompressorSupplier.get();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (decompressor == null) {
            ctx.fireChannelRead(in.readRetainedSlice(in.readableBytes()));
            return;
        }
        while (!decompressor.isFinished()) {
            int idx = in.readerIndex();
            ByteBuf decompressed = decompressor.decompress(in, ctx.alloc());
            if (decompressed != null) {
                ctx.fireChannelRead(decompressed);
            } else if (idx == in.readerIndex()) {
                return;
            }
        }
        assert decompressor.isFinished();
        if (discardBytesAfterFinished) {
            in.skipBytes(in.readableBytes());
        } else {
            ctx.fireChannelRead(in.readRetainedSlice(in.readableBytes()));
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved0(ctx);
        } finally {
            closeDecompressor();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            super.channelInactive(ctx);
        } finally {
            closeDecompressor();
        }
    }

    private void closeDecompressor() {
        if (decompressor != null) {
            decompressor.close();
            decompressor = null;
        }
    }
}
