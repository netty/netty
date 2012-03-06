/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.buffer.ChannelBuffer;
import io.netty.handler.codec.compression.CompressionException;
import io.netty.util.internal.jzlib.JZlib;
import io.netty.util.internal.jzlib.ZStream;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyHeaderBlockJZlibDecompressor extends SpdyHeaderBlockDecompressor {

    private final byte[] out = new byte[8192];
    private final ZStream z = new ZStream();

    public SpdyHeaderBlockJZlibDecompressor() {
        int resultCode;
        resultCode = z.inflateInit(JZlib.W_ZLIB);
        if (resultCode != JZlib.Z_OK) {
            throw new CompressionException("ZStream initialization failure");
        }
        z.next_out = out;
    }

    @Override
    public void setInput(ChannelBuffer compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.readBytes(in);
        z.next_in = in;
        z.next_in_index = 0;
        z.avail_in = in.length;
    }

    @Override
    public void decode(ChannelBuffer decompressed) {
        z.next_out_index = 0;
        z.avail_out = out.length;

        loop: for (;;) {
            // Decompress 'in' into 'out'
            int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
            if (z.next_out_index > 0) {
                decompressed.writeBytes(out, 0, z.next_out_index);
                z.avail_out = out.length;
            }
            z.next_out_index = 0;

            switch (resultCode) {
            case JZlib.Z_NEED_DICT:
                resultCode = z.inflateSetDictionary(SPDY_DICT, SPDY_DICT.length);
                if (resultCode != JZlib.Z_OK) {
                    throw new CompressionException("failed to set the dictionary: " + resultCode);
                }
                break;
            case JZlib.Z_STREAM_END:
                // Do not decode anymore.
                z.inflateEnd();
                break loop;
            case JZlib.Z_OK:
                break;
            case JZlib.Z_BUF_ERROR:
                if (z.avail_in <= 0) {
                    break loop;
                }
                break;
            default:
                throw new CompressionException("decompression failure: " + resultCode);
            }
        }

        if (z.next_out_index > 0) {
            decompressed.writeBytes(out, 0, z.next_out_index);
        }
    }

    @Override
    public void end() {
        z.inflateEnd();
        z.next_in = null;
        z.next_out = null;
    }
}
