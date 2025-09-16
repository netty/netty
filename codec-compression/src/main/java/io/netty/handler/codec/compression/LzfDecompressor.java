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

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFException;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static com.ning.compress.lzf.LZFChunk.HEADER_LEN_NOT_COMPRESSED;

/**
 * Uncompresses a {@link ByteBuf} encoded with the LZF format.
 * <p>
 * See original <a href="http://oldhome.schmorp.de/marc/liblzf.html">LZF package</a>
 * and <a href="https://github.com/ning/compress/wiki/LZFFormat">LZF format</a> for full description.
 */
public class LzfDecompressor extends InputBufferingDecompressor {
    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_ORIGINAL_LENGTH,
        DECOMPRESS_DATA,
    }

    private State currentState = State.INIT_BLOCK;

    /**
     * Magic number of LZF chunk.
     */
    private static final short MAGIC_NUMBER = BYTE_Z << 8 | BYTE_V;

    private final ChunkDecoder decoder;

    /**
     * Length of current received chunk of data.
     */
    private int chunkLength;

    /**
     * Original length of current received chunk of data.
     * It is equal to {@link #chunkLength} for non compressed chunks.
     */
    private int originalLength;

    /**
     * Indicates is this chunk compressed or not.
     */
    private boolean isCompressed;

    LzfDecompressor(Builder builder) {
        super(builder.allocator);
        decoder = builder.safeInstance ?
                ChunkDecoderFactory.safeInstance()
                : ChunkDecoderFactory.optimalInstance();
    }

    @Override
    void processInput(ByteBuf buf) throws DecompressionException {
        switch (currentState) {
            case INIT_BLOCK:
                if (buf.readableBytes() < HEADER_LEN_NOT_COMPRESSED) {
                    break;
                }
                final int magic = buf.readUnsignedShort();
                if (magic != MAGIC_NUMBER) {
                    throw new DecompressionException("unexpected block identifier");
                }

                final int type = buf.readByte();
                switch (type) {
                    case BLOCK_TYPE_NON_COMPRESSED:
                        isCompressed = false;
                        currentState = State.DECOMPRESS_DATA;
                        break;
                    case BLOCK_TYPE_COMPRESSED:
                        isCompressed = true;
                        currentState = State.INIT_ORIGINAL_LENGTH;
                        break;
                    default:
                        throw new DecompressionException(String.format(
                                "unknown type of chunk: %d (expected: %d or %d)",
                                type, BLOCK_TYPE_NON_COMPRESSED, BLOCK_TYPE_COMPRESSED));
                }
                chunkLength = buf.readUnsignedShort();

                // chunkLength can never exceed MAX_CHUNK_LEN as MAX_CHUNK_LEN is 64kb and readUnsignedShort can
                // never return anything bigger as well. Let's add some check any way to make things easier in terms
                // of debugging if we ever hit this because of an bug.
                if (chunkLength > LZFChunk.MAX_CHUNK_LEN) {
                    throw new DecompressionException(String.format(
                            "chunk length exceeds maximum: %d (expected: =< %d)",
                            chunkLength, LZFChunk.MAX_CHUNK_LEN));
                }

                if (type != BLOCK_TYPE_COMPRESSED) {
                    break;
                }
                // fall through
            case INIT_ORIGINAL_LENGTH:
                if (buf.readableBytes() < 2) {
                    break;
                }
                originalLength = buf.readUnsignedShort();

                // originalLength can never exceed MAX_CHUNK_LEN as MAX_CHUNK_LEN is 64kb and readUnsignedShort can
                // never return anything bigger as well. Let's add some check any way to make things easier in terms
                // of debugging if we ever hit this because of an bug.
                if (originalLength > LZFChunk.MAX_CHUNK_LEN) {
                    throw new DecompressionException(String.format(
                            "original length exceeds maximum: %d (expected: =< %d)",
                            chunkLength, LZFChunk.MAX_CHUNK_LEN));
                }

                currentState = State.DECOMPRESS_DATA;
                // fall through
            case DECOMPRESS_DATA:

                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public Status status() throws DecompressionException {
        switch (currentState) {
            case INIT_BLOCK:
            case INIT_ORIGINAL_LENGTH:
                return Status.NEED_INPUT;
            case DECOMPRESS_DATA:
                return available() < chunkLength ? Status.NEED_INPUT : Status.NEED_OUTPUT;
            default:
                throw new AssertionError("Unknown state: " + currentState);
        }
    }

    @Override
    public void endOfInput() throws DecompressionException {
    }

    @Override
    ByteBuf processOutput(ByteBuf in) throws DecompressionException {
        final int chunkLength = this.chunkLength;
        if (in.readableBytes() < chunkLength) {
            throw new IllegalStateException("Not in state NEED_OUTPUT");
        }
        final int originalLength = this.originalLength;

        if (isCompressed) {
            ByteBuf arrayView;
            if (!in.hasArray()) {
                arrayView = allocator.heapBuffer(in.readableBytes());
                arrayView.writeBytes(in, in.readerIndex(), in.readableBytes());
            } else {
                arrayView = in;
            }
            final byte[] inputArray = arrayView.array();
            final int inPos = arrayView.arrayOffset() + arrayView.readerIndex();

            ByteBuf uncompressed = allocator.heapBuffer(originalLength, originalLength);
            final byte[] outputArray = uncompressed.array();
            final int outPos = uncompressed.arrayOffset() + uncompressed.writerIndex();

            try {
                decoder.decodeChunk(inputArray, inPos, outputArray, outPos, outPos + originalLength);
                uncompressed.writerIndex(uncompressed.writerIndex() + originalLength);
            } catch (LZFException e) {
                uncompressed.release();
                throw new DecompressionException(e);
            } finally {
                in.skipBytes(chunkLength);
                if (arrayView != in) {
                    arrayView.release();
                }
            }

            currentState = State.INIT_BLOCK;
            return uncompressed;
        } else {
            currentState = State.INIT_BLOCK;
            return in.readRetainedSlice(chunkLength);
        }
    }

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        private boolean safeInstance;

        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        /**
         * If {@code true} decoder will use {@link ChunkDecoder} that only uses standard JDK access methods,
         * and should work on all Java platforms and JVMs.
         * Otherwise decoder will try to use highly optimized {@link ChunkDecoder} implementation that uses
         * Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
         *
         * @param safeInstance Whether to use the safe instance only
         * @return This builder
         */
        public Builder safeInstance(boolean safeInstance) {
            this.safeInstance = safeInstance;
            return this;
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new LzfDecompressor(this));
        }
    }
}
