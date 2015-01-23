/*
 * Copyright 2012 The Netty Project
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

import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public class JZlibDecoder extends ZlibDecoder {

    private Inflater z = new Inflater();
    private ZlibWrapper wrapper;
    private byte[] dictionary;
    private final boolean streaming;
    private volatile boolean finished;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder() {
        this(ZlibWrapper.ZLIB);
    }

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}) and option for decoder to
     * decompress multiple messages.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(boolean streaming) {
        this(ZlibWrapper.ZLIB, streaming);
    }

    /**
     * Creates a new instance with the specified wrapper.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(ZlibWrapper wrapper) {
        this(wrapper, false);
    }

    public JZlibDecoder(ZlibWrapper wrapper, boolean streaming) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        this.wrapper = wrapper;
        initWithWrapper(wrapper);
        this.streaming = streaming;
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @throws DecompressionException if failed to initialize zlib
     */
    public JZlibDecoder(byte[] dictionary) {
        this(dictionary, false);
    }

    public JZlibDecoder(byte[] dictionary, boolean streaming) {
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }
        this.dictionary = dictionary;
        initWithDictionary(dictionary);
        this.streaming = streaming;
    }

    private void initWithDictionary(byte[] dictionary) {
        int resultCode;
        resultCode = z.inflateInit(JZlib.W_ZLIB);
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    private void initWithWrapper(ZlibWrapper wrapper) {
        int resultCode = z.init(ZlibUtil.convertWrapperType(wrapper));
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

        if (!in.isReadable()) {
            return;
        }

        try {
            // Configure input.
            int inputLength = in.readableBytes();
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
            int oldNextInIndex = z.next_in_index;

            // Configure output.
            int maxOutputLength = inputLength << 1;
            ByteBuf decompressed = ctx.alloc().heapBuffer(maxOutputLength);

            try {
                loop: for (;;) {
                    z.avail_out = maxOutputLength;
                    decompressed.ensureWritable(maxOutputLength);
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
                            z.inflateEnd();
                            if (streaming) {
                                if (dictionary == null) {
                                    initWithWrapper(wrapper);
                                } else {
                                    initWithDictionary(dictionary);
                                }
                            } else {
                                finished = true; // Do not decode anymore.
                            }
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
}
