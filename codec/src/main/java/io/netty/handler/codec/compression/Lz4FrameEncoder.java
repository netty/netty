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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.EventExecutor;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.*;

/**
 * Compresses a {@link ByteBuf} using the LZ4 format.
 *
 * See original <a href="http://code.google.com/p/lz4/">LZ4 website</a>
 * and <a href="http://fastcompression.blogspot.ru/2011/05/lz4-explained.html">LZ4 block format</a>
 * for full description.
 *
 * Since the original LZ4 block format does not contains size of compressed block and size of original data
 * this encoder uses format like <a href="https://github.com/idelpivnitskiy/lz4-java">LZ4 Java</a> library
 * written by Adrien Grand and approved by Yann Collet (author of original LZ4 library).
 *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 *  * Magic * Token *  Compressed *  Decompressed *  Checksum *  +  *  LZ4 compressed *
 *  *       *       *    length   *     length    *           *     *      block      *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 */
public class Lz4FrameEncoder extends MessageToByteEncoder<ByteBuf> {
    private final int blockSize;

    /**
     * Underlying compressor in use.
     */
    private LZ4Compressor compressor;

    /**
     * Underlying checksum calculator in use.
     */
    private Checksum checksum;

    /**
     * Compression level of current LZ4 encoder (depends on {@link #compressedBlockSize}).
     */
    private final int compressionLevel;

    /**
     * Inner byte buffer for outgoing data.
     */
    private ByteBuf buffer;

    /**
     * Current length of buffered bytes in {@link #buffer}.
     */
    private int currentBlockLength;

    /**
     * Maximum size of compressed block with header.
     */
    private final int compressedBlockSize;

    /**
     * Indicates if the compressed stream has been finished.
     */
    private volatile boolean finished;

    /**
     * Used to interact with its {@link ChannelPipeline} and other handlers.
     */
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates the fastest LZ4 encoder with default block size (64 KB)
     * and xxhash hashing for Java, based on Yann Collet's work available at
     * <a href="http://code.google.com/p/xxhash/">Google Code</a>.
     */
    public Lz4FrameEncoder() {
        this(false);
    }

    /**
     * Creates a new LZ4 encoder with hight or fast compression, default block size (64 KB)
     * and xxhash hashing for Java, based on Yann Collet's work available at
     * <a href="http://code.google.com/p/xxhash/">Google Code</a>.
     *
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     */
    public Lz4FrameEncoder(boolean highCompressor) {
        this(LZ4Factory.fastestInstance(), highCompressor, DEFAULT_BLOCK_SIZE,
                XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum());
    }

    /**
     * Creates a new customizable LZ4 encoder.
     *
     * @param factory         user customizable {@link LZ4Factory} instance
     *                        which may be JNI bindings to the original C implementation, a pure Java implementation
     *                        or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     * @param blockSize       the maximum number of bytes to try to compress at once,
     *                        must be >= 64 and <= 32 M
     * @param checksum        the {@link Checksum} instance to use to check data for integrity
     */
    public Lz4FrameEncoder(LZ4Factory factory, boolean highCompressor, int blockSize, Checksum checksum) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        if (checksum == null) {
            throw new NullPointerException("checksum");
        }

        compressor = highCompressor ? factory.highCompressor() : factory.fastCompressor();
        this.checksum = checksum;

        compressionLevel = compressionLevel(blockSize);
        this.blockSize = blockSize;
        currentBlockLength = 0;
        compressedBlockSize = HEADER_LENGTH + compressor.maxCompressedLength(blockSize);

