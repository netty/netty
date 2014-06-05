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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.EmptyArrays;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A string which has been encoded into a character encoding whose character always takes a single byte, similarly
 * to ASCII.  It internally keeps its content in a byte array unlike {@link String}, which uses a character array,
 * for reduced memory footprint and faster data transfer from/to byte-based data structures such as a byte array and
 * {@link ByteBuf}.  It is often used in conjunction with {@link TextHeaders}.
 */
public final class AsciiString implements CharSequence, Comparable<CharSequence> {

    public static final AsciiString EMPTY_STRING = new AsciiString("");

    /** XXX: Make sure that this method and {@link #hashCode()} uses the same hashing algorithm */
    static int hashCode(CharSequence value) {
        if (value instanceof AsciiString) {
            return value.hashCode();
        }

        int hash = 0;
        final int end = value.length();
        for (int i = 0; i < end; i ++) {
            hash = hash * 31 ^ value.charAt(i) & 31;
        }

        return hash;
    }

    private final byte[] value;
    private String string;
    private int hash;

    public AsciiString(byte[] value) {
        this(value, true);
    }

    public AsciiString(byte[] value, boolean copy) {
        checkNull(value);
        if (copy) {
            this.value = value.clone();
        } else {
            this.value = value;
        }
    }

    public AsciiString(byte[] value, int start, int length) {
        this(value, start, length, true);
    }

