/*
 * Copyright 2012 The Netty Project
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

import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

public class JZlibDecoder extends ZlibDecoder {

    private final Inflater z = new Inflater();
    private byte[] dictionary;
    private volatile boolean finished;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws DecompressionException if failed to initialize zlib
     * @deprecated Use {@link JZlibDecoder#JZlibDecoder(int)}.
     */
    @Deprecated
    public JZlibDecoder() {
        this(ZlibWrapper.ZLIB, 0);
    }

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB})
     * and specified maximum buffer allocation.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link ByteBufAllocator}.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(int maxAllocation) {
        this(ZlibWrapper.ZLIB, maxAllocation);
    }

    /**
     * Creates a new instance with the specified wrapper.
     *
     * @throws DecompressionException if failed to initialize zlib
     * @deprecated Use {@link JZlibDecoder#JZlibDecoder(ZlibWrapper, int)}.
     */
    @Deprecated
    public JZlibDecoder(ZlibWrapper wrapper) {
        this(wrapper, 0);
    }

    /**
     * Creates a new instance with the specified wrapper and maximum buffer allocation.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link ByteBufAllocator}.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
        super(maxAllocation);

        ObjectUtil.checkNotNull(wrapper, "wrapper");

        int resultCode = z.init(ZlibUtil.convertWrapperType(wrapper));
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @throws DecompressionException if failed to initialize zlib
     * @deprecated Use {@link JZlibDecoder#JZlibDecoder(byte[], int)}.
     */
    @Deprecated
    public JZlibDecoder(byte[] dictionary) {
        this(dictionary, 0);
    }

    /**
     * Creates a new instance with the specified preset dictionary and maximum buffer allocation.
     * The wrapper is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link ByteBufAllocator}.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(byte[] dictionary, int maxAllocation) {
        super(maxAllocation);
        this.dictionary = ObjectUtil.checkNotNull(dictionary, "dictionary");
        int resultCode;
        resultCode = z.inflateInit(JZlib.W_ZLIB);
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    @Override
    public boolean isClosed() {
        return finished;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (finished) {
            // Skip data received after finished.
            in.skipBytes(in.readableBytes());
            return;
        }

        final int inputLength = in.readableBytes();
        if (inputLength == 0) {
            return;
        }

        try {
            // Configure input.
            z.avail_in = inputLength;
            if (in.hasArray()) {
                z.next_in = in.array();
                z.next_in_index = in.arrayOffset() + in.readerIndex();
            } else {
                byte[] array = new byte[inputLength];
                in.getBytes(in.readerIndex(), array);
                z.next_in = array;
                z.next_in_index = 0;
            }
            final int oldNextInIndex = z.next_in_index;

            // Configure output.
            ByteBuf decompressed = prepareDecompressBuffer(ctx, null, inputLength << 1);

            try {
                loop: for (;;) {
                    decompressed = prepareDecompressBuffer(ctx, decompressed, z.avail_in << 1);
                    z.avail_out = decompressed.writableBytes();
                    z.next_out = decompressed.array();
                    z.next_out_index = decompressed.arrayOffset() + decompressed.writerIndex();
                    int oldNextOutIndex = z.next_out_index;

                    // Decompress 'in' into 'out'
                    int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
                    int outputLength = z.next_out_index - oldNextOutIndex;
                    if (outputLength > 0) {
                        decompressed.writerIndex(decompressed.writerIndex() + outputLength);
                    }

                    switch (resultCode) {
                    case JZlib.Z_NEED_DICT:
                        if (dictionary == null) {
                            ZlibUtil.fail(z, "decompression failure", resultCode);
                        } else {
                            resultCode = z.inflateSetDictionary(dictionary, dictionary.length);
                            if (resultCode != JZlib.Z_OK) {
                                ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
                            }
                        }
                        break;
                    case JZlib.Z_STREAM_END:
                        finished = true; // Do not decode anymore.
                        z.inflateEnd();
                        break loop;
                    case JZlib.Z_OK:
                        break;
                    case JZlib.Z_BUF_ERROR:
                        if (z.avail_in <= 0) {
                            break loop;
                        }
                        break;
                    default:
                        ZlibUtil.fail(z, "decompression failure", resultCode);
                    }
                }
            } finally {
                in.skipBytes(z.next_in_index - oldNextInIndex);
                if (decompressed.isReadable()) {
                    out.add(decompressed);
                } else {
                    decompressed.release();
                }
            }
        } finally {
            // Deference the external references explicitly to tell the VM that
            // the allocated byte arrays are temporary so that the call stack
            // can be utilized.
            // I'm not sure if the modern VMs do this optimization though.
            z.next_in = null;
            z.next_out = null;
        }
    }

    @Override
    protected void decompressionBufferExhausted(ByteBuf buffer) {
        finished = true;
    }
}
