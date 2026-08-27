/*
 * Copyright 2024 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.internal.ObjectUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Decompresses a compressed block {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {
    // Don't use static here as we want to still allow to load the classes.
    {
        try {
            Zstd.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private static final int DEFAULT_MAX_FORWARD_BYTES = CompressionUtil.DEFAULT_MAX_FORWARD_BYTES;
    /**
     * Default maximum size of a single output buffer, in bytes (4 MiB).
     */
    public static final int DEFAULT_MAXIMUM_ALLOCATION_SIZE = 4 * 1024 * 1024;
    /**
     * Default upper bound on the {@code Window_Log} accepted by the decoder.
     * {@code 27} corresponds to a 128 MiB decompression window.
     */
    public static final int DEFAULT_MAX_WINDOW_LOG = 27;
    private static final int MIN_WINDOW_LOG = 10;
    private static final int MAX_WINDOW_LOG = 31;
    private final int maximumAllocationSize;
    private final int maxForwardBytes;
    private final int maxWindowLog;
    private final MutableByteBufInputStream inputStream = new MutableByteBufInputStream();
    private ZstdInputStreamNoFinalizer zstdIs;

    private boolean needsRead;
    private State currentState = State.DECOMPRESS_DATA;

    /**
     * Current state of stream.
     */
    private enum State {
        DECOMPRESS_DATA,
        CORRUPTED
    }

    /**
     * Creates a new decoder with the {@link #DEFAULT_MAXIMUM_ALLOCATION_SIZE},
     * and the {@link #DEFAULT_MAX_WINDOW_LOG} window log size.
     * <p>
     * The window log size bounds the memory usage of the sliding window for ZSTD frame decompression.
     * Frames declaring a larger window will be rejected to bound the memory the decoder may allocate per stream.
     *
     */
    public ZstdDecoder() {
        this(DEFAULT_MAXIMUM_ALLOCATION_SIZE, DEFAULT_MAX_WINDOW_LOG);
    }

    /**
     * Creates a new decoder with the given maximum allocation size,
     * and the {@link #DEFAULT_MAX_WINDOW_LOG} window log size.
     * <p>
     * The window log size bounds the memory usage of the sliding window for ZSTD frame decompression.
     * Frames declaring a larger window will be rejected to bound the memory the decoder may allocate per stream.
     *
     * @param maximumAllocationSize maximum size of a single output buffer.
     */
    public ZstdDecoder(int maximumAllocationSize) {
        this(maximumAllocationSize, DEFAULT_MAX_WINDOW_LOG);
    }

    /**
     * Creates a new decoder with an explicit upper bound on the accepted {@code Window_Log}.
     *
     * @param maximumAllocationSize maximum size of a single output buffer.
     * @param maxWindowLog          upper bound on the {@code Window_Log} field of incoming
     *                              frames; must be in {@code [10, 31]}. Frames declaring a
     *                              larger window will be rejected to bound the memory the
     *                              decoder may allocate per stream.
     */
    public ZstdDecoder(int maximumAllocationSize, int maxWindowLog) {
        this.maximumAllocationSize = ObjectUtil.checkPositiveOrZero(maximumAllocationSize, "maximumAllocationSize");
        this.maxForwardBytes = maximumAllocationSize > 0 ? maximumAllocationSize : DEFAULT_MAX_FORWARD_BYTES;
        this.maxWindowLog = ObjectUtil.checkInRange(maxWindowLog, MIN_WINDOW_LOG, MAX_WINDOW_LOG, "maxWindowLog");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        needsRead = true;
        try {
            if (currentState == State.CORRUPTED) {
                in.skipBytes(in.readableBytes());

                return;
            }
            inputStream.current = in;

            ByteBuf outBuffer = null;

            final int compressedLength = in.readableBytes();
            try {
                long uncompressedLength;
                if (in.isDirect()) {
                    uncompressedLength = com.github.luben.zstd.Zstd.getFrameContentSize(
                            CompressionUtil.safeNioBuffer(in, in.readerIndex(), in.readableBytes()));
                } else {
                    uncompressedLength = com.github.luben.zstd.Zstd.getFrameContentSize(
                            in.array(), in.readerIndex() + in.arrayOffset(), in.readableBytes());
                }
                if (uncompressedLength <= 0) {
                    // Let's start with the compressedLength * 2 as often we will not have everything
                    // we need in the in buffer and don't want to reserve too much memory.
                    uncompressedLength = compressedLength * 2L;
                }

                int w;
                do {
                    if (outBuffer == null) {
                        outBuffer = ctx.alloc().heapBuffer((int) (maximumAllocationSize == 0 ?
                                uncompressedLength : Math.min(maximumAllocationSize, uncompressedLength)));
                    }
                    do {
                        w = outBuffer.writeBytes(zstdIs, outBuffer.writableBytes());
                    } while (w > 0 && outBuffer.isWritable());
                    if (!outBuffer.isWritable() || outBuffer.readableBytes() >= maxForwardBytes) {
                        needsRead = false;
                        ctx.fireChannelRead(outBuffer);
                        outBuffer = null;
                    }
                } while (w > 0);
                if (outBuffer != null && outBuffer.isReadable()) {
                    needsRead = false;
                    ctx.fireChannelRead(outBuffer);
                    outBuffer = null;
                }
            } finally {
                if (outBuffer != null) {
                    outBuffer.release();
                }
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            throw new DecompressionException(e);
        } finally {
            inputStream.current = null;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // Discard bytes of the cumulation buffer if needed.
        discardSomeReadBytes();

        if (needsRead && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        zstdIs = new ZstdInputStreamNoFinalizer(inputStream);
        zstdIs.setContinuous(true);
        // Bound the decompression window to mitigate memory amplification from frames that
        // declare an oversized Window_Size.
        zstdIs.setLongMax(maxWindowLog);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            closeSilently(zstdIs);
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    private static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private static final class MutableByteBufInputStream extends InputStream {
        ByteBuf current;

        @Override
        public int read() {
            if (current == null || !current.isReadable()) {
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
