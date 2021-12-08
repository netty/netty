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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;

import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Compresses a {@link ByteBuf} using the deflate algorithm.
 */
public class JdkZlibEncoder extends ZlibEncoder {

    private final ZlibWrapper wrapper;
    private final Deflater deflater;
    private volatile boolean finished;
    private volatile ChannelHandlerContext ctx;

    /*
     * GZIP support
     */
    private final CRC32 crc = new CRC32();
    private static final byte[] gzipHeader = {0x1f, (byte) 0x8b, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0};
    private boolean writeHeader = true;

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder() {
        this(6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder(int compressionLevel) {
        this(ZlibWrapper.ZLIB, compressionLevel);
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the specified wrapper.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder(ZlibWrapper wrapper) {
        this(wrapper, 6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the specified wrapper.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        ObjectUtil.checkNotNull(wrapper, "wrapper");
        if (wrapper == ZlibWrapper.ZLIB_OR_NONE) {
            throw new IllegalArgumentException(
                    "wrapper '" + ZlibWrapper.ZLIB_OR_NONE + "' is not " +
                    "allowed for compression.");
        }

        this.wrapper = wrapper;
        deflater = new Deflater(compressionLevel, wrapper != ZlibWrapper.ZLIB);
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6})
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder(byte[] dictionary) {
        this(6, dictionary);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel}
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JdkZlibEncoder(int compressionLevel, byte[] dictionary) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        ObjectUtil.checkNotNull(dictionary, "dictionary");

        wrapper = ZlibWrapper.ZLIB;
        deflater = new Deflater(compressionLevel);
        deflater.setDictionary(dictionary);
    }

    @Override
    public ChannelFuture close() {
        return close(ctx().newPromise());
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        ChannelHandlerContext ctx = ctx();
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            return finishEncode(ctx, promise);
        } else {
            final ChannelPromise p = ctx.newPromise();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    ChannelFuture f = finishEncode(ctx(), p);
                    PromiseNotifier.cascade(f, promise);
                }
            });
            return p;
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
    public boolean isClosed() {
        return finished;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf uncompressed, ByteBuf out) throws Exception {
        if (finished) {
            out.writeBytes(uncompressed);
            return;
        }

        int len = uncompressed.readableBytes();
        if (len == 0) {
            return;
        }

        int offset;
        byte[] inAry;
        ByteBuf heapBuf = null;
        try {
            if (uncompressed.hasArray()) {
                // if it is backed by an array we not need to do a copy at all
                inAry = uncompressed.array();
                offset = uncompressed.arrayOffset() + uncompressed.readerIndex();
                // skip all bytes as we will consume all of them
                uncompressed.skipBytes(len);
            } else {
                heapBuf = ctx.alloc().heapBuffer(len, len);
                uncompressed.readBytes(heapBuf, len);
                inAry = heapBuf.array();
                offset = heapBuf.arrayOffset() + heapBuf.readerIndex();
            }

            if (writeHeader) {
                writeHeader = false;
                if (wrapper == ZlibWrapper.GZIP) {
                    out.writeBytes(gzipHeader);
                }
            }

            if (wrapper == ZlibWrapper.GZIP) {
                crc.update(inAry, offset, len);
            }

            deflater.setInput(inAry, offset, len);
            for (;;) {
                deflate(out);
                if (deflater.needsInput()) {
                    // Consumed everything
                    break;
                } else {
                    if (!out.isWritable()) {
                        // We did not consume everything but the buffer is not writable anymore. Increase the capacity
                        // to make more room.
                        out.ensureWritable(out.writerIndex());
                    }
                }
            }
        } finally {
            if (heapBuf != null) {
                heapBuf.release();
            }
        }
    }

    @Override
    protected final ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg,
                                           boolean preferDirect) throws Exception {
        int sizeEstimate = (int) Math.ceil(msg.readableBytes() * 1.001) + 12;
        if (writeHeader) {
            switch (wrapper) {
                case GZIP:
                    sizeEstimate += gzipHeader.length;
                    break;
                case ZLIB:
                    sizeEstimate += 2; // first two magic bytes
                    break;
                default:
                    // no op
            }
        }
        return ctx.alloc().heapBuffer(sizeEstimate);
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

    private ChannelFuture finishEncode(final ChannelHandlerContext ctx, ChannelPromise promise) {
        if (finished) {
            promise.setSuccess();
            return promise;
        }

        finished = true;
        ByteBuf footer = ctx.alloc().heapBuffer();
        if (writeHeader && wrapper == ZlibWrapper.GZIP) {
            // Write the GZIP header first if not written yet. (i.e. user wrote nothing.)
            writeHeader = false;
            footer.writeBytes(gzipHeader);
        }

        deflater.finish();

        while (!deflater.finished()) {
            deflate(footer);
            if (!footer.isWritable()) {
                // no more space so write it to the channel and continue
                ctx.write(footer);
                footer = ctx.alloc().heapBuffer();
            }
        }
        if (wrapper == ZlibWrapper.GZIP) {
            int crcValue = (int) crc.getValue();
            int uncBytes = deflater.getTotalIn();
            footer.writeByte(crcValue);
            footer.writeByte(crcValue >>> 8);
            footer.writeByte(crcValue >>> 16);
            footer.writeByte(crcValue >>> 24);
            footer.writeByte(uncBytes);
            footer.writeByte(uncBytes >>> 8);
            footer.writeByte(uncBytes >>> 16);
            footer.writeByte(uncBytes >>> 24);
        }
        deflater.end();
        return ctx.writeAndFlush(footer, promise);
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    private void deflate(ByteBuf out) {
        if (PlatformDependent.javaVersion() < 7) {
            deflateJdk6(out);
        }
        int numBytes;
        do {
            int writerIndex = out.writerIndex();
            numBytes = deflater.deflate(
                    out.array(), out.arrayOffset() + writerIndex, out.writableBytes(), Deflater.SYNC_FLUSH);
            out.writerIndex(writerIndex + numBytes);
        } while (numBytes > 0);
    }

    private void deflateJdk6(ByteBuf out) {
        int numBytes;
        do {
            int writerIndex = out.writerIndex();
            numBytes = deflater.deflate(
                    out.array(), out.arrayOffset() + writerIndex, out.writableBytes());
            out.writerIndex(writerIndex + numBytes);
        } while (numBytes > 0);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }
}
