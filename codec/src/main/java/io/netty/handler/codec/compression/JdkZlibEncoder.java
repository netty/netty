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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;


/**
 * Compresses a {@link ByteBuf} using the deflate algorithm.
 */
public class JdkZlibEncoder extends ZlibEncoder {

    private final ZlibWrapper wrapper;
    private final byte[] encodeBuf = new byte[8192];
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
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
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
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }

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
                    f.addListener(new ChannelPromiseNotifier(promise));
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

        byte[] inAry = new byte[uncompressed.readableBytes()];
        uncompressed.readBytes(inAry);

        int sizeEstimate = (int) Math.ceil(inAry.length * 1.001) + 12;

        if (writeHeader) {
            writeHeader = false;
            switch (wrapper) {
                case GZIP:
                    out.ensureWritable(sizeEstimate + gzipHeader.length);
                    out.writeBytes(gzipHeader);
                    break;
                case ZLIB:
                    out.ensureWritable(sizeEstimate + 2); // first two magic bytes
                    break;
                default:
                    out.ensureWritable(sizeEstimate);
            }
        } else {
            out.ensureWritable(sizeEstimate);
        }

        if (wrapper == ZlibWrapper.GZIP) {
            crc.update(inAry);
        }

        deflater.setInput(inAry);
        while (!deflater.needsInput()) {
            deflate(out);
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

    private ChannelFuture finishEncode(final ChannelHandlerContext ctx, ChannelPromise promise) {
        if (finished) {
            promise.setSuccess();
            return promise;
        }

        finished = true;

        ByteBuf footer = ctx.alloc().buffer();
        if (writeHeader && wrapper == ZlibWrapper.GZIP) {
            // Write the GZIP header first if not written yet. (i.e. user wrote nothing.)
            writeHeader = false;
            footer.writeBytes(gzipHeader);
        }

        deflater.finish();
        while (!deflater.finished()) {
            deflate(footer);
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

    private void deflate(ByteBuf out) {
        int numBytes;
        do {
            numBytes = deflater.deflate(encodeBuf, 0, encodeBuf.length, Deflater.SYNC_FLUSH);
            out.writeBytes(encodeBuf, 0, numBytes);
        } while (numBytes > 0);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }
}
