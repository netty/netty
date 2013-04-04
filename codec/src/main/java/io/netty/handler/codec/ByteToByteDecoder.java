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
import io.netty.channel.ChannelInboundByteHandlerAdapter;

/**
 * {@link ChannelInboundByteHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other.
 *
 * This kind of decoder is often useful for doing on-the-fly processiing like i.e. compression.
 *
 * But you can also do other things with it. For example here is an implementation which reads {@link Integer}s from
 * the input {@link ByteBuf} and square them before write them to the output {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToByteDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             if (in.readableBytes() < 4) {
 *                 return;
 *             }
 *             int value = in.readInt();
 *             out.writeInt(value * value);
 *         }
 *     }
 * </pre>
 */
public abstract class ByteToByteDecoder extends ChannelInboundByteHandlerAdapter {

    private volatile boolean singleDecode;

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
        callDecode(ctx, in, ctx.nextInboundByteBuffer());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ByteBuf in = ctx.inboundByteBuffer();
        ByteBuf out = ctx.nextInboundByteBuffer();
        if (!in.isReadable()) {
            callDecode(ctx, in, out);
        }

        int oldOutSize = out.readableBytes();
        try {
            decodeLast(ctx, in, out);
        } catch (CodecException e) {
            throw e;
        } catch (Throwable t) {
            throw new DecoderException(t);
        } finally {
            if (out.readableBytes() > oldOutSize) {
                ctx.fireInboundBufferUpdated();
            }
            ctx.fireChannelInactive();
        }
    }

    /**
     * Call the {@link #decode(ChannelHandlerContext, ByteBuf, ByteBuf)} method until it is done.
     */
    private void callDecode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
        int oldOutSize = out.readableBytes();
        try {
            while (in.isReadable()) {
                int oldInSize = in.readableBytes();
                    decode(ctx, in, out);
                if (oldInSize == in.readableBytes() || isSingleDecode()) {
                    break;
                }
            }
        } catch (CodecException e) {
            throw e;
        } catch (Throwable t) {
            throw new DecoderException(t);
        } finally {
            if (out.readableBytes() > oldOutSize) {
                ctx.fireInboundBufferUpdated();
            }
        }
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read anymore or till nothing was read from the input {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToByteDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link ByteBuf} to which the decoded data will be written
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception;

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, ByteBuf)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        decode(ctx, in, out);
    }
}
