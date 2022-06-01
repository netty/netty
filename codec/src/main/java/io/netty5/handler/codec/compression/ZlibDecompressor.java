/*
 * Copyright 2013 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static java.util.Objects.requireNonNull;

/**
 * Decompress a {@link Buffer} using the inflate algorithm.
 */
public final class ZlibDecompressor implements Decompressor {
    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;
    private static final int FRESERVED = 0xE0;

    private Inflater inflater;
    private final byte[] dictionary;

    // GZIP related
    private final BufferChecksum crc;
    private final boolean decompressConcatenated;

    /**
     * Maximum allowed size of the decompression buffer.
     */
    protected final int maxAllocation;

    private enum GzipState {
        HEADER_START,
        HEADER_END,
        FLG_READ,
        XLEN_READ,
        SKIP_FNAME,
        SKIP_COMMENT,
        PROCESS_FHCRC,
        FOOTER_START,
    }

    private GzipState gzipState = GzipState.HEADER_START;
    private int flags = -1;
    private int xlen = -1;

    private boolean finished;
    private boolean closed;

    private boolean decideZlibOrNone;

    private ZlibDecompressor(ZlibWrapper wrapper, byte[] dictionary, boolean decompressConcatenated,
                             int maxAllocation) {
        this.maxAllocation = maxAllocation;
        this.decompressConcatenated = decompressConcatenated;
        switch (wrapper) {
            case GZIP:
                inflater = new Inflater(true);
                crc = new BufferChecksum(new CRC32());
                break;
            case NONE:
                inflater = new Inflater(true);
                crc = null;
                break;
            case ZLIB:
                inflater = new Inflater();
                crc = null;
                break;
            case ZLIB_OR_NONE:
                // Postpone the decision until decode(...) is called.
                decideZlibOrNone = true;
                crc = null;
                break;
            default:
                throw new IllegalArgumentException("Only GZIP or ZLIB is supported, but you used " + wrapper);
        }
        this.dictionary = dictionary;
    }

    /**
     * Creates a zlib decompressor factory with the default wrapper ({@link ZlibWrapper#ZLIB}).
     *
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory() {
        return newFactory(ZlibWrapper.ZLIB, null, false, 0);
    }

    /**
     * Creates a zlib decompressor factory with the default wrapper ({@link ZlibWrapper#ZLIB})
     * and the specified maximum buffer allocation.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link BufferAllocator}.
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory(int maxAllocation) {
        return newFactory(ZlibWrapper.ZLIB, null, false, maxAllocation);
    }

    /**
     * Creates a zlib decompressor factory with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory(byte[] dictionary) {
        return newFactory(ZlibWrapper.ZLIB, dictionary, false, 0);
    }

    /**
     * Creates zlib decompressor factory with the specified preset dictionary and maximum buffer allocation.
     * The wrapper is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link BufferAllocator}.
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory(byte[] dictionary, int maxAllocation) {
        return newFactory(ZlibWrapper.ZLIB, dictionary, false, maxAllocation);
    }

    /**
     * Creates zlib decompressor factory with the specified wrapper.
     * Be aware that only {@link ZlibWrapper#GZIP}, {@link ZlibWrapper#ZLIB} and {@link ZlibWrapper#NONE} are
     * supported atm.
     *
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory(ZlibWrapper wrapper) {
        return newFactory(wrapper, null, false, 0);
    }

    /**
     * Creates zlib decompressor factory with the specified wrapper and maximum buffer allocation.
     * Be aware that only {@link ZlibWrapper#GZIP}, {@link ZlibWrapper#ZLIB} and {@link ZlibWrapper#NONE} are
     * supported atm.
     *
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link BufferAllocator}.
     * @return the factory.
     */
    public static Supplier<ZlibDecompressor> newFactory(ZlibWrapper wrapper, int maxAllocation) {
        return newFactory(wrapper, null, false, maxAllocation);
    }

    public static Supplier<ZlibDecompressor> newFactory(ZlibWrapper wrapper, boolean decompressConcatenated) {
        return newFactory(wrapper, null, decompressConcatenated, 0);
    }

