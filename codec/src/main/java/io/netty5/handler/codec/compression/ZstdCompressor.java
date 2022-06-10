/*
 * Copyright 2021 The Netty Project
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

import com.github.luben.zstd.Zstd;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.handler.codec.EncoderException;
import io.netty5.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static io.netty5.handler.codec.compression.ZstdConstants.DEFAULT_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.ZstdConstants.DEFAULT_COMPRESSION_LEVEL;
import static io.netty5.handler.codec.compression.ZstdConstants.MAX_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.ZstdConstants.MAX_COMPRESSION_LEVEL;

/**
 *  Compresses a {@link Buffer} using the Zstandard algorithm.
 *  See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdCompressor implements Compressor {

    private final int blockSize;
    private final int compressionLevel;
    private final int maxEncodeSize;

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    }

    private State state = State.PROCESSING;

    /**
     * Creates a new Zstd compressor factory.
     *
     * Please note that if you use the default constructor, the default BLOCK_SIZE and MAX_BLOCK_SIZE
     * will be used. If you want to specify BLOCK_SIZE and MAX_BLOCK_SIZE yourself,
     * please use the {@link #newFactory(int,int)} method.
     *
     * @return the factory.
     */
    public static Supplier<ZstdCompressor> newFactory() {
        return newFactory(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new Zstd compressor factory.
     *
     *  @param  compressionLevel
     *            specifies the level of the compression
     * @return the factory.
     */
    public static Supplier<ZstdCompressor> newFactory(int compressionLevel) {
        return newFactory(compressionLevel, DEFAULT_BLOCK_SIZE, MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new Zstd compressor factory.
     *
     *  @param  blockSize
     *            is used to calculate the compressionLevel
     *  @param  maxEncodeSize
     *            specifies the size of the largest compressed object
     * @return the factory.
     */
    public static Supplier<ZstdCompressor> newFactory(int blockSize, int maxEncodeSize) {
        return newFactory(DEFAULT_COMPRESSION_LEVEL, blockSize, maxEncodeSize);
    }

    /**
     * @param  blockSize
     *           is used to calculate the compressionLevel
     * @param  maxEncodeSize
     *           specifies the size of the largest compressed object
     * @param  compressionLevel
     *           specifies the level of the compression
     * @return the factory.
     */
    public static Supplier<ZstdCompressor> newFactory(int compressionLevel, int blockSize, int maxEncodeSize) {
        ObjectUtil.checkInRange(compressionLevel, 0, MAX_COMPRESSION_LEVEL, "compressionLevel");
        ObjectUtil.checkPositive(blockSize, "blockSize");
        ObjectUtil.checkPositive(maxEncodeSize, "maxEncodeSize");
        return () -> new ZstdCompressor(compressionLevel, blockSize, maxEncodeSize);
    }
    /**
     * @param  blockSize
     *           is used to calculate the compressionLevel
     * @param  maxEncodeSize
     *           specifies the size of the largest compressed object
     * @param  compressionLevel
     *           specifies the level of the compression
     */
    private ZstdCompressor(int compressionLevel, int blockSize, int maxEncodeSize) {
        this.compressionLevel = compressionLevel;
        this.blockSize = blockSize;
        this.maxEncodeSize = maxEncodeSize;
    }

    private Buffer allocateBuffer(BufferAllocator allocator, Buffer msg) {
        int remaining = msg.readableBytes();

        long bufferSize = 0;
        while (remaining > 0) {
            int curSize = Math.min(blockSize, remaining);
            remaining -= curSize;
            bufferSize += Zstd.compressBound(curSize);
        }

        if (bufferSize > maxEncodeSize || 0 > bufferSize) {
            throw new EncoderException("requested encode buffer size (" + bufferSize + " bytes) exceeds " +
                    "the maximum allowable size (" + maxEncodeSize + " bytes)");
        }

        // TODO: It would be better if we could allocate depending on the input type
        return allocator.allocate((int) bufferSize);
    }

    @Override
    public Buffer compress(Buffer in, BufferAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return allocator.allocate(0);
            case PROCESSING:
                if (in.readableBytes() == 0) {
                    return allocator.allocate(0);
                }
                Buffer out = allocateBuffer(allocator, in);
                try {
                    compressData(in, out);
                    return out;
                } catch (Throwable cause) {
                    out.close();
                    throw cause;
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public Buffer finish(BufferAllocator allocator) {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
            case PROCESSING:
                state = State.FINISHED;
                return allocator.allocate(0);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return state != State.PROCESSING;
    }

    @Override
    public boolean isClosed() {
        return state == State.CLOSED;
    }

    @Override
    public void close() {
        state = State.CLOSED;
    }

    private void compressData(Buffer in, Buffer out) {
        final int flushableBytes = in.readableBytes();
        if (flushableBytes == 0) {
            return;
        }

        final int bufSize = (int) Zstd.compressBound(flushableBytes);
        out.ensureWritable(bufSize);
        try {
            assert out.countWritableComponents() == 1;
            try (var writableIteration = out.forEachWritable()) {
                var writableComponent = writableIteration.first();
                try (var readableIteration = in.forEachReadable()) {
                    for (var readableComponent = readableIteration.first();
                         readableComponent != null; readableComponent = readableComponent.next()) {
                        final int compressedLength;
                        if (in.isDirect() && out.isDirect()) {
                            ByteBuffer inNioBuffer = readableComponent.readableBuffer();
                            compressedLength = Zstd.compress(
                                    writableComponent.writableBuffer(),
                                    inNioBuffer,
                                    compressionLevel);
                        } else {
                            final byte[] inArray;
                            final int inOffset;
                            final int inLen = readableComponent.readableBytes();
                            if (readableComponent.hasReadableArray()) {
                                inArray = readableComponent.readableArray();
                                inOffset = readableComponent.readableArrayOffset();
                            } else {
                                inArray = new byte[inLen];
                                readableComponent.readableBuffer().get(inArray);
                                inOffset = 0;
                            }

                            final byte[] outArray;
                            final int outOffset;
                            final int outLen = writableComponent.writableBytes();
                            if (writableComponent.hasWritableArray()) {
                                outArray = writableComponent.writableArray();
                                outOffset = writableComponent.writableArrayOffset();
                            } else {
                                outArray = new byte[out.writableBytes()];
                                outOffset = 0;
                            }

                            compressedLength = (int) Zstd.compressByteArray(
                                    outArray, outOffset, outLen, inArray, inOffset, inLen, compressionLevel);
                            if (!writableComponent.hasWritableArray()) {
                                writableComponent.writableBuffer().put(outArray);
                            }
                        }
                        writableComponent.skipWritableBytes(compressedLength);
                        readableComponent.skipReadableBytes(readableComponent.readableBytes());
                    }
                }
            }
        } catch (Exception e) {
            throw new CompressionException(e);
        }
    }
}
