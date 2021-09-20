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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import io.netty.util.internal.ObjectUtil;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_COMPRESSION_LEVEL;
import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_BLOCK_SIZE;
import static io.netty.handler.codec.compression.ZstdConstants.MAX_BLOCK_SIZE;
import static io.netty.handler.codec.compression.ZstdConstants.MAX_COMPRESSION_LEVEL;

/**
 *  Compresses a {@link ByteBuf} using the Zstandard algorithm.
 *  See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdCompressor implements Compressor {

    private final int blockSize;
    private final int compressionLevel;
    private final int maxEncodeSize;
    private boolean finished;

    /**
     * Creates a new Zstd encoder.
     *
     * Please note that if you use the default constructor, the default BLOCK_SIZE and MAX_BLOCK_SIZE
     * will be used. If you want to specify BLOCK_SIZE and MAX_BLOCK_SIZE yourself,
     * please use {@link ZstdCompressor (int,int)} constructor
     */
    public static Supplier<ZstdCompressor> newFactory() {
        return newFactory(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *  @param  compressionLevel
     *            specifies the level of the compression
     */
    public static Supplier<ZstdCompressor> newFactory(int compressionLevel) {
        return newFactory(compressionLevel, DEFAULT_BLOCK_SIZE, MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new Zstd encoder.
     *  @param  blockSize
     *            is used to calculate the compressionLevel
     *  @param  maxEncodeSize
     *            specifies the size of the largest compressed object
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

    private ByteBuf allocateBuffer(ByteBufAllocator allocator, ByteBuf msg) {
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

        return msg.isDirect() ? allocator.directBuffer((int) bufferSize) : allocator.heapBuffer((int) bufferSize);
    }

    @Override
    public ByteBuf compress(ByteBuf in, ByteBufAllocator allocator) throws CompressionException {
        if (!in.isReadable()) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf out = allocateBuffer(allocator, in);
        try {
            compressData(in, out);
            return out;
        } catch (Throwable cause) {
            out.release();
            throw cause;
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        finished = true;
        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() {
        finished = true;
    }

    private void compressData(ByteBuf in, ByteBuf out) {
        final int flushableBytes = in.readableBytes();
        if (flushableBytes == 0) {
            return;
        }

        final int bufSize = (int) Zstd.compressBound(flushableBytes);
        out.ensureWritable(bufSize);
        final int idx = out.writerIndex();
        int compressedLength;
        try {
            if (in.isDirect()) {
                ByteBuffer inNioBuffer = in.internalNioBuffer(in.readerIndex(), flushableBytes);
                ByteBuffer outNioBuffer = out.internalNioBuffer(idx, out.writableBytes());
                compressedLength = Zstd.compress(
                        outNioBuffer,
                        inNioBuffer,
                        compressionLevel);
            } else {
                byte[] inArray = in.array();
                int inOffset = in.readerIndex() + in.arrayOffset();
                byte[] outArray = out.array();
                int outOffset = out.writerIndex() + out.arrayOffset();
                compressedLength = (int) Zstd.compressByteArray(
                        outArray, outOffset, out.writableBytes(), inArray, inOffset, flushableBytes, compressionLevel);
            }

            in.skipBytes(in.readableBytes());
        } catch (Exception e) {
            throw new CompressionException(e);
        }

        out.writerIndex(idx + compressedLength);
    }
}
