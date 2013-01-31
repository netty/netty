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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.jzlib.JZlib;
import io.netty.util.internal.jzlib.ZStream;

public class JZlibDecoder extends ZlibDecoder {

    private final ZStream z = new ZStream();
    private byte[] dictionary;
    private volatile boolean finished;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibDecoder() {
        this(ZlibWrapper.ZLIB);
    }

    /**
     * Creates a new instance with the specified wrapper.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibDecoder(ZlibWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }

        int resultCode = z.inflateInit(ZlibUtil.convertWrapperType(wrapper));
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibDecoder(byte[] dictionary) {
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }
        this.dictionary = dictionary;

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
    protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf in, ByteBuf out) throws Exception {

        if (!in.isReadable()) {
            return;
        }

        try {
            // Configure input.
            int inputLength = in.readableBytes();
            boolean inHasArray = in.hasArray();
            z.avail_in = inputLength;
            if (inHasArray) {
                z.next_in = in.array();
                z.next_in_index = in.arrayOffset() + in.readerIndex();
            } else {
                byte[] array = new byte[inputLength];
                in.readBytes(array);
                z.next_in = array;
                z.next_in_index = 0;
            }
            int oldNextInIndex = z.next_in_index;

            // Configure output.
            int maxOutputLength = inputLength << 1;
            boolean outHasArray = out.hasArray();
            if (!outHasArray) {
                z.next_out = new byte[maxOutputLength];
            }

            try {
                loop: for (;;) {
                    z.avail_out = maxOutputLength;
                    if (outHasArray) {
                        out.ensureWritable(maxOutputLength);
                        z.next_out = out.array();
                        z.next_out_index = out.arrayOffset() + out.writerIndex();
                    } else {
                        z.next_out_index = 0;
                    }
                    int oldNextOutIndex = z.next_out_index;

                    // Decompress 'in' into 'out'
                    int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
                    int outputLength = z.next_out_index - oldNextOutIndex;
                    if (outputLength > 0) {
                        if (outHasArray) {
                            out.writerIndex(out.writerIndex() + outputLength);
                        } else {
                            out.writeBytes(z.next_out, 0, outputLength);
                        }
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
                if (inHasArray) {
                    in.skipBytes(z.next_in_index - oldNextInIndex);
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
