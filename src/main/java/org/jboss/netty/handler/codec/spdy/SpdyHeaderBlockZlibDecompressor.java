/*
 * Copyright 2012 The Netty Project
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

class SpdyHeaderBlockZlibDecompressor extends SpdyHeaderBlockDecompressor {

    private final int version;
    private final byte[] out = new byte[8192];
    private final Inflater decompressor = new Inflater();

    public SpdyHeaderBlockZlibDecompressor(int version) {
        if (version < SPDY_MIN_VERSION || version > SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported version: " + version);
        }
        this.version = version;
    }

    @Override
    public void setInput(ChannelBuffer compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.readBytes(in);
        decompressor.setInput(in);
    }

    @Override
    public int decode(ChannelBuffer decompressed) throws Exception {
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
            decompressed.writeBytes(out, 0, numBytes);
            return numBytes;
        } catch (DataFormatException e) {
            throw new SpdyProtocolException(
                    "Received invalid header block", e);
        }
    }

    @Override
    public void end() {
        decompressor.end();
    }
}
