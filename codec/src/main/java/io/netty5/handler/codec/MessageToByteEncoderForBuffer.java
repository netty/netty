/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.Resource;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.TypeParameterMatcher;

import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelHandler} which encodes message in a stream-like fashion from one message to a {@link Buffer}.
 *
 * <p>Example implementation which encodes {@link Integer}s to a {@link Buffer}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToByteEncoderForBuffer}&lt;{@link Integer}&gt; {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} msg, {@link Buffer} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 */
public abstract class MessageToByteEncoderForBuffer<I> extends ChannelHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToByteEncoderForBuffer() {
        matcher = TypeParameterMatcher.find(this, MessageToByteEncoderForBuffer.class, "I");
    }

    /**
     * Create a new instance.
     *
     * @param outboundMessageType The type of messages to match.
     */
    protected MessageToByteEncoderForBuffer(Class<? extends I> outboundMessageType) {
        matcher = TypeParameterMatcher.get(requireNonNull(outboundMessageType, "outboundMessageType"));
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        Buffer buf = null;
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                buf = allocateBuffer(ctx, cast);
                try {
                    encode(ctx, cast, buf);
                } finally {
                    Resource.dispose(cast);
                }

                if (buf.readableBytes() > 0) {
                    Future<Void> f = ctx.write(buf);
                    buf = null;
                    return f;
                }
                return ctx.write(ctx.bufferAllocator().allocate(0));
            }
            return ctx.write(msg);
        } catch (EncoderException e) {
            return ctx.newFailedFuture(e);
        } catch (Throwable e) {
            return ctx.newFailedFuture(new EncoderException(e));
        } finally {
            if (buf != null) {
                buf.close();
            }
        }
    }

    /**
     * Allocate a {@link Buffer} which will be used as argument of {@link #encode(ChannelHandlerContext, I, Buffer)}.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link MessageToByteEncoderForBuffer} belongs to
     * @param msg the message to be encoded
     */
    protected abstract Buffer allocateBuffer(ChannelHandlerContext ctx, I msg) throws Exception;

    /**
     * Encode a message into a {@link Buffer}. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link MessageToByteEncoderForBuffer} belongs to
     * @param msg the message to encode
     * @param out the {@link Buffer} into which the encoded message will be written
     * @throws Exception is thrown if an error occurs
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, Buffer out) throws Exception;
}
