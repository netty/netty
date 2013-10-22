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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * A collection of utility methods that is related with handling {@link ByteBuf}.
 */
public final class ByteBufUtil {

    private static final char[] HEXDUMP_TABLE = new char[256 * 4];

    static {
        final char[] DIGITS = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 256; i ++) {
            HEXDUMP_TABLE[ i << 1     ] = DIGITS[i >>> 4 & 0x0F];
            HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i       & 0x0F];
        }
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
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
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

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    public static int hashCode(ByteBuf buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 1;
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
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other as described in {@code ChannelBuffer#equals(Object)}.
     * This method is useful when implementing a new buffer type.
     */
    public static boolean equals(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }

        final int longCount = aLen >>> 3;
        final int byteCount = aLen & 7;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != bufferB.getLong(bIndex)) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        } else {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != swapLong(bufferB.getLong(bIndex))) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            if (bufferA.getByte(aIndex) != bufferB.getByte(bIndex)) {
                return false;
            }
            aIndex ++;
            bIndex ++;
        }

        return true;
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

        if (bufferA.order() == bufferB.order()) {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = bufferB.getUnsignedInt(bIndex);
                if (va > vb) {
                    return 1;
                }
                if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        } else {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = swapInt(bufferB.getInt(bIndex)) & 0xFFFFFFFFL;
                if (va > vb) {
                    return 1;
                }
                if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            short va = bufferA.getUnsignedByte(aIndex);
            short vb = bufferB.getUnsignedByte(bIndex);
            if (va > vb) {
                return 1;
            }
            if (va < vb) {
                return -1;
            }
            aIndex ++;
            bIndex ++;
        }

        return aLen - bLen;
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

        for (int i = fromIndex; i < toIndex; i ++) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    static int encodeCharBuffer(ByteBuf dst, int index, CharBuffer src, Charset charset) {
        final CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        int length = (int) ((double) src.remaining() * encoder.maxBytesPerChar());
        if (length > (dst.capacity() - index)) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: range(0, %d))", index, length, dst.capacity()));
        }
        return encodeCharBuffer0(dst, index, src, encoder);
    }

    private static int encodeCharBuffer0(ByteBuf dst, int index, CharBuffer src, CharsetEncoder encoder) {
        try {
            final ByteBuffer dstBuf = dst.internalNioBuffer(index, dst.capacity() - index);
            final int pos = dstBuf.position();
            CoderResult cr = encoder.encode(src, dstBuf, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dstBuf);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            int written = dstBuf.position() - pos;
            return written;
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
    }

    /**
     * Encode the given {@link CharSequence} using the given {@link Charset} into a new {@link ByteBuf} which
     * is allocated via the {@link ByteBufAllocator}.
     */
    public static ByteBuf encodeCharSequence(ByteBufAllocator alloc, CharSequence seq, Charset charset) {
        return encodeCharBuffer(alloc, CharBuffer.wrap(seq), charset);
    }

    @Deprecated
    public static ByteBuf encodeString(ByteBufAllocator alloc, CharBuffer src, Charset charset) {
        return encodeCharBuffer(alloc, src, charset);
    }

    /**
     * Encode the given {@link CharBuffer} using the given {@link Charset} into a new {@link ByteBuf} which
     * is allocated via the {@link ByteBufAllocator}.
     */
    public static ByteBuf encodeCharBuffer(ByteBufAllocator alloc, CharBuffer src, Charset charset) {
        final CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        int length = (int) ((double) src.remaining() * encoder.maxBytesPerChar());
        boolean release = true;
        final ByteBuf dst = alloc.buffer(length);
        try {
            int wIndex = dst.writerIndex();
            int written = encodeCharBuffer0(dst, wIndex, src, encoder);
            dst.writerIndex(wIndex + written);
            release = false;
            return dst;
        } finally {
            if (release) {
                dst.release();
            }
        }
    }

    static String decodeString(ByteBuffer src, Charset charset) {
        final CharsetDecoder decoder = CharsetUtil.getDecoder(charset);
        final CharBuffer dst = CharBuffer.allocate(
                (int) ((double) src.remaining() * decoder.maxCharsPerByte()));
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        return dst.flip().toString();
    }

    private static final int UTF8_ACCEPT = 0;
    private static final int UTF8_REJECT = 12;
    private static final char UTF8_REPLACEMENT = '\ufffd';

    private static final byte[] UTF8_TYPES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
            8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
            8, 8, 8, 8, 8, 8 };

    private static final byte[] UTF8_STATES = { 0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
            12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
            12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12 };

    // Based on:
    //  * https://github.com/nitsanw/javanetperf/blob/psylobsaw/src/psy/lob/saw/CustomUtf8Encoder.java
    //  * http://psy-lob-saw.blogspot.co.uk/2012/12/encode-utf-8-string-to-bytebuffer-faster.html
    static int encodeUtf8(CharSequence seq, byte[] dst, int index, int length) throws CharacterCodingException {
        int startIndex = index;
        int dstLimit = index + length;
        int sourceOffset = 0;
        int sourceLength = seq.length();
        int dlASCII = index + Math.min(sourceLength, dstLimit - index);
        // handle ascii encoded strings in an optimised loop
        int c;
        while (index < dlASCII && (c = seq.charAt(sourceOffset)) < 128) {
            dst[index++] = (byte) c;
            sourceOffset++;
        }

        try {
            while (sourceOffset < sourceLength) {
                c = seq.charAt(sourceOffset++);
                if (c < 128) {
                    dst[index++] = (byte) c;
                } else if (c < 2048) {
                    dst[index++] = (byte) (0xC0 | (c >> 6));
                    dst[index++] = (byte) (0x80 | (c & 0x3F));
                } else if (isSurrogate(c)) {
                    int uc = parseUtf8((char) c, seq, sourceOffset, sourceLength);
                    if (uc == -1) {
                        return index - startIndex;
                    }
                    dst[index++] = (byte) (0xF0 | uc >> 18);
                    dst[index++] = (byte) (0x80 | uc >> 12 & 0x3F);
                    dst[index++] = (byte) (0x80 | uc >> 6 & 0x3F);
                    dst[index++] = (byte) (0x80 | uc & 0x3F);
                    sourceOffset++;
                } else {
                    dst[index++] = (byte) (0xE0 | c >> 12);
                    dst[index++] = (byte) (0x80 | c >> 6 & 0x3F);
                    dst[index++] = (byte) (0x80 | c & 0x3F);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            CoderResult.OVERFLOW.throwException();
        }
        return index - startIndex;
    }

    // Based on:
    //  * https://github.com/nitsanw/javanetperf/blob/psylobsaw/src/psy/lob/saw/CustomUtf8Encoder.java
    //  * http://psy-lob-saw.blogspot.co.uk/2012/12/encode-utf-8-string-to-bytebuffer-faster.html
    static int encodeUtf8(CharSequence seq, long memoryAddress, long index, long length)
            throws CharacterCodingException {
        index = memoryAddress + index;
        length = memoryAddress + length;
        long startIndex = index;
        long dstLimit = index + length;
        int sourceOffset = 0;
        int sourceLength = seq.length();

        long dlASCII = index + Math.min(sourceLength, dstLimit - index);
        // handle ascii encoded strings in an optimised loop
        int c;
        while (index < dlASCII && (c = seq.charAt(sourceOffset)) < 128) {
            PlatformDependent.putByte(index++, (byte) c);
            sourceOffset++;
        }

        while (sourceOffset < sourceLength) {
            c = seq.charAt(sourceOffset++);
            if (c < 128) {
                if (index >= length) {
                    CoderResult.OVERFLOW.throwException();
                }
                PlatformDependent.putByte(index++, (byte) c);
            } else if (c < 2048) {
                if (length - index < 2) {
                    CoderResult.OVERFLOW.throwException();
                }
                PlatformDependent.putByte(index++, (byte) (0xC0 | (c >> 6)));
                PlatformDependent.putByte(index++, (byte) (0x80 | (c & 0x3F)));
            } else if (isSurrogate(c)) {
                int uc = parseUtf8((char) c, seq, sourceOffset, sourceLength);
                if (uc == -1) {
                    return (int) (index - startIndex);
                }
                if (length - index < 4) {
                    CoderResult.OVERFLOW.throwException();
                }
                PlatformDependent.putByte(index++, (byte) (0xF0 | uc >> 18));
                PlatformDependent.putByte(index++, (byte) (0x80 | uc >> 12 & 0x3F));
                PlatformDependent.putByte(index++, (byte) (0x80 | uc >> 6 & 0x3F));
                PlatformDependent.putByte(index++, (byte) (0x80 | uc & 0x3F));
                sourceOffset++;
            } else {
                if (length - index < 3) {
                    CoderResult.OVERFLOW.throwException();
                }
                PlatformDependent.putByte(index++, (byte) (0xE0 | c >> 12));
                PlatformDependent.putByte(index++, (byte) (0x80 | c >> 6 & 0x3F));
                PlatformDependent.putByte(index++, (byte) (0x80 | c & 0x3F));
            }
        }
        return (int) (index - startIndex);
    }

    static int parseUtf8(char c, CharSequence seq, int offset, int length) {
        if (Character.isHighSurrogate(c)) {
            if (length - offset < 2) {
                return -1;
            }
            char c2 = seq.charAt(offset + 1);
            if (Character.isLowSurrogate(c2)) {
                return Character.toCodePoint(c, c2);
            }
            // replace char
            return UTF8_REPLACEMENT;
        }
        if (Character.isLowSurrogate(c)) {
            // replace char
            return UTF8_REPLACEMENT;
        }
        return c;
    }

    static boolean isSurrogate(int c) {
        return Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE;
    }

    private ByteBufUtil() { }
}
