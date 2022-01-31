/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.ObjectUtil;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.BLOCK_TYPE_COMPRESSED;
import static io.netty.handler.codec.compression.Lz4Constants.BLOCK_TYPE_NON_COMPRESSED;
import static io.netty.handler.codec.compression.Lz4Constants.CHECKSUM_OFFSET;
import static io.netty.handler.codec.compression.Lz4Constants.COMPRESSED_LENGTH_OFFSET;
import static io.netty.handler.codec.compression.Lz4Constants.COMPRESSION_LEVEL_BASE;
import static io.netty.handler.codec.compression.Lz4Constants.DECOMPRESSED_LENGTH_OFFSET;
import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_BLOCK_SIZE;
import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static io.netty.handler.codec.compression.Lz4Constants.HEADER_LENGTH;
import static io.netty.handler.codec.compression.Lz4Constants.MAGIC_NUMBER;
import static io.netty.handler.codec.compression.Lz4Constants.MAX_BLOCK_SIZE;
import static io.netty.handler.codec.compression.Lz4Constants.MIN_BLOCK_SIZE;
import static io.netty.handler.codec.compression.Lz4Constants.TOKEN_OFFSET;
import static io.netty.handler.codec.compression.Lz4Constants.THREAD_POOL_DELAY_SECONDS;

