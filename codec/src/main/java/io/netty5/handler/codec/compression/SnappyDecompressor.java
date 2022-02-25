/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.compression;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;

import java.util.function.Supplier;

import static io.netty5.handler.codec.compression.Snappy.validateChecksum;

/**
 * Uncompresses a {@link ByteBuf} encoded with the Snappy framing format.
 *
 * See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing format</a>.
 *
 * Note that by default, validation of the checksum header in each chunk is
 * DISABLED for performance improvements. If performance is less of an issue,
 * or if you would prefer the safety that checksum validation brings, please
 * use the {@link #SnappyDecompressor(boolean)} constructor with the argument
 * set to {@code true}.
 */
public final class SnappyDecompressor implements Decompressor {
    private enum ChunkType {
        STREAM_IDENTIFIER,
        COMPRESSED_DATA,
        UNCOMPRESSED_DATA,
        RESERVED_UNSKIPPABLE,
        RESERVED_SKIPPABLE,
        CORRUPTED,
        FINISHED,
    }

    private static final int SNAPPY_IDENTIFIER_LEN = 6;
    // See https://github.com/google/snappy/blob/1.1.9/framing_format.txt#L95
    private static final int MAX_UNCOMPRESSED_DATA_SIZE = 65536 + 4;
    // See https://github.com/google/snappy/blob/1.1.9/framing_format.txt#L82
    private static final int MAX_DECOMPRESSED_DATA_SIZE = 65536;
    // See https://github.com/google/snappy/blob/1.1.9/framing_format.txt#L82
    private static final int MAX_COMPRESSED_CHUNK_SIZE = 16777216 - 1;

    private final Snappy snappy = new Snappy();
    private final boolean validateChecksums;

    private boolean started;
    private int numBytesToSkip;
    private State state = State.DECODING;

    private enum State {
        DECODING,
        FINISHED,
        CORRUPTED,
        CLOSED
    }

    /**
     * Creates a new snappy decompressor with validation of checksums
     * as specified.
     *
     * @param validateChecksums
     *        If true, the checksum field will be validated against the actual
     *        uncompressed data, and if the checksums do not match, a suitable
     *        {@link DecompressionException} will be thrown
     */
    private SnappyDecompressor(boolean validateChecksums) {
        this.validateChecksums = validateChecksums;
    }

    /**
     * Creates a new snappy decompressor factory with validation of checksums
     * turned OFF. To turn checksum validation on, please use the alternate
     * {@link #SnappyDecompressor(boolean)} constructor.
     *
     * @return the factory.
     */
    public static Supplier<SnappyDecompressor> newFactory() {
        return newFactory(false);
    }

    /**
     * Creates a new snappy decompressor factory with validation of checksums
     * as specified.
     *
     * @param validateChecksums
     *        If true, the checksum field will be validated against the actual
     *        uncompressed data, and if the checksums do not match, a suitable
     *        {@link DecompressionException} will be thrown
     * @return the factory.
     */
    public static Supplier<SnappyDecompressor> newFactory(boolean validateChecksums) {
        return () -> new SnappyDecompressor(validateChecksums);
    }

