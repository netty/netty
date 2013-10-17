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

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

class SpdyHeaderBlockZlibDecoder extends SpdyHeaderBlockRawDecoder {

    private final byte[] out = new byte[8192];
    private final Inflater decompressor = new Inflater();

    private ChannelBuffer decompressed;

    public SpdyHeaderBlockZlibDecoder(SpdyVersion spdyVersion, int maxHeaderSize) {
        super(spdyVersion, maxHeaderSize);
    }

    @Override
    void decode(ChannelBuffer encoded, SpdyHeadersFrame frame) throws Exception {
        setInput(encoded);

        int numBytes;
        do {
            numBytes = decompress(frame);
        } while (!decompressed.readable() && numBytes > 0);
    }

    private void setInput(ChannelBuffer compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.readBytes(in);
        decompressor.setInput(in);
    }

    private int decompress(SpdyHeadersFrame frame) throws Exception {
        if (decompressed == null) {
            decompressed = ChannelBuffers.dynamicBuffer(8192);
        }

        try {
            int numBytes = decompressor.inflate(out);
            if (numBytes == 0 && decompressor.needsDictionary()) {
                decompressor.setDictionary(SPDY_DICT);
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
    void reset() {
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
