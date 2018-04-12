/*
 * Copyright 2014 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.netty.util.internal.MathUtil.isOutOfBounds;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A string which has been encoded into a character encoding whose character always takes a single byte, similarly to
 * ASCII. It internally keeps its content in a byte array unlike {@link String}, which uses a character array, for
 * reduced memory footprint and faster data transfer from/to byte-based data structures such as a byte array and
 * {@link ByteBuffer}. It is often used in conjunction with {@code Headers} that require a {@link CharSequence}.
 * <p>
 * This class was designed to provide an immutable array of bytes, and caches some internal state based upon the value
 * of this array. However underlying access to this byte array is provided via not copying the array on construction or
 * {@link #array()}. If any changes are made to the underlying byte array it is the user's responsibility to call
 * {@link #arrayChanged()} so the state of this class can be reset.
 */
public final class AsciiString implements CharSequence, Comparable<CharSequence> {
    public static final AsciiString EMPTY_STRING = cached("");
    private static final char MAX_CHAR_VALUE = 255;

    public static final int INDEX_NOT_FOUND = -1;

    /**
     * If this value is modified outside the constructor then call {@link #arrayChanged()}.
     */
    private final byte[] value;
    /**
     * Offset into {@link #value} that all operations should use when acting upon {@link #value}.
     */
    private final int offset;
    /**
     * Length in bytes for {@link #value} that we care about. This is independent from {@code value.length}
     * because we may be looking at a subsection of the array.
     */
    private final int length;
    /**
     * The hash code is cached after it is first computed. It can be reset with {@link #arrayChanged()}.
     */
    private int hash;
    /**
     * Used to cache the {@link #toString()} value.
     */
    private String string;

    /**
     * Initialize this byte string based upon a byte array. A copy will be made.
     */
    public AsciiString(byte[] value) {
        this(value, true);
    }

    /**
     * Initialize this byte string based upon a byte array.
     * {@code copy} determines if a copy is made or the array is shared.
     */
    public AsciiString(byte[] value, boolean copy) {
        this(value, 0, value.length, copy);
    }

