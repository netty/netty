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
package io.netty5.buffer;

import io.netty.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.nio.charset.StandardCharsets;

import static io.netty5.util.internal.MathUtil.isOutOfBounds;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty5.util.internal.StringUtil.NEWLINE;
import static java.util.Objects.requireNonNull;

/**
 * A collection of utility methods that is related with handling {@code ByteBuf},
 * such as the generation of hex dump and swapping an integer's byte order.
 */
public final class BufferUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BufferUtil.class);

    private static final int MAX_CHAR_BUFFER_SIZE;
    private static final int THREAD_LOCAL_BUFFER_SIZE;

    static {
        THREAD_LOCAL_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty5.threadLocalDirectBufferSize", 0);
        logger.debug("-Dio.netty5.threadLocalDirectBufferSize: {}", THREAD_LOCAL_BUFFER_SIZE);

        MAX_CHAR_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty5.maxThreadLocalCharBufferSize", 16 * 1024);
        logger.debug("-Dio.netty5.maxThreadLocalCharBufferSize: {}", MAX_CHAR_BUFFER_SIZE);
    }

    /**
     * Reverses the byte order (endianness) of an unsigned short
     * @param value the number with original byte order
     * @return the number with reversed byte order
     */
    public static int reverseUnsignedShort(int value) {
        return Integer.reverseBytes(value) >>> Short.SIZE;
    }

    /**
     * Reverses the byte order (endianness) of a medium
     * @param value the number with original byte order
     * @return the number with reversed byte order
     */
    public static int reverseMedium(int value) {
        return Integer.reverseBytes(value) >> Byte.SIZE;
    }

    /**
     * Reverses the byte order (endianness) of an unsigned medium
     * @param value the number with original byte order
     * @return the number with reversed byte order
     */
    public static int reverseUnsignedMedium(int value) {
        return Integer.reverseBytes(value) >>> Byte.SIZE;
    }

    /**
     * Reverses the byte order (endianness) of an unsigned integer
     * @param value the number with original byte order
     * @return the number with reversed byte order
     */
    public static long reverseUnsignedInt(long value) {
        return Long.reverseBytes(value) >>> Integer.SIZE;
    }

    /**
     * Returns a <a href="https://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(Buffer buffer) {
        return hexDump(buffer, buffer.readerOffset(), buffer.readableBytes());
    }

    /**
     * Returns a <a href="https://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(Buffer buffer, int fromIndex, int length) {
        return HexUtil.hexDump(buffer, fromIndex, length);
    }

    /**
     * Returns a <a href="https://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array.
     */
    public static String hexDump(byte[] array) {
        return hexDump(array, 0, array.length);
    }

    /**
     * Returns a <a href="https://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array's sub-region.
     */
    public static String hexDump(byte[] array, int fromIndex, int length) {
        return HexUtil.hexDump(array, fromIndex, length);
    }

    /**
     * Encode a {@link CharSequence} in <a href="https://en.wikipedia.org/wiki/ASCII">ASCII</a> and write
     * it to a {@link Buffer} allocated with {@code alloc}.
     * @param alloc The allocator used to allocate a new {@link Buffer}.
     * @param seq The characters to write into a buffer.
     * @return The {@link Buffer} which contains the <a href="https://en.wikipedia.org/wiki/ASCII">ASCII</a> encoded
     * result.
     */
    public static Buffer writeAscii(BufferAllocator alloc, CharSequence seq) {
        return alloc.copyOf(seq.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Create a copy of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@link Buffer#readerOffset()} and copy {@link Buffer#readableBytes()} bytes.
     */
    public static byte[] getBytes(Buffer buf) {
        return getBytes(buf,  buf.readerOffset(), buf.readableBytes());
    }

    /**
     * Create a copy of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     */
    public static byte[] getBytes(Buffer buf, int start, int length) {
        int capacity = buf.capacity();
        if (isOutOfBounds(start, length, capacity)) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                                                + ") <= " + "buf.capacity(" + capacity + ')');
        }

        byte[] bytes = PlatformDependent.allocateUninitializedArray(length);
        buf.copyInto(start, bytes, 0, length);
        return bytes;
    }

    /**
     * Copies the content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#setBytes(int, byte[], int, int)}.
     * Unlike the {@link #copy(AsciiString, int, ByteBuf, int)} method,
     * this method do not increase a {@code writerIndex} of {@code dst} buffer.
     *
     * @param src the source string to copy
     * @param srcIdx the starting offset of characters to copy
     * @param dst the destination buffer
     * @param dstIdx the starting offset in the destination buffer
     * @param length the number of characters to copy
     */
    public static void copy(AsciiString src, int srcIdx, ByteBuf dst, int dstIdx, int length) {
        if (isOutOfBounds(srcIdx, length, src.length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                                                + length + ") <= srcLen(" + src.length() + ')');
        }

        requireNonNull(dst, "dst").setBytes(dstIdx, src.array(), srcIdx + src.arrayOffset(), length);
    }

    /**
     * Copies the content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     *
     * @param src the source string to copy
     * @param srcIdx the starting offset of characters to copy
     * @param dst the destination buffer
     * @param length the number of characters to copy
     */
    public static void copy(AsciiString src, int srcIdx, ByteBuf dst, int length) {
        if (isOutOfBounds(srcIdx, length, src.length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + src.length() + ')');
        }

        requireNonNull(dst, "dst").writeBytes(src.array(), srcIdx + src.arrayOffset(), length);
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other for {@code length} bytes starting at {@code firstReaderOffset}
     * index for the {@code first} buffer and {@code secondReaderOffset} index for the {@code second} buffer.
     * A more compact way to express this is:
     * <p>
     * {@code first[firstRoff : firstRoff + length] == second[secondRoff : secondRoff + length]}
     */
    public static boolean equals(Buffer first, int firstReaderOffset, Buffer second, int secondReaderOffset,
                                 int length) {
        return Statics.equals(first, firstReaderOffset, second, secondReaderOffset, length);
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified {@link Buffer} to the specified
     * {@link StringBuilder} that is easy to read by humans.
     */
    public static void appendPrettyHexDump(StringBuilder dump, Buffer buf) {
        appendPrettyHexDump(dump, buf, buf.readerOffset(), buf.readableBytes());
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified {@link Buffer} to the specified
     * {@link StringBuilder} that is easy to read by humans, starting at the given {@code offset} using
     * the given {@code length}.
     */
    public static void appendPrettyHexDump(StringBuilder dump, Buffer buf, int offset, int length) {
        HexUtil.appendPrettyHexDump(dump, buf, offset, length);
    }

    /**
     * Returns a cached thread-local direct buffer, if available.
     *
     * @return a cached thread-local direct buffer, if available.  {@code null} otherwise.
     */
    public static Buffer threadLocalDirectBuffer() {
        if (THREAD_LOCAL_BUFFER_SIZE <= 0) {
            return null;
        }

        return ThreadLocalDirectBufferHolder.BUFFER.get();
    }

    /* Separate class so that the expensive static initialization is only done when needed */
    private static final class HexUtil {

        private static final char[] BYTE2CHAR = new char[256];
        private static final char[] HEXDUMP_TABLE = new char[256 * 4];
        private static final String[] HEXPADDING = new String[16];
        private static final String[] HEXDUMP_ROWPREFIXES = new String[65536 >>> 4];
        private static final String[] BYTE2HEX = new String[256];
        private static final String[] BYTEPADDING = new String[16];

        static {
            final char[] DIGITS = "0123456789abcdef".toCharArray();
            for (int i = 0; i < 256; i++) {
                HEXDUMP_TABLE[i << 1] = DIGITS[i >>> 4 & 0x0F];
                HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i & 0x0F];
            }

            int i;

            // Generate the lookup table for hex dump paddings
            for (i = 0; i < HEXPADDING.length; i++) {
                int padding = HEXPADDING.length - i;
                HEXPADDING[i] = "   ".repeat(padding);
            }

            // Generate the lookup table for the start-offset header in each row (up to 64KiB).
            for (i = 0; i < HEXDUMP_ROWPREFIXES.length; i++) {
                StringBuilder buf = new StringBuilder(12);
                buf.append(NEWLINE);
                buf.append(Long.toHexString(i << 4L & 0xFFFFFFFFL | 0x100000000L));
                buf.setCharAt(buf.length() - 9, '|');
                buf.append('|');
                HEXDUMP_ROWPREFIXES[i] = buf.toString();
            }

            // Generate the lookup table for byte-to-hex-dump conversion
            for (i = 0; i < BYTE2HEX.length; i++) {
                BYTE2HEX[i] = ' ' + StringUtil.byteToHexStringPadded(i);
            }

            // Generate the lookup table for byte dump paddings
            for (i = 0; i < BYTEPADDING.length; i++) {
                int padding = BYTEPADDING.length - i;
                BYTEPADDING[i] = " ".repeat(padding);
            }

            // Generate the lookup table for byte-to-char conversion
            for (i = 0; i < BYTE2CHAR.length; i++) {
                if (i <= 0x1f || i >= 0x7f) {
                    BYTE2CHAR[i] = '.';
                } else {
                    BYTE2CHAR[i] = (char) i;
                }
            }
        }

        private static String hexDump(Buffer buffer, int fromIndex, int length) {
            checkPositiveOrZero(length, "length");
            if (length == 0) {
              return "";
            }

            int endIndex = fromIndex + length;
            char[] buf = new char[length << 1];

            int srcIdx = fromIndex;
            int dstIdx = 0;
            for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
              System.arraycopy(
                  HEXDUMP_TABLE, buffer.getUnsignedByte(srcIdx) << 1,
                  buf, dstIdx, 2);
            }

            return new String(buf);
        }

        private static String hexDump(byte[] array, int fromIndex, int length) {
            checkPositiveOrZero(length, "length");
            if (length == 0) {
                return "";
            }

            int endIndex = fromIndex + length;
            char[] buf = new char[length << 1];

            int srcIdx = fromIndex;
            int dstIdx = 0;
            for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
                System.arraycopy(
                    HEXDUMP_TABLE, (array[srcIdx] & 0xFF) << 1,
                    buf, dstIdx, 2);
            }

            return new String(buf);
        }

        private static void appendPrettyHexDump(StringBuilder dump, Buffer buf, int offset, int length) {
            if (isOutOfBounds(offset, length, buf.capacity())) {
                throw new IndexOutOfBoundsException(
                        "expected: " + "0 <= offset(" + offset + ") <= offset + length(" + length
                        + ") <= " + "buf.capacity(" + buf.capacity() + ')');
            }
            if (length == 0) {
                return;
            }
            dump.append(
                    "         +-------------------------------------------------+" +
                    NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                    NEWLINE + "+--------+-------------------------------------------------+----------------+");

            final int fullRows = length >>> 4;
            final int remainder = length & 0xF;

            // Dump the rows which have 16 bytes.
            for (int row = 0; row < fullRows; row ++) {
                int rowStartIndex = (row << 4) + offset;

                // Per-row prefix.
                appendHexDumpRowPrefix(dump, row, rowStartIndex);

                // Hex dump
                int rowEndIndex = rowStartIndex + 16;
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2HEX[buf.getUnsignedByte(j)]);
                }
                dump.append(" |");

                // ASCII dump
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                }
                dump.append('|');
            }

            // Dump the last row which has less than 16 bytes.
            if (remainder != 0) {
                int rowStartIndex = (fullRows << 4) + offset;
                appendHexDumpRowPrefix(dump, fullRows, rowStartIndex);

                // Hex dump
                int rowEndIndex = rowStartIndex + remainder;
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2HEX[buf.getUnsignedByte(j)]);
                }
                dump.append(HEXPADDING[remainder]);
                dump.append(" |");

                // Ascii dump
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                }
                dump.append(BYTEPADDING[remainder]);
                dump.append('|');
            }

            dump.append(NEWLINE +
                        "+--------+-------------------------------------------------+----------------+");
        }

        private static void appendHexDumpRowPrefix(StringBuilder dump, int row, int rowStartIndex) {
            if (row < HEXDUMP_ROWPREFIXES.length) {
                dump.append(HEXDUMP_ROWPREFIXES[row]);
            } else {
                dump.append(NEWLINE);
                dump.append(Long.toHexString(rowStartIndex & 0xFFFFFFFFL | 0x100000000L));
                dump.setCharAt(dump.length() - 9, '|');
                dump.append('|');
            }
        }
    }

    /* Separate class so that the expensive static initialisation is only done when needed */
    private static final class ThreadLocalDirectBufferHolder {
        static final FastThreadLocal<Buffer> BUFFER = new FastThreadLocal<Buffer>() {
            @Override
            protected Buffer initialValue() throws Exception {
                return DefaultBufferAllocators.offHeapAllocator().allocate(1024);
            }

            @Override
            protected void onRemoval(Buffer value) throws Exception {
                if (value.isAccessible()) {
                    value.close();
                }
                super.onRemoval(value);
            }
        };
    }

    private BufferUtil() { }
}