    public static Supplier<ZlibDecompressor> newFactory(
            ZlibWrapper wrapper, boolean decompressConcatenated, int maxAllocation) {
        return newFactory(wrapper, null, decompressConcatenated, maxAllocation);
    }

    public static Supplier<ZlibDecompressor> newFactory(boolean decompressConcatenated) {
        return newFactory(ZlibWrapper.GZIP, null, decompressConcatenated, 0);
    }

    public static Supplier<ZlibDecompressor> newFactory(boolean decompressConcatenated, int maxAllocation) {
        return newFactory(ZlibWrapper.GZIP, null, decompressConcatenated, maxAllocation);
    }

    private static Supplier<ZlibDecompressor> newFactory(ZlibWrapper wrapper, byte[] dictionary,
                                                         boolean decompressConcatenated, int maxAllocation) {
        requireNonNull(wrapper, "wrapper");
        return () -> new ZlibDecompressor(wrapper, dictionary, decompressConcatenated, maxAllocation);
    }

    @Override
    public Buffer decompress(Buffer in, BufferAllocator allocator) throws DecompressionException {
        if (closed) {
            throw new DecompressionException("Decompressor closed");
        }
        if (finished) {
            return allocator.allocate(0);
        }

        int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return null;
        }

        if (decideZlibOrNone) {
            // First two bytes are needed to decide if it's a ZLIB stream.
            if (readableBytes < 2) {
                return null;
            }

            boolean nowrap = !looksLikeZlib(in.getShort(in.readerOffset()));
            inflater = new Inflater(nowrap);
            decideZlibOrNone = false;
        }

        if (crc != null) {
            if (gzipState != GzipState.HEADER_END) {
                if (gzipState == GzipState.FOOTER_START) {
                    if (!handleGzipFooter(in)) {
                        // Either there was not enough data or the input is finished.
                        return null;
                    }
                    // If we consumed the footer we will start with the header again.
                    assert gzipState == GzipState.HEADER_START;
                }
                if (!readGZIPHeader(in)) {
                    // There was not enough data readable to read the GZIP header.
                    return null;
                }
                // Some bytes may have been consumed, and so we must re-set the number of readable bytes.
                readableBytes = in.readableBytes();
                if (readableBytes == 0) {
                    return null;
                }
            }
        }

        if (inflater.needsInput()) {
            try (var readableIteration = in.forEachReadable()) {
                var readableComponent = readableIteration.first();
                if (readableComponent.hasReadableArray()) {
                    inflater.setInput(readableComponent.readableArray(),
                            readableComponent.readableArrayOffset(), readableComponent.readableBytes());
                } else {
                    inflater.setInput(readableComponent.readableBuffer());
                }
            }
        }

