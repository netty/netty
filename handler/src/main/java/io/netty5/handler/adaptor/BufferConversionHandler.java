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
package io.netty5.handler.adaptor;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.buffer.api.adaptor.ByteBufBuffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandler.Sharable;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;

/**
 * {@link ChannelHandler} that converts {@link ByteBuf} messages into {@link Buffer} messages, and vice versa,
 * depending on configuration.
 * <p>
 * This class is useful as an intermediate handler that allows {@link ByteBuf}-based
 * {@linkplain ChannelHandler handlers} and {@link Buffer}-based {@linkplain ChannelHandler handlers} to work together
 * in the same {@linkplain io.netty5.channel.ChannelPipeline pipeline}.
 * <p>
 * It is, however, recommended that all handlers eventually be converted to use the {@link Buffer} API, as that is more
 * future-proof.
 * <p>
 * Instances of this handler are {@link Sharable} and can be added to multiple pipelines.
 * This is safe because the instances are immutable and thread-safe.
 *
 * @deprecated This handler will be moved out of Netty core and into a contrib repository, before Netty 5.0.0.Final
 * is released.
 */
@Deprecated
@Sharable
public final class BufferConversionHandler implements ChannelHandler {
    private final Conversion onRead;
    private final Conversion onWrite;
    private final Conversion onUserEvent;

    /**
     * Create a conversion handler where incoming reads, and outgoing writes, are passed through the given conversion.
     *
     * @param conversion The conversion to apply to all {@linkplain #channelRead(ChannelHandlerContext, Object) read}
     *                  and {@linkplain #write(ChannelHandlerContext, Object) write} messages.
     */
    public BufferConversionHandler(Conversion conversion) {
        this(conversion, conversion, Conversion.NONE);
    }

    /**
     * Create a conversion handler where incoming reads, and outgoing writes, are passed through their configured
     * conversions.
     *
     * @param onRead The conversion to apply to all incoming
     *              {@linkplain #channelRead(ChannelHandlerContext, Object) read} messages.
     * @param onWrite The conversion to apply to all outgoing {@linkplain #write(ChannelHandlerContext, Object)}
     *               messages.
     */
    public BufferConversionHandler(Conversion onRead, Conversion onWrite) {
        this(onRead, onWrite, Conversion.NONE);
    }

    /**
     * Create a conversion handler where incoming reads, outgoing writes, and user events, are passed through their
     * configured conversions.
     *
     * @param onRead The conversion to apply to all incoming
     *              {@linkplain #channelRead(ChannelHandlerContext, Object) read} messages.
     * @param onWrite The conversion to apply to all outgoing {@linkplain #write(ChannelHandlerContext, Object)}
     *               messages.
     * @param onUserEvent The conversion to apply to all incoming
     *                    {@linkplain #userEventTriggered(ChannelHandlerContext, Object) user events}.
     */
    public BufferConversionHandler(Conversion onRead, Conversion onWrite, Conversion onUserEvent) {
        this.onRead = onRead;
        this.onWrite = onWrite;
        this.onUserEvent = onUserEvent;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(onRead.convert(msg));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(onUserEvent.convert(evt));
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        return ctx.write(onWrite.convert(msg));
    }

    /**
     * The particular conversion operation to apply.
     * See the individual operations for their specific behaviour.
     */
    public enum Conversion {
        /**
         * Convert {@link ByteBuf} instances to {@link Buffer} instances.
         * <p>
         * Messages that are either already of a {@link Buffer} type, or of an unknown type, will pass through
         * unchanged.
         */
        BYTEBUF_TO_BUFFER {
            @Override
            public Object convert(Object msg) {
                if (msg instanceof ByteBuf) {
                    ByteBuf buf = (ByteBuf) msg;
                    return ByteBufBuffer.wrap(buf);
                }
                return msg;
            }
        },
        /**
         * Convert {@link Buffer} instances to {@link ByteBuf} instances.
         * <p>
         * Messages that are either already of a {@link ByteBuf} type, or of an unknown type, will pass through
         * unchanged.
         */
        BUFFER_TO_BYTEBUF {
            @Override
            public Object convert(Object msg) {
                if (msg instanceof Buffer) {
                    Buffer buf = (Buffer) msg;
                    return ByteBufAdaptor.intoByteBuf(buf);
                }
                return msg;
            }
        },
        /**
         * Convert any {@link Buffer}s into {@link ByteBuf}s, and any {@link ByteBuf}s into {@link Buffer}s.
         * <p>
         * Messages of unknown types are passed through unchanged.
         */
        BOTH {
            @Override
            public Object convert(Object msg) {
                if (msg instanceof ByteBuf) {
                    ByteBuf buf = (ByteBuf) msg;
                    return ByteBufBuffer.wrap(buf);
                }
                if (msg instanceof Buffer) {
                    Buffer buf = (Buffer) msg;
                    return ByteBufAdaptor.intoByteBuf(buf);
                }
                return msg;
            }
        },
        /**
         * Do not convert anything, but let the messages pass through unchanged.
         */
        NONE {
            @Override
            public Object convert(Object msg) {
                return msg;
            }
        };

        /**
         * Apply this conversion to the given message, if applicable.
         *
         * @param msg The message to maybe be converted.
         * @return The result of the conversion.
         */
        public abstract Object convert(Object msg);
    }
}
