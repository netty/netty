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

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFException;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;

import java.util.function.Supplier;

import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static com.ning.compress.lzf.LZFChunk.HEADER_LEN_NOT_COMPRESSED;

/**
 * Uncompresses a {@link Buffer} encoded with the LZF format.
 *
 * See original <a href="http://oldhome.schmorp.de/marc/liblzf.html">LZF package</a>
 * and <a href="https://github.com/ning/compress/wiki/LZFFormat">LZF format</a> for full description.
 */
public final class LzfDecompressor implements Decompressor {

    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_ORIGINAL_LENGTH,
        DECOMPRESS_DATA,
        CORRUPTED,
        FINISHED,
        CLOSED
    }

    private State currentState = State.INIT_BLOCK;

    /**
     * Magic number of LZF chunk.
     */
    private static final short MAGIC_NUMBER = BYTE_Z << 8 | BYTE_V;

    /**
     * Underlying decoder in use.
     */
    private ChunkDecoder decoder;

    /**
     * Object that handles details of buffer recycling.
     */
    private BufferRecycler recycler;

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

    /**
     * Creates a new LZF decompressor with specified decoding instance.
     *
     * @param safeInstance
     *        If {@code true} decoder will use {@link ChunkDecoder} that only uses standard JDK access methods,
     *        and should work on all Java platforms and JVMs.
     *        Otherwise decoder will try to use highly optimized {@link ChunkDecoder} implementation that uses
     *        Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
     */
    private LzfDecompressor(boolean safeInstance) {
        decoder = safeInstance ?
                ChunkDecoderFactory.safeInstance()
                : ChunkDecoderFactory.optimalInstance();

        recycler = BufferRecycler.instance();
    }

    /**
     * Creates a new LZF decompressor factory with the most optimal available methods for underlying data access.
     * It will "unsafe" instance if one can be used on current JVM.
     * It should be safe to call this constructor as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to use {@link #LzfDecompressor(boolean)} with {@code true} param.
     *
     * @return the factory.
     */
    public static Supplier<LzfDecompressor> newFactory() {
        return newFactory(false);
    }

    /**
     * Creates a new LZF decompressor factory with specified decoding instance.
     *
     * @param safeInstance
     *        If {@code true} decoder will use {@link ChunkDecoder} that only uses standard JDK access methods,
     *        and should work on all Java platforms and JVMs.
     *        Otherwise decoder will try to use highly optimized {@link ChunkDecoder} implementation that uses
     *        Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
     * @return the factory.
     */
    public static Supplier<LzfDecompressor> newFactory(boolean safeInstance) {
        return () -> new LzfDecompressor(safeInstance);
    }

    @Override
    public Buffer decompress(Buffer in, BufferAllocator allocator) throws DecompressionException {
        try {
            switch (currentState) {
                case FINISHED:
                case CORRUPTED:
                    return allocator.allocate(0);
                case CLOSED:
                    throw new DecompressionException("Decompressor closed");
                case INIT_BLOCK:
                    if (in.readableBytes() < HEADER_LEN_NOT_COMPRESSED) {
                        return null;
                    }
                    final int magic = in.readUnsignedShort();
                    if (magic != MAGIC_NUMBER) {
                        streamCorrupted("unexpected block identifier");
                    }

                    final int type = in.readByte();
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
                            streamCorrupted(String.format(
                                    "unknown type of chunk: %d (expected: %d or %d)",
                                    type, BLOCK_TYPE_NON_COMPRESSED, BLOCK_TYPE_COMPRESSED));
                    }
                    chunkLength = in.readUnsignedShort();

                    // chunkLength can never exceed MAX_CHUNK_LEN as MAX_CHUNK_LEN is 64kb and readUnsignedShort can
                    // never return anything bigger as well. Let's add some check any way to make things easier in
                    // terms of debugging if we ever hit this because of an bug.
                    if (chunkLength > LZFChunk.MAX_CHUNK_LEN) {
                        streamCorrupted(String.format(
                                "chunk length exceeds maximum: %d (expected: =< %d)",
                                chunkLength, LZFChunk.MAX_CHUNK_LEN));
                    }

                    if (type != BLOCK_TYPE_COMPRESSED) {
                        return null;
                    }
                    // fall through
                case INIT_ORIGINAL_LENGTH:
                    if (in.readableBytes() < 2) {
                        return null;
                    }
                    originalLength = in.readUnsignedShort();

                    // originalLength can never exceed MAX_CHUNK_LEN as MAX_CHUNK_LEN is 64kb and readUnsignedShort
                    // can never return anything bigger as well. Let's add some check any way to make things easier
                    // in terms of debugging if we ever hit this because of an bug.
                    if (originalLength > LZFChunk.MAX_CHUNK_LEN) {
                        streamCorrupted(String.format(
                                "original length exceeds maximum: %d (expected: =< %d)",
                                chunkLength, LZFChunk.MAX_CHUNK_LEN));
                    }

                    currentState = State.DECOMPRESS_DATA;
                    // fall through
                case DECOMPRESS_DATA:
                    final int chunkLength = this.chunkLength;
                    if (in.readableBytes() < chunkLength) {
                        return null;
                    }
                    final int originalLength = this.originalLength;

                    if (isCompressed) {
                        if (in.countReadableComponents() == 1) {
                            try (var readableIteration = in.forEachReadable()) {
                                var readableComponent = readableIteration.first();
                                if (readableComponent.hasReadableArray()) {
                                    byte[] inputArray = readableComponent.readableArray();
                                    int inPos = readableComponent.readableArrayOffset();
                                    try {
                                        Buffer out = decompress(allocator, inputArray, inPos, originalLength);
                                        in.skipReadable(chunkLength);
                                        currentState = State.INIT_BLOCK;
                                        return out;
                                    } finally {
                                        if (!readableComponent.hasReadableArray()) {
                                            recycler.releaseInputBuffer(inputArray);
                                        }
                                    }
                                }
                            }
                        }
                        final int idx = in.readerOffset();
                        byte[] inputArray = recycler.allocInputBuffer(chunkLength);
                        in.copyInto(idx, inputArray, 0, chunkLength);
                        try {
                            Buffer out = decompress(allocator, inputArray, 0, originalLength);
                            in.skipReadable(chunkLength);
                            currentState = State.INIT_BLOCK;
                            return out;
                        } finally {
                            recycler.releaseInputBuffer(inputArray);
                        }
                    } else if (chunkLength > 0) {
                        currentState = State.INIT_BLOCK;
                        return in.readSplit(chunkLength);
                    } else {
                        currentState = State.INIT_BLOCK;
                    }

                    return null;
                default:
                    throw new IllegalStateException();
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            decoder = null;
            recycler = null;
            if (e instanceof DecompressionException) {
                throw (DecompressionException) e;
            }
            throw new DecompressionException(e);
        }
    }

    private Buffer decompress(BufferAllocator allocator, byte[] inputArray, int offset, int len)
            throws LZFException  {
        byte[] outputArray = recycler.allocOutputBuffer(len);
        try {
            decoder.decodeChunk(inputArray, offset,
                    outputArray, 0, len);
            return allocator.allocate(len)
                    .writeBytes(outputArray, 0, len);
        } finally {
            recycler.releaseOutputBuffer(outputArray);
        }
    }

    private void streamCorrupted(String message) {
        currentState = State.CORRUPTED;
        throw new DecompressionException(message);
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
}
