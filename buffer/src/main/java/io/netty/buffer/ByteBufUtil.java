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
package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Locale;

import static io.netty.util.internal.MathUtil.isOutOfBounds;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.isSurrogate;

/**
 * A collection of utility methods that is related with handling {@link ByteBuf},
 * such as the generation of hex dump and swapping an integer's byte order.
 */
public final class ByteBufUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ByteBufUtil.class);
    private static final FastThreadLocal<byte[]> BYTE_ARRAYS = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() throws Exception {
            return PlatformDependent.allocateUninitializedArray(MAX_TL_ARRAY_LEN);
        }
    };

    private static final byte WRITE_UTF_UNKNOWN = (byte) '?';
    private static final int MAX_CHAR_BUFFER_SIZE;
    private static final int THREAD_LOCAL_BUFFER_SIZE;
    private static final int MAX_BYTES_PER_CHAR_UTF8 =
            (int) CharsetUtil.encoder(CharsetUtil.UTF_8).maxBytesPerChar();

    static final int WRITE_CHUNK_SIZE = 8192;
    static final ByteBufAllocator DEFAULT_ALLOCATOR;

    static {
        String allocType = SystemPropertyUtil.get(
                "io.netty.allocator.type", PlatformDependent.isAndroid() ? "unpooled" : "pooled");
        allocType = allocType.toLowerCase(Locale.US).trim();

        ByteBufAllocator alloc;
        if ("unpooled".equals(allocType)) {
            alloc = UnpooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else if ("pooled".equals(allocType)) {
            alloc = PooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else {
            alloc = PooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: pooled (unknown: {})", allocType);
        }

        DEFAULT_ALLOCATOR = alloc;

        THREAD_LOCAL_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty.threadLocalDirectBufferSize", 0);
        logger.debug("-Dio.netty.threadLocalDirectBufferSize: {}", THREAD_LOCAL_BUFFER_SIZE);

        MAX_CHAR_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty.maxThreadLocalCharBufferSize", 16 * 1024);
        logger.debug("-Dio.netty.maxThreadLocalCharBufferSize: {}", MAX_CHAR_BUFFER_SIZE);
    }

    static final int MAX_TL_ARRAY_LEN = 1024;

    /**
     * Allocates a new array if minLength > {@link ByteBufUtil#MAX_TL_ARRAY_LEN}
     */
    static byte[] threadLocalTempArray(int minLength) {
        return minLength <= MAX_TL_ARRAY_LEN ? BYTE_ARRAYS.get()
            : PlatformDependent.allocateUninitializedArray(minLength);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(ByteBuf buffer) {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(ByteBuf buffer, int fromIndex, int length) {
        return HexUtil.hexDump(buffer, fromIndex, length);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array.
     */
    public static String hexDump(byte[] array) {
        return hexDump(array, 0, array.length);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array's sub-region.
     */
    public static String hexDump(byte[] array, int fromIndex, int length) {
        return HexUtil.hexDump(array, fromIndex, length);
    }

    /**
     * Decode a 2-digit hex byte from within a string.
     */
    public static byte decodeHexByte(CharSequence s, int pos) {
        return StringUtil.decodeHexByte(s, pos);
    }

    /**
     * Decodes a string generated by {@link #hexDump(byte[])}
     */
    public static byte[] decodeHexDump(CharSequence hexDump) {
        return StringUtil.decodeHexDump(hexDump, 0, hexDump.length());
    }

    /**
     * Decodes part of a string generated by {@link #hexDump(byte[])}
     */
    public static byte[] decodeHexDump(CharSequence hexDump, int fromIndex, int length) {
        return StringUtil.decodeHexDump(hexDump, fromIndex, length);
    }

    /**
     * Used to determine if the return value of {@link ByteBuf#ensureWritable(int, boolean)} means that there is
     * adequate space and a write operation will succeed.
     * @param ensureWritableResult The return value from {@link ByteBuf#ensureWritable(int, boolean)}.
     * @return {@code true} if {@code ensureWritableResult} means that there is adequate space and a write operation
     * will succeed.
     */
    public static boolean ensureWritableSuccess(int ensureWritableResult) {
        return ensureWritableResult == 0 || ensureWritableResult == 2;
    }

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    public static int hashCode(ByteBuf buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = EmptyByteBuf.EMPTY_BYTE_BUF_HASH_CODE;
        int arrayIndex = buffer.readerIndex();
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
                arrayIndex += 4;
            }
        } else {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex));
                arrayIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    /**
     * Returns the reader index of needle in haystack, or -1 if needle is not in haystack.
     */
    public static int indexOf(ByteBuf needle, ByteBuf haystack) {
        // TODO: maybe use Boyer Moore for efficiency.
        int attempts = haystack.readableBytes() - needle.readableBytes() + 1;
        for (int i = 0; i < attempts; i++) {
            if (equals(needle, needle.readerIndex(),
                       haystack, haystack.readerIndex() + i,
                       needle.readableBytes())) {
                return haystack.readerIndex() + i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other for {@code length} bytes starting at {@code aStartIndex}
     * index for the {@code a} buffer and {@code bStartIndex} index for the {@code b} buffer.
     * A more compact way to express this is:
     * <p>
     * {@code a[aStartIndex : aStartIndex + length] == b[bStartIndex : bStartIndex + length]}
     */
    public static boolean equals(ByteBuf a, int aStartIndex, ByteBuf b, int bStartIndex, int length) {
        if (aStartIndex < 0 || bStartIndex < 0 || length < 0) {
            throw new IllegalArgumentException("All indexes and lengths must be non-negative");
        }
        if (a.writerIndex() - length < aStartIndex || b.writerIndex() - length < bStartIndex) {
            return false;
        }

        final int longCount = length >>> 3;
        final int byteCount = length & 7;

        if (a.order() == b.order()) {
            for (int i = longCount; i > 0; i --) {
                if (a.getLong(aStartIndex) != b.getLong(bStartIndex)) {
                    return false;
                }
                aStartIndex += 8;
                bStartIndex += 8;
            }
        } else {
            for (int i = longCount; i > 0; i --) {
                if (a.getLong(aStartIndex) != swapLong(b.getLong(bStartIndex))) {
                    return false;
                }
                aStartIndex += 8;
                bStartIndex += 8;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            if (a.getByte(aStartIndex) != b.getByte(bStartIndex)) {
                return false;
            }
            aStartIndex ++;
            bStartIndex ++;
        }

        return true;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other as described in {@link ByteBuf#equals(Object)}.
     * This method is useful when implementing a new buffer type.
     */
    public static boolean equals(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }
        return equals(bufferA, bufferA.readerIndex(), bufferB, bufferB.readerIndex(), aLen);
    }

    /**
     * Compares the two specified buffers as described in {@link ByteBuf#compareTo(ByteBuf)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int compare(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        final int minLength = Math.min(aLen, bLen);
        final int uintCount = minLength >>> 2;
        final int byteCount = minLength & 3;
        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (uintCount > 0) {
            boolean bufferAIsBigEndian = bufferA.order() == ByteOrder.BIG_ENDIAN;
            final long res;
            int uintCountIncrement = uintCount << 2;

            if (bufferA.order() == bufferB.order()) {
                res = bufferAIsBigEndian ? compareUintBigEndian(bufferA, bufferB, aIndex, bIndex, uintCountIncrement) :
                        compareUintLittleEndian(bufferA, bufferB, aIndex, bIndex, uintCountIncrement);
            } else {
                res = bufferAIsBigEndian ? compareUintBigEndianA(bufferA, bufferB, aIndex, bIndex, uintCountIncrement) :
                        compareUintBigEndianB(bufferA, bufferB, aIndex, bIndex, uintCountIncrement);
            }
            if (res != 0) {
                // Ensure we not overflow when cast
                return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, res));
            }
            aIndex += uintCountIncrement;
            bIndex += uintCountIncrement;
        }

        for (int aEnd = aIndex + byteCount; aIndex < aEnd; ++aIndex, ++bIndex) {
            int comp = bufferA.getUnsignedByte(aIndex) - bufferB.getUnsignedByte(bIndex);
            if (comp != 0) {
                return comp;
            }
        }

        return aLen - bLen;
    }

    private static long compareUintBigEndian(
            ByteBuf bufferA, ByteBuf bufferB, int aIndex, int bIndex, int uintCountIncrement) {
        for (int aEnd = aIndex + uintCountIncrement; aIndex < aEnd; aIndex += 4, bIndex += 4) {
            long comp = bufferA.getUnsignedInt(aIndex) - bufferB.getUnsignedInt(bIndex);
            if (comp != 0) {
                return comp;
            }
        }
        return 0;
    }

    private static long compareUintLittleEndian(
            ByteBuf bufferA, ByteBuf bufferB, int aIndex, int bIndex, int uintCountIncrement) {
        for (int aEnd = aIndex + uintCountIncrement; aIndex < aEnd; aIndex += 4, bIndex += 4) {
            long comp = bufferA.getUnsignedIntLE(aIndex) - bufferB.getUnsignedIntLE(bIndex);
            if (comp != 0) {
                return comp;
            }
        }
        return 0;
    }

    private static long compareUintBigEndianA(
            ByteBuf bufferA, ByteBuf bufferB, int aIndex, int bIndex, int uintCountIncrement) {
        for (int aEnd = aIndex + uintCountIncrement; aIndex < aEnd; aIndex += 4, bIndex += 4) {
            long comp =  bufferA.getUnsignedInt(aIndex) - bufferB.getUnsignedIntLE(bIndex);
            if (comp != 0) {
                return comp;
            }
        }
        return 0;
    }

    private static long compareUintBigEndianB(
            ByteBuf bufferA, ByteBuf bufferB, int aIndex, int bIndex, int uintCountIncrement) {
        for (int aEnd = aIndex + uintCountIncrement; aIndex < aEnd; aIndex += 4, bIndex += 4) {
            long comp =  bufferA.getUnsignedIntLE(aIndex) - bufferB.getUnsignedInt(bIndex);
            if (comp != 0) {
                return comp;
            }
        }
        return 0;
    }

    /**
     * The default implementation of {@link ByteBuf#indexOf(int, int, byte)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, value);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, value);
        }
    }

    /**
     * Toggles the endianness of the specified 16-bit short integer.
     */
    public static short swapShort(short value) {
        return Short.reverseBytes(value);
    }

    /**
     * Toggles the endianness of the specified 24-bit medium integer.
     */
    public static int swapMedium(int value) {
        int swapped = value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
        if ((swapped & 0x800000) != 0) {
            swapped |= 0xff000000;
        }
        return swapped;
    }

    /**
     * Toggles the endianness of the specified 32-bit integer.
     */
    public static int swapInt(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Toggles the endianness of the specified 64-bit long integer.
     */
    public static long swapLong(long value) {
        return Long.reverseBytes(value);
    }

    /**
     * Writes a big-endian 16-bit short integer to the buffer.
     */
    @SuppressWarnings("deprecation")
    public static ByteBuf writeShortBE(ByteBuf buf, int shortValue) {
        return buf.order() == ByteOrder.BIG_ENDIAN? buf.writeShort(shortValue) : buf.writeShortLE(shortValue);
    }

    /**
     * Sets a big-endian 16-bit short integer to the buffer.
     */
    @SuppressWarnings("deprecation")
    public static ByteBuf setShortBE(ByteBuf buf, int index, int shortValue) {
        return buf.order() == ByteOrder.BIG_ENDIAN? buf.setShort(index, shortValue) : buf.setShortLE(index, shortValue);
    }

    /**
     * Writes a big-endian 24-bit medium integer to the buffer.
     */
    @SuppressWarnings("deprecation")
    public static ByteBuf writeMediumBE(ByteBuf buf, int mediumValue) {
        return buf.order() == ByteOrder.BIG_ENDIAN? buf.writeMedium(mediumValue) : buf.writeMediumLE(mediumValue);
    }

    /**
     * Read the given amount of bytes into a new {@link ByteBuf} that is allocated from the {@link ByteBufAllocator}.
     */
    public static ByteBuf readBytes(ByteBufAllocator alloc, ByteBuf buffer, int length) {
        boolean release = true;
        ByteBuf dst = alloc.buffer(length);
        try {
            buffer.readBytes(dst);
            release = false;
            return dst;
        } finally {
            if (release) {
                dst.release();
            }
        }
    }

    private static int firstIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        return buffer.forEachByte(fromIndex, toIndex - fromIndex, new ByteProcessor.IndexOfProcessor(value));
    }

    private static int lastIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        int capacity = buffer.capacity();
        fromIndex = Math.min(fromIndex, capacity);
        if (fromIndex < 0 || capacity == 0) {
            return -1;
        }

        return buffer.forEachByteDesc(toIndex, fromIndex - toIndex, new ByteProcessor.IndexOfProcessor(value));
    }

    private static CharSequence checkCharSequenceBounds(CharSequence seq, int start, int end) {
        if (MathUtil.isOutOfBounds(start, end - start, seq.length())) {
            throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= end (" + end
                    + ") <= seq.length(" + seq.length() + ')');
        }
        return seq;
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write
     * it to a {@link ByteBuf} allocated with {@code alloc}.
     * @param alloc The allocator used to allocate a new {@link ByteBuf}.
     * @param seq The characters to write into a buffer.
     * @return The {@link ByteBuf} which contains the <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> encoded
     * result.
     */
    public static ByteBuf writeUtf8(ByteBufAllocator alloc, CharSequence seq) {
        // UTF-8 uses max. 3 bytes per char, so calculate the worst case.
        ByteBuf buf = alloc.buffer(utf8MaxBytes(seq));
        writeUtf8(buf, seq);
        return buf;
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write
     * it to a {@link ByteBuf}.
     * <p>
     * It behaves like {@link #reserveAndWriteUtf8(ByteBuf, CharSequence, int)} with {@code reserveBytes}
     * computed by {@link #utf8MaxBytes(CharSequence)}.<br>
     * This method returns the actual number of bytes written.
     */
    public static int writeUtf8(ByteBuf buf, CharSequence seq) {
        int seqLength = seq.length();
        return reserveAndWriteUtf8Seq(buf, seq, 0, seqLength, utf8MaxBytes(seqLength));
    }

    /**
     * Equivalent to <code>{@link #writeUtf8(ByteBuf, CharSequence) writeUtf8(buf, seq.subSequence(start, end))}</code>
     * but avoids subsequence object allocation.
     */
    public static int writeUtf8(ByteBuf buf, CharSequence seq, int start, int end) {
        checkCharSequenceBounds(seq, start, end);
        return reserveAndWriteUtf8Seq(buf, seq, start, end, utf8MaxBytes(end - start));
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write
     * it into {@code reserveBytes} of a {@link ByteBuf}.
     * <p>
     * The {@code reserveBytes} must be computed (ie eagerly using {@link #utf8MaxBytes(CharSequence)}
     * or exactly with {@link #utf8Bytes(CharSequence)}) to ensure this method to not fail: for performance reasons
     * the index checks will be performed using just {@code reserveBytes}.<br>
     * This method returns the actual number of bytes written.
     */
    public static int reserveAndWriteUtf8(ByteBuf buf, CharSequence seq, int reserveBytes) {
        return reserveAndWriteUtf8Seq(buf, seq, 0, seq.length(), reserveBytes);
    }

    /**
     * Equivalent to <code>{@link #reserveAndWriteUtf8(ByteBuf, CharSequence, int)
     * reserveAndWriteUtf8(buf, seq.subSequence(start, end), reserveBytes)}</code> but avoids
     * subsequence object allocation if possible.
     *
     * @return actual number of bytes written
     */
    public static int reserveAndWriteUtf8(ByteBuf buf, CharSequence seq, int start, int end, int reserveBytes) {
        return reserveAndWriteUtf8Seq(buf, checkCharSequenceBounds(seq, start, end), start, end, reserveBytes);
    }

    private static int reserveAndWriteUtf8Seq(ByteBuf buf, CharSequence seq, int start, int end, int reserveBytes) {
        for (;;) {
            if (buf instanceof WrappedCompositeByteBuf) {
                // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                buf = buf.unwrap();
            } else if (buf instanceof AbstractByteBuf) {
                AbstractByteBuf byteBuf = (AbstractByteBuf) buf;
                byteBuf.ensureWritable0(reserveBytes);
                int written = writeUtf8(byteBuf, byteBuf.writerIndex, seq, start, end);
                byteBuf.writerIndex += written;
                return written;
            } else if (buf instanceof WrappedByteBuf) {
                // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                buf = buf.unwrap();
            } else {
                byte[] bytes = seq.subSequence(start, end).toString().getBytes(CharsetUtil.UTF_8);
                buf.writeBytes(bytes);
                return bytes.length;
            }
        }
    }

    static int writeUtf8(AbstractByteBuf buffer, int writerIndex, CharSequence seq, int len) {
        return writeUtf8(buffer, writerIndex, seq, 0, len);
    }

    // Fast-Path implementation
    static int writeUtf8(AbstractByteBuf buffer, int writerIndex, CharSequence seq, int start, int end) {
        int oldWriterIndex = writerIndex;

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        for (int i = start; i < end; i++) {
            char c = seq.charAt(i);
            if (c < 0x80) {
                buffer._setByte(writerIndex++, (byte) c);
            } else if (c < 0x800) {
                buffer._setByte(writerIndex++, (byte) (0xc0 | (c >> 6)));
                buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
                    break;
                }
                // Extra method to allow inlining the rest of writeUtf8 which is the most likely code path.
                writerIndex = writeUtf8Surrogate(buffer, writerIndex, c, seq.charAt(i));
            } else {
                buffer._setByte(writerIndex++, (byte) (0xe0 | (c >> 12)));
                buffer._setByte(writerIndex++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
            }
        }
        return writerIndex - oldWriterIndex;
    }

    private static int writeUtf8Surrogate(AbstractByteBuf buffer, int writerIndex, char c, char c2) {
        if (!Character.isLowSurrogate(c2)) {
            buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
            buffer._setByte(writerIndex++, Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2);
            return writerIndex;
        }
        int codePoint = Character.toCodePoint(c, c2);
        // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
        buffer._setByte(writerIndex++, (byte) (0xf0 | (codePoint >> 18)));
        buffer._setByte(writerIndex++, (byte) (0x80 | ((codePoint >> 12) & 0x3f)));
        buffer._setByte(writerIndex++, (byte) (0x80 | ((codePoint >> 6) & 0x3f)));
        buffer._setByte(writerIndex++, (byte) (0x80 | (codePoint & 0x3f)));
        return writerIndex;
    }

    /**
     * Returns max bytes length of UTF8 character sequence of the given length.
     */
    public static int utf8MaxBytes(final int seqLength) {
        return seqLength * MAX_BYTES_PER_CHAR_UTF8;
    }

    /**
     * Returns max bytes length of UTF8 character sequence.
     * <p>
     * It behaves like {@link #utf8MaxBytes(int)} applied to {@code seq} {@link CharSequence#length()}.
     */
    public static int utf8MaxBytes(CharSequence seq) {
        return utf8MaxBytes(seq.length());
    }

    /**
     * Returns the exact bytes length of UTF8 character sequence.
     * <p>
     * This method is producing the exact length according to {@link #writeUtf8(ByteBuf, CharSequence)}.
     */
    public static int utf8Bytes(final CharSequence seq) {
        return utf8ByteCount(seq, 0, seq.length());
    }

    /**
     * Equivalent to <code>{@link #utf8Bytes(CharSequence) utf8Bytes(seq.subSequence(start, end))}</code>
     * but avoids subsequence object allocation.
     * <p>
     * This method is producing the exact length according to {@link #writeUtf8(ByteBuf, CharSequence, int, int)}.
     */
    public static int utf8Bytes(final CharSequence seq, int start, int end) {
        return utf8ByteCount(checkCharSequenceBounds(seq, start, end), start, end);
    }

    private static int utf8ByteCount(final CharSequence seq, int start, int end) {
        if (seq instanceof AsciiString) {
            return end - start;
        }
        int i = start;
        // ASCII fast path
        while (i < end && seq.charAt(i) < 0x80) {
            ++i;
        }
        // !ASCII is packed in a separate method to let the ASCII case be smaller
        return i < end ? (i - start) + utf8BytesNonAscii(seq, i, end) : i - start;
    }

    private static int utf8BytesNonAscii(final CharSequence seq, final int start, final int end) {
        int encodedLength = 0;
        for (int i = start; i < end; i++) {
            final char c = seq.charAt(i);
            // making it 100% branchless isn't rewarding due to the many bit operations necessary!
            if (c < 0x800) {
                // branchless version of: (c <= 127 ? 0:1) + 1
                encodedLength += ((0x7f - c) >>> 31) + 1;
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    encodedLength++;
                    // WRITE_UTF_UNKNOWN
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    encodedLength++;
                    // WRITE_UTF_UNKNOWN
                    break;
                }
                if (!Character.isLowSurrogate(seq.charAt(i))) {
                    // WRITE_UTF_UNKNOWN + (Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2)
                    encodedLength += 2;
                    continue;
                }
                // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                encodedLength += 4;
            } else {
                encodedLength += 3;
            }
        }
        return encodedLength;
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> and write
     * it to a {@link ByteBuf} allocated with {@code alloc}.
     * @param alloc The allocator used to allocate a new {@link ByteBuf}.
     * @param seq The characters to write into a buffer.
     * @return The {@link ByteBuf} which contains the <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> encoded
     * result.
     */
    public static ByteBuf writeAscii(ByteBufAllocator alloc, CharSequence seq) {
        // ASCII uses 1 byte per char
        ByteBuf buf = alloc.buffer(seq.length());
        writeAscii(buf, seq);
        return buf;
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> and write it
     * to a {@link ByteBuf}.
     *
     * This method returns the actual number of bytes written.
     */
    public static int writeAscii(ByteBuf buf, CharSequence seq) {
        // ASCII uses 1 byte per char
        final int len = seq.length();
        if (seq instanceof AsciiString) {
            AsciiString asciiString = (AsciiString) seq;
            buf.writeBytes(asciiString.array(), asciiString.arrayOffset(), len);
        } else {
            for (;;) {
                if (buf instanceof WrappedCompositeByteBuf) {
                    // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                    buf = buf.unwrap();
                } else if (buf instanceof AbstractByteBuf) {
                    AbstractByteBuf byteBuf = (AbstractByteBuf) buf;
                    byteBuf.ensureWritable0(len);
                    int written = writeAscii(byteBuf, byteBuf.writerIndex, seq, len);
                    byteBuf.writerIndex += written;
                    return written;
                } else if (buf instanceof WrappedByteBuf) {
                    // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                    buf = buf.unwrap();
                } else {
                    byte[] bytes = seq.toString().getBytes(CharsetUtil.US_ASCII);
                    buf.writeBytes(bytes);
                    return bytes.length;
                }
            }
        }
        return len;
    }

    // Fast-Path implementation
    static int writeAscii(AbstractByteBuf buffer, int writerIndex, CharSequence seq, int len) {

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        for (int i = 0; i < len; i++) {
            buffer._setByte(writerIndex++, AsciiString.c2b(seq.charAt(i)));
        }
        return len;
    }

    /**
     * Encode the given {@link CharBuffer} using the given {@link Charset} into a new {@link ByteBuf} which
     * is allocated via the {@link ByteBufAllocator}.
     */
    public static ByteBuf encodeString(ByteBufAllocator alloc, CharBuffer src, Charset charset) {
        return encodeString0(alloc, false, src, charset, 0);
    }

    /**
     * Encode the given {@link CharBuffer} using the given {@link Charset} into a new {@link ByteBuf} which
     * is allocated via the {@link ByteBufAllocator}.
     *
     * @param alloc The {@link ByteBufAllocator} to allocate {@link ByteBuf}.
     * @param src The {@link CharBuffer} to encode.
     * @param charset The specified {@link Charset}.
     * @param extraCapacity the extra capacity to alloc except the space for decoding.
     */
    public static ByteBuf encodeString(ByteBufAllocator alloc, CharBuffer src, Charset charset, int extraCapacity) {
        return encodeString0(alloc, false, src, charset, extraCapacity);
    }

    static ByteBuf encodeString0(ByteBufAllocator alloc, boolean enforceHeap, CharBuffer src, Charset charset,
                                 int extraCapacity) {
        final CharsetEncoder encoder = CharsetUtil.encoder(charset);
        int length = (int) ((double) src.remaining() * encoder.maxBytesPerChar()) + extraCapacity;
        boolean release = true;
        final ByteBuf dst;
        if (enforceHeap) {
            dst = alloc.heapBuffer(length);
        } else {
            dst = alloc.buffer(length);
        }
        try {
            final ByteBuffer dstBuf = dst.internalNioBuffer(dst.readerIndex(), length);
            final int pos = dstBuf.position();
            CoderResult cr = encoder.encode(src, dstBuf, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dstBuf);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            dst.writerIndex(dst.writerIndex() + dstBuf.position() - pos);
            release = false;
            return dst;
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        } finally {
            if (release) {
                dst.release();
            }
        }
    }

    @SuppressWarnings("deprecation")
    static String decodeString(ByteBuf src, int readerIndex, int len, Charset charset) {
        if (len == 0) {
            return StringUtil.EMPTY_STRING;
        }
        final byte[] array;
        final int offset;

        if (src.hasArray()) {
            array = src.array();
            offset = src.arrayOffset() + readerIndex;
        } else {
            array = threadLocalTempArray(len);
            offset = 0;
            src.getBytes(readerIndex, array, 0, len);
        }
        if (CharsetUtil.US_ASCII.equals(charset)) {
            // Fast-path for US-ASCII which is used frequently.
            return new String(array, 0, offset, len);
        }
        return new String(array, offset, len, charset);
    }

    /**
     * Returns a cached thread-local direct buffer, if available.
     *
     * @return a cached thread-local direct buffer, if available.  {@code null} otherwise.
     */
    public static ByteBuf threadLocalDirectBuffer() {
        if (THREAD_LOCAL_BUFFER_SIZE <= 0) {
            return null;
        }

        if (PlatformDependent.hasUnsafe()) {
            return ThreadLocalUnsafeDirectByteBuf.newInstance();
        } else {
            return ThreadLocalDirectByteBuf.newInstance();
        }
    }

    /**
     * Create a copy of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@link ByteBuf#readerIndex()} and copy {@link ByteBuf#readableBytes()} bytes.
     */
    public static byte[] getBytes(ByteBuf buf) {
        return getBytes(buf,  buf.readerIndex(), buf.readableBytes());
    }

    /**
     * Create a copy of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     */
    public static byte[] getBytes(ByteBuf buf, int start, int length) {
        return getBytes(buf, start, length, true);
    }

    /**
     * Return an array of the underlying storage from {@code buf} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     * If {@code copy} is true a copy will be made of the memory.
     * If {@code copy} is false the underlying storage will be shared, if possible.
     */
    public static byte[] getBytes(ByteBuf buf, int start, int length, boolean copy) {
        int capacity = buf.capacity();
        if (isOutOfBounds(start, length, capacity)) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                    + ") <= " + "buf.capacity(" + capacity + ')');
        }

        if (buf.hasArray()) {
            if (copy || start != 0 || length != capacity) {
                int baseOffset = buf.arrayOffset() + start;
                return Arrays.copyOfRange(buf.array(), baseOffset, baseOffset + length);
            } else {
                return buf.array();
            }
        }

        byte[] v = PlatformDependent.allocateUninitializedArray(length);
        buf.getBytes(start, v);
        return v;
    }

    /**
     * Copies the all content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     *
     * @param src the source string to copy
     * @param dst the destination buffer
     */
    public static void copy(AsciiString src, ByteBuf dst) {
        copy(src, 0, dst, src.length());
    }

    /**
     * Copies the content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#setBytes(int, byte[], int, int)}.
     * Unlike the {@link #copy(AsciiString, ByteBuf)} and {@link #copy(AsciiString, int, ByteBuf, int)} methods,
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

        checkNotNull(dst, "dst").setBytes(dstIdx, src.array(), srcIdx + src.arrayOffset(), length);
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

        checkNotNull(dst, "dst").writeBytes(src.array(), srcIdx + src.arrayOffset(), length);
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified {@link ByteBuf} that is easy to read by humans.
     */
    public static String prettyHexDump(ByteBuf buffer) {
        return prettyHexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified {@link ByteBuf} that is easy to read by humans,
     * starting at the given {@code offset} using the given {@code length}.
     */
    public static String prettyHexDump(ByteBuf buffer, int offset, int length) {
        return HexUtil.prettyHexDump(buffer, offset, length);
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified {@link ByteBuf} to the specified
     * {@link StringBuilder} that is easy to read by humans.
     */
    public static void appendPrettyHexDump(StringBuilder dump, ByteBuf buf) {
        appendPrettyHexDump(dump, buf, buf.readerIndex(), buf.readableBytes());
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified {@link ByteBuf} to the specified
     * {@link StringBuilder} that is easy to read by humans, starting at the given {@code offset} using
     * the given {@code length}.
     */
    public static void appendPrettyHexDump(StringBuilder dump, ByteBuf buf, int offset, int length) {
        HexUtil.appendPrettyHexDump(dump, buf, offset, length);
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
            for (int i = 0; i < 256; i ++) {
                HEXDUMP_TABLE[ i << 1     ] = DIGITS[i >>> 4 & 0x0F];
                HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i       & 0x0F];
            }

            int i;

            // Generate the lookup table for hex dump paddings
            for (i = 0; i < HEXPADDING.length; i ++) {
                int padding = HEXPADDING.length - i;
                StringBuilder buf = new StringBuilder(padding * 3);
                for (int j = 0; j < padding; j ++) {
                    buf.append("   ");
                }
                HEXPADDING[i] = buf.toString();
            }

            // Generate the lookup table for the start-offset header in each row (up to 64KiB).
            for (i = 0; i < HEXDUMP_ROWPREFIXES.length; i ++) {
                StringBuilder buf = new StringBuilder(12);
                buf.append(NEWLINE);
                buf.append(Long.toHexString(i << 4 & 0xFFFFFFFFL | 0x100000000L));
                buf.setCharAt(buf.length() - 9, '|');
                buf.append('|');
                HEXDUMP_ROWPREFIXES[i] = buf.toString();
            }

            // Generate the lookup table for byte-to-hex-dump conversion
            for (i = 0; i < BYTE2HEX.length; i ++) {
                BYTE2HEX[i] = ' ' + StringUtil.byteToHexStringPadded(i);
            }

            // Generate the lookup table for byte dump paddings
            for (i = 0; i < BYTEPADDING.length; i ++) {
                int padding = BYTEPADDING.length - i;
                StringBuilder buf = new StringBuilder(padding);
                for (int j = 0; j < padding; j ++) {
                    buf.append(' ');
                }
                BYTEPADDING[i] = buf.toString();
            }

            // Generate the lookup table for byte-to-char conversion
            for (i = 0; i < BYTE2CHAR.length; i ++) {
                if (i <= 0x1f || i >= 0x7f) {
                    BYTE2CHAR[i] = '.';
                } else {
                    BYTE2CHAR[i] = (char) i;
                }
            }
        }

        private static String hexDump(ByteBuf buffer, int fromIndex, int length) {
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

        private static String prettyHexDump(ByteBuf buffer, int offset, int length) {
            if (length == 0) {
              return StringUtil.EMPTY_STRING;
            } else {
                int rows = length / 16 + ((length & 15) == 0? 0 : 1) + 4;
                StringBuilder buf = new StringBuilder(rows * 80);
                appendPrettyHexDump(buf, buffer, offset, length);
                return buf.toString();
            }
        }

        private static void appendPrettyHexDump(StringBuilder dump, ByteBuf buf, int offset, int length) {
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

            final int startIndex = offset;
            final int fullRows = length >>> 4;
            final int remainder = length & 0xF;

            // Dump the rows which have 16 bytes.
            for (int row = 0; row < fullRows; row ++) {
                int rowStartIndex = (row << 4) + startIndex;

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
                int rowStartIndex = (fullRows << 4) + startIndex;
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

    static final class ThreadLocalUnsafeDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

        private static final ObjectPool<ThreadLocalUnsafeDirectByteBuf> RECYCLER =
                ObjectPool.newPool(new ObjectCreator<ThreadLocalUnsafeDirectByteBuf>() {
                    @Override
                    public ThreadLocalUnsafeDirectByteBuf newObject(Handle<ThreadLocalUnsafeDirectByteBuf> handle) {
                        return new ThreadLocalUnsafeDirectByteBuf(handle);
                    }
                });

        static ThreadLocalUnsafeDirectByteBuf newInstance() {
            ThreadLocalUnsafeDirectByteBuf buf = RECYCLER.get();
            buf.resetRefCnt();
            return buf;
        }

        private final Handle<ThreadLocalUnsafeDirectByteBuf> handle;

        private ThreadLocalUnsafeDirectByteBuf(Handle<ThreadLocalUnsafeDirectByteBuf> handle) {
            super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
            this.handle = handle;
        }

        @Override
        protected void deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate();
            } else {
                clear();
                handle.recycle(this);
            }
        }
    }

    static final class ThreadLocalDirectByteBuf extends UnpooledDirectByteBuf {

        private static final ObjectPool<ThreadLocalDirectByteBuf> RECYCLER = ObjectPool.newPool(
                new ObjectCreator<ThreadLocalDirectByteBuf>() {
            @Override
            public ThreadLocalDirectByteBuf newObject(Handle<ThreadLocalDirectByteBuf> handle) {
                return new ThreadLocalDirectByteBuf(handle);
            }
        });

        static ThreadLocalDirectByteBuf newInstance() {
            ThreadLocalDirectByteBuf buf = RECYCLER.get();
            buf.resetRefCnt();
            return buf;
        }

        private final Handle<ThreadLocalDirectByteBuf> handle;

        private ThreadLocalDirectByteBuf(Handle<ThreadLocalDirectByteBuf> handle) {
            super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
            this.handle = handle;
        }

        @Override
        protected void deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate();
            } else {
                clear();
                handle.recycle(this);
            }
        }
    }

    /**
     * Returns {@code true} if the given {@link ByteBuf} is valid text using the given {@link Charset},
     * otherwise return {@code false}.
     *
     * @param buf The given {@link ByteBuf}.
     * @param charset The specified {@link Charset}.
     */
    public static boolean isText(ByteBuf buf, Charset charset) {
        return isText(buf, buf.readerIndex(), buf.readableBytes(), charset);
    }

    /**
     * Returns {@code true} if the specified {@link ByteBuf} starting at {@code index} with {@code length} is valid
     * text using the given {@link Charset}, otherwise return {@code false}.
     *
     * @param buf The given {@link ByteBuf}.
     * @param index The start index of the specified buffer.
     * @param length The length of the specified buffer.
     * @param charset The specified {@link Charset}.
     *
     * @throws IndexOutOfBoundsException if {@code index} + {@code length} is greater than {@code buf.readableBytes}
     */
    public static boolean isText(ByteBuf buf, int index, int length, Charset charset) {
        checkNotNull(buf, "buf");
        checkNotNull(charset, "charset");
        final int maxIndex = buf.readerIndex() + buf.readableBytes();
        if (index < 0 || length < 0 || index > maxIndex - length) {
            throw new IndexOutOfBoundsException("index: " + index + " length: " + length);
        }
        if (charset.equals(CharsetUtil.UTF_8)) {
            return isUtf8(buf, index, length);
        } else if (charset.equals(CharsetUtil.US_ASCII)) {
            return isAscii(buf, index, length);
        } else {
            CharsetDecoder decoder = CharsetUtil.decoder(charset, CodingErrorAction.REPORT, CodingErrorAction.REPORT);
            try {
                if (buf.nioBufferCount() == 1) {
                    decoder.decode(buf.nioBuffer(index, length));
                } else {
                    ByteBuf heapBuffer = buf.alloc().heapBuffer(length);
                    try {
                        heapBuffer.writeBytes(buf, index, length);
                        decoder.decode(heapBuffer.internalNioBuffer(heapBuffer.readerIndex(), length));
                    } finally {
                        heapBuffer.release();
                    }
                }
                return true;
            } catch (CharacterCodingException ignore) {
                return false;
            }
        }
    }

    /**
     * Aborts on a byte which is not a valid ASCII character.
     */
    private static final ByteProcessor FIND_NON_ASCII = new ByteProcessor() {
        @Override
        public boolean process(byte value) {
            return value >= 0;
        }
    };

    /**
     * Returns {@code true} if the specified {@link ByteBuf} starting at {@code index} with {@code length} is valid
     * ASCII text, otherwise return {@code false}.
     *
     * @param buf    The given {@link ByteBuf}.
     * @param index  The start index of the specified buffer.
     * @param length The length of the specified buffer.
     */
    private static boolean isAscii(ByteBuf buf, int index, int length) {
        return buf.forEachByte(index, length, FIND_NON_ASCII) == -1;
    }

    /**
     * Returns {@code true} if the specified {@link ByteBuf} starting at {@code index} with {@code length} is valid
     * UTF8 text, otherwise return {@code false}.
     *
     * @param buf The given {@link ByteBuf}.
     * @param index The start index of the specified buffer.
     * @param length The length of the specified buffer.
     *
     * @see
     * <a href=http://www.ietf.org/rfc/rfc3629.txt>UTF-8 Definition</a>
     *
     * <pre>
     * 1. Bytes format of UTF-8
     *
     * The table below summarizes the format of these different octet types.
     * The letter x indicates bits available for encoding bits of the character number.
     *
     * Char. number range  |        UTF-8 octet sequence
     *    (hexadecimal)    |              (binary)
     * --------------------+---------------------------------------------
     * 0000 0000-0000 007F | 0xxxxxxx
     * 0000 0080-0000 07FF | 110xxxxx 10xxxxxx
     * 0000 0800-0000 FFFF | 1110xxxx 10xxxxxx 10xxxxxx
     * 0001 0000-0010 FFFF | 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
     * </pre>
     *
     * <pre>
     * 2. Syntax of UTF-8 Byte Sequences
     *
     * UTF8-octets = *( UTF8-char )
     * UTF8-char   = UTF8-1 / UTF8-2 / UTF8-3 / UTF8-4
     * UTF8-1      = %x00-7F
     * UTF8-2      = %xC2-DF UTF8-tail
     * UTF8-3      = %xE0 %xA0-BF UTF8-tail /
     *               %xE1-EC 2( UTF8-tail ) /
     *               %xED %x80-9F UTF8-tail /
     *               %xEE-EF 2( UTF8-tail )
     * UTF8-4      = %xF0 %x90-BF 2( UTF8-tail ) /
     *               %xF1-F3 3( UTF8-tail ) /
     *               %xF4 %x80-8F 2( UTF8-tail )
     * UTF8-tail   = %x80-BF
     * </pre>
     */
    private static boolean isUtf8(ByteBuf buf, int index, int length) {
        final int endIndex = index + length;
        while (index < endIndex) {
            byte b1 = buf.getByte(index++);
            byte b2, b3, b4;
            if ((b1 & 0x80) == 0) {
                // 1 byte
                continue;
            }
            if ((b1 & 0xE0) == 0xC0) {
                // 2 bytes
                //
                // Bit/Byte pattern
                // 110xxxxx    10xxxxxx
                // C2..DF      80..BF
                if (index >= endIndex) { // no enough bytes
                    return false;
                }
                b2 = buf.getByte(index++);
                if ((b2 & 0xC0) != 0x80) { // 2nd byte not starts with 10
                    return false;
                }
                if ((b1 & 0xFF) < 0xC2) { // out of lower bound
                    return false;
                }
            } else if ((b1 & 0xF0) == 0xE0) {
                // 3 bytes
                //
                // Bit/Byte pattern
                // 1110xxxx    10xxxxxx    10xxxxxx
                // E0          A0..BF      80..BF
                // E1..EC      80..BF      80..BF
                // ED          80..9F      80..BF
                // E1..EF      80..BF      80..BF
                if (index > endIndex - 2) { // no enough bytes
                    return false;
                }
                b2 = buf.getByte(index++);
                b3 = buf.getByte(index++);
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) { // 2nd or 3rd bytes not start with 10
                    return false;
                }
                if ((b1 & 0x0F) == 0x00 && (b2 & 0xFF) < 0xA0) { // out of lower bound
                    return false;
                }
                if ((b1 & 0x0F) == 0x0D && (b2 & 0xFF) > 0x9F) { // out of upper bound
                    return false;
                }
            } else if ((b1 & 0xF8) == 0xF0) {
                // 4 bytes
                //
                // Bit/Byte pattern
                // 11110xxx    10xxxxxx    10xxxxxx    10xxxxxx
                // F0          90..BF      80..BF      80..BF
                // F1..F3      80..BF      80..BF      80..BF
                // F4          80..8F      80..BF      80..BF
                if (index > endIndex - 3) { // no enough bytes
                    return false;
                }
                b2 = buf.getByte(index++);
                b3 = buf.getByte(index++);
                b4 = buf.getByte(index++);
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80) {
                    // 2nd, 3rd or 4th bytes not start with 10
                    return false;
                }
                if ((b1 & 0xFF) > 0xF4 // b1 invalid
                        || (b1 & 0xFF) == 0xF0 && (b2 & 0xFF) < 0x90    // b2 out of lower bound
                        || (b1 & 0xFF) == 0xF4 && (b2 & 0xFF) > 0x8F) { // b2 out of upper bound
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Read bytes from the given {@link ByteBuffer} into the given {@link OutputStream} using the {@code position} and
     * {@code length}. The position and limit of the given {@link ByteBuffer} may be adjusted.
     */
    static void readBytes(ByteBufAllocator allocator, ByteBuffer buffer, int position, int length, OutputStream out)
            throws IOException {
        if (buffer.hasArray()) {
            out.write(buffer.array(), position + buffer.arrayOffset(), length);
        } else {
            int chunkLen = Math.min(length, WRITE_CHUNK_SIZE);
            buffer.clear().position(position);

            if (length <= MAX_TL_ARRAY_LEN || !allocator.isDirectBufferPooled()) {
                getBytes(buffer, threadLocalTempArray(chunkLen), 0, chunkLen, out, length);
            } else {
                // if direct buffers are pooled chances are good that heap buffers are pooled as well.
                ByteBuf tmpBuf = allocator.heapBuffer(chunkLen);
                try {
                    byte[] tmp = tmpBuf.array();
                    int offset = tmpBuf.arrayOffset();
                    getBytes(buffer, tmp, offset, chunkLen, out, length);
                } finally {
                    tmpBuf.release();
                }
            }
        }
    }

    private static void getBytes(ByteBuffer inBuffer, byte[] in, int inOffset, int inLen, OutputStream out, int outLen)
            throws IOException {
        do {
            int len = Math.min(inLen, outLen);
            inBuffer.get(in, inOffset, len);
            out.write(in, inOffset, len);
            outLen -= len;
        } while (outLen > 0);
    }

    private ByteBufUtil() { }
}
