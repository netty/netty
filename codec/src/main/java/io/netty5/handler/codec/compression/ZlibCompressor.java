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

import io.netty.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static io.netty5.util.internal.ObjectUtil.checkInRange;
import static java.util.Objects.requireNonNull;

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
    public Buffer compress(Buffer uncompressed, BufferAllocator allocator) throws CompressionException {
        switch (state) {
            case CLOSED:
                throw new CompressionException("Compressor closed");
            case FINISHED:
                return allocator.allocate(0);
            case PROCESSING:
                return compressData(uncompressed, allocator);
            default:
                throw new IllegalStateException();
        }
    }

    private Buffer compressData(Buffer uncompressed, BufferAllocator allocator) {
        int len = uncompressed.readableBytes();
        if (len == 0) {
            return allocator.allocate(0);
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
        Buffer out = allocator.allocate(sizeEstimate);

        try (var readableIteration = uncompressed.forEachReadable()) {
            for (var readableComponent = readableIteration.first();
                 readableComponent != null; readableComponent = readableComponent.next()) {
                compressData(readableComponent.readableBuffer(), out);
            }
        }

        return out;
    }

    private void compressData(ByteBuffer in, Buffer out) {
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
                    if (out.writableBytes() == 0) {
                        // We did not consume everything but the buffer is not writable anymore. Increase the
                        // capacity to make more room.
                        out.ensureWritable(out.writerOffset());
                    }
                }
            }
        } catch (Throwable cause) {
            out.close();
            throw cause;
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
                Buffer footer = allocator.allocate(256);
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
                        footer.writeByte((byte) crcValue);
                        footer.writeByte((byte) (crcValue >>> 8));
                        footer.writeByte((byte) (crcValue >>> 16));
                        footer.writeByte((byte) (crcValue >>> 24));
                        footer.writeByte((byte) uncBytes);
                        footer.writeByte((byte) (uncBytes >>> 8));
                        footer.writeByte((byte) (uncBytes >>> 16));
                        footer.writeByte((byte) (uncBytes >>> 24));
                    }
                    deflater.end();
                    return footer;
                } catch (Throwable cause) {
                    footer.close();
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

    private void deflate(Buffer out) {
        try (var writableIteration = out.forEachWritable()) {
            for (var writableComponent = writableIteration.first();
                 writableComponent != null; writableComponent = writableComponent.next()) {
                if (writableComponent.hasWritableArray()) {
                    for (;;) {
                        int numBytes = deflater.deflate(
                                writableComponent.writableArray(), writableComponent.writableArrayOffset(),
                                writableComponent.writableBytes(), Deflater.SYNC_FLUSH);
                        if (numBytes <= 0) {
                            break;
                        }
                        writableComponent.skipWritableBytes(numBytes);
                    }
                } else {
                    for (;;) {
                        int numBytes = deflater.deflate(writableComponent.writableBuffer(), Deflater.SYNC_FLUSH);
                        if (numBytes <= 0) {
                            break;
                        }
                        writableComponent.skipWritableBytes(numBytes);
                    }
                }
            }
        }
    }
}
