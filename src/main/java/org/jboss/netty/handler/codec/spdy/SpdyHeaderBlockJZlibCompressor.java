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
package org.jboss.netty.handler.codec.spdy;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.compression.CompressionException;
import org.jboss.netty.util.internal.jzlib.JZlib;
import org.jboss.netty.util.internal.jzlib.ZStream;

class SpdyHeaderBlockJZlibCompressor extends SpdyHeaderBlockCompressor {

    private final ZStream z = new ZStream();

    public SpdyHeaderBlockJZlibCompressor(int compressionLevel, int windowBits, int memLevel) {
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
            resultCode = z.deflateSetDictionary(SPDY_DICT, SPDY_DICT.length);
            if (resultCode != JZlib.Z_OK) {
                throw new CompressionException(
                        "failed to set the SPDY dictionary: " + resultCode);
            }
        }
    }

    @Override
    public void setInput(ChannelBuffer decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        z.next_in = in;
        z.next_in_index = 0;
        z.avail_in = in.length;
    }

    @Override
    public void encode(ChannelBuffer compressed) {
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
    public void end() {
        z.deflateEnd();
        z.next_in = null;
        z.next_out = null;
    }
}
