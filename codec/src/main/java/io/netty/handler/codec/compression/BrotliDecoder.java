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
import com.nixxcode.jvmbrotli.dec.BrotliInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
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

    private ByteBufOutputStream byteBufOutputStream;
    private ByteBuf byteBuf;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (byteBufOutputStream == null) {
            byteBuf = ctx.alloc().buffer();
            byteBufOutputStream = new ByteBufOutputStream(byteBuf);
        }

        BrotliInputStream brotliInputStream = new BrotliInputStream(new ByteBufInputStream(in));

        int read = brotliInputStream.read();
        while (read > -1) {
            byteBufOutputStream.write(read);
            read = brotliInputStream.read();
        }

        byteBufOutputStream.flush();
        brotliInputStream.close();

        out.add(byteBuf);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        byteBufOutputStream.close();
        if (byteBuf.refCnt() > 0) {
            byteBuf.release();
        }
    }
}
