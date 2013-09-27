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
package org.jboss.netty.handler.codec.spdy;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

import java.util.zip.Deflater;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

class SpdyHeaderBlockZlibEncoder extends SpdyHeaderBlockRawEncoder {

    private final byte[] out = new byte[8192];
    private final Deflater compressor;

    private boolean finished;

    public SpdyHeaderBlockZlibEncoder(SpdyVersion spdyVersion, int compressionLevel) {
        super(spdyVersion);
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        compressor = new Deflater(compressionLevel);
        int version = spdyVersion.getVersion();
        if (version < 3) {
            compressor.setDictionary(SPDY2_DICT);
        } else {
            compressor.setDictionary(SPDY_DICT);
        }
    }

    private void setInput(ChannelBuffer decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        compressor.setInput(in);
    }

    private void encode(ChannelBuffer compressed) {
        int numBytes = out.length;
        while (numBytes == out.length) {
            numBytes = compressor.deflate(out, 0, out.length, Deflater.SYNC_FLUSH);
            compressed.writeBytes(out, 0, numBytes);
        }
    }

    @Override
    public synchronized ChannelBuffer encode(SpdyHeadersFrame frame) throws Exception {
        if (frame == null) {
            throw new IllegalArgumentException("frame");
        }

        if (finished) {
            return ChannelBuffers.EMPTY_BUFFER;
        }

        ChannelBuffer decompressed = super.encode(frame);
        if (decompressed.readableBytes() == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }

        ChannelBuffer compressed = ChannelBuffers.dynamicBuffer();
        setInput(decompressed);
        encode(compressed);
        return compressed;
    }

    @Override
    public synchronized void end() {
        if (finished) {
            return;
        }
        finished = true;
        compressor.end();
        super.end();
    }
}
