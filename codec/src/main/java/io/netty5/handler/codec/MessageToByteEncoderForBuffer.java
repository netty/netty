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
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.TypeParameterMatcher;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;

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
    private final boolean preferDirect;

    /**
     * see {@link #MessageToByteEncoderForBuffer(boolean)} with {@code true} as boolean parameter.
     */
    protected MessageToByteEncoderForBuffer() {
        this(true);
    }

    /**
     * see {@link #MessageToByteEncoderForBuffer(Class, boolean)} with {@code true} as boolean value.
     */
    protected MessageToByteEncoderForBuffer(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect {@code true} if a direct {@link Buffer} should be tried to be used as target for
     *                     the encoded messages. If {@code false} is used it will allocate a heap {@link Buffer}.
     */
    protected MessageToByteEncoderForBuffer(boolean preferDirect) {
        matcher = TypeParameterMatcher.find(this, MessageToByteEncoderForBuffer.class, "I");
        this.preferDirect = preferDirect;
    }

    /**
     * Create a new instance.
     *
     * @param outboundMessageType The type of messages to match.
     * @param preferDirect        {@code true} if a direct {@link Buffer} should be tried to be used as target for
     *                            the encoded messages. If {@code false} is used it will allocate a heap {@link Buffer}.
     */
    protected MessageToByteEncoderForBuffer(Class<? extends I> outboundMessageType, boolean preferDirect) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
        this.preferDirect = preferDirect;
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
                buf = allocateBuffer(ctx, cast, preferDirect);
                try {
                    encode(ctx, cast, buf);
                } finally {
                    ReferenceCountUtil.release(cast);
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
     * Sub-classes may override this method to return {@link Buffer} with a perfect matching {@code size}.
     */
    protected Buffer allocateBuffer(@SuppressWarnings("unused") ChannelHandlerContext ctx,
                                    @SuppressWarnings("unused") I msg, boolean preferDirect) throws Exception {
        if (preferDirect) {
            return offHeapAllocator().allocate(256);
        } else {
            return onHeapAllocator().allocate(256);
        }
    }

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

    protected boolean isPreferDirect() {
        return preferDirect;
    }
}
