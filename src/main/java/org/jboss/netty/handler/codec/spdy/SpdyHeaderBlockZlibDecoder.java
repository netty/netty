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

final class SpdyHeaderBlockZlibDecoder extends SpdyHeaderBlockRawDecoder {

    private static final int DEFAULT_BUFFER_CAPACITY = 4096;

    private final Inflater decompressor = new Inflater();

    private ChannelBuffer decompressed;

    public SpdyHeaderBlockZlibDecoder(SpdyVersion spdyVersion, int maxHeaderSize) {
        super(spdyVersion, maxHeaderSize);
    }

    @Override
    void decode(ChannelBuffer encoded, SpdyHeadersFrame frame) throws Exception {
        int len = setInput(encoded);

        int numBytes;
        do {
            numBytes = decompress(frame);
        } while (!isFinished() && numBytes > 0);

        if (decompressor.getRemaining() != 0) {
            throw new SpdyProtocolException("client sent extra data beyond headers");
        }

        encoded.skipBytes(len);
    }

    private int setInput(ChannelBuffer compressed) {
        int len = compressed.readableBytes();

        if (compressed.hasArray()) {
            decompressor.setInput(compressed.array(), compressed.arrayOffset() + compressed.readerIndex(), len);
        } else {
            byte[] in = new byte[len];
            compressed.getBytes(compressed.readerIndex(), in);
            decompressor.setInput(in, 0, in.length);
        }

        return len;
    }

    private int decompress(SpdyHeadersFrame frame) throws Exception {
        ensureBuffer();
        byte[] out = decompressed.array();
        int off = decompressed.arrayOffset() + decompressed.writerIndex();
        try {
            int numBytes = decompressor.inflate(out, off, decompressed.writableBytes());
            if (numBytes == 0 && decompressor.needsDictionary()) {
                decompressor.setDictionary(SPDY_DICT);
                numBytes = decompressor.inflate(out, off, decompressed.writableBytes());
            }
            if (frame != null) {
                decompressed.writerIndex(decompressed.writerIndex() + numBytes);
                super.decode(decompressed, frame);
                decompressed.discardReadBytes();
            }

            return numBytes;
        } catch (DataFormatException e) {
            throw new SpdyProtocolException("Received invalid header block", e);
        }
    }

    private void ensureBuffer() {
        if (decompressed == null) {
            decompressed = ChannelBuffers.dynamicBuffer(DEFAULT_BUFFER_CAPACITY);
        }
        decompressed.ensureWritableBytes(1);
    }

    private void clearBuffer() {
        if (decompressed != null) {
            if (decompressed.capacity() > DEFAULT_BUFFER_CAPACITY) {
                decompressed = null;
            } else {
                decompressed.clear();
            }
        }
    }

    @Override
    void reset() {
        clearBuffer();
        super.reset();
    }

    @Override
    public void end() {
        clearBuffer();
        decompressor.end();
        super.end();
    }
}
