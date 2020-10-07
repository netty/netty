/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.dec.Decoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * Brotli Decoder (Decompressor)
 */
public class BrotliDecoder extends ByteToMessageDecoder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BrotliDecoder.class);

    static {
        logger.info("Brotli Loader Status: {}", BrotliLoader.isBrotliAvailable());
    }

    /**
     * Aggregate up to a single block
     */
    private ByteBuf aggregatingBuf;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            out.add(in);
            return;
        }

        if (aggregatingBuf == null) {
            aggregatingBuf = ctx.alloc().buffer();
        }

        aggregatingBuf.writeBytes(in);

        byte[] compressedData = ByteBufUtil.getBytes(aggregatingBuf, aggregatingBuf.readerIndex(),
                aggregatingBuf.readableBytes(), false);
        byte[] decompressedData = Decoder.decompress(compressedData);

        if (decompressedData != null) {
            aggregatingBuf.clear();
            ByteBuf byteBuf = ctx.alloc().buffer();
            byteBuf.writeBytes(decompressedData);
            out.add(byteBuf);
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        if (aggregatingBuf.refCnt() > 0) {
            aggregatingBuf.release();
        }
        aggregatingBuf = null;
    }
}
