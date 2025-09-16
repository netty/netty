package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.BLOCK_TYPE_COMPRESSED;
import static io.netty.handler.codec.compression.Lz4Constants.BLOCK_TYPE_NON_COMPRESSED;
import static io.netty.handler.codec.compression.Lz4Constants.COMPRESSION_LEVEL_BASE;
import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static io.netty.handler.codec.compression.Lz4Constants.HEADER_LENGTH;
import static io.netty.handler.codec.compression.Lz4Constants.MAGIC_NUMBER;
import static io.netty.handler.codec.compression.Lz4Constants.MAX_BLOCK_SIZE;

public final class Lz4FrameDecompressor extends InputBufferingDecompressor {
    /**
     * Current state of stream.
     */
    private enum State {
        INIT_BLOCK,
        DECOMPRESS_DATA,
        FINISHED,
    }

    private State currentState = State.INIT_BLOCK;

    /**
     * Underlying decompressor in use.
     */
    private LZ4FastDecompressor decompressor;

    /**
     * Underlying checksum calculator in use.
     */
    private ByteBufChecksum checksum;

    /**
     * Type of current block.
     */
    private int blockType;

    /**
     * Compressed length of current incoming block.
     */
    private int compressedLength;

    /**
     * Decompressed length of current incoming block.
     */
    private int decompressedLength;

    /**
     * Checksum value of current incoming block.
     */
    private int currentChecksum;

    Lz4FrameDecompressor(Builder builder) {
        super(builder.allocator);
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();
        this.checksum = builder.checksum == null ? null : ByteBufChecksum.wrapChecksum(builder.checksum);
    }

    @Override
    void processInput(ByteBuf buf) throws DecompressionException {
        if (currentState != State.INIT_BLOCK) {
            return;
        }

        if (buf.readableBytes() < HEADER_LENGTH) {
            return;
        }
        final long magic = buf.readLong();
        if (magic != MAGIC_NUMBER) {
            throw new DecompressionException("unexpected block identifier");
        }

        final int token = buf.readByte();
        final int compressionLevel = (token & 0x0F) + COMPRESSION_LEVEL_BASE;
        int blockType = token & 0xF0;

        int compressedLength = Integer.reverseBytes(buf.readInt());
        if (compressedLength < 0 || compressedLength > MAX_BLOCK_SIZE) {
            throw new DecompressionException(String.format(
                    "invalid compressedLength: %d (expected: 0-%d)",
                    compressedLength, MAX_BLOCK_SIZE));
        }

        int decompressedLength = Integer.reverseBytes(buf.readInt());
        final int maxDecompressedLength = 1 << compressionLevel;
        if (decompressedLength < 0 || decompressedLength > maxDecompressedLength) {
            throw new DecompressionException(String.format(
                    "invalid decompressedLength: %d (expected: 0-%d)",
                    decompressedLength, maxDecompressedLength));
        }
        if (decompressedLength == 0 && compressedLength != 0
                || decompressedLength != 0 && compressedLength == 0
                || blockType == BLOCK_TYPE_NON_COMPRESSED && decompressedLength != compressedLength) {
            throw new DecompressionException(String.format(
                    "stream corrupted: compressedLength(%d) and decompressedLength(%d) mismatch",
                    compressedLength, decompressedLength));
        }

        int currentChecksum = Integer.reverseBytes(buf.readInt());
        if (decompressedLength == 0 && compressedLength == 0) {
            if (currentChecksum != 0) {
                throw new DecompressionException("stream corrupted: checksum error");
            }
            currentState = State.FINISHED;
            decompressor = null;
            checksum = null;
            return;
        }

        this.blockType = blockType;
        this.compressedLength = compressedLength;
        this.decompressedLength = decompressedLength;
        this.currentChecksum = currentChecksum;

        currentState = State.DECOMPRESS_DATA;
    }

    @Override
    public Status status() throws DecompressionException {
        switch (currentState) {
            case INIT_BLOCK:
                return Status.NEED_INPUT;
            case DECOMPRESS_DATA:
                return available() < compressedLength ? Status.NEED_INPUT : Status.NEED_OUTPUT;
            case FINISHED:
                return Status.COMPLETE;
            default:
                throw new AssertionError("Unexpected state: " + currentState);
        }
    }

    @Override
    public void endOfInput() throws DecompressionException {
    }

    @Override
    ByteBuf processOutput(ByteBuf in) throws DecompressionException {
        ByteBuf uncompressed = null;
        try {
            switch (blockType) {
                case BLOCK_TYPE_NON_COMPRESSED:
                    // Just pass through, we not update the readerIndex yet as we do this outside of the
                    // switch statement.
                    uncompressed = in.retainedSlice(in.readerIndex(), decompressedLength);
                    break;
                case BLOCK_TYPE_COMPRESSED:
                    uncompressed = allocator.buffer(decompressedLength, decompressedLength);

                    try {
                        decompressor.decompress(CompressionUtil.safeReadableNioBuffer(in),
                                uncompressed.internalNioBuffer(uncompressed.writerIndex(), decompressedLength));
                    } catch (LZ4Exception e) {
                        throw new DecompressionException(e);
                    }
                    // Update the writerIndex now to reflect what we decompressed.
                    uncompressed.writerIndex(uncompressed.writerIndex() + decompressedLength);
                    break;
                default:
                    throw new DecompressionException(String.format(
                            "unexpected blockType: %d (expected: %d or %d)",
                            blockType, BLOCK_TYPE_NON_COMPRESSED, BLOCK_TYPE_COMPRESSED));
            }
            // Skip inbound bytes after we processed them.
            in.skipBytes(compressedLength);
            if (checksum != null) {
                CompressionUtil.checkChecksum(checksum, uncompressed, currentChecksum);
            }
            currentState = State.INIT_BLOCK;
            return uncompressed;
        } catch (Throwable t) {
            if (uncompressed != null) {
                uncompressed.release();
            }
            throw t;
        }
    }

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        private Checksum checksum;

        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        public Builder checksum(Checksum checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder defaultChecksum() {
            return checksum(new Lz4XXHash32(DEFAULT_SEED));
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new Lz4FrameDecompressor(this));
        }
    }
}