        Buffer decompressed = prepareDecompressBuffer(allocator, null, inflater.getRemaining() << 1);
        try {
            boolean readFooter = false;
            while (!inflater.needsInput()) {
                int writableComponents = decompressed.countWritableComponents();
                if (writableComponents == 0) {
                    break;
                } else if (writableComponents > 1) {
                    throw new IllegalStateException(
                            "Decompress buffer must have array or exactly 1 NIO buffer: " + decompressed);
                }
                try (var writableIteration = decompressed.forEachWritable()) {
                    var writableComponent = writableIteration.first();
                    int writerIndex = decompressed.writerOffset();
                    int writable = decompressed.writableBytes();
                    int outputLength;
                    if (writableComponent.hasWritableArray()) {
                        byte[] outArray = writableComponent.writableArray();
                        int outIndex = writableComponent.writableArrayOffset();
                        outputLength = inflater.inflate(outArray, outIndex, writable);
                    } else {
                        ByteBuffer buffer = writableComponent.writableBuffer();
                        outputLength = inflater.inflate(buffer);
                    }
                    if (outputLength > 0) {
                        writableComponent.skipWritable(outputLength);
                        if (crc != null) {
                            crc.update(decompressed, writerIndex, outputLength);
                        }
                    } else if (inflater.needsDictionary()) {
                        if (dictionary == null) {
                            throw new DecompressionException(
                                    "decompression failure, unable to set dictionary as non was specified");
                        }
                        inflater.setDictionary(dictionary);
                    }

                    if (inflater.finished()) {
                        if (crc == null) {
                            finished = true; // Do not decode anymore.
                        } else {
                            readFooter = true;
                        }
                        break;
                    }
                }
                decompressed = prepareDecompressBuffer(allocator, decompressed, inflater.getRemaining() << 1);
            }

            int remaining = inflater.getRemaining();
            in.skipReadable(readableBytes - remaining);

            if (readFooter) {
                gzipState = GzipState.FOOTER_START;
                handleGzipFooter(in);
            }

            if (decompressed.readableBytes() > 0) {
                return decompressed;
            } else {
                decompressed.close();
                return null;
            }
        } catch (DataFormatException e) {
            decompressed.close();
            throw new DecompressionException("decompression failure", e);
        } catch (Throwable cause) {
            decompressed.close();
            throw cause;
        }
    }

    private boolean handleGzipFooter(Buffer in) {
        if (readGZIPFooter(in)) {
            finished = !decompressConcatenated;

            if (!finished) {
                inflater.reset();
                crc.reset();
                gzipState = GzipState.HEADER_START;
                return true;
            }
        }
        return false;
    }

    private boolean readGZIPHeader(Buffer in) {
        switch (gzipState) {
            case HEADER_START:
                if (in.readableBytes() < 10) {
                    return false;
                }
                // read magic numbers
                int magic0 = in.readByte();
                int magic1 = in.readByte();

                if (magic0 != 31) {
                    throw new DecompressionException("Input is not in the GZIP format");
                }
                crc.update(magic0);
                crc.update(magic1);

                int method = in.readUnsignedByte();
                if (method != Deflater.DEFLATED) {
                    throw new DecompressionException("Unsupported compression method "
                            + method + " in the GZIP header");
                }
                crc.update(method);

                flags = in.readUnsignedByte();
                crc.update(flags);

                if ((flags & FRESERVED) != 0) {
                    throw new DecompressionException(
                            "Reserved flags are set in the GZIP header");
                }

                // mtime (int)
                crc.update(in, in.readerOffset(), 4);
                in.skipReadable(4);

                crc.update(in.readUnsignedByte()); // extra flags
                crc.update(in.readUnsignedByte()); // operating system

                gzipState = GzipState.FLG_READ;
                // fall through
            case FLG_READ:
                if ((flags & FEXTRA) != 0) {
                    if (in.readableBytes() < 2) {
                        return false;
                    }
                    int xlen1 = in.readUnsignedByte();
                    int xlen2 = in.readUnsignedByte();
                    crc.update(xlen1);
                    crc.update(xlen2);

                    xlen |= xlen1 << 8 | xlen2;
                }
                gzipState = GzipState.XLEN_READ;
                // fall through
            case XLEN_READ:
                if (xlen != -1) {
                    if (in.readableBytes() < xlen) {
                        return false;
                    }
                    crc.update(in, in.readerOffset(), xlen);
                    in.skipReadable(xlen);
                }
                gzipState = GzipState.SKIP_FNAME;
                // fall through
            case SKIP_FNAME:
                if (!skipIfNeeded(in, FNAME)) {
                    return false;
                }
                gzipState = GzipState.SKIP_COMMENT;
                // fall through
            case SKIP_COMMENT:
                if (!skipIfNeeded(in, FCOMMENT)) {
                    return false;
                }
                gzipState = GzipState.PROCESS_FHCRC;
                // fall through
            case PROCESS_FHCRC:
                if ((flags & FHCRC) != 0) {
                    if (!verifyCrc16(in)) {
                        return false;
                    }
                }
                crc.reset();
                gzipState = GzipState.HEADER_END;
                // fall through
            case HEADER_END:
                return true;
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Skip bytes in the input if needed until we find the end marker {@code 0x00}.
     * @param   in the input
     * @param   flagMask the mask that should be present in the {@code flags} when we need to skip bytes.
     * @return  {@code true} if the operation is complete and we can move to the next state, {@code false} if
     * we need to retry again once we have more readable bytes.
     */
    private boolean skipIfNeeded(Buffer in, int flagMask) {
        if ((flags & flagMask) != 0) {
            for (;;) {
                if (in.readableBytes() == 0) {
                    // We didnt find the end yet, need to retry again once more data is readable
                    return false;
                }
                int b = in.readUnsignedByte();
                crc.update(b);
                if (b == 0x00) {
                    break;
                }
            }
        }
        // Skip is handled, we can move to the next processing state.
        return true;
    }

    /**
     * Read the GZIP footer.
     *
     * @param   in the input.
     * @return  {@code true} if the footer could be read, {@code false} if the read could not be performed as
     *          the input {@link Buffer} doesn't have enough readable bytes (8 bytes).
     */
    private boolean readGZIPFooter(Buffer in) {
        if (in.readableBytes() < 8) {
            return false;
        }

        boolean enoughData = verifyCrc(in);
        assert enoughData;

        // read ISIZE and verify
        int dataLength = 0;
        for (int i = 0; i < 4; ++i) {
            dataLength |= in.readUnsignedByte() << i * 8;
        }
        int readLength = inflater.getTotalOut();
        if (dataLength != readLength) {
            throw new DecompressionException(
                    "Number of bytes mismatch. Expected: " + dataLength + ", Got: " + readLength);
        }
        return true;
    }

    /**
     * Verifies CRC.
     *
     * @param   in the input.
     * @return  {@code true} if verification could be performed, {@code false} if verification could not be
     * performed as the input {@link Buffer} doesn't have enough readable bytes (4 bytes).
     */
    private boolean verifyCrc(Buffer in) {
        if (in.readableBytes() < 4) {
            return false;
        }
        long crcValue = 0;
        for (int i = 0; i < 4; ++i) {
            crcValue |= (long) in.readUnsignedByte() << i * 8;
        }
        long readCrc = crc.getValue();
        if (crcValue != readCrc) {
            throw new DecompressionException(
                    "CRC value mismatch. Expected: " + crcValue + ", Got: " + readCrc);
        }
        return true;
    }

    private boolean verifyCrc16(Buffer in) {
        if (in.readableBytes() < 2) {
            return false;
        }
        long readCrc32 = crc.getValue();
        long crc16Value = 0;
        long readCrc16 = 0; // the two least significant bytes from the CRC32
        for (int i = 0; i < 2; ++i) {
            crc16Value |= (long) in.readUnsignedByte() << (i * 8);
            readCrc16 |= ((readCrc32 >> (i * 8)) & 0xff) << (i * 8);
        }

        if (crc16Value != readCrc16) {
            throw new DecompressionException(
                    "CRC16 value mismatch. Expected: " + crc16Value + ", Got: " + readCrc16);
        }
        return true;
    }

    /*
     * Returns true if the cmf_flg parameter (think: first two bytes of a zlib stream)
     * indicates that this is a zlib stream.
     * <p>
     * You can lookup the details in the ZLIB RFC:
     * <a href="https://tools.ietf.org/html/rfc1950#section-2.2">RFC 1950</a>.
     */
    private static boolean looksLikeZlib(short cmf_flg) {
        return (cmf_flg & 0x7800) == 0x7800 &&
                cmf_flg % 31 == 0;
    }

    /**
     * Allocate or expand the decompression buffer, without exceeding the maximum allocation.
     * Calls {@link #decompressionBufferExhausted(Buffer)} if the buffer is full and cannot be expanded further.
     */
    protected Buffer prepareDecompressBuffer(BufferAllocator allocator, Buffer buffer, int preferredSize) {
        if (buffer == null) {
            if (maxAllocation == 0) {
                return allocator.allocate(preferredSize);
            }

            Buffer buf = allocator.allocate(Math.min(preferredSize, maxAllocation));
            buf.implicitCapacityLimit(maxAllocation);
            return buf;
        }

        // TODO:
        buffer.ensureWritable(preferredSize);
        if (buffer.writableBytes() < preferredSize) {
            decompressionBufferExhausted(buffer);
            buffer.skipReadable(buffer.readableBytes());
            throw new DecompressionException(
                    "Decompression buffer has reached maximum size: " + buffer.capacity());
        }
        return buffer;
    }

    protected void decompressionBufferExhausted(Buffer buffer) {
        finished = true;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() {
        closed = true;
        finished = true;
        if (inflater != null) {
            inflater.end();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
