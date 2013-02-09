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
import io.netty.handler.codec.ByteToByteDecoder;

import java.nio.charset.Charset;
import java.util.Arrays;

import static io.netty.handler.codec.compression.SnappyChecksumUtil.*;

/**
 * Uncompresses a {@link ByteBuf} encoded with the Snappy framing format.
 *
 * See http://code.google.com/p/snappy/source/browse/trunk/framing_format.txt
 */
public class SnappyFramedDecoder extends ByteToByteDecoder {
    enum ChunkType {
        STREAM_IDENTIFIER,
        COMPRESSED_DATA,
        UNCOMPRESSED_DATA,
        RESERVED_UNSKIPPABLE,
        RESERVED_SKIPPABLE
    }

    private static final byte[] SNAPPY = "sNaPpY".getBytes(Charset.forName("US-ASCII"));

    private final Snappy snappy = new Snappy();
    private final boolean validateChecksums;

    private boolean started;
    private boolean corrupted;

    /**
     * Creates a new snappy-framed decoder with validation of checksums
     * turned off
     */
    public SnappyFramedDecoder() {
        this(false);
    }

    /**
     * Creates a new snappy-framed decoder with validation of checksums
     * as specified.
     *
     * @param validateChecksums
     *        If true, the checksum field will be validated against the actual
     *        uncompressed data, and if the checksums do not match, a suitable
     *        {@link CompressionException} will be thrown
     */
    public SnappyFramedDecoder(boolean validateChecksums) {
        this.validateChecksums = validateChecksums;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (corrupted) {
            in.skipBytes(in.readableBytes());
            return;
        }

        try {
            int idx = in.readerIndex();
            final int inSize = in.writerIndex() - idx;
            if (inSize < 4) {
                // We need to be at least able to read the chunk type identifier (one byte),
                // and the length of the chunk (3 bytes) in order to proceed
                return;
            }

            final int chunkTypeVal = in.getUnsignedByte(idx);
            final ChunkType chunkType = mapChunkType((byte) chunkTypeVal);
            final int chunkLength = in.getUnsignedByte(idx + 1)
                                  | in.getUnsignedByte(idx + 2) << 8
                                  | in.getUnsignedByte(idx + 3) << 16;

            switch (chunkType) {
                case STREAM_IDENTIFIER:
                    if (chunkLength != SNAPPY.length) {
                        throw new CompressionException("Unexpected length of stream identifier: " + chunkLength);
                    }

                    if (inSize < 4 + SNAPPY.length) {
                        break;
                    }

                    byte[] identifier = new byte[chunkLength];
                    in.skipBytes(4).readBytes(identifier);

                    if (!Arrays.equals(identifier, SNAPPY)) {
                        throw new CompressionException("Unexpected stream identifier contents. Mismatched snappy " +
                                "protocol version?");
                    }

                    started = true;
                    break;
                case RESERVED_SKIPPABLE:
                    if (!started) {
                        throw new CompressionException("Received RESERVED_SKIPPABLE tag before STREAM_IDENTIFIER");
                    }

                    if (inSize < 4 + chunkLength) {
                        // TODO: Don't keep skippable bytes
                        return;
                    }

                    in.skipBytes(4 + chunkLength);
                    break;
                case RESERVED_UNSKIPPABLE:
                    // The spec mandates that reserved unskippable chunks must immediately
                    // return an error, as we must assume that we cannot decode the stream
                    // correctly
                    throw new CompressionException(
                            "Found reserved unskippable chunk type: 0x" + Integer.toHexString(chunkTypeVal));
                case UNCOMPRESSED_DATA:
                    if (!started) {
                        throw new CompressionException("Received UNCOMPRESSED_DATA tag before STREAM_IDENTIFIER");
                    }
                    if (chunkLength > 65536 + 4) {
                        throw new CompressionException("Received UNCOMPRESSED_DATA larger than 65540 bytes");
                    }

                    if (inSize < 4 + chunkLength) {
                        return;
                    }

                    in.skipBytes(4);
                    if (validateChecksums) {
                        int checksum = in.readUnsignedByte()
                                     | in.readUnsignedByte() << 8
                                     | in.readUnsignedByte() << 16
                                     | in.readUnsignedByte() << 24;
                        ByteBuf data = in.readSlice(chunkLength - 4);
                        validateChecksum(data, checksum);
                        out.writeBytes(data);
                    } else {
                        in.skipBytes(4);
                        in.readBytes(out, chunkLength - 4);
                    }
                    break;
                case COMPRESSED_DATA:
                    if (!started) {
                        throw new CompressionException("Received COMPRESSED_DATA tag before STREAM_IDENTIFIER");
                    }

                    if (inSize < 4 + chunkLength) {
                        return;
                    }

                    in.skipBytes(4);
                    int checksum = in.readUnsignedByte()
                                 | in.readUnsignedByte() << 8
                                 | in.readUnsignedByte() << 16
                                 | in.readUnsignedByte() << 24;
                    if (validateChecksums) {
                        ByteBuf uncompressed = ctx.alloc().buffer();
                        snappy.decode(in, uncompressed, chunkLength);
                        validateChecksum(uncompressed, checksum);
                    } else {
                        snappy.decode(in, out, chunkLength);
                    }
                    snappy.reset();
                    break;
            }
        } catch (Exception e) {
            corrupted = true;
            throw e;
        }
    }

    /**
     * Decodes the chunk type from the type tag byte.
     *
     * @param type The tag byte extracted from the stream
     * @return The appropriate {@link ChunkType}, defaulting to
     *     {@link ChunkType#RESERVED_UNSKIPPABLE}
     */
    static ChunkType mapChunkType(byte type) {
        if (type == 0) {
            return ChunkType.COMPRESSED_DATA;
        } else if (type == 1) {
            return ChunkType.UNCOMPRESSED_DATA;
        } else if (type == -0x80) {
            return ChunkType.STREAM_IDENTIFIER;
        } else if ((type & 0x80) == 0x80) {
            return ChunkType.RESERVED_SKIPPABLE;
        } else {
            return ChunkType.RESERVED_UNSKIPPABLE;
        }
    }
}
