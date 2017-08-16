/*
 * Copyright 2014 The Netty Project
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

import com.ning.compress.BufferRecycler;
import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static com.ning.compress.lzf.LZFChunk.HEADER_LEN_NOT_COMPRESSED;

/**
 * Uncompresses a {@link ByteBuf} encoded with the LZF format.
 *
 * See original <a href="http://oldhome.schmorp.de/marc/liblzf.html">LZF package</a>
 * and <a href="https://github.com/ning/compress/wiki/LZFFormat">LZF format</a> for full description.
 */
public class LzfDecoder extends ByteToMessageDecoder {
    /**
     * Current state of decompression.
     */
    private enum State {
        INIT_BLOCK,
        INIT_ORIGINAL_LENGTH,
        DECOMPRESS_DATA,
        CORRUPTED
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
     * Creates a new LZF decoder with the most optimal available methods for underlying data access.
     * It will "unsafe" instance if one can be used on current JVM.
     * It should be safe to call this constructor as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to use {@link #LzfDecoder(boolean)} with {@code true} param.
     */
    public LzfDecoder() {
        this(false);
    }

    /**
     * Creates a new LZF decoder with specified decoding instance.
     *
     * @param safeInstance
     *        If {@code true} decoder will use {@link ChunkDecoder} that only uses standard JDK access methods,
     *        and should work on all Java platforms and JVMs.
     *        Otherwise decoder will try to use highly optimized {@link ChunkDecoder} implementation that uses
     *        Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
     */
    public LzfDecoder(boolean safeInstance) {
        decoder = safeInstance ?
                ChunkDecoderFactory.safeInstance()
              : ChunkDecoderFactory.optimalInstance();

        recycler = BufferRecycler.instance();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (currentState) {
            case INIT_BLOCK:
                if (in.readableBytes() < HEADER_LEN_NOT_COMPRESSED) {
                    break;
                }
                final int magic = in.readUnsignedShort();
                if (magic != MAGIC_NUMBER) {
                    throw new DecompressionException("unexpected block identifier");
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
                    throw new DecompressionException(String.format(
                            "unknown type of chunk: %d (expected: %d or %d)",
                            type, BLOCK_TYPE_NON_COMPRESSED, BLOCK_TYPE_COMPRESSED));
                }
                chunkLength = in.readUnsignedShort();

                if (type != BLOCK_TYPE_COMPRESSED) {
                    break;
                }
                // fall through
            case INIT_ORIGINAL_LENGTH:
                if (in.readableBytes() < 2) {
                    break;
                }
                originalLength = in.readUnsignedShort();

                currentState = State.DECOMPRESS_DATA;
                // fall through
            case DECOMPRESS_DATA:
                final int chunkLength = this.chunkLength;
                if (in.readableBytes() < chunkLength) {
                    break;
                }
                final int originalLength = this.originalLength;

                if (isCompressed) {
                    final int idx = in.readerIndex();

                    final byte[] inputArray;
                    final int inPos;
                    if (in.hasArray()) {
                        inputArray = in.array();
                        inPos = in.arrayOffset() + idx;
                    } else {
                        inputArray = recycler.allocInputBuffer(chunkLength);
                        in.getBytes(idx, inputArray, 0, chunkLength);
                        inPos = 0;
                    }

                    ByteBuf uncompressed = ctx.alloc().heapBuffer(originalLength, originalLength);
                    final byte[] outputArray = uncompressed.array();
                    final int outPos = uncompressed.arrayOffset() + uncompressed.writerIndex();

                    boolean success = false;
                    try {
                        decoder.decodeChunk(inputArray, inPos, outputArray, outPos, outPos + originalLength);
                        uncompressed.writerIndex(uncompressed.writerIndex() + originalLength);
                        out.add(uncompressed);
                        in.skipBytes(chunkLength);
                        success = true;
                    } finally {
                        if (!success) {
                            uncompressed.release();
                        }
                    }

                    if (!in.hasArray()) {
                        recycler.releaseInputBuffer(inputArray);
                    }
                } else if (chunkLength > 0) {
                    out.add(in.readRetainedSlice(chunkLength));
                }

                currentState = State.INIT_BLOCK;
                break;
            case CORRUPTED:
                in.skipBytes(in.readableBytes());
                break;
            default:
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            decoder = null;
            recycler = null;
            throw e;
        }
    }
}