        finished = false;
    }

    /**
     * Calculates compression level on the basis of block size.
     */
    private static int compressionLevel(int blockSize) {
        if (blockSize < MIN_BLOCK_SIZE || blockSize > MAX_BLOCK_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "blockSize: %d (expected: %d-%d)", blockSize, MIN_BLOCK_SIZE, MAX_BLOCK_SIZE));
        }
        int compressionLevel = 32 - Integer.numberOfLeadingZeros(blockSize - 1); // ceil of log2
        compressionLevel = Math.max(0, compressionLevel - COMPRESSION_LEVEL_BASE);
        return compressionLevel;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (finished) {
            out.writeBytes(in);
            return;
        }

        int length = in.readableBytes();

        final ByteBuf buffer = this.buffer;
        final int blockSize = buffer.capacity();
        while (currentBlockLength + length >= blockSize) {
            final int tail = blockSize - currentBlockLength;
            in.getBytes(in.readerIndex(), buffer, currentBlockLength, tail);
            currentBlockLength = blockSize;
            flushBufferedData(out);
            in.skipBytes(tail);
            length -= tail;
        }
        in.readBytes(buffer, currentBlockLength, length);
        currentBlockLength += length;
    }

    private void flushBufferedData(ByteBuf out) {
        int currentBlockLength = this.currentBlockLength;
        if (currentBlockLength == 0) {
            return;
        }
        checksum.reset();
        checksum.update(buffer.array(), buffer.arrayOffset(), currentBlockLength);
        final int check = (int) checksum.getValue();

        out.ensureWritable(compressedBlockSize);
        final int idx = out.writerIndex();
        int compressedLength;
        try {
            ByteBuffer outNioBuffer = out.internalNioBuffer(idx + HEADER_LENGTH, out.writableBytes() - HEADER_LENGTH);
            int pos = outNioBuffer.position();
            // We always want to start at position 0 as we take care of reusing the buffer in the encode(...) loop.
            compressor.compress(buffer.internalNioBuffer(0, currentBlockLength), outNioBuffer);
            compressedLength = outNioBuffer.position() - pos;
        } catch (LZ4Exception e) {
            throw new CompressionException(e);
        }
        final int blockType;
        if (compressedLength >= currentBlockLength) {
            blockType = BLOCK_TYPE_NON_COMPRESSED;
            compressedLength = currentBlockLength;
            out.setBytes(idx + HEADER_LENGTH, buffer, 0, currentBlockLength);
        } else {
            blockType = BLOCK_TYPE_COMPRESSED;
        }

        out.setLong(idx, MAGIC_NUMBER);
        out.setByte(idx + TOKEN_OFFSET, (byte) (blockType | compressionLevel));
        out.setIntLE(idx + COMPRESSED_LENGTH_OFFSET, compressedLength);
        out.setIntLE(idx + DECOMPRESSED_LENGTH_OFFSET, currentBlockLength);
        out.setIntLE(idx + CHECKSUM_OFFSET, check);
        out.writerIndex(idx + HEADER_LENGTH + compressedLength);
        currentBlockLength = 0;

        this.currentBlockLength = currentBlockLength;
    }

    private ChannelFuture finishEncode(final ChannelHandlerContext ctx, ChannelPromise promise) {
        if (finished) {
            promise.setSuccess();
            return promise;
        }
        finished = true;

        try {
            final ByteBuf footer = ctx.alloc().heapBuffer(
                    compressor.maxCompressedLength(currentBlockLength) + HEADER_LENGTH);
            flushBufferedData(footer);

            final int idx = footer.writerIndex();
            footer.setLong(idx, MAGIC_NUMBER);
            footer.setByte(idx + TOKEN_OFFSET, (byte) (BLOCK_TYPE_NON_COMPRESSED | compressionLevel));
            footer.setInt(idx + COMPRESSED_LENGTH_OFFSET, 0);
            footer.setInt(idx + DECOMPRESSED_LENGTH_OFFSET, 0);
            footer.setInt(idx + CHECKSUM_OFFSET, 0);

            footer.writerIndex(idx + HEADER_LENGTH);

            return ctx.writeAndFlush(footer, promise);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        compressor = null;
        checksum = null;
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }

    /**
     * Returns {@code true} if and only if the compressed stream has been finished.
     */
    public boolean isClosed() {
        return finished;
    }

    /**
     * Close this {@link Lz4FrameEncoder} and so finish the encoding.
     *
     * The returned {@link ChannelFuture} will be notified once the operation completes.
     */
    public ChannelFuture close() {
        return close(ctx().newPromise());
    }

    /**
     * Close this {@link Lz4FrameEncoder} and so finish the encoding.
     * The given {@link ChannelFuture} will be notified once the operation
     * completes and will also be returned.
     */
    public ChannelFuture close(final ChannelPromise promise) {
        ChannelHandlerContext ctx = ctx();
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            return finishEncode(ctx, promise);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    ChannelFuture f = finishEncode(ctx(), promise);
                    f.addListener(new ChannelPromiseNotifier(promise));
                }
            });
            return promise;
        }
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        ChannelFuture f = finishEncode(ctx, ctx.newPromise());
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                ctx.close(promise);
            }
        });

        if (!f.isDone()) {
            // Ensure the channel is closed even if the write operation completes in time.
            ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    ctx.close(promise);
                }
            }, 10, TimeUnit.SECONDS); // FIXME: Magic number
        }
    }

    private ChannelHandlerContext ctx() {
        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException("not added to a pipeline");
        }
        return ctx;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        // Ensure we use a heap based ByteBuf.
        buffer = Unpooled.wrappedBuffer(new byte[blockSize]);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        cleanup();
    }
}
