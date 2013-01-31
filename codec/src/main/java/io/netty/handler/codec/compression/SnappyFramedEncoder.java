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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToByteEncoder;

import static io.netty.handler.codec.compression.SnappyChecksumUtil.*;

/**
 * Compresses a {@link ByteBuf} using the Snappy framing format.
 *
 * See http://code.google.com/p/snappy/source/browse/trunk/framing_format.txt
 */
public class SnappyFramedEncoder extends ByteToByteEncoder {
    /**
     * The minimum amount that we'll consider actually attempting to compress.
     * This value is preamble + the minimum length our Snappy service will
     * compress (instead of just emitting a literal).
     */
    private static final int MIN_COMPRESSIBLE_LENGTH = 18;

    /**
     * All streams should start with the "Stream identifier", containing chunk
     * type 0xff, a length field of 0x6, and 'sNaPpY' in ASCII.
     */
    private static final byte[] STREAM_START = {
        -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59
    };

    private final Snappy snappy = new Snappy();
    private boolean started;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        if (!started) {
            started = true;
            out.writeBytes(STREAM_START);
        }

        final int chunkLength = in.readableBytes();
        if (chunkLength > MIN_COMPRESSIBLE_LENGTH) {
            // If we have lots of available data, break it up into smaller chunks
            int numberOfChunks = 1 + chunkLength / Short.MAX_VALUE;
            for (int i = 0; i < numberOfChunks; i++) {
                int subChunkLength = Math.min(Short.MAX_VALUE, chunkLength);
                out.writeByte(0);
                writeChunkLength(out, subChunkLength);
                ByteBuf slice = in.slice();
                calculateAndWriteChecksum(slice, out);

                snappy.encode(slice, out, subChunkLength);
            }
        } else {
            out.writeByte(1);
            writeChunkLength(out, chunkLength);
            ByteBuf slice = in.slice();
            calculateAndWriteChecksum(slice, out);
            out.writeBytes(slice);
        }

        in.readerIndex(in.readerIndex() + chunkLength);
    }

    /**
     * Writes the 2-byte chunk length to the output buffer.
     *
     * @param out The buffer to write to
     * @param chunkLength The length to write
     */
    private static void writeChunkLength(ByteBuf out, int chunkLength) {
        out.writeByte(chunkLength & 0x0ff);
        out.writeByte(chunkLength >> 8 & 0x0ff);
    }

    /**
     * Calculates and writes the 4-byte checksum to the output buffer
     *
     * @param slice The data to calculate the checksum for
     * @param out The output buffer to write the checksum to
     */
    private static void calculateAndWriteChecksum(ByteBuf slice, ByteBuf out) {
        int checksum = calculateChecksum(slice);
        out.writeByte(checksum & 0x0ff);
        out.writeByte(checksum >> 8 & 0x0ff);
        out.writeByte(checksum >> 16 & 0x0ff);
        out.writeByte(checksum >> 24 & 0x0ff);
    }
}
