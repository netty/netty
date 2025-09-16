package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import static io.netty.handler.codec.compression.Snappy.validateChecksum;

public class SnappyFrameDecompressor extends InputBufferingDecompressor {

    private enum ChunkType {
        STREAM_IDENTIFIER,
        COMPRESSED_DATA,
        UNCOMPRESSED_DATA,
        RESERVED_UNSKIPPABLE,
        RESERVED_SKIPPABLE
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

    private ByteBuf pendingOutput;

    SnappyFrameDecompressor(Builder builder) {
        super(builder.allocator);
        this.validateChecksums = builder.validateChecksums;
    }

    @Override
    public void close() {
        super.close();
        if (pendingOutput != null) {
            pendingOutput.release();
        }
    }

    @Override
    public Status status() throws DecompressionException {
        return pendingOutput == null ? Status.NEED_INPUT : Status.NEED_OUTPUT;
    }

    @Override
    public void endOfInput() throws DecompressionException {
    }

    @Override
    void processInput(ByteBuf in) throws DecompressionException {
        while (in.isReadable()) {
            if (numBytesToSkip != 0) {
                // The last chunkType we detected was RESERVED_SKIPPABLE and we still have some bytes to skip.
                int skipBytes = Math.min(numBytesToSkip, in.readableBytes());
                in.skipBytes(skipBytes);
                numBytesToSkip -= skipBytes;

                // Let's return and try again.
                continue;
            }

            int idx = in.readerIndex();
            final int inSize = in.readableBytes();
            if (inSize < 4) {
                // We need to be at least able to read the chunk type identifier (one byte),
                // and the length of the chunk (3 bytes) in order to proceed
                return;
            }

            final int chunkTypeVal = in.getUnsignedByte(idx);
            final ChunkType chunkType = mapChunkType((byte) chunkTypeVal);
            final int chunkLength = in.getUnsignedMediumLE(idx + 1);

            switch (chunkType) {
                case STREAM_IDENTIFIER:
                    if (chunkLength != SNAPPY_IDENTIFIER_LEN) {
                        throw new DecompressionException("Unexpected length of stream identifier: " + chunkLength);
                    }

                    if (inSize < 4 + SNAPPY_IDENTIFIER_LEN) {
                        break;
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
                    break;
                case RESERVED_SKIPPABLE:
                    if (!started) {
                        throw new DecompressionException("Received RESERVED_SKIPPABLE tag before STREAM_IDENTIFIER");
                    }

                    in.skipBytes(4);

                    int skipBytes = Math.min(chunkLength, in.readableBytes());
                    in.skipBytes(skipBytes);
                    if (skipBytes != chunkLength) {
                        // We could skip all bytes, let's store the remaining so we can do so once we receive more
                        // data.
                        numBytesToSkip = chunkLength - skipBytes;
                    }
                    break;
                case RESERVED_UNSKIPPABLE:
                    // The spec mandates that reserved unskippable chunks must immediately
                    // return an error, as we must assume that we cannot decode the stream
                    // correctly
                    throw new DecompressionException(
                            "Found reserved unskippable chunk type: 0x" + Integer.toHexString(chunkTypeVal));
                case UNCOMPRESSED_DATA:
                    if (!started) {
                        throw new DecompressionException("Received UNCOMPRESSED_DATA tag before STREAM_IDENTIFIER");
                    }
                    if (chunkLength > MAX_UNCOMPRESSED_DATA_SIZE) {
                        throw new DecompressionException("Received UNCOMPRESSED_DATA larger than " +
                                MAX_UNCOMPRESSED_DATA_SIZE + " bytes");
                    }

                    if (inSize < 4 + chunkLength) {
                        return;
                    }

                    in.skipBytes(4);
                    if (validateChecksums) {
                        int checksum = in.readIntLE();
                        validateChecksum(checksum, in, in.readerIndex(), chunkLength - 4);
                    } else {
                        in.skipBytes(4);
                    }
                    pendingOutput = in.readRetainedSlice(chunkLength - 4);
                    return;
                case COMPRESSED_DATA:
                    if (!started) {
                        throw new DecompressionException("Received COMPRESSED_DATA tag before STREAM_IDENTIFIER");
                    }

                    if (chunkLength > MAX_COMPRESSED_CHUNK_SIZE) {
                        throw new DecompressionException("Received COMPRESSED_DATA that contains" +
                                " chunk that exceeds " + MAX_COMPRESSED_CHUNK_SIZE + " bytes");
                    }

                    if (inSize < 4 + chunkLength) {
                        return;
                    }

                    in.skipBytes(4);
                    int checksum = in.readIntLE();

                    int uncompressedSize = snappy.getPreamble(in);
                    if (uncompressedSize > MAX_DECOMPRESSED_DATA_SIZE) {
                        throw new DecompressionException("Received COMPRESSED_DATA that contains" +
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
                            validateChecksum(checksum, uncompressed, 0, uncompressed.writerIndex());
                        } else {
                            snappy.decode(in.readSlice(chunkLength - 4), uncompressed);
                        }
                        pendingOutput = uncompressed;
                        uncompressed = null;
                    } finally {
                        if (uncompressed != null) {
                            uncompressed.release();
                        }
                    }
                    snappy.reset();
                    return;
            }
        }
    }

    @Override
    ByteBuf processOutput(ByteBuf buf) throws DecompressionException {
        ByteBuf p = pendingOutput;
        pendingOutput = null;
        return p;
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

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        boolean validateChecksums;

        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        public Builder validateChecksums(boolean validateChecksums) {
            this.validateChecksums = validateChecksums;
            return this;
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new SnappyFrameDecompressor(this));
        }
    }
}
