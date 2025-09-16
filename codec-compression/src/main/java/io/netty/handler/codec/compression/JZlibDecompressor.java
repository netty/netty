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

import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class JZlibDecompressor extends ZlibDecompressor {

    private final Inflater z = new Inflater();
    private boolean finished;
    private boolean inputBufferInInflater;

    JZlibDecompressor(Builder builder) {
        super(builder);
        int resultCode;
        if (dictionary == null) {
            resultCode = z.init(ZlibUtil.convertWrapperType(builder.wrapper));
        } else {
            if (builder.wrapper != ZlibWrapper.ZLIB) {
                throw new DecompressionException("Dictionary is only supported for ZLIB wrapper");
            }
            resultCode = z.inflateInit(JZlib.W_ZLIB);
        }
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }
    }

    @Override
    void processInput(ByteBuf buf) throws DecompressionException {
        // Configure input.
        z.avail_in = buf.readableBytes();
        if (buf.hasArray()) {
            z.next_in = buf.array();
            z.next_in_index = buf.arrayOffset() + buf.readerIndex();
            inputBufferInInflater = true;
        } else {
            byte[] array = new byte[buf.readableBytes()];
            buf.readBytes(array);
            z.next_in = array;
            z.next_in_index = 0;
        }
    }

    @Override
    public Status status() throws DecompressionException {
        if (finished) {
            return Status.COMPLETE;
        } else if (z.avail_in == 0) {
            return Status.NEED_INPUT;
        } else {
            return Status.NEED_OUTPUT;
        }
    }

    @Override
    public void endOfInput() throws DecompressionException {
    }

    @Override
    ByteBuf processOutput(ByteBuf buf) throws DecompressionException {
        int proposedCapacity = z.avail_in << 1;
        int targetCapacity = maxAllocation == 0
                ? proposedCapacity : Math.min(maxAllocation, proposedCapacity);
        ByteBuf decompressed = allocator.heapBuffer(targetCapacity);
        z.avail_out = decompressed.writableBytes();
        z.next_out = decompressed.array();
        z.next_out_index = decompressed.arrayOffset() + decompressed.writerIndex();

        // Decompress 'in' into 'out'
        int resultCode = z.inflate(JZlib.Z_SYNC_FLUSH);
        decompressed.writerIndex(z.next_out_index - decompressed.arrayOffset());

        if (inputBufferInInflater) {
            buf.readerIndex(buf.writerIndex() - z.avail_in);
            if (z.avail_in == 0) {
                inputBufferInInflater = false;
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
                break;
            case JZlib.Z_OK:
            case JZlib.Z_BUF_ERROR:
                break;
            default:
                ZlibUtil.fail(z, "decompression failure", resultCode);
        }

        return decompressed;
    }

    public static Builder builder(ByteBufAllocator allocator) {
        return new Builder(allocator);
    }

    public static final class Builder extends AbstractZlibDecompressorBuilder {
        Builder(ByteBufAllocator allocator) {
            super(allocator);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder wrapper(ZlibWrapper wrapper) {
            return (Builder) super.wrapper(wrapper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder dictionary(byte[] dictionary) {
            return (Builder) super.dictionary(dictionary);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Builder maxAllocation(int maxAllocation) {
            return (Builder) super.maxAllocation(maxAllocation);
        }

        @Override
        public Decompressor build() throws DecompressionException {
            return new DefensiveDecompressor(new JZlibDecompressor(this));
        }
    }
}
