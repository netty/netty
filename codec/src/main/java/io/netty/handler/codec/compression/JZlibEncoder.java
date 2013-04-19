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

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.JZlib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Compresses a {@link ByteBuf} using the deflate algorithm.
 */
public class JZlibEncoder extends ZlibEncoder {

    private static final byte[] EMPTY_ARRAY = new byte[0];

    private final Deflater z = new Deflater();
    private final AtomicBoolean finished = new AtomicBoolean();
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
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        if (wrapper == ZlibWrapper.ZLIB_OR_NONE) {
            throw new IllegalArgumentException(
                    "wrapper '" + ZlibWrapper.ZLIB_OR_NONE + "' is not " +
                    "allowed for compression.");
        }

        synchronized (z) {
            int resultCode = z.init(
                    compressionLevel, windowBits, memLevel,
                    ZlibUtil.convertWrapperType(wrapper));
            if (resultCode != JZlib.Z_OK) {
                ZlibUtil.fail(z, "initialization failure", resultCode);
            }
        }
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
        if (dictionary == null) {
            throw new NullPointerException("dictionary");
        }

        synchronized (z) {
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
        }
    }

    @Override
    public ChannelFuture close() {
        return close(ctx().channel().newPromise());
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return finishEncode(ctx(), promise);
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
        return finished.get();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx,
            ByteBuf in, ByteBuf out) throws Exception {
        if (finished.get()) {
            return;
        }

        synchronized (z) {
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
                int maxOutputLength = (int) Math.ceil(inputLength * 1.001) + 12;
                boolean outHasArray = out.hasArray();
                z.avail_out = maxOutputLength;
                if (outHasArray) {
                    out.ensureWritable(maxOutputLength);
                    z.next_out = out.array();
                    z.next_out_index = out.arrayOffset() + out.writerIndex();
                } else {
                    z.next_out = new byte[maxOutputLength];
                    z.next_out_index = 0;
                }
                int oldNextOutIndex = z.next_out_index;

                // Note that Z_PARTIAL_FLUSH has been deprecated.
                int resultCode;
                try {
                    resultCode = z.deflate(JZlib.Z_SYNC_FLUSH);
                } finally {
                    if (inHasArray) {
                        in.skipBytes(z.next_in_index - oldNextInIndex);
                    }
                }

                if (resultCode != JZlib.Z_OK) {
                    ZlibUtil.fail(z, "compression failure", resultCode);
                }

                int outputLength = z.next_out_index - oldNextOutIndex;
                if (outputLength > 0) {
                    if (outHasArray) {
                        out.writerIndex(out.writerIndex() + outputLength);
                    } else {
                        out.writeBytes(z.next_out, 0, outputLength);
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

    @Override
    public void close(
            final ChannelHandlerContext ctx,
            final ChannelPromise promise) throws Exception {
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

    private ChannelFuture finishEncode(ChannelHandlerContext ctx, ChannelPromise future) {
        if (!finished.compareAndSet(false, true)) {
            future.setSuccess();
            return future;
        }

        ByteBuf footer;
        synchronized (z) {
            try {
                // Configure input.
                z.next_in = EMPTY_ARRAY;
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
                    future.setFailure(ZlibUtil.deflaterException(z, "compression failure", resultCode));
                    return future;
                } else if (z.next_out_index != 0) {
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
        }

        ctx.write(footer, future);
        return future;
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }
}