    /**
     * Construct a new instance from a {@code byte[]} array.
     * @param copy {@code true} then a copy of the memory will be made. {@code false} the underlying memory
     * will be shared.
     */
    public AsciiString(byte[] value, int start, int length, boolean copy) {
        if (copy) {
            this.value = Arrays.copyOfRange(value, start, start + length);
            this.offset = 0;
        } else {
            if (isOutOfBounds(start, length, value.length)) {
                throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" +
                        length + ") <= " + "value.length(" + value.length + ')');
            }
            this.value = value;
            this.offset = start;
        }
        this.length = length;
    }

    /**
     * Create a copy of the underlying storage from {@code value}.
     * The copy will start at {@link ByteBuffer#position()} and copy {@link ByteBuffer#remaining()} bytes.
     */
    public AsciiString(ByteBuffer value) {
        this(value, true);
    }

    /**
     * Initialize an instance based upon the underlying storage from {@code value}.
     * There is a potential to share the underlying array storage if {@link ByteBuffer#hasArray()} is {@code true}.
     * if {@code copy} is {@code true} a copy will be made of the memory.
     * if {@code copy} is {@code false} the underlying storage will be shared, if possible.
     */
    public AsciiString(ByteBuffer value, boolean copy) {
        this(value, value.position(), value.remaining(), copy);
    }

    /**
     * Initialize an {@link AsciiString} based upon the underlying storage from {@code value}.
     * There is a potential to share the underlying array storage if {@link ByteBuffer#hasArray()} is {@code true}.
     * if {@code copy} is {@code true} a copy will be made of the memory.
     * if {@code copy} is {@code false} the underlying storage will be shared, if possible.
     */
    public AsciiString(ByteBuffer value, int start, int length, boolean copy) {
        if (isOutOfBounds(start, length, value.capacity())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.capacity(" + value.capacity() + ')');
        }

        if (value.hasArray()) {
            if (copy) {
                final int bufferOffset = value.arrayOffset() + start;
                this.value = Arrays.copyOfRange(value.array(), bufferOffset, bufferOffset + length);
                offset = 0;
            } else {
                this.value = value.array();
                this.offset = start;
            }
        } else {
            this.value = new byte[length];
            int oldPos = value.position();
            value.get(this.value, 0, length);
            value.position(oldPos);
            this.offset = 0;
        }
        this.length = length;
    }

    /**
     * Create a copy of {@code value} into this instance assuming ASCII encoding.
     */
    public AsciiString(char[] value) {
        this(value, 0, value.length);
    }

    /**
     * Create a copy of {@code value} into this instance assuming ASCII encoding.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public AsciiString(char[] value, int start, int length) {
        if (isOutOfBounds(start, length, value.length)) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.length(" + value.length + ')');
        }

        this.value = new byte[length];
        for (int i = 0, j = start; i < length; i++, j++) {
            this.value[i] = c2b(value[j]);
        }
        this.offset = 0;
        this.length = length;
    }

    /**
     * Create a copy of {@code value} into this instance using the encoding type of {@code charset}.
     */
    public AsciiString(char[] value, Charset charset) {
        this(value, charset, 0, value.length);
    }

    /**
     * Create a copy of {@code value} into a this instance using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public AsciiString(char[] value, Charset charset, int start, int length) {
        CharBuffer cbuf = CharBuffer.wrap(value, start, length);
        CharsetEncoder encoder = CharsetUtil.encoder(charset);
        ByteBuffer nativeBuffer = ByteBuffer.allocate((int) (encoder.maxBytesPerChar() * length));
        encoder.encode(cbuf, nativeBuffer, true);
        final int bufferOffset = nativeBuffer.arrayOffset();
        this.value = Arrays.copyOfRange(nativeBuffer.array(), bufferOffset, bufferOffset + nativeBuffer.position());
        this.offset = 0;
        this.length =  this.value.length;
    }

    /**
     * Create a copy of {@code value} into this instance assuming ASCII encoding.
     */
    public AsciiString(CharSequence value) {
        this(value, 0, value.length());
    }

    /**
     * Create a copy of {@code value} into this instance assuming ASCII encoding.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public AsciiString(CharSequence value, int start, int length) {
        if (isOutOfBounds(start, length, value.length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.length(" + value.length() + ')');
        }

        this.value = new byte[length];
        for (int i = 0, j = start; i < length; i++, j++) {
            this.value[i] = c2b(value.charAt(j));
        }
        this.offset = 0;
        this.length = length;
    }

    /**
     * Create a copy of {@code value} into this instance using the encoding type of {@code charset}.
     */
    public AsciiString(CharSequence value, Charset charset) {
        this(value, charset, 0, value.length());
    }

    /**
     * Create a copy of {@code value} into this instance using the encoding type of {@code charset}.
     * The copy will start at index {@code start} and copy {@code length} bytes.
     */
    public AsciiString(CharSequence value, Charset charset, int start, int length) {
        CharBuffer cbuf = CharBuffer.wrap(value, start, start + length);
        CharsetEncoder encoder = CharsetUtil.encoder(charset);
        ByteBuffer nativeBuffer = ByteBuffer.allocate((int) (encoder.maxBytesPerChar() * length));
        encoder.encode(cbuf, nativeBuffer, true);
        final int offset = nativeBuffer.arrayOffset();
        this.value = Arrays.copyOfRange(nativeBuffer.array(), offset, offset + nativeBuffer.position());
        this.offset = 0;
        this.length = this.value.length;
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public int forEachByte(ByteProcessor visitor) throws Exception {
        return forEachByte0(0, length(), visitor);
    }

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in ascending order.
     * (i.e. {@code index}, {@code (index + 1)},  .. {@code (index + length - 1)}).
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public int forEachByte(int index, int length, ByteProcessor visitor) throws Exception {
        if (isOutOfBounds(index, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= index(" + index + ") <= start + length(" + length
                    + ") <= " + "length(" + length() + ')');
        }
        return forEachByte0(index, length, visitor);
    }

    private int forEachByte0(int index, int length, ByteProcessor visitor) throws Exception {
        final int len = offset + index + length;
        for (int i = offset + index; i < len; ++i) {
            if (!visitor.process(value[i])) {
                return i - offset;
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
    public int forEachByteDesc(ByteProcessor visitor) throws Exception {
        return forEachByteDesc0(0, length(), visitor);
    }

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in descending order.
     * (i.e. {@code (index + length - 1)}, {@code (index + length - 2)}, ... {@code index}).
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public int forEachByteDesc(int index, int length, ByteProcessor visitor) throws Exception {
        if (isOutOfBounds(index, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= index(" + index + ") <= start + length(" + length
                    + ") <= " + "length(" + length() + ')');
        }
        return forEachByteDesc0(index, length, visitor);
    }

    private int forEachByteDesc0(int index, int length, ByteProcessor visitor) throws Exception {
        final int end = offset + index;
        for (int i = offset + index + length - 1; i >= end; --i) {
            if (!visitor.process(value[i])) {
                return i - offset;
            }
        }
        return -1;
    }

    public byte byteAt(int index) {
        // We must do a range check here to enforce the access does not go outside our sub region of the array.
        // We rely on the array access itself to pick up the array out of bounds conditions
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index: " + index + " must be in the range [0," + length + ")");
        }
        // Try to use unsafe to avoid double checking the index bounds
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getByte(value, index + offset);
        }
        return value[index + offset];
    }

    /**
     * Determine if this instance has 0 length.
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * The length in bytes of this instance.
     */
    @Override
    public int length() {
        return length;
    }

    /**
     * During normal use cases the {@link AsciiString} should be immutable, but if the underlying array is shared,
     * and changes then this needs to be called.
     */
    public void arrayChanged() {
        string = null;
        hash = 0;
    }

    /**
     * This gives direct access to the underlying storage array.
     * The {@link #toByteArray()} should be preferred over this method.
     * If the return value is changed then {@link #arrayChanged()} must be called.
     * @see #arrayOffset()
     * @see #isEntireArrayUsed()
     */
    public byte[] array() {
        return value;
    }

    /**
     * The offset into {@link #array()} for which data for this ByteString begins.
     * @see #array()
     * @see #isEntireArrayUsed()
     */
    public int arrayOffset() {
        return offset;
    }

    /**
     * Determine if the storage represented by {@link #array()} is entirely used.
     * @see #array()
     */
    public boolean isEntireArrayUsed() {
        return offset == 0 && length == value.length;
    }

    /**
     * Converts this string to a byte array.
     */
    public byte[] toByteArray() {
        return toByteArray(0, length());
    }

    /**
     * Converts a subset of this string to a byte array.
     * The subset is defined by the range [{@code start}, {@code end}).
     */
    public byte[] toByteArray(int start, int end) {
        return Arrays.copyOfRange(value, start + offset, end + offset);
    }

    /**
     * Copies the content of this string to a byte array.
     *
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination byte array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    public void copy(int srcIdx, byte[] dst, int dstIdx, int length) {
        if (isOutOfBounds(srcIdx, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + length() + ')');
        }

        System.arraycopy(value, srcIdx + offset, checkNotNull(dst, "dst"), dstIdx, length);
    }

    @Override
    public char charAt(int index) {
        return b2c(byteAt(index));
    }

    /**
     * Determines if this {@code String} contains the sequence of characters in the {@code CharSequence} passed.
     *
     * @param cs the character sequence to search for.
     * @return {@code true} if the sequence of characters are contained in this string, otherwise {@code false}.
     */
    public boolean contains(CharSequence cs) {
        return indexOf(cs) >= 0;
    }

    /**
     * Compares the specified string to this string using the ASCII values of the characters. Returns 0 if the strings
     * contain the same characters in the same order. Returns a negative integer if the first non-equal character in
     * this string has an ASCII value which is less than the ASCII value of the character at the same position in the
     * specified string, or if this string is a prefix of the specified string. Returns a positive integer if the first
     * non-equal character in this string has a ASCII value which is greater than the ASCII value of the character at
     * the same position in the specified string, or if the specified string is a prefix of this string.
     *
     * @param string the string to compare.
     * @return 0 if the strings are equal, a negative integer if this string is before the specified string, or a
     *         positive integer if this string is after the specified string.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    @Override
    public int compareTo(CharSequence string) {
        if (this == string) {
            return 0;
        }

        int result;
        int length1 = length();
        int length2 = string.length();
        int minLength = Math.min(length1, length2);
        for (int i = 0, j = arrayOffset(); i < minLength; i++, j++) {
            result = b2c(value[j]) - string.charAt(i);
            if (result != 0) {
                return result;
            }
        }

        return length1 - length2;
    }

    /**
     * Concatenates this string and the specified string.
     *
     * @param string the string to concatenate
     * @return a new string which is the concatenation of this string and the specified string.
     */
    public AsciiString concat(CharSequence string) {
        int thisLen = length();
        int thatLen = string.length();
        if (thatLen == 0) {
            return this;
        }

        if (string.getClass() == AsciiString.class) {
            AsciiString that = (AsciiString) string;
            if (isEmpty()) {
                return that;
            }

            byte[] newValue = new byte[thisLen + thatLen];
            System.arraycopy(value, arrayOffset(), newValue, 0, thisLen);
            System.arraycopy(that.value, that.arrayOffset(), newValue, thisLen, thatLen);
            return new AsciiString(newValue, false);
        }

        if (isEmpty()) {
            return new AsciiString(string);
        }

        byte[] newValue = new byte[thisLen + thatLen];
        System.arraycopy(value, arrayOffset(), newValue, 0, thisLen);
        for (int i = thisLen, j = 0; i < newValue.length; i++, j++) {
            newValue[i] = c2b(string.charAt(j));
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Compares the specified string to this string to determine if the specified string is a suffix.
     *
     * @param suffix the suffix to look for.
     * @return {@code true} if the specified string is a suffix of this string, {@code false} otherwise.
     * @throws NullPointerException if {@code suffix} is {@code null}.
     */
    public boolean endsWith(CharSequence suffix) {
        int suffixLen = suffix.length();
        return regionMatches(length() - suffixLen, suffix, 0, suffixLen);
    }

    /**
     * Compares the specified string to this string ignoring the case of the characters and returns true if they are
     * equal.
     *
     * @param string the string to compare.
     * @return {@code true} if the specified string is equal to this string, {@code false} otherwise.
     */
    public boolean contentEqualsIgnoreCase(CharSequence string) {
        if (string == null || string.length() != length()) {
            return false;
        }

        if (string.getClass() == AsciiString.class) {
            AsciiString rhs = (AsciiString) string;
            for (int i = arrayOffset(), j = rhs.arrayOffset(); i < length(); ++i, ++j) {
                if (!equalsIgnoreCase(value[i], rhs.value[j])) {
                    return false;
                }
            }
            return true;
        }

        for (int i = arrayOffset(), j = 0; i < length(); ++i, ++j) {
            if (!equalsIgnoreCase(b2c(value[i]), string.charAt(j))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies the characters in this string to a character array.
     *
     * @return a character array containing the characters of this string.
     */
    public char[] toCharArray() {
        return toCharArray(0, length());
    }

    /**
     * Copies the characters in this string to a character array.
     *
     * @return a character array containing the characters of this string.
     */
    public char[] toCharArray(int start, int end) {
        int length = end - start;
        if (length == 0) {
            return EmptyArrays.EMPTY_CHARS;
        }

        if (isOutOfBounds(start, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + length() + ')');
        }

        final char[] buffer = new char[length];
        for (int i = 0, j = start + arrayOffset(); i < length; i++, j++) {
            buffer[i] = b2c(value[j]);
        }
        return buffer;
    }

    /**
     * Copied the content of this string to a character array.
     *
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination character array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    public void copy(int srcIdx, char[] dst, int dstIdx, int length) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        if (isOutOfBounds(srcIdx, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + length() + ')');
        }

        final int dstEnd = dstIdx + length;
        for (int i = dstIdx, j = srcIdx + arrayOffset(); i < dstEnd; i++, j++) {
            dst[i] = b2c(value[j]);
        }
    }

    /**
     * Copies a range of characters into a new string.
     * @param start the offset of the first character (inclusive).
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if {@code start < 0} or {@code start > length()}.
     */
    public AsciiString subSequence(int start) {
        return subSequence(start, length());
    }

    /**
     * Copies a range of characters into a new string.
     * @param start the offset of the first character (inclusive).
     * @param end The index to stop at (exclusive).
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if {@code start < 0} or {@code start > length()}.
     */
    @Override
    public AsciiString subSequence(int start, int end) {
       return subSequence(start, end, true);
    }

    /**
     * Either copy or share a subset of underlying sub-sequence of bytes.
     * @param start the offset of the first character (inclusive).
     * @param end The index to stop at (exclusive).
     * @param copy If {@code true} then a copy of the underlying storage will be made.
     * If {@code false} then the underlying storage will be shared.
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if {@code start < 0} or {@code start > length()}.
     */
    public AsciiString subSequence(int start, int end, boolean copy) {
        if (isOutOfBounds(start, end - start, length())) {
            throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= end (" + end + ") <= length("
                            + length() + ')');
        }

        if (start == 0 && end == length()) {
            return this;
        }

        if (end == start) {
            return EMPTY_STRING;
        }

        return new AsciiString(value, start + offset, end - start, copy);
    }

    /**
     * Searches in this string for the first index of the specified string. The search for the string starts at the
     * beginning and moves towards the end of this string.
     *
     * @param string the string to find.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public int indexOf(CharSequence string) {
        return indexOf(string, 0);
    }

    /**
     * Searches in this string for the index of the specified string. The search for the string starts at the specified
     * offset and moves towards the end of this string.
     *
     * @param subString the string to find.
     * @param start the starting offset.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if {@code subString} is {@code null}.
     */
    public int indexOf(CharSequence subString, int start) {
        final int subCount = subString.length();
        if (start < 0) {
            start = 0;
        }
        if (subCount <= 0) {
            return start < length ? start : length;
        }
        if (subCount > length - start) {
            return INDEX_NOT_FOUND;
        }

        final char firstChar = subString.charAt(0);
        if (firstChar > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND;
        }
        final byte firstCharAsByte = c2b0(firstChar);
        final int len = offset + length - subCount;
        for (int i = start + offset; i <= len; ++i) {
            if (value[i] == firstCharAsByte) {
                int o1 = i, o2 = 0;
                while (++o2 < subCount && b2c(value[++o1]) == subString.charAt(o2)) {
                    // Intentionally empty
                }
                if (o2 == subCount) {
                    return i - offset;
                }
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * Searches in this string for the index of the specified char {@code ch}.
     * The search for the char starts at the specified offset {@code start} and moves towards the end of this string.
     *
     * @param ch the char to find.
     * @param start the starting offset.
     * @return the index of the first occurrence of the specified char {@code ch} in this string,
     * -1 if found no occurrence.
     */
    public int indexOf(char ch, int start) {
        if (ch > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND;
        }

        if (start < 0) {
            start = 0;
        }

        final byte chAsByte = c2b0(ch);
        final int len = offset + start + length;
        for (int i = start + offset; i < len; ++i) {
            if (value[i] == chAsByte) {
                return i - offset;
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * Searches in this string for the last index of the specified string. The search for the string starts at the end
     * and moves towards the beginning of this string.
     *
     * @param string the string to find.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public int lastIndexOf(CharSequence string) {
        // Use count instead of count - 1 so lastIndexOf("") answers count
        return lastIndexOf(string, length());
    }

    /**
     * Searches in this string for the index of the specified string. The search for the string starts at the specified
     * offset and moves towards the beginning of this string.
     *
     * @param subString the string to find.
     * @param start the starting offset.
     * @return the index of the first character of the specified string in this string , -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if {@code subString} is {@code null}.
     */
    public int lastIndexOf(CharSequence subString, int start) {
        final int subCount = subString.length();
        if (start < 0) {
            start = 0;
        }
        if (subCount <= 0) {
            return start < length ? start : length;
        }
        if (subCount > length - start) {
            return INDEX_NOT_FOUND;
        }

        final char firstChar = subString.charAt(0);
        if (firstChar > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND;
        }
        final byte firstCharAsByte = c2b0(firstChar);
        final int end = offset + start;
        for (int i = offset + length - subCount; i >= end; --i) {
            if (value[i] == firstCharAsByte) {
                int o1 = i, o2 = 0;
                while (++o2 < subCount && b2c(value[++o1]) == subString.charAt(o2)) {
                    // Intentionally empty
                }
                if (o2 == subCount) {
                    return i - offset;
                }
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * Compares the specified string to this string and compares the specified range of characters to determine if they
     * are the same.
     *
     * @param thisStart the starting offset in this string.
     * @param string the string to compare.
     * @param start the starting offset in the specified string.
     * @param length the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false} otherwise
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public boolean regionMatches(int thisStart, CharSequence string, int start, int length) {
        if (string == null) {
            throw new NullPointerException("string");
        }

        if (start < 0 || string.length() - start < length) {
            return false;
        }

        final int thisLen = length();
        if (thisStart < 0 || thisLen - thisStart < length) {
            return false;
        }

        if (length <= 0) {
            return true;
        }

        final int thatEnd = start + length;
        for (int i = start, j = thisStart + arrayOffset(); i < thatEnd; i++, j++) {
            if (b2c(value[j]) != string.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the specified string to this string and compares the specified range of characters to determine if they
     * are the same. When ignoreCase is true, the case of the characters is ignored during the comparison.
     *
     * @param ignoreCase specifies if case should be ignored.
     * @param thisStart the starting offset in this string.
     * @param string the string to compare.
     * @param start the starting offset in the specified string.
     * @param length the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false} otherwise.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public boolean regionMatches(boolean ignoreCase, int thisStart, CharSequence string, int start, int length) {
        if (!ignoreCase) {
            return regionMatches(thisStart, string, start, length);
        }

        if (string == null) {
            throw new NullPointerException("string");
        }

        final int thisLen = length();
        if (thisStart < 0 || length > thisLen - thisStart) {
            return false;
        }
        if (start < 0 || length > string.length() - start) {
            return false;
        }

        thisStart += arrayOffset();
        final int thisEnd = thisStart + length;
        while (thisStart < thisEnd) {
            if (!equalsIgnoreCase(b2c(value[thisStart++]), string.charAt(start++))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies this string replacing occurrences of the specified character with another character.
     *
     * @param oldChar the character to replace.
     * @param newChar the replacement character.
     * @return a new string with occurrences of oldChar replaced by newChar.
     */
    public AsciiString replace(char oldChar, char newChar) {
        if (oldChar > MAX_CHAR_VALUE) {
            return this;
        }

        final byte oldCharAsByte = c2b0(oldChar);
        final byte newCharAsByte = c2b(newChar);
        final int len = offset + length;
        for (int i = offset; i < len; ++i) {
            if (value[i] == oldCharAsByte) {
                byte[] buffer = new byte[length()];
                System.arraycopy(value, offset, buffer, 0, i - offset);
                buffer[i - offset] = newCharAsByte;
                ++i;
                for (; i < len; ++i) {
                    byte oldValue = value[i];
                    buffer[i - offset] = oldValue != oldCharAsByte ? oldValue : newCharAsByte;
                }
                return new AsciiString(buffer, false);
            }
        }
        return this;
    }

    /**
     * Compares the specified string to this string to determine if the specified string is a prefix.
     *
     * @param prefix the string to look for.
     * @return {@code true} if the specified string is a prefix of this string, {@code false} otherwise
     * @throws NullPointerException if {@code prefix} is {@code null}.
     */
    public boolean startsWith(CharSequence prefix) {
        return startsWith(prefix, 0);
    }

    /**
     * Compares the specified string to this string, starting at the specified offset, to determine if the specified
     * string is a prefix.
     *
     * @param prefix the string to look for.
     * @param start the starting offset.
     * @return {@code true} if the specified string occurs in this string at the specified offset, {@code false}
     *         otherwise.
     * @throws NullPointerException if {@code prefix} is {@code null}.
     */
    public boolean startsWith(CharSequence prefix, int start) {
        return regionMatches(start, prefix, 0, prefix.length());
    }

    /**
     * Converts the characters in this string to lowercase, using the default Locale.
     *
     * @return a new string containing the lowercase characters equivalent to the characters in this string.
     */
    public AsciiString toLowerCase() {
        boolean lowercased = true;
        int i, j;
        final int len = length() + arrayOffset();
        for (i = arrayOffset(); i < len; ++i) {
            byte b = value[i];
            if (b >= 'A' && b <= 'Z') {
                lowercased = false;
                break;
            }
        }

        // Check if this string does not contain any uppercase characters.
        if (lowercased) {
            return this;
        }

        final byte[] newValue = new byte[length()];
        for (i = 0, j = arrayOffset(); i < newValue.length; ++i, ++j) {
            newValue[i] = toLowerCase(value[j]);
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Converts the characters in this string to uppercase, using the default Locale.
     *
     * @return a new string containing the uppercase characters equivalent to the characters in this string.
     */
    public AsciiString toUpperCase() {
        boolean uppercased = true;
        int i, j;
        final int len = length() + arrayOffset();
        for (i = arrayOffset(); i < len; ++i) {
            byte b = value[i];
            if (b >= 'a' && b <= 'z') {
                uppercased = false;
                break;
            }
        }

        // Check if this string does not contain any lowercase characters.
        if (uppercased) {
            return this;
        }

        final byte[] newValue = new byte[length()];
        for (i = 0, j = arrayOffset(); i < newValue.length; ++i, ++j) {
            newValue[i] = toUpperCase(value[j]);
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Copies this string removing white space characters from the beginning and end of the string, and tries not to
     * copy if possible.
     *
     * @param c The {@link CharSequence} to trim.
     * @return a new string with characters {@code <= \\u0020} removed from the beginning and the end.
     */
    public static CharSequence trim(CharSequence c) {
        if (c.getClass() == AsciiString.class) {
            return ((AsciiString) c).trim();
        }
        if (c instanceof String) {
            return ((String) c).trim();
        }
        int start = 0, last = c.length() - 1;
        int end = last;
        while (start <= end && c.charAt(start) <= ' ') {
            start++;
        }
        while (end >= start && c.charAt(end) <= ' ') {
            end--;
        }
        if (start == 0 && end == last) {
            return c;
        }
        return c.subSequence(start, end);
    }

    /**
     * Duplicates this string removing white space characters from the beginning and end of the
     * string, without copying.
     *
     * @return a new string with characters {@code <= \\u0020} removed from the beginning and the end.
     */
    public AsciiString trim() {
        int start = arrayOffset(), last = arrayOffset() + length() - 1;
        int end = last;
        while (start <= end && value[start] <= ' ') {
            start++;
        }
        while (end >= start && value[end] <= ' ') {
            end--;
        }
        if (start == 0 && end == last) {
            return this;
        }
        return new AsciiString(value, start, end - start + 1, false);
    }

    /**
     * Compares a {@code CharSequence} to this {@code String} to determine if their contents are equal.
     *
     * @param a the character sequence to compare to.
     * @return {@code true} if equal, otherwise {@code false}
     */
    public boolean contentEquals(CharSequence a) {
        if (a == null || a.length() != length()) {
            return false;
        }
        if (a.getClass() == AsciiString.class) {
            return equals(a);
        }

        for (int i = arrayOffset(), j = 0; j < a.length(); ++i, ++j) {
            if (b2c(value[i]) != a.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines whether this string matches a given regular expression.
     *
     * @param expr the regular expression to be matched.
     * @return {@code true} if the expression matches, otherwise {@code false}.
     * @throws PatternSyntaxException if the syntax of the supplied regular expression is not valid.
     * @throws NullPointerException if {@code expr} is {@code null}.
     */
    public boolean matches(String expr) {
        return Pattern.matches(expr, this);
    }

    /**
     * Splits this string using the supplied regular expression {@code expr}. The parameter {@code max} controls the
     * behavior how many times the pattern is applied to the string.
     *
     * @param expr the regular expression used to divide the string.
     * @param max the number of entries in the resulting array.
     * @return an array of Strings created by separating the string along matches of the regular expression.
     * @throws NullPointerException if {@code expr} is {@code null}.
     * @throws PatternSyntaxException if the syntax of the supplied regular expression is not valid.
     * @see Pattern#split(CharSequence, int)
     */
    public AsciiString[] split(String expr, int max) {
        return toAsciiStringArray(Pattern.compile(expr).split(this, max));
    }

    /**
     * Splits the specified {@link String} with the specified delimiter..
     */
    public AsciiString[] split(char delim) {
        final List<AsciiString> res = InternalThreadLocalMap.get().arrayList();

        int start = 0;
        final int length = length();
        for (int i = start; i < length; i++) {
            if (charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(new AsciiString(value, start + arrayOffset(), i - start, false));
                }
                start = i + 1;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(this);
        } else {
            if (start != length) {
                // Add the last element if it's not empty.
                res.add(new AsciiString(value, start + arrayOffset(), length - start, false));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i--) {
                    if (res.get(i).isEmpty()) {
                        res.remove(i);
                    } else {
                        break;
                    }
                }
            }
        }

        return res.toArray(new AsciiString[res.size()]);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Provides a case-insensitive hash code for Ascii like byte strings.
     */
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = PlatformDependent.hashCodeAscii(value, offset, length);
            hash = h;
        }
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != AsciiString.class) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        AsciiString other = (AsciiString) obj;
        return length() == other.length() &&
               hashCode() == other.hashCode() &&
               PlatformDependent.equals(array(), arrayOffset(), other.array(), other.arrayOffset(), length());
    }

    /**
     * Translates the entire byte string to a {@link String}.
     * @see #toString(int)
     */
    @Override
    public String toString() {
        String cache = string;
        if (cache == null) {
            cache = toString(0);
            string = cache;
        }
        return cache;
    }

    /**
     * Translates the entire byte string to a {@link String} using the {@code charset} encoding.
     * @see #toString(int, int)
     */
    public String toString(int start) {
        return toString(start, length());
    }

    /**
     * Translates the [{@code start}, {@code end}) range of this byte string to a {@link String}.
     */
    public String toString(int start, int end) {
        int length = end - start;
        if (length == 0) {
            return "";
        }

        if (isOutOfBounds(start, length, length())) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + length() + ')');
        }

        @SuppressWarnings("deprecation")
        final String str = new String(value, 0, start + offset, length);
        return str;
    }

    public boolean parseBoolean() {
        return length >= 1 && value[offset] != 0;
    }

    public char parseChar() {
        return parseChar(0);
    }

    public char parseChar(int start) {
        if (start + 1 >= length()) {
            throw new IndexOutOfBoundsException("2 bytes required to convert to character. index " +
                    start + " would go out of bounds.");
        }
        final int startWithOffset = start + offset;
        return (char) ((b2c(value[startWithOffset]) << 8) | b2c(value[startWithOffset + 1]));
    }

    public short parseShort() {
        return parseShort(0, length(), 10);
    }

    public short parseShort(int radix) {
        return parseShort(0, length(), radix);
    }

    public short parseShort(int start, int end) {
        return parseShort(start, end, 10);
    }

    public short parseShort(int start, int end, int radix) {
        int intValue = parseInt(start, end, radix);
        short result = (short) intValue;
        if (result != intValue) {
            throw new NumberFormatException(subSequence(start, end, false).toString());
        }
        return result;
    }

    public int parseInt() {
        return parseInt(0, length(), 10);
    }

    public int parseInt(int radix) {
        return parseInt(0, length(), radix);
    }

    public int parseInt(int start, int end) {
        return parseInt(start, end, 10);
    }

    public int parseInt(int start, int end, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException();
        }

        if (start == end) {
            throw new NumberFormatException();
        }

        int i = start;
        boolean negative = byteAt(i) == '-';
        if (negative && ++i == end) {
            throw new NumberFormatException(subSequence(start, end, false).toString());
        }

        return parseInt(i, end, radix, negative);
    }

    private int parseInt(int start, int end, int radix, boolean negative) {
        int max = Integer.MIN_VALUE / radix;
        int result = 0;
        int currOffset = start;
        while (currOffset < end) {
            int digit = Character.digit((char) (value[currOffset++ + offset] & 0xFF), radix);
            if (digit == -1) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            if (max > result) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            int next = result * radix - digit;
            if (next > result) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            result = next;
        }
        if (!negative) {
            result = -result;
            if (result < 0) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
        }
        return result;
    }

    public long parseLong() {
        return parseLong(0, length(), 10);
    }

    public long parseLong(int radix) {
        return parseLong(0, length(), radix);
    }

    public long parseLong(int start, int end) {
        return parseLong(start, end, 10);
    }

    public long parseLong(int start, int end, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException();
        }

        if (start == end) {
            throw new NumberFormatException();
        }

        int i = start;
        boolean negative = byteAt(i) == '-';
        if (negative && ++i == end) {
            throw new NumberFormatException(subSequence(start, end, false).toString());
        }

        return parseLong(i, end, radix, negative);
    }

    private long parseLong(int start, int end, int radix, boolean negative) {
        long max = Long.MIN_VALUE / radix;
        long result = 0;
        int currOffset = start;
        while (currOffset < end) {
            int digit = Character.digit((char) (value[currOffset++ + offset] & 0xFF), radix);
            if (digit == -1) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            if (max > result) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            long next = result * radix - digit;
            if (next > result) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
            result = next;
        }
        if (!negative) {
            result = -result;
            if (result < 0) {
                throw new NumberFormatException(subSequence(start, end, false).toString());
            }
        }
        return result;
    }

    public float parseFloat() {
        return parseFloat(0, length());
    }

    public float parseFloat(int start, int end) {
        return Float.parseFloat(toString(start, end));
    }

    public double parseDouble() {
        return parseDouble(0, length());
    }

    public double parseDouble(int start, int end) {
        return Double.parseDouble(toString(start, end));
    }

    public static final HashingStrategy<CharSequence> CASE_INSENSITIVE_HASHER =
            new HashingStrategy<CharSequence>() {
        @Override
        public int hashCode(CharSequence o) {
            return AsciiString.hashCode(o);
        }

        @Override
        public boolean equals(CharSequence a, CharSequence b) {
            return AsciiString.contentEqualsIgnoreCase(a, b);
        }
    };

    public static final HashingStrategy<CharSequence> CASE_SENSITIVE_HASHER =
            new HashingStrategy<CharSequence>() {
        @Override
        public int hashCode(CharSequence o) {
            return AsciiString.hashCode(o);
        }

        @Override
        public boolean equals(CharSequence a, CharSequence b) {
            return AsciiString.contentEquals(a, b);
        }
    };

    /**
     * Returns an {@link AsciiString} containing the given character sequence. If the given string is already a
     * {@link AsciiString}, just returns the same instance.
     */
    public static AsciiString of(CharSequence string) {
        return string.getClass() == AsciiString.class ? (AsciiString) string : new AsciiString(string);
    }

    /**
     * Returns an {@link AsciiString} containing the given string and retains/caches the input
     * string for later use in {@link #toString()}.
     * Used for the constants (which already stored in the JVM's string table) and in cases
     * where the guaranteed use of the {@link #toString()} method.
     */
    public static AsciiString cached(String string) {
        AsciiString asciiString = new AsciiString(string);
        asciiString.string = string;
        return asciiString;
    }

    /**
     * Returns the case-insensitive hash code of the specified string. Note that this method uses the same hashing
     * algorithm with {@link #hashCode()} so that you can put both {@link AsciiString}s and arbitrary
     * {@link CharSequence}s into the same headers.
     */
    public static int hashCode(CharSequence value) {
        if (value == null) {
            return 0;
        }
        if (value.getClass() == AsciiString.class) {
            return value.hashCode();
        }

        return PlatformDependent.hashCodeAscii(value);
    }

    /**
     * Determine if {@code a} contains {@code b} in a case sensitive manner.
     */
    public static boolean contains(CharSequence a, CharSequence b) {
        return contains(a, b, DefaultCharEqualityComparator.INSTANCE);
    }

    /**
     * Determine if {@code a} contains {@code b} in a case insensitive manner.
     */
    public static boolean containsIgnoreCase(CharSequence a, CharSequence b) {
        return contains(a, b, AsciiCaseInsensitiveCharEqualityComparator.INSTANCE);
    }

    /**
     * Returns {@code true} if both {@link CharSequence}'s are equals when ignore the case. This only supports 8-bit
     * ASCII.
     */
    public static boolean contentEqualsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.getClass() == AsciiString.class) {
            return ((AsciiString) a).contentEqualsIgnoreCase(b);
        }
        if (b.getClass() == AsciiString.class) {
            return ((AsciiString) b).contentEqualsIgnoreCase(a);
        }

        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0, j = 0; i < a.length(); ++i, ++j) {
            if (!equalsIgnoreCase(a.charAt(i),  b.charAt(j))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine if {@code collection} contains {@code value} and using
     * {@link #contentEqualsIgnoreCase(CharSequence, CharSequence)} to compare values.
     * @param collection The collection to look for and equivalent element as {@code value}.
     * @param value The value to look for in {@code collection}.
     * @return {@code true} if {@code collection} contains {@code value} according to
     * {@link #contentEqualsIgnoreCase(CharSequence, CharSequence)}. {@code false} otherwise.
     * @see #contentEqualsIgnoreCase(CharSequence, CharSequence)
     */
    public static boolean containsContentEqualsIgnoreCase(Collection<CharSequence> collection, CharSequence value) {
        for (CharSequence v : collection) {
            if (contentEqualsIgnoreCase(value, v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine if {@code a} contains all of the values in {@code b} using
     * {@link #contentEqualsIgnoreCase(CharSequence, CharSequence)} to compare values.
     * @param a The collection under test.
     * @param b The values to test for.
     * @return {@code true} if {@code a} contains all of the values in {@code b} using
     * {@link #contentEqualsIgnoreCase(CharSequence, CharSequence)} to compare values. {@code false} otherwise.
     * @see #contentEqualsIgnoreCase(CharSequence, CharSequence)
     */
    public static boolean containsAllContentEqualsIgnoreCase(Collection<CharSequence> a, Collection<CharSequence> b) {
        for (CharSequence v : b) {
            if (!containsContentEqualsIgnoreCase(a, v)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if the content of both {@link CharSequence}'s are equals. This only supports 8-bit ASCII.
     */
    public static boolean contentEquals(CharSequence a, CharSequence b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.getClass() == AsciiString.class) {
            return ((AsciiString) a).contentEquals(b);
        }

        if (b.getClass() == AsciiString.class) {
            return ((AsciiString) b).contentEquals(a);
        }

        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i <  a.length(); ++i) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static AsciiString[] toAsciiStringArray(String[] jdkResult) {
        AsciiString[] res = new AsciiString[jdkResult.length];
        for (int i = 0; i < jdkResult.length; i++) {
            res[i] = new AsciiString(jdkResult[i]);
        }
        return res;
    }

    private interface CharEqualityComparator {
        boolean equals(char a, char b);
    }

    private static final class DefaultCharEqualityComparator implements CharEqualityComparator {
        static final DefaultCharEqualityComparator INSTANCE = new DefaultCharEqualityComparator();
        private DefaultCharEqualityComparator() { }

        @Override
        public boolean equals(char a, char b) {
            return a == b;
        }
    }

    private static final class AsciiCaseInsensitiveCharEqualityComparator implements CharEqualityComparator {
        static final AsciiCaseInsensitiveCharEqualityComparator
                INSTANCE = new AsciiCaseInsensitiveCharEqualityComparator();
        private AsciiCaseInsensitiveCharEqualityComparator() { }

        @Override
        public boolean equals(char a, char b) {
            return equalsIgnoreCase(a, b);
        }
    }

    private static final class GeneralCaseInsensitiveCharEqualityComparator implements CharEqualityComparator {
        static final GeneralCaseInsensitiveCharEqualityComparator
                INSTANCE = new GeneralCaseInsensitiveCharEqualityComparator();
        private GeneralCaseInsensitiveCharEqualityComparator() { }

        @Override
        public boolean equals(char a, char b) {
            //For motivation, why we need two checks, see comment in String#regionMatches
            return Character.toUpperCase(a) == Character.toUpperCase(b) ||
                Character.toLowerCase(a) == Character.toLowerCase(b);
        }
    }

    private static boolean contains(CharSequence a, CharSequence b, CharEqualityComparator cmp) {
        if (a == null || b == null || a.length() < b.length()) {
            return false;
        }
        if (b.length() == 0) {
            return true;
        }
        int bStart = 0;
        for (int i = 0; i < a.length(); ++i) {
            if (cmp.equals(b.charAt(bStart), a.charAt(i))) {
                // If b is consumed then true.
                if (++bStart == b.length()) {
                    return true;
                }
            } else if (a.length() - i < b.length()) {
                // If there are not enough characters left in a for b to be contained, then false.
                return false;
            } else {
                bStart = 0;
            }
        }
        return false;
    }

    private static boolean regionMatchesCharSequences(final CharSequence cs, final int csStart,
                                         final CharSequence string, final int start, final int length,
                                         CharEqualityComparator charEqualityComparator) {
        //general purpose implementation for CharSequences
        if (csStart < 0 || length > cs.length() - csStart) {
            return false;
        }
        if (start < 0 || length > string.length() - start) {
            return false;
        }

        int csIndex = csStart;
        int csEnd = csIndex + length;
        int stringIndex = start;

        while (csIndex < csEnd) {
            char c1 = cs.charAt(csIndex++);
            char c2 = string.charAt(stringIndex++);

            if (!charEqualityComparator.equals(c1, c2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * This methods make regionMatches operation correctly for any chars in strings
     * @param cs the {@code CharSequence} to be processed
     * @param ignoreCase specifies if case should be ignored.
     * @param csStart the starting offset in the {@code cs} CharSequence
     * @param string the {@code CharSequence} to compare.
     * @param start the starting offset in the specified {@code string}.
     * @param length the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false} otherwise.
     */
    public static boolean regionMatches(final CharSequence cs, final boolean ignoreCase, final int csStart,
                                        final CharSequence string, final int start, final int length) {
        if (cs == null || string == null) {
            return false;
        }

        if (cs instanceof String && string instanceof String) {
            return ((String) cs).regionMatches(ignoreCase, csStart, (String) string, start, length);
        }

        if (cs instanceof AsciiString) {
            return ((AsciiString) cs).regionMatches(ignoreCase, csStart, string, start, length);
        }

        return regionMatchesCharSequences(cs, csStart, string, start, length,
                                            ignoreCase ? GeneralCaseInsensitiveCharEqualityComparator.INSTANCE :
                                                    DefaultCharEqualityComparator.INSTANCE);
    }

    /**
     * This is optimized version of regionMatches for string with ASCII chars only
     * @param cs the {@code CharSequence} to be processed
     * @param ignoreCase specifies if case should be ignored.
     * @param csStart the starting offset in the {@code cs} CharSequence
     * @param string the {@code CharSequence} to compare.
     * @param start the starting offset in the specified {@code string}.
     * @param length the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false} otherwise.
     */
    public static boolean regionMatchesAscii(final CharSequence cs, final boolean ignoreCase, final int csStart,
                                        final CharSequence string, final int start, final int length) {
        if (cs == null || string == null) {
            return false;
        }

        if (!ignoreCase && cs instanceof String && string instanceof String) {
            //we don't call regionMatches from String for ignoreCase==true. It's a general purpose method,
            //which make complex comparison in case of ignoreCase==true, which is useless for ASCII-only strings.
            //To avoid applying this complex ignore-case comparison, we will use regionMatchesCharSequences
            return ((String) cs).regionMatches(false, csStart, (String) string, start, length);
        }

        if (cs instanceof AsciiString) {
            return ((AsciiString) cs).regionMatches(ignoreCase, csStart, string, start, length);
        }

        return regionMatchesCharSequences(cs, csStart, string, start, length,
                                          ignoreCase ? AsciiCaseInsensitiveCharEqualityComparator.INSTANCE :
                                                      DefaultCharEqualityComparator.INSTANCE);
    }

    /**
     * <p>Case in-sensitive find of the first index within a CharSequence
     * from the specified position.</p>
     *
     * <p>A {@code null} CharSequence will return {@code -1}.
     * A negative start position is treated as zero.
     * An empty ("") search CharSequence always matches.
     * A start position greater than the string length only matches
     * an empty search CharSequence.</p>
     *
     * <pre>
     * AsciiString.indexOfIgnoreCase(null, *, *)          = -1
     * AsciiString.indexOfIgnoreCase(*, null, *)          = -1
     * AsciiString.indexOfIgnoreCase("", "", 0)           = 0
     * AsciiString.indexOfIgnoreCase("aabaabaa", "A", 0)  = 0
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 0)  = 2
     * AsciiString.indexOfIgnoreCase("aabaabaa", "AB", 0) = 1
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 3)  = 5
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 9)  = -1
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", -1) = 2
     * AsciiString.indexOfIgnoreCase("aabaabaa", "", 2)   = 2
     * AsciiString.indexOfIgnoreCase("abc", "", 9)        = -1
     * </pre>
     *
     * @param str  the CharSequence to check, may be null
     * @param searchStr  the CharSequence to find, may be null
     * @param startPos  the start position, negative treated as zero
     * @return the first index of the search CharSequence (always &ge; startPos),
     *  -1 if no match or {@code null} string input
     */
    public static int indexOfIgnoreCase(final CharSequence str, final CharSequence searchStr, int startPos) {
        if (str == null || searchStr == null) {
            return INDEX_NOT_FOUND;
        }
        if (startPos < 0) {
            startPos = 0;
        }
        int searchStrLen = searchStr.length();
        final int endLimit = str.length() - searchStrLen + 1;
        if (startPos > endLimit) {
            return INDEX_NOT_FOUND;
        }
        if (searchStrLen == 0) {
            return startPos;
        }
        for (int i = startPos; i < endLimit; i++) {
            if (regionMatches(str, true, i, searchStr, 0, searchStrLen)) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * <p>Case in-sensitive find of the first index within a CharSequence
     * from the specified position. This method optimized and works correctly for ASCII CharSequences only</p>
     *
     * <p>A {@code null} CharSequence will return {@code -1}.
     * A negative start position is treated as zero.
     * An empty ("") search CharSequence always matches.
     * A start position greater than the string length only matches
     * an empty search CharSequence.</p>
     *
     * <pre>
     * AsciiString.indexOfIgnoreCase(null, *, *)          = -1
     * AsciiString.indexOfIgnoreCase(*, null, *)          = -1
     * AsciiString.indexOfIgnoreCase("", "", 0)           = 0
     * AsciiString.indexOfIgnoreCase("aabaabaa", "A", 0)  = 0
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 0)  = 2
     * AsciiString.indexOfIgnoreCase("aabaabaa", "AB", 0) = 1
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 3)  = 5
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", 9)  = -1
     * AsciiString.indexOfIgnoreCase("aabaabaa", "B", -1) = 2
     * AsciiString.indexOfIgnoreCase("aabaabaa", "", 2)   = 2
     * AsciiString.indexOfIgnoreCase("abc", "", 9)        = -1
     * </pre>
     *
     * @param str  the CharSequence to check, may be null
     * @param searchStr  the CharSequence to find, may be null
     * @param startPos  the start position, negative treated as zero
     * @return the first index of the search CharSequence (always &ge; startPos),
     *  -1 if no match or {@code null} string input
     */
    public static int indexOfIgnoreCaseAscii(final CharSequence str, final CharSequence searchStr, int startPos) {
        if (str == null || searchStr == null) {
            return INDEX_NOT_FOUND;
        }
        if (startPos < 0) {
            startPos = 0;
        }
        int searchStrLen = searchStr.length();
        final int endLimit = str.length() - searchStrLen + 1;
        if (startPos > endLimit) {
            return INDEX_NOT_FOUND;
        }
        if (searchStrLen == 0) {
            return startPos;
        }
        for (int i = startPos; i < endLimit; i++) {
            if (regionMatchesAscii(str, true, i, searchStr, 0, searchStrLen)) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    /**
     * <p>Finds the first index in the {@code CharSequence} that matches the
     * specified character.</p>
     *
     * @param cs  the {@code CharSequence} to be processed, not null
     * @param searchChar the char to be searched for
     * @param start  the start index, negative starts at the string start
     * @return the index where the search char was found,
     * -1 if char {@code searchChar} is not found or {@code cs == null}
     */
    //-----------------------------------------------------------------------
    public static int indexOf(final CharSequence cs, final char searchChar, int start) {
        if (cs instanceof String) {
            return ((String) cs).indexOf(searchChar, start);
        } else if (cs instanceof AsciiString) {
            return ((AsciiString) cs).indexOf(searchChar, start);
        }
        if (cs == null) {
            return INDEX_NOT_FOUND;
        }
        final int sz = cs.length();
        for (int i = start < 0 ? 0 : start; i < sz; i++) {
            if (cs.charAt(i) == searchChar) {
                return i;
            }
        }
        return INDEX_NOT_FOUND;
    }

    private static boolean equalsIgnoreCase(byte a, byte b) {
        return a == b || toLowerCase(a) == toLowerCase(b);
    }

    private static boolean equalsIgnoreCase(char a, char b) {
        return a == b || toLowerCase(a) == toLowerCase(b);
    }

    private static byte toLowerCase(byte b) {
        return isUpperCase(b) ? (byte) (b + 32) : b;
    }

    private static char toLowerCase(char c) {
        return isUpperCase(c) ? (char) (c + 32) : c;
    }

    private static byte toUpperCase(byte b) {
        return isLowerCase(b) ? (byte) (b - 32) : b;
    }

    private static boolean isLowerCase(byte value) {
        return value >= 'a' && value <= 'z';
    }

    public static boolean isUpperCase(byte value) {
        return value >= 'A' && value <= 'Z';
    }

    public static boolean isUpperCase(char value) {
        return value >= 'A' && value <= 'Z';
    }

    public static byte c2b(char c) {
        return (byte) ((c > MAX_CHAR_VALUE) ? '?' : c);
    }

    private static byte c2b0(char c) {
        return (byte) c;
    }

    public static char b2c(byte b) {
        return (char) (b & 0xFF);
    }
}
