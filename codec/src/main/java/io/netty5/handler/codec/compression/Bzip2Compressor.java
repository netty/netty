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

import java.util.function.Supplier;

import static io.netty5.handler.codec.compression.Bzip2Constants.BASE_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Bzip2Constants.END_OF_STREAM_MAGIC_1;
import static io.netty5.handler.codec.compression.Bzip2Constants.END_OF_STREAM_MAGIC_2;
import static io.netty5.handler.codec.compression.Bzip2Constants.MAGIC_NUMBER;
import static io.netty5.handler.codec.compression.Bzip2Constants.MAX_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Bzip2Constants.MIN_BLOCK_SIZE;

/**
 * Compresses a {@link ByteBuf} using the Bzip2 algorithm.
 *
 * See <a href="https://en.wikipedia.org/wiki/Bzip2">Bzip2</a>.
 */
public final class Bzip2Compressor implements Compressor {

    /**
     * Creates a new bzip2 compressor with the specified {@code blockSizeMultiplier}.
     * @param blockSizeMultiplier
     *        The Bzip2 block size as a multiple of 100,000 bytes (minimum {@code 1}, maximum {@code 9}).
     *        Larger block sizes require more memory for both compression and decompression,
     *        but give better compression ratios. {@code 9} will usually be the best value to use.
     */
    private Bzip2Compressor(final int blockSizeMultiplier) {
        streamBlockSize = blockSizeMultiplier * BASE_BLOCK_SIZE;
    }

    /**
     * Creates a new bzip2 compressor factory with the maximum (900,000 byte) block size.
     *
     * @return the factory.
     */
    public static Supplier<Bzip2Compressor> newFactory() {
        return newFactory(MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new bzip2 compressor factory with the specified {@code blockSizeMultiplier}.
     *
     * @param blockSizeMultiplier
     *        The Bzip2 block size as a multiple of 100,000 bytes (minimum {@code 1}, maximum {@code 9}).
     *        Larger block sizes require more memory for both compression and decompression,
     *        but give better compression ratios. {@code 9} will usually be the best value to use.
     * @return the factory.
     */
    public static Supplier<Bzip2Compressor> newFactory(final int blockSizeMultiplier) {
        if (blockSizeMultiplier < MIN_BLOCK_SIZE || blockSizeMultiplier > MAX_BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "blockSizeMultiplier: " + blockSizeMultiplier + " (expected: 1-9)");
        }
        return () -> new Bzip2Compressor(blockSizeMultiplier);
    }

    /**
     * Current state of stream.
     */
    private enum State {
        INIT,
        INIT_BLOCK,
        WRITE_DATA,
        CLOSE_BLOCK
    }

    private State currentState = State.INIT;

    /**
     * A writer that provides bit-level writes.
     */
    private final Bzip2BitWriter writer = new Bzip2BitWriter();

    /**
     * The declared maximum block size of the stream (before final run-length decoding).
     */
    private final int streamBlockSize;

    /**
     * The merged CRC of all blocks compressed so far.
     */
    private int streamCRC;

    /**
     * The compressor for the current block.
     */
    private Bzip2BlockCompressor blockCompressor;

    private enum CompressorState {
        PROCESSING,
        FINISHED,
        CLOSED
    }

    private CompressorState compressorState = CompressorState.PROCESSING;

    @Override
    public ByteBuf compress(ByteBuf in, ByteBufAllocator allocator) throws CompressionException {
        switch (compressorState) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                return compressData(in, allocator);
            default:
                throw new IllegalStateException();
        }
    }

    private ByteBuf compressData(ByteBuf in, ByteBufAllocator allocator) {
        ByteBuf out = allocator.buffer();
        for (;;) {
            switch (currentState) {
                case INIT:
                    out.ensureWritable(4);
                    out.writeMedium(MAGIC_NUMBER);
                    out.writeByte('0' + streamBlockSize / BASE_BLOCK_SIZE);
                    currentState = State.INIT_BLOCK;
                    // fall through
                case INIT_BLOCK:
                    blockCompressor = new Bzip2BlockCompressor(writer, streamBlockSize);
                    currentState = State.WRITE_DATA;
                    // fall through
                case WRITE_DATA:
                    if (!in.isReadable()) {
                        return out;
                    }
                    Bzip2BlockCompressor blockCompressor = this.blockCompressor;
                    final int length = Math.min(in.readableBytes(), blockCompressor.availableSize());
                    final int bytesWritten = blockCompressor.write(in, in.readerIndex(), length);
                    in.skipBytes(bytesWritten);
                    if (!blockCompressor.isFull()) {
                        if (in.isReadable()) {
                            break;
                        } else {
                            return out;
                        }
                    }
                    currentState = State.CLOSE_BLOCK;
                    // fall through
                case CLOSE_BLOCK:
                    closeBlock(out);
                    currentState = State.INIT_BLOCK;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * Close current block and update {@link #streamCRC}.
     */
    private void closeBlock(ByteBuf out) {
        final Bzip2BlockCompressor blockCompressor = this.blockCompressor;
        if (!blockCompressor.isEmpty()) {
            blockCompressor.close(out);
            final int blockCRC = blockCompressor.crc();
            streamCRC = (streamCRC << 1 | streamCRC >>> 31) ^ blockCRC;
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        switch (compressorState) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                compressorState = CompressorState.FINISHED;
                final ByteBuf footer = allocator.buffer();
                try {
                    closeBlock(footer);

                    final int streamCRC = this.streamCRC;
                    final Bzip2BitWriter writer = this.writer;
                    try {
                        writer.writeBits(footer, 24, END_OF_STREAM_MAGIC_1);
                        writer.writeBits(footer, 24, END_OF_STREAM_MAGIC_2);
                        writer.writeInt(footer, streamCRC);
                        writer.flush(footer);
                    } finally {
                        blockCompressor = null;
                    }
                    return footer;
                } catch (Throwable cause) {
                    footer.release();
                    throw cause;
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return compressorState != CompressorState.PROCESSING;
    }

    @Override
    public boolean isClosed() {
        return compressorState == CompressorState.CLOSED;
    }

    @Override
    public void close() {
        compressorState = CompressorState.CLOSED;
    }
}
