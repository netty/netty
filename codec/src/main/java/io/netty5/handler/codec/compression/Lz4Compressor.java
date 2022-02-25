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
import io.netty5.handler.codec.EncoderException;
import static java.util.Objects.requireNonNull;

import io.netty5.util.internal.ObjectUtil;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.zip.Checksum;

import static io.netty5.handler.codec.compression.Lz4Constants.BLOCK_TYPE_COMPRESSED;
import static io.netty5.handler.codec.compression.Lz4Constants.BLOCK_TYPE_NON_COMPRESSED;
import static io.netty5.handler.codec.compression.Lz4Constants.CHECKSUM_OFFSET;
import static io.netty5.handler.codec.compression.Lz4Constants.COMPRESSED_LENGTH_OFFSET;
import static io.netty5.handler.codec.compression.Lz4Constants.COMPRESSION_LEVEL_BASE;
import static io.netty5.handler.codec.compression.Lz4Constants.DECOMPRESSED_LENGTH_OFFSET;
import static io.netty5.handler.codec.compression.Lz4Constants.DEFAULT_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static io.netty5.handler.codec.compression.Lz4Constants.HEADER_LENGTH;
import static io.netty5.handler.codec.compression.Lz4Constants.MAGIC_NUMBER;
import static io.netty5.handler.codec.compression.Lz4Constants.MAX_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Lz4Constants.MIN_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Lz4Constants.TOKEN_OFFSET;

