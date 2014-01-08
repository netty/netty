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

import org.jboss.netty.buffer.ChannelBuffer;

import static org.jboss.netty.handler.codec.spdy.SpdyCodecUtil.*;

public class SpdyHeaderBlockRawDecoder extends SpdyHeaderBlockDecoder {

    private static final int LENGTH_FIELD_SIZE = 4;

    private final int maxHeaderSize;

    // Header block decoding fields
    private int headerSize;
    private int numHeaders = -1;

    public SpdyHeaderBlockRawDecoder(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    private int readLengthField(ChannelBuffer buffer) {
        int length = getSignedInt(buffer, buffer.readerIndex());
        buffer.skipBytes(LENGTH_FIELD_SIZE);
        return length;
    }

    @Override
    void decode(ChannelBuffer encoded, SpdyHeadersFrame frame) throws Exception {
        if (encoded == null) {
            throw new NullPointerException("encoded");
        }
        if (frame == null) {
            throw new NullPointerException("frame");
        }

        if (numHeaders == -1) {
            // Read number of Name/Value pairs
            if (encoded.readableBytes() < LENGTH_FIELD_SIZE) {
                return;
            }
            numHeaders = readLengthField(encoded);
            if (numHeaders < 0) {
                frame.setInvalid();
                return;
            }
        }

        while (numHeaders > 0) {
            int headerSize = this.headerSize;
            encoded.markReaderIndex();

            // Try to read length of name
            if (encoded.readableBytes() < LENGTH_FIELD_SIZE) {
                encoded.resetReaderIndex();
                return;
            }
            int nameLength = readLengthField(encoded);

            // Recipients of a zero-length name must issue a stream error
            if (nameLength <= 0) {
                frame.setInvalid();
                return;
            }
            headerSize += nameLength;
            if (headerSize > maxHeaderSize) {
                frame.setTruncated();
                return;
            }

            // Try to read name
            if (encoded.readableBytes() < nameLength) {
                encoded.resetReaderIndex();
                return;
            }
            byte[] nameBytes = new byte[nameLength];
            encoded.readBytes(nameBytes);
            String name = new String(nameBytes, "UTF-8");

            // Check for identically named headers
            if (frame.headers().contains(name)) {
                frame.setInvalid();
                return;
            }

            // Try to read length of value
            if (encoded.readableBytes() < LENGTH_FIELD_SIZE) {
                encoded.resetReaderIndex();
                return;
            }
            int valueLength = readLengthField(encoded);

            // Recipients of illegal value fields must issue a stream error
            if (valueLength < 0) {
                frame.setInvalid();
                return;
            }

            // SPDY/3 allows zero-length (empty) header values
            if (valueLength == 0) {
                frame.headers().add(name, "");
                numHeaders --;
                this.headerSize = headerSize;
                continue;
            }

            headerSize += valueLength;
            if (headerSize > maxHeaderSize) {
                frame.setTruncated();
                return;
            }

            // Try to read value
            if (encoded.readableBytes() < valueLength) {
                encoded.resetReaderIndex();
                return;
            }
            byte[] valueBytes = new byte[valueLength];
            encoded.readBytes(valueBytes);

            // Add Name/Value pair to headers
            int index = 0;
            int offset = 0;
            while (index < valueLength) {
                while (index < valueBytes.length && valueBytes[index] != (byte) 0) {
                    index ++;
                }
                if (index < valueBytes.length && valueBytes[index + 1] == (byte) 0) {
                    // Received multiple, in-sequence NULL characters
                    // Recipients of illegal value fields must issue a stream error
                    frame.setInvalid();
                    return;
                }
                String value = new String(valueBytes, offset, index - offset, "UTF-8");

                try {
                    frame.headers().add(name, value);
                } catch (IllegalArgumentException e) {
                    // Name contains NULL or non-ascii characters
                    frame.setInvalid();
                    return;
                }
                index ++;
                offset = index;
            }
            numHeaders --;
            this.headerSize = headerSize;
        }
    }

    @Override
    void reset() {
        // Initialize header block decoding fields
        headerSize = 0;
        numHeaders = -1;
    }

    @Override
    void end() {
    }
}
