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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.ObjectUtil;

/**
 * Compress a {@link ByteBuf} with with the brotli format.
 *
 * See <a href="https://github.com/google/brotli">brotli</a>.
 */
public final class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private final Encoder.Parameters parameters;

    /**
     * Create a new {@link BrotliEncoder} Instance with Quality set to 4.
     */
    public BrotliEncoder() {
        parameters = new Encoder.Parameters().setQuality(4);
    }

    /**
     * Create a new {@link BrotliEncoder} Instance
     *
     * @param parameters {@link Encoder.Parameters} Instance
     */
    public BrotliEncoder(Encoder.Parameters parameters) {
        this.parameters = ObjectUtil.checkNotNull(parameters, "Parameters");
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        // If ByteBuf is unreadable, then just ignore it.
        if (!msg.isReadable()) {
            return;
        }

        byte[] uncompressed = ByteBufUtil.getBytes(msg, msg.readerIndex(), msg.readableBytes(), false);
        out.writeBytes(Encoder.compress(uncompressed, parameters));
    }
}
