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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.RecyclableArrayList;

/**
 * This is a base class for backpressure-aware decompressing handler. The purpose of this class is to share code
 * between the "normal" decompressing handler and the HTTP/1.1 decompressor. This class can handle a single compressed
 * stream or multiple consecutive ones (as in HTTP/1.1 connections), and it can handle framing messages.
 */
public abstract class AbstractBackpressureDecompressionHandler extends ChannelDuplexHandler {
    /**
     * The decompressor for the current message.
     */
    private Decompressor decompressor;

    private RecyclableArrayList heldBack;

    private boolean reading;

    private boolean discardRemainingContent;

    private boolean anyMessageWrittenSinceReadStart;

    private final BackpressureGauge backpressureGauge;

    protected AbstractBackpressureDecompressionHandler(Builder builder) {
        this.backpressureGauge = builder.backpressureGaugeBuilder.build();
    }

    private Decompressor.Status decompressorStatus(ChannelHandlerContext ctx) {
        assert decompressor != null;
        try {
            return decompressor.status();
        } catch (Exception e) {
            handleDecompressorException(ctx, e);
            return Decompressor.Status.COMPLETE;
        }
    }

    private void handleDecompressorException(ChannelHandlerContext ctx, Exception e) {
        try {
            decompressor.close();
        } catch (Exception f) {
            e.addSuppressed(f);
        }
        decompressor = null;
        discardRemainingContent = true;
        ctx.fireExceptionCaught(e);
        fireEndOfOutput(ctx);
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!reading) {
            reading = true;
            anyMessageWrittenSinceReadStart = false;
        }

        if (heldBack != null) {
            heldBack.add(msg);
            return;
        }

