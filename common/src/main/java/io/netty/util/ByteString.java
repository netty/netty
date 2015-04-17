/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The primary use case for this class is to function as an immutable array of bytes. For performance reasons this
 * class supports sharing memory with external arrays, and also direct access to the underlying memory via
 * {@link #array()}. Care must be taken when directly accessing the memory as that may invalidate assumptions that
 * this object is immutable.
 */
public class ByteString {
    /**
     * A byte wise comparator between two {@link ByteString} objects.
     */
    public static final Comparator<ByteString> DEFAULT_COMPARATOR = new Comparator<ByteString>() {
        @Override
        public int compare(ByteString o1, ByteString o2) {
            if (o1 == o2) {
                return 0;
            }

            int result;
            int length1 = o1.length();
            int length2 = o2.length();
            int minLength = Math.min(length1, length2);
            for (int i = 0, j = 0; j < minLength; i++, j++) {
                result = o1.value[i] - o2.value[j];
                if (result != 0) {
                    return result;
                }
            }

            return length1 - length2;
        }
    };
    public static final ByteString EMPTY_STRING = new ByteString(0);
    protected static final int HASH_CODE_PRIME = 31;

    /**
     * If this value is modified outside the constructor then call {@link #arrayChanged()}.
     */
    protected final byte[] value;
    /**
     * The hash code is cached after it is first computed. It can be reset with {@link #arrayChanged()}.
     */
    private int hash;

    /**
     * Used for classes which extend this class and want to initialize the {@link #value} array by them selves.
     */
    ByteString(int length) { value = new byte[length]; }

    /**
     * Initialize this byte string based upon a byte array. A copy will be made.
     */
    public ByteString(byte[] value) {
        this(value, true);
    }

    /**
     * Initialize this byte string based upon a byte array.
     * {@code copy} determines if a copy is made or the array is shared.
     */
    public ByteString(byte[] value, boolean copy) {
        if (copy) {
            this.value = checkNotNull(value, "value").clone();
        } else {
            this.value = checkNotNull(value, "value");
        }
    }

    /**
     * Initialize this byte string based upon a range of a byte array. A copy will be made.
     */
    public ByteString(byte[] value, int start, int length) {
        this(value, start, length, true);
    }

    /**
     * Construct a new {@link BinaryString} object from a {@code byte[]} array.
     * @param copy {@code true} then a copy of the memory will be made. {@code false} the underlying memory
     * will be shared. If this shared memory changes then {@link #arrayChanged()} must be called.
     */
    public ByteString(byte[] value, int start, int length, boolean copy) {
        if (start < 0 || start > checkNotNull(value, "value").length - length) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.length(" + value.length + ')');
        }

        if (copy || start != 0 || length != value.length) {
            this.value = Arrays.copyOfRange(value, start, start + length);
        } else {
            this.value = value;
        }
    }

    /**
     * Create a copy of the underlying storage from {@link value}.
     * The copy will start at {@link ByteBuffer#position()} and copy {@link ByteBuffer#remaining()} bytes.
     */
    public ByteString(ByteBuffer value) {
        this.value = getBytes(value);
    }

    /**
     * Create a copy of the underlying storage from {@link value}.
     * The copy will start at {@code start} and copy {@code length} bytes.
     */
    public ByteString(ByteBuffer value, int start, int length) {
        this.value = getBytes(value, start, length, true);
    }

    /**
     * Initialize a {@link ByteString} based upon the underlying storage from {@link value}.
     * The copy will start at {@code start} and copy {@code length} bytes.
     * if {@code copy} is true a copy will be made of the memory.
     * if {@code copy} is false the underlying storage will be shared, if possible.
     */
    public ByteString(ByteBuffer value, int start, int length, boolean copy) {
        this.value = getBytes(value, start, length, copy);
    }

    /**
     * Create a copy of {@link value} into a {@link ByteString} using the encoding type of {@code charset}.
     */
    public ByteString(char[] value, Charset charset) {
        this.value = getBytes(value, charset);
    }

    /**
     * Create a copy of {@link value} into a {@link ByteString} using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public ByteString(char[] value, Charset charset, int start, int length) {
        this.value = getBytes(value, charset, start, length);
    }

    /**
     * Create a copy of {@link value} into a {@link ByteString} using the encoding type of {@code charset}.
     */
    public ByteString(CharSequence value, Charset charset) {
        this.value = getBytes(value, charset);
    }

    /**
     * Create a copy of {@link value} into a {@link ByteString} using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public ByteString(CharSequence value, Charset charset, int start, int length) {
        this.value = getBytes(value, charset, start, length);
    }

    /**
     * Create a copy of the underlying storage from {@link value} into a byte array.
     * The copy will start at {@link ByteBuffer#position()} and copy {@link ByteBuffer#remaining()} bytes.
     */
    private static byte[] getBytes(ByteBuffer value) {
        return getBytes(value, value.position(), checkNotNull(value, "value").remaining());
    }

    /**
     * Create a copy of the underlying storage from {@link value} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     */
    private static byte[] getBytes(ByteBuffer value, int start, int length) {
        return getBytes(value, start, length, true);
    }

    /**
     * Return an array of the underlying storage from {@link value} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     * if {@code copy} is true a copy will be made of the memory.
     * if {@code copy} is false the underlying storage will be shared, if possible.
     */
    private static byte[] getBytes(ByteBuffer value, int start, int length, boolean copy) {
        if (start < 0 || length > checkNotNull(value, "value").capacity() - start) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.capacity(" + value.capacity() + ')');
        }

        if (value.hasArray()) {
            if (copy || start != 0 || length != value.capacity()) {
                int baseOffset = value.arrayOffset() + start;
                return Arrays.copyOfRange(value.array(), baseOffset, baseOffset + length);
            } else {
                return value.array();
            }
        }

        byte[] v = new byte[length];
        int oldPos = value.position();
        value.get(v, 0, length);
        value.position(oldPos);
        return v;
    }

    /**
     * Create a copy of {@link value} into a byte array using the encoding type of {@code charset}.
     */
    private static byte[] getBytes(char[] value, Charset charset) {
        return getBytes(value, charset, 0, checkNotNull(value, "value").length);
    }

    /**
     * Create a copy of {@link value} into a byte array using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    private static byte[] getBytes(char[] value, Charset charset, int start, int length) {
        if (start < 0 || length > checkNotNull(value, "value").length - start) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                    + ") <= " + "length(" + length + ')');
        }

        CharBuffer cbuf = CharBuffer.wrap(value, start, start + length);
        CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        ByteBuffer nativeBuffer = ByteBuffer.allocate((int) (encoder.maxBytesPerChar() * length));
        encoder.encode(cbuf, nativeBuffer, true);
        final int offset = nativeBuffer.arrayOffset();
        return Arrays.copyOfRange(nativeBuffer.array(), offset, offset + nativeBuffer.position());
    }

    /**
     * Create a copy of {@link value} into a byte array using the encoding type of {@code charset}.
     */
    private static byte[] getBytes(CharSequence value, Charset charset) {
        return getBytes(value, charset, 0, checkNotNull(value, "value").length());
    }

    /**
     * Create a copy of {@link value} into a byte array using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    private static byte[] getBytes(CharSequence value, Charset charset, int start, int length) {
        if (start < 0 || length > checkNotNull(value, "value").length() - start) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                    + ") <= " + "length(" + value.length() + ')');
        }

        CharBuffer cbuf = CharBuffer.wrap(value, start, start + length);
        CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        ByteBuffer nativeBuffer = ByteBuffer.allocate((int) (encoder.maxBytesPerChar() * length));
        encoder.encode(cbuf, nativeBuffer, true);
        final int offset = nativeBuffer.arrayOffset();
        return Arrays.copyOfRange(nativeBuffer.array(), offset, offset + nativeBuffer.position());
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public final int forEachByte(ByteProcessor visitor) throws Exception {
        return forEachByte(0, value.length, visitor);
    }

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in ascending order.
     * (i.e. {@code index}, {@code (index + 1)},  .. {@code (index + length - 1)}).
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public final int forEachByte(int index, int length, ByteProcessor visitor) throws Exception {
        if (index < 0 || length > value.length - index) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= index(" + index + ") <= start + length(" + length
                    + ") <= " + "length(" + value.length + ')');
        }

        for (int i = index; i < length; ++i) {
            if (!visitor.process(value[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in descending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public final int forEachByteDesc(ByteProcessor visitor) throws Exception {
        return forEachByteDesc(0, length(), visitor);
    }

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in descending order.
     * (i.e. {@code (index + length - 1)}, {@code (index + length - 2)}, ... {@code index}).
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public final int forEachByteDesc(int index, int length, ByteProcessor visitor) throws Exception {
        if (index < 0 || length > value.length - index) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= index(" + index + ") <= start + length(" + length
                    + ") <= " + "length(" + value.length + ')');
        }

        for (int i = index + length - 1; i >= index; --i) {
            if (!visitor.process(value[i])) {
                return i;
            }
        }
        return -1;
    }

    public final byte byteAt(int index) {
        return value[index];
    }

    public final boolean isEmpty() {
        return value.length == 0;
    }

    public final int length() {
        return value.length;
    }

    /**
     * During normal use cases the {@link ByteString} should be immutable, but if the underlying array is shared,
     * and changes then this needs to be called.
     */
    public final void arrayChanged() {
        hash = 0;
    }

    /**
     * This gives direct access to the underlying storage array.
     * The {@link #toByteArray()} should be preferred over this method.
     * If the return value is changed then {@link #arrayChanged()} must be called.
     */
    public final byte[] array() {
        return value;
    }

    /**
     * Converts this string to a byte array.
     */
    public final byte[] toByteArray() {
        return toByteArray(0, length());
    }

    /**
     * Converts a subset of this string to a byte array.
     * The subset is defined by the range [{@code start}, {@code end}).
     */
    public final byte[] toByteArray(int start, int end) {
        return Arrays.copyOfRange(value, start, end);
    }

    /**
     * Copies the content of this string to a byte array.
     *
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination byte array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    public final void copy(int srcIdx, byte[] dst, int dstIdx, int length) {
        final int thisLen = value.length;

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + thisLen + ')');
        }

        System.arraycopy(value, srcIdx, checkNotNull(dst, "dst"), dstIdx, length);
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            for (int i = 0; i < value.length; ++i) {
                h = h * HASH_CODE_PRIME ^ value[i] & HASH_CODE_PRIME;
            }

            hash = h;
        }
        return h;
    }

    /**
     * Copies a range of characters into a new string.
     *
     * @param start the offset of the first character.
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if {@code start < 0} or {@code start > length()}.
     */
    public final ByteString subSequence(int start) {
        return subSequence(start, length());
    }

    public ByteString subSequence(int start, int end) {
        if (start < 0 || start > end || end > length()) {
            throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= end (" + end + ") <= length("
                            + length() + ')');
        }

        if (start == 0 && end == value.length) {
            return this;
        }

        if (end == start) {
            return EMPTY_STRING;
        }

        return new ByteString(value, start, end - start, false);
    }

    public final int parseAsciiInt() {
        return parseAsciiInt(0, length(), 10);
    }

    public final int parseAsciiInt(int radix) {
        return parseAsciiInt(0, length(), radix);
    }

    public final int parseAsciiInt(int start, int end) {
        return parseAsciiInt(start, end, 10);
    }

    public final int parseAsciiInt(int start, int end, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException();
        }

        if (start == end) {
            throw new NumberFormatException();
        }

        int i = start;
        boolean negative = byteAt(i) == '-';
        if (negative && ++i == end) {
            throw new NumberFormatException(subSequence(start, end).toString());
        }

        return parseAsciiInt(i, end, radix, negative);
    }

    private int parseAsciiInt(int start, int end, int radix, boolean negative) {
        int max = Integer.MIN_VALUE / radix;
        int result = 0;
        int offset = start;
        while (offset < end) {
            int digit = Character.digit((char) (value[offset++] & 0xFF), radix);
            if (digit == -1) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            if (max > result) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            int next = result * radix - digit;
            if (next > result) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            result = next;
        }
        if (!negative) {
            result = -result;
            if (result < 0) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
        }
        return result;
    }

    public final long parseAsciiLong() {
        return parseAsciiLong(0, length(), 10);
    }

    public final long parseAsciiLong(int radix) {
        return parseAsciiLong(0, length(), radix);
    }

    public final long parseAsciiLong(int start, int end) {
        return parseAsciiLong(start, end, 10);
    }

    public final long parseAsciiLong(int start, int end, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException();
        }

        if (start == end) {
            throw new NumberFormatException();
        }

        int i = start;
        boolean negative = byteAt(i) == '-';
        if (negative && ++i == end) {
            throw new NumberFormatException(subSequence(start, end).toString());
        }

        return parseAsciiLong(i, end, radix, negative);
    }

    private long parseAsciiLong(int start, int end, int radix, boolean negative) {
        long max = Long.MIN_VALUE / radix;
        long result = 0;
        int offset = start;
        while (offset < end) {
            int digit = Character.digit((char) (value[offset++] & 0xFF), radix);
            if (digit == -1) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            if (max > result) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            long next = result * radix - digit;
            if (next > result) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
            result = next;
        }
        if (!negative) {
            result = -result;
            if (result < 0) {
                throw new NumberFormatException(subSequence(start, end).toString());
            }
        }
        return result;
    }

    public final char parseChar() {
        return parseChar(0);
    }

    public char parseChar(int start) {
        if (start + 1 >= value.length) {
            throw new IndexOutOfBoundsException("2 bytes required to convert to character. index " +
                    start + " would go out of bounds.");
        }
        return (char) (((value[start] & 0xFF) << 8) | (value[start + 1] & 0xFF));
    }

    public final short parseAsciiShort() {
        return parseAsciiShort(0, length(), 10);
    }

    public final short parseAsciiShort(int radix) {
        return parseAsciiShort(0, length(), radix);
    }

    public final short parseAsciiShort(int start, int end) {
        return parseAsciiShort(start, end, 10);
    }

    public final short parseAsciiShort(int start, int end, int radix) {
        int intValue = parseAsciiInt(start, end, radix);
        short result = (short) intValue;
        if (result != intValue) {
            throw new NumberFormatException(subSequence(start, end).toString());
        }
        return result;
    }

    public final float parseAsciiFloat() {
        return parseAsciiFloat(0, length());
    }

    public final float parseAsciiFloat(int start, int end) {
        return Float.parseFloat(toString(start, end));
    }

    public final double parseAsciiDouble() {
        return parseAsciiDouble(0, length());
    }

    public final double parseAsciiDouble(int start, int end) {
        return Double.parseDouble(toString(start, end));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ByteString)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        ByteString other = (ByteString) obj;
        return hashCode() == other.hashCode() &&
               PlatformDependent.equals(array(), 0, array().length, other.array(), 0, other.array().length);
    }

    /**
     * Translates the entire byte string to a {@link String}.
     * @see {@link #toString(int, int)}
     */
    @Override
    public String toString() {
        return toString(0, length());
    }

    /**
     * Translates the entire byte string to a {@link String} using the {@code charset} encoding.
     * @see {@link #toString(Charset, int, int)}
     */
    public final String toString(Charset charset) {
        return toString(charset, 0, length());
    }

    /**
     * Translates the [{@code start}, {@code end}) range of this byte string to a {@link String}.
     * @see {@link #toString(Charset, int, int)}
     */
    public final String toString(int start, int end) {
        return toString(CharsetUtil.ISO_8859_1, start, end);
    }

    /**
     * Translates the [{@code start}, {@code end}) range of this byte string to a {@link String}
     * using the {@code charset} encoding.
     */
    public String toString(Charset charset, int start, int end) {
        int length = end - start;
        if (length == 0) {
            return StringUtil.EMPTY_STRING;
        }

        return new String(value, start, length, charset);
    }
}
