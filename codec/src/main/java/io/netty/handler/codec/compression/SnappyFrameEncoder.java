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
import io.netty.handler.codec.MessageToByteEncoder;

import static io.netty.handler.codec.compression.Snappy.*;

/**
 * Compresses a {@link ByteBuf} using the Snappy framing format.
 *
 * See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing format</a>.
 */
public class SnappyFrameEncoder extends MessageToByteEncoder<ByteBuf> {
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
        (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59
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
                if (dataLength < MIN_COMPRESSIBLE_LENGTH) {
                    ByteBuf slice = in.readSlice(dataLength);
                    writeUnencodedChunk(slice, out, dataLength);
                    break;
                }

                out.writeInt(0);
                if (dataLength > Short.MAX_VALUE) {
                    ByteBuf slice = in.readSlice(Short.MAX_VALUE);
                    calculateAndWriteChecksum(slice, out);
                    snappy.encode(slice, out, Short.MAX_VALUE);
                    setChunkLength(out, lengthIdx);
                    dataLength -= Short.MAX_VALUE;
                } else {
                    ByteBuf slice = in.readSlice(dataLength);
                    calculateAndWriteChecksum(slice, out);
                    snappy.encode(slice, out, dataLength);
                    setChunkLength(out, lengthIdx);
                    break;
                }
            }
        } else {
            writeUnencodedChunk(in, out, dataLength);
        }
    }

    private static void writeUnencodedChunk(ByteBuf in, ByteBuf out, int dataLength) {
        out.writeByte(1);
        writeChunkLength(out, dataLength + 4);
        calculateAndWriteChecksum(in, out);
        out.writeBytes(in, dataLength);
    }

    private static void setChunkLength(ByteBuf out, int lengthIdx) {
        int chunkLength = out.writerIndex() - lengthIdx - 3;
        if (chunkLength >>> 24 != 0) {
            throw new CompressionException("compressed data too large: " + chunkLength);
        }
        out.setMediumLE(lengthIdx, chunkLength);
    }

    /**
     * Writes the 2-byte chunk length to the output buffer.
     *
     * @param out The buffer to write to
     * @param chunkLength The length to write
     */
    private static void writeChunkLength(ByteBuf out, int chunkLength) {
        out.writeMediumLE(chunkLength);
    }

    /**
     * Calculates and writes the 4-byte checksum to the output buffer
     *
     * @param slice The data to calculate the checksum for
     * @param out The output buffer to write the checksum to
     */
    private static void calculateAndWriteChecksum(ByteBuf slice, ByteBuf out) {
        out.writeIntLE(calculateChecksum(slice));
    }
}