        channelRead0(ctx, msg);
    }

    /**
     * Handle an input message from {@link #channelRead(ChannelHandlerContext, Object)}. This method is only called
     * when the decompressor is capable of accepting input ({@link #channelReadBytes} or
     * {@link #channelReadEndOfInput}).
     */
    protected abstract void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * Feed some input data to the decompressor. Must be called at most once from {@link #channelRead0}, and only from
     * there.
     */
    protected final void channelReadBytes(ChannelHandlerContext ctx, ByteBuf msg) {
        if (decompressor == null) {
            if (msg.isReadable() && !discardRemainingContent) {
                msg.release();
                throw new DecompressionException("Additional input after compressed data");
            }
            msg.release();
        } else {
            assert decompressor.status() == Decompressor.Status.NEED_INPUT : "heldBack should be set";
            if (msg.isReadable()) {
                boolean failed = false;
                try {
                    decompressor.addInput(msg);
                } catch (Exception e) {
                    handleDecompressorException(ctx, e);
                    failed = true;
                }
                if (!failed) {
                    forwardOutput(ctx);
                }
            } else {
                msg.release();
            }
        }
    }

    /**
     * Signal end of input from {@link #channelRead0}. Must be called at most once, and only from that method. Must not
     * be called if {@link #channelReadBytes} has been called–if you need both, please go through {@link #channelRead}
     * twice instead, so that the message can be delayed if necessary.
     */
    protected final void channelReadEndOfInput(ChannelHandlerContext ctx) {
        if (decompressor == null) {
            // already done
            return;
        }
        if (decompressorStatus(ctx) == Decompressor.Status.NEED_INPUT) {
            decompressor.endOfInput();
            forwardOutput(ctx);
        } else {
            throw new IllegalStateException(
                    "Please don't call channelReadEndOfInput immediately after channelReadBytes, go through " +
                            "channelRead again instead.");
        }
    }

    /**
     * Called when the end of decompressed output is reached. This can also happen as a result of a decompression
     * exception.
     */
    protected abstract void fireEndOfOutput(ChannelHandlerContext ctx);

    /**
     * Start decompressing a message.
     *
     * @param builder The decompression format
     */
    protected final void beginDecompression(
            ChannelHandlerContext ctx, Decompressor.AbstractDecompressorBuilder builder) {
        decompressor = builder.build(ctx.alloc());
        backpressureGauge.relieveBackpressure();
        discardRemainingContent = false;
        anyMessageWrittenSinceReadStart = true;
    }

    /**
     * Send a framing message downstream, i.e. {@link BackpressureGauge#countNonByteMessage()}. You can also use this
     * to send byte messages when processing non-compressed input.
     */
    protected final void fireFramingMessage(ChannelHandlerContext ctx, Object msg) {
        backpressureGauge.countNonByteMessage();
        anyMessageWrittenSinceReadStart = true;
        ctx.fireChannelRead(msg);
    }

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        do {
            if (!anyMessageWrittenSinceReadStart) {
                // we didn't forward any messages, so we need to ask upstream for more
                reading = false;
                if (!isAutoRead(ctx)) {
                    ctx.read();
                }
                return;
            }

            // accept that we might not have hit the target.
            backpressureGauge.increaseBackpressure();

            ctx.fireChannelReadComplete();
            reading = false;

        } while (fulfillDemandOutsideRead(ctx));
    }

    /**
     * @return {@code true} if {@link #channelReadComplete(ChannelHandlerContext)} should be called next. This is to
     * avoid recursion
     */
    private boolean fulfillDemandOutsideRead(ChannelHandlerContext ctx) throws Exception {
        assert !reading;

        if (decompressor == null) {
            return false;
        }
        if (downstreamMessageLimitExceeded(ctx)) {
            return false;
        }

        RecyclableArrayList heldBack = this.heldBack;
        if (heldBack == null) {
            if (!isAutoRead(ctx)) {
                ctx.read();
            }
            return false;
        }

        reading = true;
        anyMessageWrittenSinceReadStart = false;
        forwardOutput(ctx);
        if (decompressor == null || decompressorStatus(ctx) != Decompressor.Status.NEED_OUTPUT) {
            this.heldBack = null;
            if (heldBack.isEmpty() && !anyMessageWrittenSinceReadStart) {
                heldBack.recycle();
                return false;
            } else {
                // this sets reading = true
                for (Object msg : heldBack) {
                    channelRead(ctx, msg);
                }
                heldBack.recycle();
                return true; // channelReadComplete(ctx)
            }
        } else {
            if (anyMessageWrittenSinceReadStart) {
                return true; // channelReadComplete(ctx)
            }
            ctx.read();
            return false;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isAutoRead(ChannelHandlerContext ctx) {
        return ctx.channel().config().isAutoRead();
    }

    private boolean downstreamMessageLimitExceeded(ChannelHandlerContext ctx) {
        return backpressureGauge.backpressureLimitExceeded() && !isAutoRead(ctx);
    }

    @Override
    public final void read(ChannelHandlerContext ctx) throws Exception {
        if (decompressor == null) {
            ctx.read();
            return;
        }

        backpressureGauge.relieveBackpressure();
        if (!reading) {
            if (fulfillDemandOutsideRead(ctx)) {
                channelReadComplete(ctx);
            }
        }
    }

    private void forwardOutput(ChannelHandlerContext ctx) {
        while (true) {
            Decompressor.Status status = decompressorStatus(ctx);
            switch (status) {
                case NEED_OUTPUT:
                    if (downstreamMessageLimitExceeded(ctx)) {
                        if (heldBack == null) {
                            heldBack = RecyclableArrayList.newInstance();
                        }
                        return;
                    }
                    ByteBuf output;
                    try {
                        output = decompressor.takeOutput();
                    } catch (Exception e) {
                        handleDecompressorException(ctx, e);
                        return;
                    }
                    backpressureGauge.countMessage(output.readableBytes());
                    anyMessageWrittenSinceReadStart = true;
                    ctx.fireChannelRead(wrapOutputBuffer(output));
                    break;
                case NEED_INPUT:
                    return;
                case COMPLETE:
                    if (decompressor != null) {
                        try {
                            decompressor.close();
                        } catch (Exception e) {
                            ctx.fireExceptionCaught(e);
                        }
                    }
                    decompressor = null;
                    fireEndOfOutput(ctx);
                    return;
                default:
                    throw new AssertionError("Unknown status: " + status);
            }
        }
    }

    /**
     * Wrap an output buffer before sending it down the pipeline. This is used for HttpContent.
     */
    protected Object wrapOutputBuffer(ByteBuf output) {
        return output;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (decompressor != null) {
            decompressor.close();
            decompressor = null;
        }
        if (heldBack != null) {
            for (Object o : heldBack) {
                ReferenceCountUtil.release(o);
            }
            heldBack = null;
        }
    }

    public abstract static class Builder {
        BackpressureGauge.Builder backpressureGaugeBuilder = BackpressureGauge.builder();

        public Builder backpressureGaugeBuilder(BackpressureGauge.Builder backpressureGaugeBuilder) {
            this.backpressureGaugeBuilder =
                    ObjectUtil.checkNotNull(backpressureGaugeBuilder, "backpressureGaugeBuilder");
            return this;
        }

        public abstract ChannelDuplexHandler build();
    }
}
