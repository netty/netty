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
import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import static com.ning.compress.lzf.LZFChunk.*;

/**
 * Compresses a {@link ByteBuf} using the LZF format.
 *
 * See original <a href="http://oldhome.schmorp.de/marc/liblzf.html">LZF package</a>
 * and <a href="https://github.com/ning/compress/wiki/LZFFormat">LZF format</a> for full description.
 */
public class LzfEncoder extends MessageToByteEncoder<ByteBuf> {
    /**
     * Minimum block size ready for compression. Blocks with length
     * less than {@link #MIN_BLOCK_TO_COMPRESS} will write as uncompressed.
     */
    private static final int MIN_BLOCK_TO_COMPRESS = 16;

    /**
     * Underlying decoder in use.
     */
    private final ChunkEncoder encoder;

    /**
     * Object that handles details of buffer recycling.
     */
    private final BufferRecycler recycler;

    /**
     * Creates a new LZF encoder with the most optimal available methods for underlying data access.
     * It will "unsafe" instance if one can be used on current JVM.
     * It should be safe to call this constructor as implementations are dynamically loaded; however, on some
     * non-standard platforms it may be necessary to use {@link #LzfEncoder(boolean)} with {@code true} param.
     */
    public LzfEncoder() {
        this(false, MAX_CHUNK_LEN);
    }

    /**
     * Creates a new LZF encoder with specified encoding instance.
     *
     * @param safeInstance
     *        If {@code true} encoder will use {@link ChunkEncoder} that only uses standard JDK access methods,
     *        and should work on all Java platforms and JVMs.
     *        Otherwise encoder will try to use highly optimized {@link ChunkEncoder} implementation that uses
     *        Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
     */
    public LzfEncoder(boolean safeInstance) {
        this(safeInstance, MAX_CHUNK_LEN);
    }

    /**
     * Creates a new LZF encoder with specified total length of encoded chunk. You can configure it to encode
     * your data flow more efficient if you know the avarage size of messages that you send.
     *
     * @param totalLength
     *        Expected total length of content to compress; only matters for outgoing messages that is smaller
     *        than maximum chunk size (64k), to optimize encoding hash tables.
     */
    public LzfEncoder(int totalLength) {
        this(false, totalLength);
    }

    /**
     * Creates a new LZF encoder with specified settings.
     *
     * @param safeInstance
     *        If {@code true} encoder will use {@link ChunkEncoder} that only uses standard JDK access methods,
     *        and should work on all Java platforms and JVMs.
     *        Otherwise encoder will try to use highly optimized {@link ChunkEncoder} implementation that uses
     *        Sun JDK's {@link sun.misc.Unsafe} class (which may be included by other JDK's as well).
     * @param totalLength
     *        Expected total length of content to compress; only matters for outgoing messages that is smaller
     *        than maximum chunk size (64k), to optimize encoding hash tables.
     */
    public LzfEncoder(boolean safeInstance, int totalLength) {
        super(false);
        if (totalLength < MIN_BLOCK_TO_COMPRESS || totalLength > MAX_CHUNK_LEN) {
            throw new IllegalArgumentException("totalLength: " + totalLength +
                    " (expected: " + MIN_BLOCK_TO_COMPRESS + '-' + MAX_CHUNK_LEN + ')');
        }

        encoder = safeInstance ?
                ChunkEncoderFactory.safeNonAllocatingInstance(totalLength)
              : ChunkEncoderFactory.optimalNonAllocatingInstance(totalLength);

        recycler = BufferRecycler.instance();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        final int length = in.readableBytes();
        final int idx = in.readerIndex();
        final byte[] input;
        final int inputPtr;
        if (in.hasArray()) {
            input = in.array();
            inputPtr = in.arrayOffset() + idx;
        } else {
            input = recycler.allocInputBuffer(length);
            in.getBytes(idx, input, 0, length);
            inputPtr = 0;
        }

        final int maxOutputLength = LZFEncoder.estimateMaxWorkspaceSize(length);
        out.ensureWritable(maxOutputLength);
        final byte[] output = out.array();
        final int outputPtr = out.arrayOffset() + out.writerIndex();
        final int outputLength = LZFEncoder.appendEncoded(encoder,
                        input, inputPtr, length,  output, outputPtr) - outputPtr;
        out.writerIndex(out.writerIndex() + outputLength);
        in.skipBytes(length);

        if (!in.hasArray()) {
            recycler.releaseInputBuffer(input);
        }
    }
}
