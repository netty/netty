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

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decompresses a compressed block {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecompressor implements Decompressor {
    private final ByteBufAllocator allocator;

    private final MutableByteBufInputStream mutableInput = new MutableByteBufInputStream();
    private final ZstdInputStreamNoFinalizer output;

    {
        try {
            output = new ZstdInputStreamNoFinalizer(mutableInput);
        } catch (IOException e) {
            throw new DecompressionException(e);
        }
        output.setContinuous(true);
    }

    ZstdDecompressor(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public Status status() throws DecompressionException {
        try {
            if (output.available() == 0) {
                if (!output.getContinuous()) {
                    return Status.COMPLETE;
                }
                return Status.NEED_INPUT;
            }
            return Status.NEED_OUTPUT;
        } catch (IOException e) {
            throw new DecompressionException(e);
        }
    }

    @Override
    public void addInput(ByteBuf buf) throws DecompressionException {
        if (mutableInput.current != null) {
            mutableInput.current.release();
        }
        mutableInput.current = buf;
    }

    @Override
    public void endOfInput() throws DecompressionException {
        output.setContinuous(false);
    }

    @Override
    public ByteBuf takeOutput() throws DecompressionException {
        ByteBuf buf = allocator.buffer();
        try {
            buf.writeBytes(output, buf.maxFastWritableBytes());
        } catch (IOException e) {
            buf.release();
            throw new DecompressionException(e);
        }
        return buf;
    }

    @Override
    public void close() {
        if (mutableInput.current != null) {
            mutableInput.current.release();
        }
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDecompressorBuilder {
        Builder() {
        }

        @Override
        public Decompressor build(ByteBufAllocator allocator) throws DecompressionException {
            return new DefensiveDecompressor(new ZstdDecompressor(allocator));
        }
    }

    private static final class MutableByteBufInputStream extends InputStream {
        ByteBuf current;

        @Override
        public int read() {
            if (available() == 0) {
                return -1;
            }
            return current.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int available = available();
            if (available == 0) {
                return -1;
            }

            len = Math.min(available, len);
            current.readBytes(b, off, len);
            return len;
        }

        @Override
        public int available() {
            return current == null ? 0 : current.readableBytes();
        }
    }
}
