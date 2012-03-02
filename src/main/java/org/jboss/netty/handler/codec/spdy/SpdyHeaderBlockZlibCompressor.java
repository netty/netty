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

import java.util.zip.Deflater;

import org.jboss.netty.buffer.ChannelBuffer;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyHeaderBlockZlibCompressor extends SpdyHeaderBlockCompressor {

    private final byte[] out = new byte[8192];
    private final Deflater compressor;

    public SpdyHeaderBlockZlibCompressor(int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        compressor = new Deflater(compressionLevel);
        compressor.setDictionary(SPDY_DICT);
    }

    @Override
    public void setInput(ChannelBuffer decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        compressor.setInput(in);
    }

    @Override
    public void encode(ChannelBuffer compressed) {
        while (!compressor.needsInput()) {
            int numBytes = compressor.deflate(out, 0, out.length, Deflater.SYNC_FLUSH);
            compressed.writeBytes(out, 0, numBytes);
        }
    }

    @Override
    public void end() {
        compressor.end();
    }
}
