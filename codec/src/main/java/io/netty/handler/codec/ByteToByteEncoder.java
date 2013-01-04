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
import io.netty.channel.ChannelOutboundByteHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PartialFlushException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ChannelOutboundByteHandlerAdapter} which encodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other.
 *
 * This kind of decoder is often useful for doing on-the-fly processing like i.e. compression.
 *
 * But you can also do other things with it. For example here is an implementation which reads {@link Integer}s from
 * the input {@link ByteBuf} and square them before write them to the output {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareEncoder extends {@link ByteToByteEncoder} {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, {@link ByteBuf} out)
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
public abstract class ByteToByteEncoder extends ChannelOutboundByteHandlerAdapter {

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ByteBuf in = ctx.outboundByteBuffer();
        ByteBuf out = ctx.nextOutboundByteBuffer();
        boolean partialFlush = partialFlush();
        List<Throwable> causes = null;

        while (in.readable()) {
            int oldInSize = in.readableBytes();
            try {
                encode(ctx, in, out);
            } catch (Throwable t) {
                if (causes == null) {
                    causes = new ArrayList<Throwable>();
                }
                if (t instanceof CodecException) {
                    causes.add(t);
                } else {
                    causes.add(new EncoderException(t));
                }
                if (!partialFlush) {
                    break;
                }
            }
            if (oldInSize == in.readableBytes()) {
                break;
            }
        }
        in.discardSomeReadBytes();

        if (causes != null) {
            promise.setFailure(new PartialFlushException("Unable to encoded all messages",
                    causes.toArray(new Throwable[causes.size()])));
            return;
        }
        ctx.flush(promise);
    }

    /**
     * Encodes the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read anymore or till nothing was read from the input {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToByteDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link ByteBuf} to which the decoded data will be written
     * @throws Exception    is thrown if an error accour
     */
    protected abstract void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception;

    /**
     * Return {@code true} if this encoder allows partial flushs, which means it will continue to try to encode and
     * flush all messages of the inbound {@link ByteBuf} even if a exception was raised during it. Otherwise it will
     * stop on the first exception.
     *
     * Default is {@code true}, but sub-classes may override this method to change the behaviour.
     */
    protected boolean partialFlush() {
        return true;
    }
}
