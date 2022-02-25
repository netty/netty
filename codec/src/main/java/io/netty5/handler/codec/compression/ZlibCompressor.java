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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static java.util.Objects.requireNonNull;
import static io.netty5.util.internal.ObjectUtil.checkInRange;

/**
 * Compresses a {@link ByteBuf} using the deflate algorithm.
 */
public final class ZlibCompressor implements Compressor {
    private final ZlibWrapper wrapper;
    private final Deflater deflater;

    /*
     * GZIP support
     */
    private final CRC32 crc = new CRC32();
    private static final byte[] gzipHeader = {0x1f, (byte) 0x8b, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0};

    private enum State {
        PROCESSING,
        FINISHED,
        CLOSED
    }
    private State state = State.PROCESSING;
    private boolean writeHeader = true;

    private ZlibCompressor(ZlibWrapper wrapper, int compressionLevel) {
        this.wrapper = wrapper;
        deflater = new Deflater(compressionLevel, wrapper != ZlibWrapper.ZLIB);
    }

    private ZlibCompressor(int compressionLevel, byte[] dictionary) {
        wrapper = ZlibWrapper.ZLIB;
        deflater = new Deflater(compressionLevel);
        deflater.setDictionary(dictionary);
    }

    /**
     * Creates a zlib compressor factory with the default compression level ({@code 6})
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @return the factory.
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory() {
        return newFactory(6);
    }

    /**
     * Creates a zlib compressor factory with the specified {@code compressionLevel}
     * and the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     *
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory(int compressionLevel) {
        return newFactory(ZlibWrapper.ZLIB, compressionLevel);
    }

    /**
     * Creates a zlib compressor factory with the default compression level ({@code 6})
     * and the specified wrapper.
     *
     * @return the factory.
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory(ZlibWrapper wrapper) {
        return newFactory(wrapper, 6);
    }

    /**
     * Creates a zlib compressor factory with the specified {@code compressionLevel}
     * and the specified wrapper.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @return the factory.
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory(ZlibWrapper wrapper, int compressionLevel) {
        checkInRange(compressionLevel, 0, 9 , "compressionLevel");
        requireNonNull(wrapper, "wrapper");
        if (wrapper == ZlibWrapper.ZLIB_OR_NONE) {
            throw new IllegalArgumentException(
                    "wrapper '" + ZlibWrapper.ZLIB_OR_NONE + "' is not " +
                            "allowed for compression.");
        }

        return () -> new ZlibCompressor(wrapper, compressionLevel);
    }

    /**
     * Creates a zlib compressor factory with the default compression level ({@code 6})
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param dictionary  the preset dictionary
     * @return the factory.
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory(byte[] dictionary) {
        return newFactory(6, dictionary);
    }

    /**
     * Creates a zlib compressor factory with the specified {@code compressionLevel}
     * and the specified preset dictionary.  The wrapper is always
     * {@link ZlibWrapper#ZLIB} because it is the only format that supports
     * the preset dictionary.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     * @param dictionary  the preset dictionary
     * @return the factory.
     * @throws CompressionException if failed to initialize zlib
     */
    public static Supplier<ZlibCompressor> newFactory(int compressionLevel, byte[] dictionary) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        requireNonNull(dictionary, "dictionary");

        return () -> new ZlibCompressor(compressionLevel, dictionary);
    }

    @Override
    public ByteBuf compress(ByteBuf uncompressed, ByteBufAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return Unpooled.EMPTY_BUFFER;
            case PROCESSING:
                return compressData(uncompressed, allocator);
            default:
                throw new IllegalStateException();
        }
    }

    private ByteBuf compressData(ByteBuf uncompressed, ByteBufAllocator allocator) {
        int len = uncompressed.readableBytes();
        if (len == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        int sizeEstimate = (int) Math.ceil(len * 1.001) + 12;
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
        ByteBuf out = allocator.buffer(sizeEstimate);

        if (uncompressed.nioBufferCount() == 1) {
            ByteBuffer in = uncompressed.internalNioBuffer(uncompressed.readerIndex(), len);
            compressData(in, out);
        } else {
            ByteBuffer[] ins = uncompressed.nioBuffers(uncompressed.readerIndex(), len);
            for (ByteBuffer in: ins) {
                compressData(in, out);
            }
        }
        return out;
    }

    private void compressData(ByteBuffer in, ByteBuf out) {
        try {
            if (writeHeader) {
                writeHeader = false;
                if (wrapper == ZlibWrapper.GZIP) {
                    out.writeBytes(gzipHeader);
                }
            }

            if (wrapper == ZlibWrapper.GZIP) {
                int position = in.position();
                crc.update(in);
                in.position(position);
            }

            deflater.setInput(in);
            for (;;) {
                deflate(out);
                if (deflater.needsInput()) {
                    // Consumed everything
                    break;
                } else {
                    if (!out.isWritable()) {
                        // We did not consume everything but the buffer is not writable anymore. Increase the
                        // capacity to make more room.
                        out.ensureWritable(out.writerIndex());
                    }
                }
            }
        } catch (Throwable cause) {
            out.release();
            throw cause;
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
            case PROCESSING:
                state = State.FINISHED;
                ByteBuf footer = allocator.heapBuffer();
                try {
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
                    return footer;
                } catch (Throwable cause) {
                    footer.release();
                    throw cause;
                }
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
        if (state == State.PROCESSING) {
            deflater.end();
        }
        state = State.CLOSED;
    }

    private void deflate(ByteBuf out) {
        if (out.hasArray()) {
            int numBytes;
            do {
                int writerIndex = out.writerIndex();
                numBytes = deflater.deflate(
                        out.array(), out.arrayOffset() + writerIndex, out.writableBytes(), Deflater.SYNC_FLUSH);
                out.writerIndex(writerIndex + numBytes);
            } while (numBytes > 0);
        } else if (out.nioBufferCount() == 1) {
            // Use internalNioBuffer because nioBuffer is allowed to copy,
            // which is fine for reading but not for writing.
            int numBytes;
            do {
                int writerIndex = out.writerIndex();
                ByteBuffer buffer = out.internalNioBuffer(writerIndex, out.writableBytes());
                numBytes = deflater.deflate(buffer, Deflater.SYNC_FLUSH);
                out.writerIndex(writerIndex + numBytes);
            } while (numBytes > 0);
        } else {
            throw new IllegalArgumentException(
                    "Don't know how to deflate buffer without array or NIO buffer count of 1: " + out);
        }
    }
}
