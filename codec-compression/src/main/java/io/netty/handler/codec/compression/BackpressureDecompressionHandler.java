/*
 * Copyright 2025 The Netty Project
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
import io.netty.util.internal.ObjectUtil;

/**
 * A channel handler that decompresses incoming {@link ByteBuf}s using a {@link Decompressor}. This is designed as a
 * drop-in replacement for the various decompression decoders (e.g. {@link BrotliDecoder}), but properly handles
 * downstream backpressure.
 */
public final class BackpressureDecompressionHandler extends AbstractBackpressureDecompressionHandler {
    private final Decompressor.AbstractDecompressorBuilder decompressorBuilder;
    /**
     * Decompressor is created lazily to avoid memory leaks when a BackpressureDecompressionHandler is never added to a
     * channel.
     */
    private Decompressor decompressor;

    BackpressureDecompressionHandler(Builder builder) {
        super(builder);
        this.decompressorBuilder = builder.decompressorBuilder;
    }

    /**
     * Create a new handler builder.
     *
     * @param decompressorBuilder The decompressor builder to use. This builder is invoked lazily when the handler
     *                            is added to the pipeline.
     * @return The builder for the decompression handler
     */
    public static Builder builder(Decompressor.AbstractDecompressorBuilder decompressorBuilder) {
        return new Builder(decompressorBuilder);
    }

    /**
     * Create a new decompression handler with default settings. Equivalent to
     * {@code builder(decompressorBuilder).build()}.
     *
     * @param decompressorBuilder The decompressor builder to use. This builder is invoked lazily when the handler
     *                            is added to the pipeline.
     * @return The decompression handler
     */
    public static BackpressureDecompressionHandler create(
            Decompressor.AbstractDecompressorBuilder decompressorBuilder) {
        return builder(decompressorBuilder).build();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == EndOfContentEvent.INSTANCE) {
            channelRead(ctx, evt);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == EndOfContentEvent.INSTANCE) {
            channelReadEndOfInput(ctx);
        } else {
            channelReadBytes(ctx, (ByteBuf) msg);
        }
    }

    @Override
    protected void fireEndOfOutput(ChannelHandlerContext ctx) {
        ctx.fireUserEventTriggered(EndOfContentEvent.INSTANCE);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        beginDecompression(ctx, decompressorBuilder);
    }

    public static final class Builder extends AbstractBackpressureDecompressionHandler.Builder {
        private final Decompressor.AbstractDecompressorBuilder decompressorBuilder;

        Builder(Decompressor.AbstractDecompressorBuilder decompressorBuilder) {
            this.decompressorBuilder = ObjectUtil.checkNotNull(decompressorBuilder, "decompressorBuilder");
        }

        /**
         * Create a new decompression handler. Note that the actual decompressor is only created when the handler is
         * added to a pipeline, to avoid memory leaks if the handler is never used.
         *
         * @return The handler
         */
        public BackpressureDecompressionHandler build() {
            return new BackpressureDecompressionHandler(this);
        }
    }

    /**
     * Special {@link #userEventTriggered(ChannelHandlerContext, Object) user event} that is used to signify that no
     * more {@link #channelRead(ChannelHandlerContext, Object)} events are forthcoming.
     * {@link BackpressureDecompressionHandler} will handle this event and
     * {@link Decompressor#endOfInput() forward it to the decompressor}. When the decompressor signals no more output
     * is available, the handler will also fire this event.
     */
    public static final class EndOfContentEvent {
        @SuppressWarnings("InstantiationOfUtilityClass")
        public static final EndOfContentEvent INSTANCE = new EndOfContentEvent();

        private EndOfContentEvent() {
        }
    }
}
