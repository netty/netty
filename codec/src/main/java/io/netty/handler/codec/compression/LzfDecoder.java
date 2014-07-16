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

import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.MAX_HEADER_LEN;
import static com.ning.compress.lzf.LZFChunk.HEADER_LEN_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.HEADER_LEN_NOT_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_COMPRESSED;

/**
 * Uncompresses a {@link ByteBuf} encoded with the LZF format.
 *
 * See original <a href="http://oldhome.schmorp.de/marc/liblzf.html">LZF package</a>
 * and <a href="https://github.com/ning/compress/wiki/LZFFormat">LZF format</a> for full description.
 */
public class LzfDecoder extends ByteToMessageDecoder {
    /**
     * A brief signature for content auto-detection.
     */
    private static final short SIGNATURE_OF_CHUNK = BYTE_Z << 8 | BYTE_V;

    /**
     * Offset to the "Type" in chunk header.
     */
    private static final int TYPE_OFFSET = 2;

    /**
     * Offset to the "ChunkLength" in chunk header.
     */
    private static final int CHUNK_LENGTH_OFFSET = 3;

    /**
     * Offset to the "OriginalLength" in chunk header.
     */
    private static final int ORIGINAL_LENGTH_OFFSET = 5;

    /**
     * Underlying decoder in use.
     */
    private final ChunkDecoder decoder;

    /**
     * Object that handles details of buffer recycling.
     */
    private final BufferRecycler recycler;

    /**
     * Determines the state of flow.
     */
    private boolean corrupted;

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
        for (;;) {
            if (corrupted) {
                in.skipBytes(in.readableBytes());
                return;
            }

            if (in.readableBytes() < HEADER_LEN_NOT_COMPRESSED) {
                return;
            }
            final int idx = in.readerIndex();
            final int type = in.getByte(idx + TYPE_OFFSET);
            final int chunkLength = in.getUnsignedShort(idx + CHUNK_LENGTH_OFFSET);
            final int totalLength = (type == BLOCK_TYPE_NON_COMPRESSED ?
                    HEADER_LEN_NOT_COMPRESSED : MAX_HEADER_LEN) + chunkLength;
            if (in.readableBytes() < totalLength) {
                return;
            }

            try {
                if (in.getUnsignedShort(idx) != SIGNATURE_OF_CHUNK) {
                    throw new DecompressionException("Unexpected signature of chunk");
                }
                switch (type) {
                    case BLOCK_TYPE_NON_COMPRESSED: {
                        in.skipBytes(HEADER_LEN_NOT_COMPRESSED);
                        out.add(in.readBytes(chunkLength));
                        break;
                    }
                    case BLOCK_TYPE_COMPRESSED: {
                        final int originalLength = in.getUnsignedShort(idx + ORIGINAL_LENGTH_OFFSET);

                        final byte[] inputArray;
                        final int inPos;
                        if (in.hasArray()) {
                            inputArray = in.array();
                            inPos = in.arrayOffset() + idx + HEADER_LEN_COMPRESSED;
                        } else {
                            inputArray = recycler.allocInputBuffer(chunkLength);
                            in.getBytes(idx + HEADER_LEN_COMPRESSED, inputArray, 0, chunkLength);
                            inPos = 0;
                        }

                        ByteBuf uncompressed = ctx.alloc().heapBuffer(originalLength, originalLength);
                        final byte[] outputArray = uncompressed.array();
                        final int outPos = uncompressed.arrayOffset();

                        boolean success = false;
                        try {
                            decoder.decodeChunk(inputArray, inPos, outputArray, outPos, outPos + originalLength);
                            uncompressed.writerIndex(uncompressed.writerIndex() + originalLength);
                            out.add(uncompressed);
                            in.skipBytes(totalLength);
                            success = true;
                        } finally {
                            if (!success) {
                                uncompressed.release();
                            }
                        }

                        if (!in.hasArray()) {
                            recycler.releaseInputBuffer(inputArray);
                        }
                        break;
                    }
                    default:
                        throw new DecompressionException("Unknown type of chunk: " + type + " (expected: 0 or 1)");
                }
            } catch (Exception e) {
                corrupted = true;
                throw e;
            }
        }
    }
}
