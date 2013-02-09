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
        -0x80, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59
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

        int dataLength = in.readableBytes();
        if (dataLength > MIN_COMPRESSIBLE_LENGTH) {
            for (;;) {
                final int lengthIdx = out.writerIndex() + 1;
                out.writeInt(0);
                if (dataLength > 65536) {
                    ByteBuf slice = in.readSlice(65536);
                    calculateAndWriteChecksum(slice, out);
                    snappy.encode(slice, out, 65536);
                    setChunkLength(out, lengthIdx);
                    dataLength -= 65536;
                } else {
                    ByteBuf slice = in.readSlice(dataLength);
                    calculateAndWriteChecksum(slice, out);
                    snappy.encode(slice, out, dataLength);
                    setChunkLength(out, lengthIdx);
                    break;
                }
            }
        } else {
            out.writeByte(1);
            writeChunkLength(out, dataLength + 4);
            calculateAndWriteChecksum(in, out);
            out.writeBytes(in, dataLength);
        }
    }

    private static void setChunkLength(ByteBuf out, int lengthIdx) {
        int chunkLength = out.writerIndex() - lengthIdx - 3;
        if (chunkLength >>> 24 != 0) {
            throw new CompressionException("compressed data too large: " + chunkLength);
        }
        out.setByte(lengthIdx, chunkLength & 0xff);
        out.setByte(lengthIdx + 1, chunkLength >>> 8 & 0xff);
        out.setByte(lengthIdx + 2, chunkLength >>> 16 & 0xff);
    }

    /**
     * Writes the 2-byte chunk length to the output buffer.
     *
     * @param out The buffer to write to
     * @param chunkLength The length to write
     */
    private static void writeChunkLength(ByteBuf out, int chunkLength) {
        out.writeByte(chunkLength & 0xff);
        out.writeByte(chunkLength >>> 8 & 0xff);
        out.writeByte(chunkLength >>> 16 & 0xff);
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
        out.writeByte(checksum >>> 8 & 0x0ff);
        out.writeByte(checksum >>> 16 & 0x0ff);
        out.writeByte(checksum >>> 24);
    }
}