/**
 * Compresses a {@link ByteBuf} using the LZ4 format.
 *
 * See original <a href="https://github.com/Cyan4973/lz4">LZ4 Github project</a>
 * and <a href="https://fastcompression.blogspot.ru/2011/05/lz4-explained.html">LZ4 block format</a>
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
    static final int DEFAULT_MAX_ENCODE_SIZE = Integer.MAX_VALUE;

    private final int blockSize;

    /**
     * Underlying compressor in use.
     */
    private final LZ4Compressor compressor;

    /**
     * Underlying checksum calculator in use.
     */
    private final ByteBufChecksum checksum;

    /**
     * Compression level of current LZ4 encoder (depends on {@link #blockSize}).
     */
    private final int compressionLevel;

    /**
     * Inner byte buffer for outgoing data. It's capacity will be {@link #blockSize}.
     */
    private ByteBuf buffer;

    /**
     * Maximum size for any buffer to write encoded (compressed) data into.
     */
    private final int maxEncodeSize;

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
     * <a href="https://github.com/Cyan4973/xxHash">Github</a>.
     */
    public Lz4FrameEncoder() {
        this(false);
    }

    /**
     * Creates a new LZ4 encoder with hight or fast compression, default block size (64 KB)
     * and xxhash hashing for Java, based on Yann Collet's work available at
     * <a href="https://github.com/Cyan4973/xxHash">Github</a>.
     *
     * @param highCompressor  if {@code true} codec will use compressor which requires more memory
     *                        and is slower but compresses more efficiently
     */
    public Lz4FrameEncoder(boolean highCompressor) {
        this(LZ4Factory.fastestInstance(), highCompressor, DEFAULT_BLOCK_SIZE, new Lz4XXHash32(DEFAULT_SEED));
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
        this(factory, highCompressor, blockSize, checksum, DEFAULT_MAX_ENCODE_SIZE);
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
         * @param maxEncodeSize   the maximum size for an encode (compressed) buffer
         */
    public Lz4FrameEncoder(LZ4Factory factory, boolean highCompressor, int blockSize,
                           Checksum checksum, int maxEncodeSize) {
        ObjectUtil.checkNotNull(factory, "factory");
        ObjectUtil.checkNotNull(checksum, "checksum");

        compressor = highCompressor ? factory.highCompressor() : factory.fastCompressor();
        this.checksum = ByteBufChecksum.wrapChecksum(checksum);

        compressionLevel = compressionLevel(blockSize);
        this.blockSize = blockSize;
        this.maxEncodeSize = ObjectUtil.checkPositive(maxEncodeSize, "maxEncodeSize");
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
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        return allocateBuffer(ctx, msg, preferDirect, true);
    }

    private ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect,
                                   boolean allowEmptyReturn) {
        int targetBufSize = 0;
        int remaining = msg.readableBytes() + buffer.readableBytes();

        // quick overflow check
        if (remaining < 0) {
            throw new EncoderException("too much data to allocate a buffer for compression");
        }

        while (remaining > 0) {
            int curSize = Math.min(blockSize, remaining);
            remaining -= curSize;
            // calculate the total compressed size of the current block (including header) and add to the total
            targetBufSize += compressor.maxCompressedLength(curSize) + HEADER_LENGTH;
        }

        // in addition to just the raw byte count, the headers (HEADER_LENGTH) per block (configured via
        // #blockSize) will also add to the targetBufSize, and the combination of those would never wrap around
        // again to be >= 0, this is a good check for the overflow case.
        if (targetBufSize > maxEncodeSize || 0 > targetBufSize) {
            throw new EncoderException(String.format("requested encode buffer size (%d bytes) exceeds the maximum " +
                                                     "allowable size (%d bytes)", targetBufSize, maxEncodeSize));
        }

        if (allowEmptyReturn && targetBufSize < blockSize) {
            return Unpooled.EMPTY_BUFFER;
        }

        if (preferDirect) {
            return ctx.alloc().ioBuffer(targetBufSize, targetBufSize);
        } else {
            return ctx.alloc().heapBuffer(targetBufSize, targetBufSize);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Encodes the input buffer into {@link #blockSize} chunks in the output buffer. Data is only compressed and
     * written once we hit the {@link #blockSize}; else, it is copied into the backing {@link #buffer} to await
     * more data.
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (finished) {
            if (!out.isWritable(in.readableBytes())) {
                // out should be EMPTY_BUFFER because we should have allocated enough space above in allocateBuffer.
                throw new IllegalStateException("encode finished and not enough space to write remaining data");
            }
            out.writeBytes(in);
            return;
        }

        final ByteBuf buffer = this.buffer;
        int length;
        while ((length = in.readableBytes()) > 0) {
            final int nextChunkSize = Math.min(length, buffer.writableBytes());
            in.readBytes(buffer, nextChunkSize);

            if (!buffer.isWritable()) {
                flushBufferedData(out);
            }
        }
    }

    private void flushBufferedData(ByteBuf out) {
        int flushableBytes = buffer.readableBytes();
        if (flushableBytes == 0) {
            return;
        }
        checksum.reset();
        checksum.update(buffer, buffer.readerIndex(), flushableBytes);
        final int check = (int) checksum.getValue();

        final int bufSize = compressor.maxCompressedLength(flushableBytes) + HEADER_LENGTH;
        out.ensureWritable(bufSize);
        final int idx = out.writerIndex();
        int compressedLength;
        try {
            ByteBuffer outNioBuffer = out.internalNioBuffer(idx + HEADER_LENGTH, out.writableBytes() - HEADER_LENGTH);
            int pos = outNioBuffer.position();
            // We always want to start at position 0 as we take care of reusing the buffer in the encode(...) loop.
            compressor.compress(buffer.internalNioBuffer(buffer.readerIndex(), flushableBytes), outNioBuffer);
            compressedLength = outNioBuffer.position() - pos;
        } catch (LZ4Exception e) {
            throw new CompressionException(e);
        }
        final int blockType;
        if (compressedLength >= flushableBytes) {
            blockType = BLOCK_TYPE_NON_COMPRESSED;
            compressedLength = flushableBytes;
            out.setBytes(idx + HEADER_LENGTH, buffer, buffer.readerIndex(), flushableBytes);
        } else {
            blockType = BLOCK_TYPE_COMPRESSED;
        }

        out.setLong(idx, MAGIC_NUMBER);
        out.setByte(idx + TOKEN_OFFSET, (byte) (blockType | compressionLevel));
        out.setIntLE(idx + COMPRESSED_LENGTH_OFFSET, compressedLength);
        out.setIntLE(idx + DECOMPRESSED_LENGTH_OFFSET, flushableBytes);
        out.setIntLE(idx + CHECKSUM_OFFSET, check);
        out.writerIndex(idx + HEADER_LENGTH + compressedLength);
        buffer.clear();
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        if (buffer != null && buffer.isReadable()) {
            final ByteBuf buf = allocateBuffer(ctx, Unpooled.EMPTY_BUFFER, isPreferDirect(), false);
            flushBufferedData(buf);
            ctx.write(buf);
        }
        ctx.flush();
    }

    private ChannelFuture finishEncode(final ChannelHandlerContext ctx, ChannelPromise promise) {
        if (finished) {
            promise.setSuccess();
            return promise;
        }
        finished = true;

        final ByteBuf footer = ctx.alloc().heapBuffer(
                compressor.maxCompressedLength(buffer.readableBytes()) + HEADER_LENGTH);
        flushBufferedData(footer);

        footer.ensureWritable(HEADER_LENGTH);
        final int idx = footer.writerIndex();
        footer.setLong(idx, MAGIC_NUMBER);
        footer.setByte(idx + TOKEN_OFFSET, (byte) (BLOCK_TYPE_NON_COMPRESSED | compressionLevel));
        footer.setInt(idx + COMPRESSED_LENGTH_OFFSET, 0);
        footer.setInt(idx + DECOMPRESSED_LENGTH_OFFSET, 0);
        footer.setInt(idx + CHECKSUM_OFFSET, 0);

        footer.writerIndex(idx + HEADER_LENGTH);

        return ctx.writeAndFlush(footer, promise);
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
                    PromiseNotifier.cascade(f, promise);
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
            }, THREAD_POOL_DELAY_SECONDS, TimeUnit.SECONDS);
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
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        // Ensure we use a heap based ByteBuf.
        buffer = Unpooled.wrappedBuffer(new byte[blockSize]);
        buffer.clear();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        if (buffer != null) {
            buffer.release();
            buffer = null;
        }
    }

    final ByteBuf getBackingBuffer() {
        return buffer;
    }
}