/**
 * Compresses a {@link ByteBuf} using the LZ4 format.
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
public final class Lz4Compressor implements Compressor {
    static final int DEFAULT_MAX_ENCODE_SIZE = Integer.MAX_VALUE;

    private final int blockSize;

    /**
     * Underlying compressor in use.
     */
    private final LZ4Compressor compressor;

    /**
     * Underlying checksum calculator in use.
     */
    private final ByteBufChecksum checksum;

    /**
     * Compression level of current LZ4 encoder (depends on {@link #blockSize}).
     */
    private final int compressionLevel;

    /**
     * Maximum size for any buffer to write encoded (compressed) data into.
     */
    private final int maxEncodeSize;

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    }

    private State state = State.PROCESSING;

    /**
     * Creates the fastest LZ4 compressor factory with default block size (64 KB)
     * and xxhash hashing for Java, based on Yann Collet's work available at
     * <a href="https://github.com/Cyan4973/xxHash">Github</a>.
     */
    public static Supplier<Lz4Compressor> newFactory() {
        return newFactory(false);
    }

    /**
     * Creates a new LZ4 compressor factory with high or fast compression, default block size (64 KB)
     * and xxhash hashing for Java, based on Yann Collet's work available at
     * <a href="https://github.com/Cyan4973/xxHash">Github</a>.
     *
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     * @return the factory.
     */
    public static Supplier<Lz4Compressor> newFactory(boolean highCompressor) {
        return newFactory(LZ4Factory.fastestInstance(), highCompressor,
                DEFAULT_BLOCK_SIZE, new Lz4XXHash32(DEFAULT_SEED));
    }

    /**
     * Creates a new customizable LZ4 compressor factory.
     *
     * @param factory         user customizable {@link LZ4Factory} instance
     *                        which may be JNI bindings to the original C implementation, a pure Java implementation
     *                        or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     * @param blockSize       the maximum number of bytes to try to compress at once,
     *                        must be >= 64 and <= 32 M
     * @param checksum        the {@link Checksum} instance to use to check data for integrity
     * @return the factory.
     */
    public static Supplier<Lz4Compressor> newFactory(LZ4Factory factory, boolean highCompressor,
                                                     int blockSize, Checksum checksum) {
        return newFactory(factory, highCompressor, blockSize, checksum, DEFAULT_MAX_ENCODE_SIZE);
    }

    /**
     * Creates a new customizable LZ4 compressor factory.
     *
     * @param factory         user customizable {@link LZ4Factory} instance
     *                        which may be JNI bindings to the original C implementation, a pure Java implementation
     *                        or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     * @param blockSize       the maximum number of bytes to try to compress at once,
     *                        must be >= 64 and <= 32 M
     * @param checksum        the {@link Checksum} instance to use to check data for integrity
     * @param maxEncodeSize   the maximum size for an encode (compressed) buffer
     * @return the factory.
     */
    public static Supplier<Lz4Compressor> newFactory(LZ4Factory factory, boolean highCompressor, int blockSize,
                                                     Checksum checksum, int maxEncodeSize) {
        requireNonNull(factory, "factory");
        requireNonNull(checksum, "checksum");
        ObjectUtil.checkPositive(blockSize, "blockSize");
        ObjectUtil.checkPositive(maxEncodeSize, "maxEncodeSize");
        return () -> new Lz4Compressor(factory, highCompressor, blockSize, checksum, maxEncodeSize);
    }

    private Lz4Compressor(LZ4Factory factory, boolean highCompressor, int blockSize,
                          Checksum checksum, int maxEncodeSize) {
        compressor = highCompressor ? factory.highCompressor() : factory.fastCompressor();
        this.checksum = ByteBufChecksum.wrapChecksum(checksum);

        compressionLevel = compressionLevel(blockSize);
        this.blockSize = blockSize;
        this.maxEncodeSize = maxEncodeSize;
    }

    /**
     * Calculates compression level on the basis of block size.
     */
    private static int compressionLevel(int blockSize) {
        if (blockSize < MIN_BLOCK_SIZE || blockSize > MAX_BLOCK_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "blockSize: %d (expected: %d-%d)", blockSize, MIN_BLOCK_SIZE, MAX_BLOCK_SIZE));
        }
        int compressionLevel = 32 - Integer.numberOfLeadingZeros(blockSize - 1); // ceil of log2
        compressionLevel = Math.max(0, compressionLevel - COMPRESSION_LEVEL_BASE);
        return compressionLevel;
    }

    private ByteBuf allocateBuffer(ByteBufAllocator allocator, ByteBuf msg) {
        int targetBufSize = 0;
        int remaining = msg.readableBytes();

        // quick overflow check
        if (remaining < 0) {
            throw new EncoderException("too much data to allocate a buffer for compression");
        }

        while (remaining > 0) {
            int curSize = Math.min(blockSize, remaining);
            remaining -= curSize;
            // calculate the total compressed size of the current block (including header) and add to the total
            targetBufSize += compressor.maxCompressedLength(curSize) + HEADER_LENGTH;
        }

        // in addition to just the raw byte count, the headers (HEADER_LENGTH) per block (configured via
        // #blockSize) will also add to the targetBufSize, and the combination of those would never wrap around
        // again to be >= 0, this is a good check for the overflow case.
        if (targetBufSize > maxEncodeSize || 0 > targetBufSize) {
            throw new EncoderException(String.format("requested encode buffer size (%d bytes) exceeds the maximum " +
                                                     "allowable size (%d bytes)", targetBufSize, maxEncodeSize));
        }

        return allocator.buffer(targetBufSize);
    }

    @Override
    public ByteBuf compress(ByteBuf input, ByteBufAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                if (!input.isReadable()) {
                    return Unpooled.EMPTY_BUFFER;
                }

                ByteBuf out = allocateBuffer(allocator, input);
                try {
                    // We need to compress as long as we have input to read as we are limited by the blockSize that
                    // is used.
                    while (input.isReadable()) {
                        compressData(input, out);
                    }
                } catch (Throwable cause) {
                    out.release();
                    throw cause;
                }
                return out;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
            case PROCESSING:
                state = State.FINISHED;

                final ByteBuf footer = allocator.buffer(HEADER_LENGTH);
                footer.ensureWritable(HEADER_LENGTH);
                final int idx = footer.writerIndex();
                footer.setLong(idx, MAGIC_NUMBER);
                footer.setByte(idx + TOKEN_OFFSET, (byte) (BLOCK_TYPE_NON_COMPRESSED | compressionLevel));
                footer.setInt(idx + COMPRESSED_LENGTH_OFFSET, 0);
                footer.setInt(idx + DECOMPRESSED_LENGTH_OFFSET, 0);
                footer.setInt(idx + CHECKSUM_OFFSET, 0);

                footer.writerIndex(idx + HEADER_LENGTH);
                return footer;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return state != State.PROCESSING;
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public void close() {
        state = State.CLOSED;
    }

    /**
     *
     * Encodes the input buffer into {@link #blockSize} chunks in the output buffer.
     */
    private void compressData(ByteBuf in, ByteBuf out) {
        int inReaderIndex = in.readerIndex();
        int flushableBytes = Math.min(in.readableBytes(), blockSize);
        assert flushableBytes > 0;
        checksum.reset();
        checksum.update(in, inReaderIndex, flushableBytes);
        final int check = (int) checksum.getValue();

        final int bufSize = compressor.maxCompressedLength(flushableBytes) + HEADER_LENGTH;
        out.ensureWritable(bufSize);
        final int idx = out.writerIndex();
        int compressedLength;
        try {
            ByteBuffer outNioBuffer = out.internalNioBuffer(idx + HEADER_LENGTH, out.writableBytes() - HEADER_LENGTH);
            int pos = outNioBuffer.position();
            // We always want to start at position 0 as we take care of reusing the buffer in the encode(...) loop.
            compressor.compress(in.internalNioBuffer(inReaderIndex, flushableBytes), outNioBuffer);
            compressedLength = outNioBuffer.position() - pos;
        } catch (LZ4Exception e) {
            throw new CompressionException(e);
        }
        final int blockType;
        if (compressedLength >= flushableBytes) {
            blockType = BLOCK_TYPE_NON_COMPRESSED;
            compressedLength = flushableBytes;
            out.setBytes(idx + HEADER_LENGTH, in, inReaderIndex, flushableBytes);
        } else {
            blockType = BLOCK_TYPE_COMPRESSED;
        }

        out.setLong(idx, MAGIC_NUMBER);
        out.setByte(idx + TOKEN_OFFSET, (byte) (blockType | compressionLevel));
        out.setIntLE(idx + COMPRESSED_LENGTH_OFFSET, compressedLength);
        out.setIntLE(idx + DECOMPRESSED_LENGTH_OFFSET, flushableBytes);
        out.setIntLE(idx + CHECKSUM_OFFSET, check);
        out.writerIndex(idx + HEADER_LENGTH + compressedLength);

        in.readerIndex(inReaderIndex + flushableBytes);
    }
}
