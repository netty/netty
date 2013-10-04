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

    private int setInput(ChannelBuffer decompressed) {
        int len = decompressed.readableBytes();

        if (decompressed.hasArray()) {
            compressor.setInput(decompressed.array(), decompressed.arrayOffset() + decompressed.readerIndex(), len);
        } else {
            byte[] in = new byte[len];
            decompressed.getBytes(decompressed.readerIndex(), in);
            compressor.setInput(in, 0, in.length);
        }

        return len;
    }

    private void encode(ChannelBuffer compressed) {
        while (compressInto(compressed)) {
            // Although unlikely, it's possible that the compressed size is larger than the decompressed size
            compressed.ensureWritableBytes(compressed.capacity() << 1);
        }
    }

    private boolean compressInto(ChannelBuffer compressed) {
        byte[] out = compressed.array();
        int off = compressed.arrayOffset() + compressed.writerIndex();
        int toWrite = compressed.writableBytes();
        int numBytes = compressor.deflate(out, off, toWrite, Deflater.SYNC_FLUSH);
        compressed.writerIndex(compressed.writerIndex() + numBytes);
        return numBytes == toWrite;
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

        ChannelBuffer compressed = ChannelBuffers.dynamicBuffer(decompressed.readableBytes());
        int len = setInput(decompressed);
        encode(compressed);
        decompressed.skipBytes(len);

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
