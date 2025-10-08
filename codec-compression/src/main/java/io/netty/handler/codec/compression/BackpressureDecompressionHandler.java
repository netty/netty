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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.RecyclableArrayList;

/**
 * A channel handler that decompresses incoming {@link ByteBuf}s using a {@link Decompressor}. This is designed as a
 * drop-in replacement for the various decompression decoders (e.g. {@link BrotliDecoder}), but properly handles
 * downstream backpressure.
 */
public final class BackpressureDecompressionHandler extends ChannelDuplexHandler {
    private static final int DEFAULT_MAX_MESSAGES_PER_READ = 32;

    private final Decompressor.AbstractDecompressorBuilder decompressorBuilder;
    /**
     * Decompressor is created lazily to avoid memory leaks when a BackpressureDecompressionHandler is never added to a
     * channel.
     */
    private Decompressor decompressor;
    private final int maxMessagesPerRead;
    private RecyclableArrayList inputBuffer;
    private boolean closed;
    private int downstreamWantsMore;
    private boolean reading;

    BackpressureDecompressionHandler(Builder builder) {
        this.decompressorBuilder = builder.decompressorBuilder;
        this.maxMessagesPerRead = builder.maxMessagesPerRead;
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
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        decompressor = decompressorBuilder.build(ctx.alloc());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (!closed) {
            decompressor.close();
            closed = true;
        }
    }

    private void handleException(Exception e) {
        try {
            decompressor.close();
        } catch (Exception s) {
            e.addSuppressed(s);
        }
        closed = true;
    }

    private Decompressor.Status status() {
        try {
            return decompressor.status();
        } catch (Exception e) {
            handleException(e);
            throw e;
        }
    }

    private boolean downstreamWantsMore(ChannelHandlerContext ctx) {
        return downstreamWantsMore > 0 || isAutoRead(ctx);
    }

    private static boolean isAutoRead(ChannelHandlerContext ctx) {
        return ctx.channel().config().isAutoRead();
    }

    private void processSome(ChannelHandlerContext ctx) {
        while (!closed && downstreamWantsMore(ctx)) {
            Decompressor.Status status = status();
            switch (status) {
                case NEED_OUTPUT:
                    ByteBuf buf;
                    try {
                        buf = decompressor.takeOutput();
                    } catch (Exception e) {
                        handleException(e);
                        throw e;
                    }
                    if (!buf.isReadable()) {
                        // try again.
                        buf.release();
                        break;
                    }
                    int newDemand = --downstreamWantsMore;
                    if (newDemand == 0 && isAutoRead(ctx)) {
                        downstreamWantsMore = maxMessagesPerRead;
                    }
                    ctx.fireChannelRead(buf);
                    if (newDemand == 0) {
                        ctx.fireChannelReadComplete();
                    }
                    break;
                case NEED_INPUT:
                    if (inputBuffer == null) {
                        return;
                    }
                    Object item = inputBuffer.remove(0);
                    if (inputBuffer.isEmpty()) {
                        inputBuffer.recycle();
                        inputBuffer = null;
                    }
                    try {
                        if (item == EndOfContentEvent.INSTANCE) {
                            decompressor.endOfInput();
                        } else {
                            decompressor.addInput((ByteBuf) item);
                        }
                    } catch (Exception e) {
                        handleException(e);
                        throw e;
                    }
                    break;
                case COMPLETE:
                    decompressor.close();
                    ctx.fireUserEventTriggered(EndOfContentEvent.INSTANCE);
                    closed = true;
                    break;
                default:
                    throw new AssertionError("Unknown status: " + status);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        reading = true;
        if (closed) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (!(msg instanceof ByteBuf)) {
            throw new DecoderException("Only byte bufs allowed");
        }
        if (status() == Decompressor.Status.NEED_INPUT) {
            try {
                decompressor.addInput((ByteBuf) msg);
            } catch (Exception e) {
                handleException(e);
                throw e;
            }
        } else {
            if (inputBuffer == null) {
                inputBuffer = RecyclableArrayList.newInstance();
            }
            inputBuffer.add(msg);
        }
        processSome(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        reading = false;
        endOfRead(ctx, false);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (closed) {
            return;
        }
        downstreamWantsMore = maxMessagesPerRead;
        processSome(ctx);
        if (!reading) {
            endOfRead(ctx, true);
        }
    }

    /**
     * Called when we just finished processing a batch of data and no more upstream data is forthcoming until we ask
     * for it. This method checks for downstream demand and potentially sends an upstream read request.
     *
     * @param ctx       The context
     * @param forceRead Whether to force an upstream .read even if auto read is on
     */
    private void endOfRead(ChannelHandlerContext ctx, boolean forceRead) {
        if (downstreamWantsMore == maxMessagesPerRead) {
            // no new data, ask upstream
            if (forceRead || !isAutoRead(ctx)) {
                ctx.read();
            }
        } else if (downstreamWantsMore > 0) {
            // we forwarded some data, let's wait for downstream demand before asking for more
            downstreamWantsMore = isAutoRead(ctx) ? maxMessagesPerRead : 0;
            ctx.fireChannelReadComplete();
        } // if downstreamWantsMore == 0, we already fired readComplete
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == EndOfContentEvent.INSTANCE) {
            if (inputBuffer == null) {
                inputBuffer = RecyclableArrayList.newInstance();
            }
            inputBuffer.add(evt);
            processSome(ctx);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    public static final class Builder {
        private final Decompressor.AbstractDecompressorBuilder decompressorBuilder;
        private int maxMessagesPerRead = DEFAULT_MAX_MESSAGES_PER_READ;

        Builder(Decompressor.AbstractDecompressorBuilder decompressorBuilder) {
            this.decompressorBuilder = ObjectUtil.checkNotNull(decompressorBuilder, "decompressorBuilder");
        }

        /**
         * The maximum number of buffers to send down the pipeline before a new downstream read request is required. If
         * the channel has auto read enabled, more buffers than this may be sent.
         *
         * @param maxMessagesPerRead The maximum number of buffers to send per single read request
         * @return This builder
         */
        public Builder maxMessagesPerRead(int maxMessagesPerRead) {
            this.maxMessagesPerRead = ObjectUtil.checkPositive(maxMessagesPerRead, "maxMessagesPerRead");
            return this;
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
