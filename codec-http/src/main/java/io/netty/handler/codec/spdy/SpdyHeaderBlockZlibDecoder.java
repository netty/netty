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

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

class SpdyHeaderBlockZlibDecoder extends SpdyHeaderBlockRawDecoder {

    private final int version;
    private final byte[] out = new byte[8192];
    private final Inflater decompressor = new Inflater();

    private ByteBuf decompressed;

    public SpdyHeaderBlockZlibDecoder(SpdyVersion version, int maxHeaderSize) {
        super(version, maxHeaderSize);
        this.version = version.getVersion();
    }

    @Override
    void decode(ByteBuf encoded, SpdyHeadersFrame frame) throws Exception {
        setInput(encoded);

        int numBytes;
        do {
            numBytes = decompress(frame);
        } while (!decompressed.isReadable() && numBytes > 0);
    }

    private void setInput(ByteBuf compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.readBytes(in);
        decompressor.setInput(in);
    }

    private int decompress(SpdyHeadersFrame frame) throws Exception {
        if (decompressed == null) {
            decompressed = Unpooled.buffer(8192);
        }
        try {
            int numBytes = decompressor.inflate(out);
            if (numBytes == 0 && decompressor.needsDictionary()) {
                if (version < 3) {
                    decompressor.setDictionary(SPDY2_DICT);
                } else {
                    decompressor.setDictionary(SPDY_DICT);
                }
                numBytes = decompressor.inflate(out);
            }
            if (frame != null) {
                decompressed.writeBytes(out, 0, numBytes);
                super.decode(decompressed, frame);
                decompressed.discardReadBytes();
            }
            return numBytes;
        } catch (DataFormatException e) {
            throw new SpdyProtocolException(
                    "Received invalid header block", e);
        }
    }

    @Override
    public void reset() {
        decompressed = null;
        super.reset();
    }

    @Override
    public void end() {
        decompressed = null;
        decompressor.end();
        super.end();
    }
}