    @Override
    public ByteBuf decompress(ByteBuf in, ByteBufAllocator allocator)
            throws DecompressionException {
        switch (state) {
            case FINISHED:
            case CORRUPTED:
                return Unpooled.EMPTY_BUFFER;
            case CLOSED:
                throw new DecompressionException("Decompressor closed");
            case DECODING:
                if (numBytesToSkip != 0) {
                    // The last chunkType we detected was RESERVED_SKIPPABLE and we still have some bytes to skip.
                    int skipBytes = Math.min(numBytesToSkip, in.readableBytes());
                    in.skipBytes(skipBytes);
                    numBytesToSkip -= skipBytes;

                    // Let's return and try again.
                    return null;
                }

                int idx = in.readerIndex();
                final int inSize = in.readableBytes();
                if (inSize < 4) {
                    // We need to be at least able to read the chunk type identifier (one byte),
                    // and the length of the chunk (3 bytes) in order to proceed
                    return null;
                }

                final int chunkTypeVal = in.getUnsignedByte(idx);
                final ChunkType chunkType = mapChunkType((byte) chunkTypeVal);
                final int chunkLength = in.getUnsignedMediumLE(idx + 1);

                switch (chunkType) {
                    case STREAM_IDENTIFIER:
                        if (chunkLength != SNAPPY_IDENTIFIER_LEN) {
                            streamCorrupted("Unexpected length of stream identifier: " + chunkLength);
                        }

                        if (inSize < 4 + SNAPPY_IDENTIFIER_LEN) {
                            return null;
                        }

                        in.skipBytes(4);
                        int offset = in.readerIndex();
                        in.skipBytes(SNAPPY_IDENTIFIER_LEN);

                        checkByte(in.getByte(offset++), (byte) 's');
                        checkByte(in.getByte(offset++), (byte) 'N');
                        checkByte(in.getByte(offset++), (byte) 'a');
                        checkByte(in.getByte(offset++), (byte) 'P');
                        checkByte(in.getByte(offset++), (byte) 'p');
                        checkByte(in.getByte(offset), (byte) 'Y');

                        started = true;
                        return null;
                    case RESERVED_SKIPPABLE:
                        if (!started) {
                            streamCorrupted("Received RESERVED_SKIPPABLE tag before STREAM_IDENTIFIER");
                        }

                        in.skipBytes(4);

                        int skipBytes = Math.min(chunkLength, in.readableBytes());
                        in.skipBytes(skipBytes);
                        if (skipBytes != chunkLength) {
                            // We could skip all bytes, let's store the remaining so we can do so once we receive more
                            // data.
                            numBytesToSkip = chunkLength - skipBytes;
                        }
                        return null;
                    case RESERVED_UNSKIPPABLE:
                        // The spec mandates that reserved unskippable chunks must immediately
                        // return an error, as we must assume that we cannot decode the stream
                        // correctly
                        streamCorrupted(
                                "Found reserved unskippable chunk type: 0x" + Integer.toHexString(chunkTypeVal));
                    case UNCOMPRESSED_DATA:
                        if (!started) {
                            streamCorrupted("Received UNCOMPRESSED_DATA tag before STREAM_IDENTIFIER");
                        }
                        if (chunkLength > MAX_UNCOMPRESSED_DATA_SIZE) {
                            streamCorrupted("Received UNCOMPRESSED_DATA larger than " +
                                    MAX_UNCOMPRESSED_DATA_SIZE + " bytes");
                        }

                        if (inSize < 4 + chunkLength) {
                            return null;
                        }

                        in.skipBytes(4);
                        if (validateChecksums) {
                            int checksum = in.readIntLE();
                            try {
                                validateChecksum(checksum, in, in.readerIndex(), chunkLength - 4);
                            } catch (DecompressionException e) {
                                state = State.CORRUPTED;
                                throw e;
                            }
                        } else {
                            in.skipBytes(4);
                        }
                        return in.readRetainedSlice(chunkLength - 4);
                    case COMPRESSED_DATA:
                        if (!started) {
                            streamCorrupted("Received COMPRESSED_DATA tag before STREAM_IDENTIFIER");
                        }

                        if (chunkLength > MAX_COMPRESSED_CHUNK_SIZE) {
                            streamCorrupted("Received COMPRESSED_DATA that contains" +
                                    " chunk that exceeds " + MAX_COMPRESSED_CHUNK_SIZE + " bytes");
                        }

                        if (inSize < 4 + chunkLength) {
                            return null;
                        }

                        in.skipBytes(4);
                        int checksum = in.readIntLE();

                        int uncompressedSize = snappy.getPreamble(in);
                        if (uncompressedSize > MAX_DECOMPRESSED_DATA_SIZE) {
                            streamCorrupted("Received COMPRESSED_DATA that contains" +
                                    " uncompressed data that exceeds " + MAX_DECOMPRESSED_DATA_SIZE + " bytes");
                        }

                        ByteBuf uncompressed = allocator.buffer(uncompressedSize, MAX_DECOMPRESSED_DATA_SIZE);
                        try {
                            if (validateChecksums) {
                                int oldWriterIndex = in.writerIndex();
                                try {
                                    in.writerIndex(in.readerIndex() + chunkLength - 4);
                                    snappy.decode(in, uncompressed);
                                } finally {
                                    in.writerIndex(oldWriterIndex);
                                }
                                try {
                                    validateChecksum(checksum, uncompressed, 0, uncompressed.writerIndex());
                                } catch (DecompressionException e) {
                                    state = State.CORRUPTED;
                                    throw e;
                                }
                            } else {
                                snappy.decode(in.readSlice(chunkLength - 4), uncompressed);
                            }
                            snappy.reset();
                            ByteBuf buffer = uncompressed;
                            uncompressed = null;
                            return buffer;
                        } finally {
                            if (uncompressed != null) {
                                uncompressed.release();
                            }
                        }
                    default:
                        streamCorrupted("Unexpected state");
                        return null;
                }
            default:
                throw new IllegalStateException();
        }
    }

    private static void checkByte(byte actual, byte expect) {
        if (actual != expect) {
            throw new DecompressionException("Unexpected stream identifier contents. Mismatched snappy " +
                    "protocol version?");
        }
    }

    /**
     * Decodes the chunk type from the type tag byte.
     *
     * @param type The tag byte extracted from the stream
     * @return The appropriate {@link ChunkType}, defaulting to {@link ChunkType#RESERVED_UNSKIPPABLE}
     */
    private static ChunkType mapChunkType(byte type) {
        if (type == 0) {
            return ChunkType.COMPRESSED_DATA;
        } else if (type == 1) {
            return ChunkType.UNCOMPRESSED_DATA;
        } else if (type == (byte) 0xff) {
            return ChunkType.STREAM_IDENTIFIER;
        } else if ((type & 0x80) == 0x80) {
            return ChunkType.RESERVED_SKIPPABLE;
        } else {
            return ChunkType.RESERVED_UNSKIPPABLE;
        }
    }

    private void streamCorrupted(String message) {
        state = State.CORRUPTED;
        throw new DecompressionException(message);
    }

    @Override
    public boolean isFinished() {
        switch (state) {
            case FINISHED:
            case CLOSED:
            case CORRUPTED:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public void close() {
        state = State.FINISHED;
    }
}
