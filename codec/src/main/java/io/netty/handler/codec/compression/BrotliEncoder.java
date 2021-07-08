/*
 * Copyright 2021 The Netty Project
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

import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

/**
 * Compress a {@link ByteBuf} with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
@ChannelHandler.Sharable
public final class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private final Encoder.Parameters parameters;

    /**
     * Create a new {@link BrotliEncoder} Instance
     * with {@link Encoder.Parameters#setQuality(int)} set to 4
     * and {@link Encoder.Parameters#setMode(Encoder.Mode)} set to {@link Encoder.Mode#TEXT}
     */
    public BrotliEncoder() {
        this(BrotliOptions.DEFAULT);
    }

    /**
     * Create a new {@link BrotliEncoder} Instance
     *
     * @param parameters {@link Encoder.Parameters} Instance
     */
    public BrotliEncoder(Encoder.Parameters parameters) {
        this.parameters = ObjectUtil.checkNotNull(parameters, "Parameters");
    }

    /**
     * Create a new {@link BrotliEncoder} Instance
     *
     * @param brotliOptions {@link BrotliOptions} to use.
     */
    public BrotliEncoder(BrotliOptions brotliOptions) {
        this(brotliOptions.parameters());
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        // NO-OP
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) throws Exception {
        // If ByteBuf is unreadable, then return EMPTY_BUFFER.
        if (!msg.isReadable()) {
            return Unpooled.EMPTY_BUFFER;
        }

        try {
            byte[] uncompressed = ByteBufUtil.getBytes(msg, msg.readerIndex(), msg.readableBytes(), false);
            byte[] compressed = Encoder.compress(uncompressed, parameters);
            if (preferDirect) {
                ByteBuf out = ctx.alloc().ioBuffer(compressed.length);
                out.writeBytes(compressed);
                return out;
            } else {
                return Unpooled.wrappedBuffer(compressed);
            }
        } catch (Exception e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }
    }
}
