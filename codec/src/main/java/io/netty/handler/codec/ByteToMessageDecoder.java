/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * {@link ChannelInboundByteHandler} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an other
 * Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public {@link Object} decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in)
 *                 throws {@link Exception} {
 *             return in.readBytes(in.readableBytes());
 *         }
 *     }
 * </pre>
 */
public abstract class ByteToMessageDecoder
    extends ChannelInboundHandlerAdapter implements ChannelInboundByteHandler {

    private ChannelHandlerContext ctx;

    private volatile boolean singleDecode;
    private boolean removed;

    /**
     * If set then only one message is decoded on each {@link #inboundBufferUpdated(ChannelHandlerContext)} call.
     * This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #inboundBufferUpdated(ChannelHandlerContext)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        super.beforeAdd(ctx);
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ctx.alloc().buffer();
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        callDecode(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ByteBuf in = ctx.inboundByteBuffer();
        if (in.readable()) {
            callDecode(ctx);
        }

        try {
            if (ChannelHandlerUtil.unfoldAndAdd(ctx, decodeLast(ctx, in), true)) {
                ctx.fireInboundBufferUpdated();
            }
        } catch (Throwable t) {
            if (t instanceof CodecException) {
                ctx.fireExceptionCaught(t);
            } else {
                ctx.fireExceptionCaught(new DecoderException(t));
            }
        }

        ctx.fireChannelInactive();
    }

    protected void callDecode(ChannelHandlerContext ctx) {
        ByteBuf in = ctx.inboundByteBuffer();

        boolean decoded = false;
        while (!removed && in.readable()) {
            try {
                int oldInputLength = in.readableBytes();
                Object o = decode(ctx, in);
                if (o == null) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }
                if (oldInputLength == in.readableBytes()) {
                    throw new IllegalStateException(
                            "decode() did not read anything but decoded a message.");
                }

                if (ChannelHandlerUtil.unfoldAndAdd(ctx, o, true)) {
                    decoded = true;
                    if (isSingleDecode()) {
                        break;
                    }
                } else {
                    break;
                }
            } catch (Throwable t) {
                if (decoded) {
                    decoded = false;
                    ctx.fireInboundBufferUpdated();
                }

                if (t instanceof CodecException) {
                    ctx.fireExceptionCaught(t);
                } else {
                    ctx.fireExceptionCaught(new DecoderException(t));
                }
            }
        }

        if (decoded) {
            ctx.fireInboundBufferUpdated();
        }
    }

    /**
     * Replace this decoder in the {@link ChannelPipeline} with the given handler.
     * All remaining bytes in the inbound buffer will be forwarded to the new handler's
     * inbound buffer.
     */
    public void replace(String newHandlerName, ChannelInboundByteHandler newHandler) {
        if (!ctx.executor().inEventLoop()) {
            throw new IllegalStateException("not in event loop");
        }

        // We do not use ChannelPipeline.replace() here so that the current context points
        // the new handler.
        ctx.pipeline().addAfter(ctx.name(), newHandlerName, newHandler);

        ByteBuf in = ctx.inboundByteBuffer();
        try {
            if (in.readable()) {
                ctx.nextInboundByteBuffer().writeBytes(in);
                ctx.fireInboundBufferUpdated();
            }
        } finally {
            ctx.pipeline().remove(this);
        }
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read anymore, till nothing was read from the input {@link ByteBuf} or till
     * this method returns {@code null}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToByteDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @return message      the message to which the content of the {@link ByteBuf} was decoded, or {@code null} if
     *                      there was not enough data left in the {@link ByteBuf} to decode.
     * @throws Exception    is thrown if an error accour
     */
    protected abstract Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception;

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected Object decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        return decode(ctx, in);
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        super.afterRemove(ctx);
        removed = true;
    }
}
