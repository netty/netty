/*
 * Copyright 2025 The Netty Project
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

/**
 * Uncompresses a {@link ByteBuf} encoded with the LZ4 format.
 *
 * See original <a href="https://github.com/Cyan4973/lz4">LZ4 Github project</a>
 * and <a href="https://fastcompression.blogspot.ru/2011/05/lz4-explained.html">LZ4 block format</a>
 * for full description.
 *
 * Since the original LZ4 block format does not contains size of compressed block and size of original data
 * this encoder uses format like <a href="https://github.com/idelpivnitskiy/lz4-java">LZ4 Java</a> library
 * written by Adrien Grand and approved by Yann Collet (author of original LZ4 library).
 *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 *  * Magic * Token *  Compressed *  Decompressed *  Checksum *  +  *  LZ4 compressed *
 *  *       *       *    length   *     length    *           *     *      block      *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 */
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

    Lz4FrameDecompressor(Builder builder, ByteBufAllocator allocator) {
        super(allocator);
        this.decompressor = builder.factory.fastDecompressor();
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        private LZ4Factory factory = LZ4Factory.fastestJavaInstance();
        private Checksum checksum;

        Builder() {
        }

        /**
         * User customizable {@link LZ4Factory} instance
         * which may be JNI bindings to the original C implementation, a pure Java implementation
         * or a Java implementation that uses the {@link sun.misc.Unsafe}.
         *
         * @param factory The factory to use
         * @return This builder
         */
        public Builder factory(LZ4Factory factory) {
            this.factory = factory;
            return this;
        }

        /**
         * The {@link Checksum} instance to use to check data for integrity. By default, no checksum validation is
         * performed.
         *
         * @param checksum The checksum to use
         * @return This builder
         */
        public Builder checksum(Checksum checksum) {
            this.checksum = checksum;
            return this;
        }

        /**
         * Enable checksum validation using the default checksum, xxhash.
         *
         * @return This builder
         */
        public Builder defaultChecksum() {
            return checksum(new Lz4XXHash32(DEFAULT_SEED));
        }

        @Override
        public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
            return new DefensiveDecompressor(new Lz4FrameDecompressor(this, allocator));
        }
    }
}
