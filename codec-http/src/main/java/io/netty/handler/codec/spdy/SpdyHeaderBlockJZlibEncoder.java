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

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.CompressionException;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyHeaderBlockJZlibEncoder extends SpdyHeaderBlockRawEncoder {

    private final Deflater z = new Deflater();

    private boolean finished;

    public SpdyHeaderBlockJZlibEncoder(
            int version, int compressionLevel, int windowBits, int memLevel) {
        super(version);
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException(
                    "windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException(
                    "memLevel: " + memLevel + " (expected: 1-9)");
        }

        int resultCode = z.deflateInit(
                compressionLevel, windowBits, memLevel, JZlib.W_ZLIB);
        if (resultCode != JZlib.Z_OK) {
            throw new CompressionException(
                    "failed to initialize an SPDY header block deflater: " + resultCode);
        } else {
            if (version < 3) {
                resultCode = z.deflateSetDictionary(SPDY2_DICT, SPDY2_DICT.length);
            } else {
                resultCode = z.deflateSetDictionary(SPDY_DICT, SPDY_DICT.length);
            }
            if (resultCode != JZlib.Z_OK) {
                throw new CompressionException(
                        "failed to set the SPDY dictionary: " + resultCode);
            }
        }
    }

    private void setInput(ByteBuf decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        z.next_in = in;
        z.next_in_index = 0;
        z.avail_in = in.length;
    }

    private void encode(ByteBuf compressed) {
        try {
            byte[] out = new byte[(int) Math.ceil(z.next_in.length * 1.001) + 12];
            z.next_out = out;
            z.next_out_index = 0;
            z.avail_out = out.length;

            int resultCode = z.deflate(JZlib.Z_SYNC_FLUSH);
            if (resultCode != JZlib.Z_OK) {
                throw new CompressionException("compression failure: " + resultCode);
            }

            if (z.next_out_index != 0) {
                compressed.writeBytes(out, 0, z.next_out_index);
            }
        } finally {
            // Deference the external references explicitly to tell the VM that
            // the allocated byte arrays are temporary so that the call stack
            // can be utilized.
            // I'm not sure if the modern VMs do this optimization though.
            z.next_in = null;
            z.next_out = null;
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
        z.deflateEnd();
        z.next_in = null;
        z.next_out = null;
    }
}
