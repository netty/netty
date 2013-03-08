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
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelInboundByteHandlerAdapter;

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
    extends ChannelInboundByteHandlerAdapter {

    private volatile boolean singleDecode;
    private boolean decodeWasNull;

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
    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        callDecode(ctx, in);
    }

    @Override
    public void channelReadSuspended(ChannelHandlerContext ctx) throws Exception {
        if (decodeWasNull) {
            decodeWasNull = false;
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        super.channelReadSuspended(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ByteBuf in = ctx.inboundByteBuffer();
        if (in.isReadable()) {
            callDecode(ctx, in);
        }

        try {
            Object msg = decodeLast(ctx, in);
            if (ctx.nextInboundMessageBuffer().unfoldAndAdd(msg)) {
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

    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in) {
        boolean wasNull = false;

        boolean decoded = false;
        while (in.isReadable()) {
            try {
                int oldInputLength = in.readableBytes();
                Object o = decode(ctx, in);
                if (o == null) {
                    wasNull = true;
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }
                wasNull = false;
                if (oldInputLength == in.readableBytes()) {
                    throw new IllegalStateException(
                            "decode() did not read anything but decoded a message.");
                }

                if (ctx.nextInboundMessageBuffer().unfoldAndAdd(o)) {
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
            decodeWasNull = false;
            ctx.fireInboundBufferUpdated();
        } else {
            if (wasNull) {
                decodeWasNull = true;
            }
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
}
