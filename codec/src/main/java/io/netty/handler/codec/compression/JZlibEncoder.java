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

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.TimeUnit;

/**
 * Compresses a {@link ByteBuf} using the deflate algorithm.
 */
public class JZlibEncoder extends ZlibEncoder {

    private final int wrapperOverhead;
    private final Deflater z = new Deflater();
    private volatile boolean finished;
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6}),
     * default window bits ({@code 15}), default memory level ({@code 8}),
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder() {
        this(6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel},
     * default window bits ({@code 15}), default memory level ({@code 8}),
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(int compressionLevel) {
        this(ZlibWrapper.ZLIB, compressionLevel);
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6}),
     * default window bits ({@code 15}), default memory level ({@code 8}),
     * and the specified wrapper.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(ZlibWrapper wrapper) {
        this(wrapper, 6);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel},
     * default window bits ({@code 15}), default memory level ({@code 8}),
     * and the specified wrapper.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        this(wrapper, compressionLevel, 15, 8);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel},
     * the specified {@code windowBits}, the specified {@code memLevel}, and
     * the specified wrapper.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param windowBits
     *        The base two logarithm of the size of the history buffer.  The
     *        value should be in the range {@code 9} to {@code 15} inclusive.
     *        Larger values result in better compression at the expense of
     *        memory usage.  The default value is {@code 15}.
     * @param memLevel
     *        How much memory should be allocated for the internal compression
     *        state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *        memory.  Larger values result in better and faster compression
     *        at the expense of memory usage.  The default value is {@code 8}
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {

        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel +
                    " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException(
                    "windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException(
                    "memLevel: " + memLevel + " (expected: 1-9)");
        }
        ObjectUtil.checkNotNull(wrapper, "wrapper");
        if (wrapper == ZlibWrapper.ZLIB_OR_NONE) {
            throw new IllegalArgumentException(
                    "wrapper '" + ZlibWrapper.ZLIB_OR_NONE + "' is not " +
                    "allowed for compression.");
        }

        int resultCode = z.init(
                compressionLevel, windowBits, memLevel,
                ZlibUtil.convertWrapperType(wrapper));
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        }

        wrapperOverhead = ZlibUtil.wrapperOverhead(wrapper);
    }

    /**
     * Creates a new zlib encoder with the default compression level ({@code 6}),
     * default window bits ({@code 15}), default memory level ({@code 8}),
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(byte[] dictionary) {
        this(6, dictionary);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel},
     * default window bits ({@code 15}), default memory level ({@code 8}),
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
    public JZlibEncoder(int compressionLevel, byte[] dictionary) {
        this(compressionLevel, 15, 8, dictionary);
    }

    /**
     * Creates a new zlib encoder with the specified {@code compressionLevel},
     * the specified {@code windowBits}, the specified {@code memLevel},
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param windowBits
     *        The base two logarithm of the size of the history buffer.  The
     *        value should be in the range {@code 9} to {@code 15} inclusive.
     *        Larger values result in better compression at the expense of
     *        memory usage.  The default value is {@code 15}.
     * @param memLevel
     *        How much memory should be allocated for the internal compression
     *        state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *        memory.  Larger values result in better and faster compression
     *        at the expense of memory usage.  The default value is {@code 8}
     * @param dictionary  the preset dictionary
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public JZlibEncoder(int compressionLevel, int windowBits, int memLevel, byte[] dictionary) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException(
                    "windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException(
                    "memLevel: " + memLevel + " (expected: 1-9)");
        }
        ObjectUtil.checkNotNull(dictionary, "dictionary");

        int resultCode;
        resultCode = z.deflateInit(
                compressionLevel, windowBits, memLevel,
                JZlib.W_ZLIB); // Default: ZLIB format
        if (resultCode != JZlib.Z_OK) {
            ZlibUtil.fail(z, "initialization failure", resultCode);
        } else {
            resultCode = z.deflateSetDictionary(dictionary, dictionary.length);
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "failed to set the dictionary", resultCode);
            }
        }

        wrapperOverhead = ZlibUtil.wrapperOverhead(ZlibWrapper.ZLIB);
    }

    @Override
    public ChannelFuture close() {
        return close(ctx().channel().newPromise());
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
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        if (finished) {
            out.writeBytes(in);
            return;
        }

        int inputLength = in.readableBytes();
        if (inputLength == 0) {
            return;
        }

        try {
            // Configure input.
            boolean inHasArray = in.hasArray();
            z.avail_in = inputLength;
            if (inHasArray) {
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
            int maxOutputLength = (int) Math.ceil(inputLength * 1.001) + 12 + wrapperOverhead;
            out.ensureWritable(maxOutputLength);
            z.avail_out = maxOutputLength;
            z.next_out = out.array();
            z.next_out_index = out.arrayOffset() + out.writerIndex();
            int oldNextOutIndex = z.next_out_index;

            // Note that Z_PARTIAL_FLUSH has been deprecated.
            int resultCode;
            try {
                resultCode = z.deflate(JZlib.Z_SYNC_FLUSH);
            } finally {
                in.skipBytes(z.next_in_index - oldNextInIndex);
            }

            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "compression failure", resultCode);
            }

            int outputLength = z.next_out_index - oldNextOutIndex;
            if (outputLength > 0) {
                out.writerIndex(out.writerIndex() + outputLength);
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

    @Override
    public void close(
            final ChannelHandlerContext ctx,
            final ChannelPromise promise) {
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

    private ChannelFuture finishEncode(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (finished) {
            promise.setSuccess();
            return promise;
        }
        finished = true;

        ByteBuf footer;
        try {
            // Configure input.
            z.next_in = EmptyArrays.EMPTY_BYTES;
            z.next_in_index = 0;
            z.avail_in = 0;

            // Configure output.
            byte[] out = new byte[32]; // room for ADLER32 + ZLIB / CRC32 + GZIP header
            z.next_out = out;
            z.next_out_index = 0;
            z.avail_out = out.length;

            // Write the ADLER32 checksum (stream footer).
            int resultCode = z.deflate(JZlib.Z_FINISH);
            if (resultCode != JZlib.Z_OK && resultCode != JZlib.Z_STREAM_END) {
                promise.setFailure(ZlibUtil.deflaterException(z, "compression failure", resultCode));
                return promise;
            } else if (z.next_out_index != 0) { // lgtm[java/constant-comparison]
                // Suppressed a warning above to be on the safe side
                // even if z.next_out_index seems to be always 0 here
                footer = Unpooled.wrappedBuffer(out, 0, z.next_out_index);
            } else {
                footer = Unpooled.EMPTY_BUFFER;
            }
        } finally {
            z.deflateEnd();

            // Deference the external references explicitly to tell the VM that
            // the allocated byte arrays are temporary so that the call stack
            // can be utilized.
            // I'm not sure if the modern VMs do this optimization though.
            z.next_in = null;
            z.next_out = null;
        }
        return ctx.writeAndFlush(footer, promise);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }
}
