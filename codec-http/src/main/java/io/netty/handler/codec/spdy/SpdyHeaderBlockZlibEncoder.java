/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.zip.Deflater;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyHeaderBlockZlibEncoder extends SpdyHeaderBlockRawEncoder {

    private final byte[] out = new byte[8192];
    private final Deflater compressor;

    private boolean finished;

    public SpdyHeaderBlockZlibEncoder(int version, int compressionLevel) {
        super(version);
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        compressor = new Deflater(compressionLevel);
        if (version < 3) {
            compressor.setDictionary(SPDY2_DICT);
        } else {
            compressor.setDictionary(SPDY_DICT);
        }
    }

    private void setInput(ByteBuf decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        compressor.setInput(in);
    }

    private void encode(ByteBuf compressed) {
        int numBytes = out.length;
        while (numBytes == out.length) {
            numBytes = compressor.deflate(out, 0, out.length, Deflater.SYNC_FLUSH);
            compressed.writeBytes(out, 0, numBytes);
        }
    }

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, SpdyHeadersFrame frame) throws Exception {
        if (frame == null) {
            throw new IllegalArgumentException("frame");
        }

        if (finished) {
            return Unpooled.EMPTY_BUFFER;
        }

        ByteBuf decompressed = super.encode(ctx, frame);
        if (decompressed.readableBytes() == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        ByteBuf compressed = ctx.alloc().buffer();
        setInput(decompressed);
        encode(compressed);
        return compressed;
    }

    @Override
    public void end() {
        if (finished) {
            return;
        }
        finished = true;
        compressor.end();
        super.end();
    }
}