    public AsciiString(byte[] value, int start, int length, boolean copy) {
        checkNull(value);
        if (start < 0 || start > value.length - length) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= start(" + start + ") <= start + length(" + length + ") <= " +
                    "value.length(" + value.length + ')');
        }

        if (copy || start != 0 || length != value.length) {
            this.value = Arrays.copyOfRange(value, start, start + length);
        } else {
            this.value = value;
        }
    }

    public AsciiString(char[] value) {
        this(checkNull(value), 0, value.length);
    }

    public AsciiString(char[] value, int start, int length) {
        checkNull(value);
        if (start < 0 || start > value.length - length) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= start(" + start + ") <= start + length(" + length + ") <= " +
                    "value.length(" + value.length + ')');
        }

        this.value = new byte[length];
        for (int i = 0, j = start; i < length; i ++, j ++) {
            this.value[i] = c2b(value[j]);
        }
    }

    public AsciiString(CharSequence value) {
        this(checkNull(value), 0, value.length());
    }

    public AsciiString(CharSequence value, int start, int length) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        if (start < 0 || length < 0 || length > value.length() - start) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= start(" + start + ") <= start + length(" + length + ") <= " +
                    "value.length(" + value.length() + ')');
        }

        this.value = new byte[length];
        for (int i = 0; i < length; i++) {
            this.value[i] = c2b(value.charAt(start + i));
        }
    }

    public AsciiString(ByteBuffer value) {
        this(checkNull(value), value.position(), value.remaining());
    }

    public AsciiString(ByteBuffer value, int start, int length) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        if (start < 0 || length > value.capacity() - start) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= start(" + start + ") <= start + length(" + length + ") <= " +
                    "value.capacity(" + value.capacity() + ')');
        }

        if (value.hasArray()) {
            int baseOffset = value.arrayOffset() + start;
            this.value = Arrays.copyOfRange(value.array(), baseOffset, baseOffset + length);
        } else {
            this.value = new byte[length];
            int oldPos = value.position();
            value.get(this.value, 0, this.value.length);
            value.position(oldPos);
        }
    }

    private static <T> T checkNull(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return value;
    }

    @Override
    public int length() {
        return value.length;
    }

    @Override
    public char charAt(int index) {
        return (char) (byteAt(index) & 0xFF);
    }

    public byte byteAt(int index) {
        return value[index];
    }

    public byte[] array() {
        return value;
    }

    public int arrayOffset() {
        return 0;
    }

    private static byte c2b(char c) {
        if (c > 255) {
            return '?';
        }
        return (byte) c;
    }

    private static byte toLowerCase(byte b) {
        if ('A' <= b && b <= 'Z') {
            return (byte) (b + 32);
        }
        return b;
    }

    private static char toLowerCase(char c) {
        if ('A' <= c && c <= 'Z') {
            return (char) (c + 32);
        }
        return c;
    }

    private static byte toUpperCase(byte b) {
        if ('a' <= b && b <= 'z') {
            return (byte) (b - 32);
        }
        return b;
    }

    /**
     * Copies a range of characters into a new string.
     *
     * @param start
     *            the offset of the first character.
     * @return a new string containing the characters from start to the end of
     *         the string.
     * @throws IndexOutOfBoundsException
     *             if {@code start < 0} or {@code start > length()}.
     */
    public AsciiString subSequence(int start) {
        return subSequence(start, length());
    }

    @Override
    public AsciiString subSequence(int start, int end) {
        if (start < 0 || start > end || end > length()) {
            throw new IndexOutOfBoundsException(
                    "expected: 0 <= start(" + start + ") <= end (" + end + ") <= length(" + length() + ')');
        }

        final byte[] value = this.value;
        if (start == 0 && end == value.length) {
            return this;
        }

        if (end == start) {
            return EMPTY_STRING;
        }

        return new AsciiString(value, start, end - start, false);
    }

    @Override
    public int hashCode() {
        int hash = this.hash;
        final byte[] value = this.value;
        if (hash != 0 || value.length == 0) {
            return hash;
        }

        for (byte b: value) {
            hash = hash * 31 ^ b & 31;
        }

        return this.hash = hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AsciiString)) {
            return false;
        }

        if (this == obj) {
            return true;
        }

        AsciiString that = (AsciiString) obj;
        int thisHash = hashCode();
        int thatHash = that.hashCode();
        if (thisHash != thatHash || length() != that.length()) {
            return false;
        }

        byte[] thisValue = value;
        byte[] thatValue = that.value;
        int end = thisValue.length;
        for (int i = 0, j = 0; i < end; i ++, j ++) {
            if (thisValue[i] != thatValue[j]) {
                return false;
            }
        }

        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        String string = this.string;
        if (string != null) {
            return string;
        }

        final byte[] value = this.value;
        return this.string = new String(value, 0, 0, value.length);
    }

    @SuppressWarnings("deprecation")
    public String toString(int start, int end) {
        final byte[] value = this.value;
        if (start == 0 && end == value.length) {
            return toString();
        }

        int length = end - start;
        if (length == 0) {
            return "";
        }

        return new String(value, 0, start, length);
    }

    /**
     * Compares the specified string to this string using the ASCII values of
     * the characters. Returns 0 if the strings contain the same characters in
     * the same order. Returns a negative integer if the first non-equal
     * character in this string has an ASCII value which is less than the
     * ASCII value of the character at the same position in the specified
     * string, or if this string is a prefix of the specified string. Returns a
     * positive integer if the first non-equal character in this string has a
     * ASCII value which is greater than the ASCII value of the character at
     * the same position in the specified string, or if the specified string is
     * a prefix of this string.
     *
     * @param string
     *            the string to compare.
     * @return 0 if the strings are equal, a negative integer if this string is
     *         before the specified string, or a positive integer if this string
     *         is after the specified string.
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
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
        byte[] value = this.value;
        for (int i = 0, j = 0; j < minLength; i ++, j ++) {
            result = (value[i] & 0xFF) - string.charAt(j);
            if (result != 0) {
                return result;
            }
        }

        return length1 - length2;
    }

    /**
     * Compares the specified string to this string using the ASCII values of
     * the characters, ignoring case differences. Returns 0 if the strings
     * contain the same characters in the same order. Returns a negative integer
     * if the first non-equal character in this string has an ASCII value which
     * is less than the ASCII value of the character at the same position in
     * the specified string, or if this string is a prefix of the specified
     * string. Returns a positive integer if the first non-equal character in
     * this string has an ASCII value which is greater than the ASCII value
     * of the character at the same position in the specified string, or if the
     * specified string is a prefix of this string.
     *
     * @param string
     *            the string to compare.
     * @return 0 if the strings are equal, a negative integer if this string is
     *         before the specified string, or a positive integer if this string
     *         is after the specified string.
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
     */
    public int compareToIgnoreCase(CharSequence string) {
        if (this == string) {
            return 0;
        }

        int result;
        int length1 = length();
        int length2 = string.length();
        int minLength = Math.min(length1, length2);
        byte[] thisValue = value;
        if (string instanceof AsciiString) {
            AsciiString that = (AsciiString) string;
            byte[] thatValue = that.value;
            for (int i = 0; i < minLength; i ++) {
                byte v1 = thisValue[i];
                byte v2 = thatValue[i];
                if (v1 == v2) {
                    continue;
                }
                int c1 = toLowerCase(v1) & 0xFF;
                int c2 = toLowerCase(v2) & 0xFF;
                result = c1 - c2;
                if (result != 0) {
                    return result;
                }
            }
        } else {
            for (int i = 0; i < minLength; i ++) {
                int c1 = toLowerCase(thisValue[i]) & 0xFF;
                int c2 = toLowerCase(string.charAt(i));
                result = c1 - c2;
                if (result != 0) {
                    return result;
                }
            }
        }

        return length1 - length2;
    }

    /**
     * Concatenates this string and the specified string.
     *
     * @param string
     *            the string to concatenate
     * @return a new string which is the concatenation of this string and the
     *         specified string.
     */
    public AsciiString concat(CharSequence string) {
        int thisLen = length();
        int thatLen = string.length();
        if (thatLen == 0) {
            return this;
        }

        if (string instanceof AsciiString) {
            AsciiString that = (AsciiString) string;
            if (isEmpty()) {
                return that;
            }

            byte[] newValue = Arrays.copyOf(value, thisLen + thatLen);
            System.arraycopy(that.value, 0, newValue, thisLen, thatLen);

            return new AsciiString(newValue, false);
        }

        if (isEmpty()) {
            return new AsciiString(string);
        }

        int newLen = thisLen + thatLen;
        byte[] newValue = Arrays.copyOf(value, newLen);
        for (int i = thisLen, j = 0; i < newLen; i ++, j ++) {
            newValue[i] = c2b(string.charAt(j));
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Compares the specified string to this string to determine if the
     * specified string is a suffix.
     *
     * @param suffix
     *            the suffix to look for.
     * @return {@code true} if the specified string is a suffix of this string,
     *         {@code false} otherwise.
     * @throws NullPointerException
     *             if {@code suffix} is {@code null}.
     */
    public boolean endsWith(CharSequence suffix) {
        int suffixLen = suffix.length();
        return regionMatches(length() - suffixLen, suffix, 0, suffixLen);
    }

    /**
     * Compares the specified string to this string ignoring the case of the
     * characters and returns true if they are equal.
     *
     * @param string
     *            the string to compare.
     * @return {@code true} if the specified string is equal to this string,
     *         {@code false} otherwise.
     */
    public boolean equalsIgnoreCase(CharSequence string) {
        if (string == this) {
            return true;
        }

        if (string == null) {
            return false;
        }

        final byte[] value = this.value;
        final int thisLen = value.length;
        final int thatLen = string.length();
        if (thisLen != thatLen) {
            return false;
        }

        for (int i = 0; i < thisLen; i ++) {
            char c1 = (char) (value[i] & 0xFF);
            char c2 = string.charAt(i);
            if (c1 != c2 && toLowerCase(c1) != toLowerCase(c2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts this string to a byte array using the ASCII encoding.
     *
     * @return the byte array encoding of this string.
     */
    public byte[] toByteArray() {
        return toByteArray(0, length());
    }

    /**
     * Converts this string to a byte array using the ASCII encoding.
     *
     * @return the byte array encoding of this string.
     */
    public byte[] toByteArray(int start, int end) {
        return Arrays.copyOfRange(value, start, end);
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

        final byte[] value = this.value;
        final char[] buffer = new char[length];
        for (int i = 0, j = start; i < length; i ++, j ++) {
            buffer[i] = (char) (value[j] & 0xFF);
        }
        return buffer;
    }

    /**
     * Copies the content of this string to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     *
     * @param srcIdx
     *            the starting offset of characters to copy.
     * @param dst
     *            the destination byte array.
     * @param dstIdx
     *            the starting offset in the destination byte array.
     * @param length
     *            the number of characters to copy.
     */
    public void copy(int srcIdx, ByteBuf dst, int dstIdx, int length) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        final byte[] value = this.value;
        final int thisLen = value.length;

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length(" + length + ") <= srcLen(" + thisLen + ')');
        }

        dst.setBytes(dstIdx, value, srcIdx, length);
    }

    /**
     * Copies the content of this string to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     *
     * @param srcIdx
     *            the starting offset of characters to copy.
     * @param dst
     *            the destination byte array.
     * @param length
     *            the number of characters to copy.
     */
    public void copy(int srcIdx, ByteBuf dst, int length) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        final byte[] value = this.value;
        final int thisLen = value.length;

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length(" + length + ") <= srcLen(" + thisLen + ')');
        }

        dst.writeBytes(value, srcIdx, length);
    }

    /**
     * Copies the content of this string to a byte array.
     *
     * @param srcIdx
     *            the starting offset of characters to copy.
     * @param dst
     *            the destination byte array.
     * @param dstIdx
     *            the starting offset in the destination byte array.
     * @param length
     *            the number of characters to copy.
     */
    public void copy(int srcIdx, byte[] dst, int dstIdx, int length) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        final byte[] value = this.value;
        final int thisLen = value.length;

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length(" + length + ") <= srcLen(" + thisLen + ')');
        }

        System.arraycopy(value, srcIdx, dst, dstIdx, length);
    }

    /**
     * Copied the content of this string to a character array.
     *
     * @param srcIdx
     *            the starting offset of characters to copy.
     * @param dst
     *            the destination character array.
     * @param dstIdx
     *            the starting offset in the destination byte array.
     * @param length
     *            the number of characters to copy.
     */
    public void copy(int srcIdx, char[] dst, int dstIdx, int length) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        final byte[] value = this.value;
        final int thisLen = value.length;

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " +
                    "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length(" + length + ") <= srcLen(" + thisLen + ')');
        }

        final int dstEnd = dstIdx + length;
        for (int i = srcIdx, j = dstIdx; j < dstEnd; i ++, j ++) {
            dst[j] = (char) (value[i] & 0xFF);
        }
    }

    /**
     * Searches in this string for the first index of the specified character.
     * The search for the character starts at the beginning and moves towards
     * the end of this string.
     *
     * @param c
     *            the character to find.
     * @return the index in this string of the specified character, -1 if the
     *         character isn't found.
     */
    public int indexOf(int c) {
        return indexOf(c, 0);
    }

    /**
     * Searches in this string for the index of the specified character. The
     * search for the character starts at the specified offset and moves towards
     * the end of this string.
     *
     * @param c
     *            the character to find.
     * @param start
     *            the starting offset.
     * @return the index in this string of the specified character, -1 if the
     *         character isn't found.
     */
    public int indexOf(int c, int start) {
        final byte[] value = this.value;
        final int length = value.length;
        if (start < length) {
            if (start < 0) {
                start = 0;
            }

            for (int i = start; i < length; i ++) {
                if ((value[i] & 0xFF) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Searches in this string for the first index of the specified string. The
     * search for the string starts at the beginning and moves towards the end
     * of this string.
     *
     * @param string
     *            the string to find.
     * @return the index of the first character of the specified string in this
     *         string, -1 if the specified string is not a substring.
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
     */
    public int indexOf(CharSequence string) {
        return indexOf(string, 0);
    }

    /**
     * Searches in this string for the index of the specified string. The search
     * for the string starts at the specified offset and moves towards the end
     * of this string.
     *
     * @param subString
     *            the string to find.
     * @param start
     *            the starting offset.
     * @return the index of the first character of the specified string in this
     *         string, -1 if the specified string is not a substring.
     * @throws NullPointerException
     *             if {@code subString} is {@code null}.
     */
    public int indexOf(CharSequence subString, int start) {
        if (start < 0) {
            start = 0;
        }

        final byte[] value = this.value;
        final int thisLen = value.length;

        int subCount = subString.length();
        if (subCount <= 0) {
            return start < thisLen ? start : thisLen;
        }
        if (subCount > thisLen - start) {
            return -1;
        }

        char firstChar = subString.charAt(0);
        for (;;) {
            int i = indexOf(firstChar, start);
            if (i == -1 || subCount + i > thisLen) {
                return -1; // handles subCount > count || start >= count
            }
            int o1 = i, o2 = 0;
            while (++o2 < subCount && (value[++o1] & 0xFF) == subString.charAt(o2)) {
                // Intentionally empty
            }
            if (o2 == subCount) {
                return i;
            }
            start = i + 1;
        }
    }

    /**
     * Searches in this string for the last index of the specified character.
     * The search for the character starts at the end and moves towards the
     * beginning of this string.
     *
     * @param c
     *            the character to find.
     * @return the index in this string of the specified character, -1 if the
     *         character isn't found.
     */
    public int lastIndexOf(int c) {
        return lastIndexOf(c, length() - 1);
    }

    /**
     * Searches in this string for the index of the specified character. The
     * search for the character starts at the specified offset and moves towards
     * the beginning of this string.
     *
     * @param c
     *            the character to find.
     * @param start
     *            the starting offset.
     * @return the index in this string of the specified character, -1 if the
     *         character isn't found.
     */
    public int lastIndexOf(int c, int start) {
        if (start >= 0) {
            final byte[] value = this.value;
            final int length = value.length;
            if (start >= length) {
                start = length - 1;
            }
            for (int i = start; i >= 0; -- i) {
                if ((value[i] & 0xFF) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Searches in this string for the last index of the specified string. The
     * search for the string starts at the end and moves towards the beginning
     * of this string.
     *
     * @param string
     *            the string to find.
     * @return the index of the first character of the specified string in this
     *         string, -1 if the specified string is not a substring.
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
     */
    public int lastIndexOf(CharSequence string) {
        // Use count instead of count - 1 so lastIndexOf("") answers count
        return lastIndexOf(string, length());
    }

    /**
     * Searches in this string for the index of the specified string. The search
     * for the string starts at the specified offset and moves towards the
     * beginning of this string.
     *
     * @param subString
     *            the string to find.
     * @param start
     *            the starting offset.
     * @return the index of the first character of the specified string in this
     *         string , -1 if the specified string is not a substring.
     * @throws NullPointerException
     *             if {@code subString} is {@code null}.
     */
    public int lastIndexOf(CharSequence subString, int start) {
        final byte[] value = this.value;
        final int thisLen = value.length;
        final int subCount = subString.length();

        if (subCount > thisLen || start < 0) {
            return -1;
        }

        if (subCount <= 0) {
            return start < thisLen ? start : thisLen;
        }

        start = Math.min(start, thisLen - subCount);

        // count and subCount are both >= 1
        char firstChar = subString.charAt(0);
        for (;;) {
            int i = lastIndexOf(firstChar, start);
            if (i == -1) {
                return -1;
            }
            int o1 = i, o2 = 0;
            while (++o2 < subCount && (value[++o1] & 0xFF) == subString.charAt(o2)) {
                // Intentionally empty
            }
            if (o2 == subCount) {
                return i;
            }
            start = i - 1;
        }
    }

    /**
     * Answers if the size of this String is zero.
     *
     * @return true if the size of this String is zero, false otherwise
     */
    public boolean isEmpty() {
        return value.length == 0;
    }

    /**
     * Compares the specified string to this string and compares the specified
     * range of characters to determine if they are the same.
     *
     * @param thisStart
     *            the starting offset in this string.
     * @param string
     *            the string to compare.
     * @param start
     *            the starting offset in the specified string.
     * @param length
     *            the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false}
     *         otherwise
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
     */
    public boolean regionMatches(int thisStart, CharSequence string, int start, int length) {
        if (string == null) {
            throw new NullPointerException("string");
        }

        if (start < 0 || string.length() - start < length) {
            return false;
        }

        final byte[] value = this.value;
        final int thisLen = value.length;
        if (thisStart < 0 || thisLen - thisStart < length) {
            return false;
        }

        if (length <= 0) {
            return true;
        }

        final int thisEnd = thisStart + length;
        for (int i = thisStart, j = start; i < thisEnd; i ++, j ++) {
            if ((value[i] & 0xFF) != string.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the specified string to this string and compares the specified
     * range of characters to determine if they are the same. When ignoreCase is
     * true, the case of the characters is ignored during the comparison.
     *
     * @param ignoreCase
     *            specifies if case should be ignored.
     * @param thisStart
     *            the starting offset in this string.
     * @param string
     *            the string to compare.
     * @param start
     *            the starting offset in the specified string.
     * @param length
     *            the number of characters to compare.
     * @return {@code true} if the ranges of characters are equal, {@code false}
     *         otherwise.
     * @throws NullPointerException
     *             if {@code string} is {@code null}.
     */
    public boolean regionMatches(boolean ignoreCase, int thisStart,
                                 CharSequence string, int start, int length) {
        if (!ignoreCase) {
            return regionMatches(thisStart, string, start, length);
        }

        if (string == null) {
            throw new NullPointerException("string");
        }

        final byte[] value = this.value;
        final int thisLen = value.length;
        if (thisStart < 0 || length > thisLen - thisStart) {
            return false;
        }
        if (start < 0 || length > string.length() - start) {
            return false;
        }

        int thisEnd = thisStart + length;
        while (thisStart < thisEnd) {
            char c1 = (char) (value[thisStart++] & 0xFF);
            char c2 = string.charAt(start++);
            if (c1 != c2 && toLowerCase(c1) != toLowerCase(c2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies this string replacing occurrences of the specified character with
     * another character.
     *
     * @param oldChar
     *            the character to replace.
     * @param newChar
     *            the replacement character.
     * @return a new string with occurrences of oldChar replaced by newChar.
     */
    public AsciiString replace(char oldChar, char newChar) {
        int index = indexOf(oldChar, 0);
        if (index == -1) {
            return this;
        }

        final byte[] value = this.value;
        final int count = value.length;
        byte[] buffer = new byte[count];
        for (int i = 0, j = 0; i < value.length; i ++, j ++) {
            byte b = value[i];
            if ((char) (b & 0xFF) == oldChar) {
                b = (byte) newChar;
            }
            buffer[j] = b;
        }

        return new AsciiString(buffer, false);
    }

    /**
     * Compares the specified string to this string to determine if the
     * specified string is a prefix.
     *
     * @param prefix
     *            the string to look for.
     * @return {@code true} if the specified string is a prefix of this string,
     *         {@code false} otherwise
     * @throws NullPointerException
     *             if {@code prefix} is {@code null}.
     */
    public boolean startsWith(CharSequence prefix) {
        return startsWith(prefix, 0);
    }

    /**
     * Compares the specified string to this string, starting at the specified
     * offset, to determine if the specified string is a prefix.
     *
     * @param prefix
     *            the string to look for.
     * @param start
     *            the starting offset.
     * @return {@code true} if the specified string occurs in this string at the
     *         specified offset, {@code false} otherwise.
     * @throws NullPointerException
     *             if {@code prefix} is {@code null}.
     */
    public boolean startsWith(CharSequence prefix, int start) {
        return regionMatches(start, prefix, 0, prefix.length());
    }

    /**
     * Converts the characters in this string to lowercase, using the default
     * Locale.
     *
     * @return a new string containing the lowercase characters equivalent to
     *         the characters in this string.
     */
    public AsciiString toLowerCase() {
        boolean lowercased = true;
        final byte[] value = this.value;

        for (byte b: value) {
            if (b >= 'A' && b <= 'Z') {
                lowercased = false;
                break;
            }
        }

        // Check if this string does not contain any uppercase characters.
        if (lowercased) {
            return this;
        }

        final int length = value.length;
        final byte[] newValue = new byte[length];
        for (int i = 0, j = 0; i < length; i ++, j ++) {
            newValue[i] = toLowerCase(value[j]);
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Converts the characters in this string to uppercase, using the default
     * Locale.
     *
     * @return a new string containing the uppercase characters equivalent to
     *         the characters in this string.
     */
    public AsciiString toUpperCase() {
        final byte[] value = this.value;
        boolean uppercased = true;
        for (byte b: value) {
            if (b >= 'a' && b <= 'z') {
                uppercased = false;
                break;
            }
        }

        // Check if this string does not contain any lowercase characters.
        if (uppercased) {
            return this;
        }

        final int length = value.length;
        final byte[] newValue = new byte[length];
        for (int i = 0, j = 0; i < length; i ++, j ++) {
            newValue[i] = toUpperCase(value[j]);
        }

        return new AsciiString(newValue, false);
    }

    /**
     * Copies this string removing white space characters from the beginning and
     * end of the string.
     *
     * @return a new string with characters {@code <= \\u0020} removed from
     *         the beginning and the end.
     */
    public AsciiString trim() {
        final byte[] value = this.value;
        int start = 0, last = value.length;
        int end = last;
        while (start <= end && value[start] <= ' ') {
            start ++;
        }
        while (end >= start && value[end] <= ' ') {
            end --;
        }
        if (start == 0 && end == last) {
            return this;
        }
        return new AsciiString(value, start, end - start + 1, false);
    }

    /**
     * Compares a {@code CharSequence} to this {@code String} to determine if
     * their contents are equal.
     *
     * @param cs
     *            the character sequence to compare to.
     * @return {@code true} if equal, otherwise {@code false}
     */
    public boolean contentEquals(CharSequence cs) {
        if (cs == null) {
            throw new NullPointerException();
        }

        int length1 = length();
        int length2 = cs.length();
        if (length1 != length2) {
            return false;
        }

        if (length1 == 0 && length2 == 0) {
            return true; // since both are empty strings
        }

        return regionMatches(0, cs, 0, length2);
    }

    /**
     * Determines whether this string matches a given regular expression.
     *
     * @param expr
     *            the regular expression to be matched.
     * @return {@code true} if the expression matches, otherwise {@code false}.
     * @throws PatternSyntaxException
     *             if the syntax of the supplied regular expression is not
     *             valid.
     * @throws NullPointerException
     *             if {@code expr} is {@code null}.
     */
    public boolean matches(String expr) {
        return Pattern.matches(expr, this);
    }

    /**
     * Splits this string using the supplied regular expression {@code expr}.
     * The parameter {@code max} controls the behavior how many times the
     * pattern is applied to the string.
     *
     * @param expr
     *            the regular expression used to divide the string.
     * @param max
     *            the number of entries in the resulting array.
     * @return an array of Strings created by separating the string along
     *         matches of the regular expression.
     * @throws NullPointerException
     *             if {@code expr} is {@code null}.
     * @throws PatternSyntaxException
     *             if the syntax of the supplied regular expression is not
     *             valid.
     * @see Pattern#split(CharSequence, int)
     */
    public AsciiString[] split(String expr, int max) {
        return toAsciiStringArray(Pattern.compile(expr).split(this, max));
    }

    private static AsciiString[] toAsciiStringArray(String[] jdkResult) {
        AsciiString[] res = new AsciiString[jdkResult.length];
        for (int i = 0; i < jdkResult.length; i ++) {
            res[i] = new AsciiString(jdkResult[i]);
        }
        return res;
    }

    /**
     * Splits the specified {@link String} with the specified delimiter..
     */
    public AsciiString[] split(char delim) {
        final List<AsciiString> res = new ArrayList<AsciiString>();

        int start = 0;
        final byte[] value = this.value;
        final int length = value.length;
        for (int i = start; i < length; i ++) {
            if (charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(new AsciiString(value, start, i - start, false));
                }
                start = i + 1;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(this);
        } else {
            if (start != length) {
                // Add the last element if it's not empty.
                res.add(new AsciiString(value, start, length - start, false));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i --) {
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
     * Determines if this {@code String} contains the sequence of characters in
     * the {@code CharSequence} passed.
     *
     * @param cs
     *            the character sequence to search for.
     * @return {@code true} if the sequence of characters are contained in this
     *         string, otherwise {@code false}.
     */
    public boolean contains(CharSequence cs) {
        if (cs == null) {
            throw new NullPointerException();
        }
        return indexOf(cs) >= 0;
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
        boolean negative = charAt(i) == '-';
        if (negative && ++ i == end) {
            throw new NumberFormatException(subSequence(start, end).toString());
        }

        return parseInt(i, end, radix, negative);
    }

    private int parseInt(int start, int end, int radix, boolean negative) {
        final byte[] value = this.value;
        int max = Integer.MIN_VALUE / radix;
        int result = 0;
        int offset = start;
        while (offset < end) {
            int digit = Character.digit((char) (value[offset ++] & 0xFF), radix);
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
        boolean negative = charAt(i) == '-';
        if (negative && ++ i == end) {
            throw new NumberFormatException(subSequence(start, end).toString());
        }

        return parseLong(i, end, radix, negative);
    }

    private long parseLong(int start, int end, int radix, boolean negative) {
        final byte[] value = this.value;
        long max = Long.MIN_VALUE / radix;
        long result = 0;
        int offset = start;
        while (offset < end) {
            int digit = Character.digit((char) (value[offset ++] & 0xFF), radix);
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
            throw new NumberFormatException(subSequence(start, end).toString());
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
}
