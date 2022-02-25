/*
 * Copyright 2014 The Netty Project
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
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.util.function.Supplier;
import java.util.zip.Checksum;

import static io.netty5.handler.codec.compression.Lz4Constants.*;
import static java.util.Objects.requireNonNull;

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
public final class Lz4Decompressor implements Decompressor {
    /**
     * Current state of stream.
     */
    private enum State {
        INIT_BLOCK,
        DECOMPRESS_DATA,
        FINISHED,
        CORRUPTED,
        CLOSED
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

    /**
     * Creates a new customizable LZ4 decompressor factory.
     *
     * @param factory   user customizable {@link LZ4Factory} instance
     *                  which may be JNI bindings to the original C implementation, a pure Java implementation
     *                  or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param checksum  the {@link Checksum} instance to use to check data for integrity.
     *                  You may set {@code null} if you do not want to validate checksum of each block
     */
    private Lz4Decompressor(LZ4Factory factory, Checksum checksum) {
        decompressor = factory.fastDecompressor();
        this.checksum = checksum == null ? null : ByteBufChecksum.wrapChecksum(checksum);
    }

    /**
     * Creates the fastest LZ4 decompressor factory.
     *
     * Note that by default, validation of the checksum header in each chunk is
     * DISABLED for performance improvements. If performance is less of an issue,
     * or if you would prefer the safety that checksum validation brings, please
     * use the {@link #newFactory(boolean)} constructor with the argument
     * set to {@code true}.
     *
     * @return the factory.
     */
    public static Supplier<Lz4Decompressor> newFactory() {
        return newFactory(false);
    }

    /**
     * Creates a LZ4 decompressor factory with fastest decoder instance available on your machine.
     *
     * @param validateChecksums  if {@code true}, the checksum field will be validated against the actual
     *                           uncompressed data, and if the checksums do not match, a suitable
     *                           {@link DecompressionException} will be thrown
     * @return the factory.
     */
    public static Supplier<Lz4Decompressor> newFactory(boolean validateChecksums) {
        return newFactory(LZ4Factory.fastestInstance(), validateChecksums);
    }

    /**
     * Creates a LZ4 decompressor factory with customizable implementation.
     *
     * @param factory            user customizable {@link LZ4Factory} instance
     *                           which may be JNI bindings to the original C implementation, a pure Java implementation
     *                           or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param validateChecksums  if {@code true}, the checksum field will be validated against the actual
     *                           uncompressed data, and if the checksums do not match, a suitable
     *                           {@link DecompressionException} will be thrown. In this case encoder will use
     *                           xxhash hashing for Java, based on Yann Collet's work available at
     *                           <a href="https://github.com/Cyan4973/xxHash">Github</a>.
     * @return the factory.
     */
    public static Supplier<Lz4Decompressor> newFactory(LZ4Factory factory, boolean validateChecksums) {
        return newFactory(factory, validateChecksums ? new Lz4XXHash32(DEFAULT_SEED) : null);
    }

    /**
     * Creates a customizable LZ4 decompressor factory.
     *
     * @param factory   user customizable {@link LZ4Factory} instance
     *                  which may be JNI bindings to the original C implementation, a pure Java implementation
     *                  or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param checksum  the {@link Checksum} instance to use to check data for integrity.
     *                  You may set {@code null} if you do not want to validate checksum of each block
     * @return the factory.
     */
    public static Supplier<Lz4Decompressor> newFactory(LZ4Factory factory, Checksum checksum) {
        requireNonNull(factory, "factory");
        return () -> new Lz4Decompressor(factory, checksum);
    }

    @Override
    public ByteBuf decompress(ByteBuf in, ByteBufAllocator allocator) throws DecompressionException {
        try {
            switch (currentState) {
                case CORRUPTED:
                case FINISHED:
                    return Unpooled.EMPTY_BUFFER;
                case CLOSED:
                    throw new DecompressionException("Decompressor closed");
                case INIT_BLOCK:
                    if (in.readableBytes() < HEADER_LENGTH) {
                        return null;
                    }
                    final long magic = in.readLong();
                    if (magic != MAGIC_NUMBER) {
                        streamCorrupted("unexpected block identifier");
                    }

                    final int token = in.readByte();
                    final int compressionLevel = (token & 0x0F) + COMPRESSION_LEVEL_BASE;
                    int blockType = token & 0xF0;

                    int compressedLength = Integer.reverseBytes(in.readInt());
                    if (compressedLength < 0 || compressedLength > MAX_BLOCK_SIZE) {
                        streamCorrupted(String.format(
                                "invalid compressedLength: %d (expected: 0-%d)",
                                compressedLength, MAX_BLOCK_SIZE));
                    }

                    int decompressedLength = Integer.reverseBytes(in.readInt());
                    final int maxDecompressedLength = 1 << compressionLevel;
                    if (decompressedLength < 0 || decompressedLength > maxDecompressedLength) {
                        streamCorrupted(String.format(
                                "invalid decompressedLength: %d (expected: 0-%d)",
                                decompressedLength, maxDecompressedLength));
                    }
                    if (decompressedLength == 0 && compressedLength != 0
                            || decompressedLength != 0 && compressedLength == 0
                            || blockType == BLOCK_TYPE_NON_COMPRESSED && decompressedLength != compressedLength) {
                        streamCorrupted(String.format(
                                "stream corrupted: compressedLength(%d) and decompressedLength(%d) mismatch",
                                compressedLength, decompressedLength));
                    }

                    int currentChecksum = Integer.reverseBytes(in.readInt());
                    if (decompressedLength == 0 && compressedLength == 0) {
                        if (currentChecksum != 0) {
                            streamCorrupted("stream corrupted: checksum error");
                        }
                        currentState = State.FINISHED;
                        decompressor = null;
                        checksum = null;
                        return null;
                    }

                    this.blockType = blockType;
                    this.compressedLength = compressedLength;
                    this.decompressedLength = decompressedLength;
                    this.currentChecksum = currentChecksum;

                    currentState = State.DECOMPRESS_DATA;
                    // fall through
                case DECOMPRESS_DATA:
                    blockType = this.blockType;
                    compressedLength = this.compressedLength;
                    decompressedLength = this.decompressedLength;
                    currentChecksum = this.currentChecksum;

                    if (in.readableBytes() < compressedLength) {
                        return null;
                    }

                    final ByteBufChecksum checksum = this.checksum;
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

                                decompressor.decompress(CompressionUtil.safeNioBuffer(in),
                                        uncompressed.internalNioBuffer(
                                                uncompressed.writerIndex(), decompressedLength));
                                // Update the writerIndex now to reflect what we decompressed.
                                uncompressed.writerIndex(uncompressed.writerIndex() + decompressedLength);
                                break;
                            default:
                                streamCorrupted(String.format(
                                        "unexpected blockType: %d (expected: %d or %d)",
                                        blockType, BLOCK_TYPE_NON_COMPRESSED, BLOCK_TYPE_COMPRESSED));
                        }
                        // Skip inbound bytes after we processed them.
                        in.skipBytes(compressedLength);

                        if (checksum != null) {
                            CompressionUtil.checkChecksum(checksum, uncompressed, currentChecksum);
                        }
                        ByteBuf buffer = uncompressed;
                        uncompressed = null;
                        currentState = State.INIT_BLOCK;
                        return buffer;
                    } catch (LZ4Exception e) {
                        streamCorrupted(e);
                    } finally {
                        if (uncompressed != null) {
                            uncompressed.release();
                        }
                    }
                default:
                    throw new IllegalStateException();
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            throw e;
        }
    }

    @Override
    public boolean isFinished() {
        switch (currentState) {
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
        return currentState == State.CLOSED;
    }

    @Override
    public void close() {
        currentState = State.CLOSED;
    }

    private void streamCorrupted(String message) {
        currentState = State.CORRUPTED;
        throw new DecompressionException(message);
    }

    private void streamCorrupted(Exception cause) {
        currentState = State.CORRUPTED;
        throw new DecompressionException(cause);
    }
}
