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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.StringUtil.UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET;
import io.netty.util.ByteProcessor.IndexOfProcessor;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A string which has been encoded into a character encoding whose character always takes a single byte, similarly to
 * ASCII. It internally keeps its content in a byte array unlike {@link String}, which uses a character array, for
 * reduced memory footprint and faster data transfer from/to byte-based data structures such as a byte array and
 * {@link ByteBuffer}. It is often used in conjunction with {@link TextHeaders}.
 */
public final class AsciiString extends ByteString implements CharSequence, Comparable<CharSequence> {

    private static final char MAX_CHAR_VALUE = 255;
    public static final AsciiString EMPTY_STRING = new AsciiString("");

    public static final Comparator<AsciiString> CASE_INSENSITIVE_ORDER = new Comparator<AsciiString>() {
        @Override
        public int compare(AsciiString o1, AsciiString o2) {
            return CHARSEQUENCE_CASE_INSENSITIVE_ORDER.compare(o1, o2);
        }
    };
    public static final Comparator<AsciiString> CASE_SENSITIVE_ORDER = new Comparator<AsciiString>() {
        @Override
        public int compare(AsciiString o1, AsciiString o2) {
            return CHARSEQUENCE_CASE_SENSITIVE_ORDER.compare(o1, o2);
        }
    };

    public static final Comparator<CharSequence> CHARSEQUENCE_CASE_INSENSITIVE_ORDER = new Comparator<CharSequence>() {

        @Override
        public int compare(CharSequence o1, CharSequence o2) {
            int len1 = o1.length();
            int delta = len1 - o2.length();
            if (delta != 0) {
                return delta;
            }
            if (o1.getClass().equals(AsciiString.class) && o2.getClass().equals(AsciiString.class)) {
                AsciiString a1 = (AsciiString) o1;
                AsciiString a2 = (AsciiString) o2;
                final int a1Len = a1.length() + a1.arrayOffset();
                for (int i = a1.arrayOffset(), j = a2.arrayOffset(); i < a1Len; i++, j++) {
                    byte c1 = a1.value[i];
                    byte c2 = a2.value[j];
                    if (c1 != c2) {
                        if (c1 >= 'A' && c1 <= 'Z') {
                            c1 += 32;
                        }
                        if (c2 >= 'A' && c2 <= 'Z') {
                            c2 += 32;
                        }
                        delta = c1 - c2;
                        if (delta != 0) {
                            return delta;
                        }
                    }
                }
            } else {
                for (int i = len1 - 1; i >= 0; i --) {
                    char c1 = o1.charAt(i);
                    char c2 = o2.charAt(i);
                    if (c1 != c2) {
                        if (c1 >= 'A' && c1 <= 'Z') {
                            c1 += 32;
                        }
                        if (c2 >= 'A' && c2 <= 'Z') {
                            c2 += 32;
                        }
                        delta = c1 - c2;
                        if (delta != 0) {
                            return delta;
                        }
                    }
                }
            }
            return 0;
        }
    };

    public static final Comparator<CharSequence> CHARSEQUENCE_CASE_SENSITIVE_ORDER = new Comparator<CharSequence>() {
        @Override
        public int compare(CharSequence o1, CharSequence o2) {
            if (o1 == o2) {
                return 0;
            }

            AsciiString a1 = o1 instanceof AsciiString ? (AsciiString) o1 : null;
            AsciiString a2 = o2 instanceof AsciiString ? (AsciiString) o2 : null;

            int result;
            int length1 = o1.length();
            int length2 = o2.length();
            int minLength = Math.min(length1, length2);
            if (a1 != null && a2 != null) {
                final int a1Len = minLength + a1.arrayOffset();
                for (int i = a1.arrayOffset(), j = a2.arrayOffset(); i < a1Len; i++, j++) {
                    byte v1 = a1.value[i];
                    byte v2 = a2.value[j];
                    result = v1 - v2;
                    if (result != 0) {
                        return result;
                    }
                }
            } else if (a1 != null) {
                for (int i = a1.arrayOffset(), j = 0; j < minLength; i++, j++) {
                    int c1 = a1.value[i];
                    int c2 = o2.charAt(j);
                    result = c1 - c2;
                    if (result != 0) {
                        return result;
                    }
                }
            } else if (a2 != null) {
                for (int i = 0, j = a2.arrayOffset(); i < minLength; i++, j++) {
                    int c1 = o1.charAt(i);
                    int c2 = a2.value[j];
                    result = c1 - c2;
                    if (result != 0) {
                        return result;
                    }
                }
            } else {
                for (int i = 0; i < minLength; i++) {
                    int c1 = o1.charAt(i);
                    int c2 = o2.charAt(i);
                    result = c1 - c2;
                    if (result != 0) {
                        return result;
                    }
                }
            }

            return length1 - length2;
        }
    };

    /**
     * Factory which uses the {@link #AsciiString(byte[], int, int, boolean)} constructor.
     */
    private static final ByteStringFactory DEFAULT_FACTORY = new ByteStringFactory() {
        @Override
        public ByteString newInstance(byte[] value, int start, int length, boolean copy) {
            return new AsciiString(value, start, length, copy);
        }
    };

    /**
     * Returns the case-insensitive hash code of the specified string. Note that this method uses the same hashing
     * algorithm with {@link #hashCode()} so that you can put both {@link AsciiString}s and arbitrary
     * {@link CharSequence}s into the same {@link TextHeaders}.
     */
    public static int caseInsensitiveHashCode(CharSequence value) {
        if (value instanceof AsciiString) {
            try {
                ByteProcessor processor = new ByteProcessor() {
                    private int hash;
                    @Override
                    public boolean process(byte value) throws Exception {
                        hash = hash * HASH_CODE_PRIME ^ toLowerCase(value) & HASH_CODE_PRIME;
                        return true;
                    }

                    @Override
                    public int hashCode() {
                        return hash;
                    }
                };
                ((AsciiString) value).forEachByte(processor);
                return processor.hashCode();
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        }

        int hash = 0;
        final int end = value.length();
        for (int i = 0; i < end; i++) {
            hash = hash * HASH_CODE_PRIME ^ toLowerCase(value.charAt(i)) & HASH_CODE_PRIME;
        }
        return hash;
    }

    /**
     * Returns {@code true} if both {@link CharSequence}'s are equals when ignore the case. This only supports 8-bit
     * ASCII.
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }

        if (a instanceof AsciiString) {
            AsciiString aa = (AsciiString) a;
            return aa.equalsIgnoreCase(b);
        }

        if (b instanceof AsciiString) {
            AsciiString ab = (AsciiString) b;
            return ab.equalsIgnoreCase(a);
        }

        if (a == null || b == null) {
            return false;
        }

        return a.toString().equalsIgnoreCase(b.toString());
    }

    /**
     * Returns {@code true} if both {@link CharSequence}'s are equals. This only supports 8-bit ASCII.
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }

        if (a instanceof AsciiString) {
            AsciiString aa = (AsciiString) a;
            return aa.equals(b);
        }

        if (b instanceof AsciiString) {
            AsciiString ab = (AsciiString) b;
            return ab.equals(a);
        }

        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    private String string;

    /**
     * Returns an {@link AsciiString} containing the given character sequence. If the given string is already a
     * {@link AsciiString}, just returns the same instance.
     */
    public static AsciiString of(CharSequence string) {
        return string instanceof AsciiString ? (AsciiString) string : new AsciiString(string);
    }

    public AsciiString(byte[] value) {
        super(value);
    }

    public AsciiString(byte[] value, boolean copy) {
        super(value, copy);
    }

    public AsciiString(byte[] value, int start, int length, boolean copy) {
        super(value, start, length, copy);
    }

    public AsciiString(ByteString value, boolean copy) {
        super(value, copy);
    }

    public AsciiString(ByteBuffer value) {
        super(value);
    }

    public AsciiString(ByteBuffer value, int start, int length, boolean copy) {
        super(value, start, length, copy);
    }

    public AsciiString(char[] value) {
        this(checkNotNull(value, "value"), 0, value.length);
    }

    public AsciiString(char[] value, int start, int length) {
        super(length);
        if (start < 0 || start > checkNotNull(value, "value").length - length) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.length(" + value.length + ')');
        }

        for (int i = 0, j = start; i < length; i++, j++) {
            this.value[i] = c2b(value[j]);
        }
    }

    public AsciiString(CharSequence value) {
        this(checkNotNull(value, "value"), 0, value.length());
    }

    public AsciiString(CharSequence value, int start, int length) {
        super(length);
        if (start < 0 || length < 0 || length > checkNotNull(value, "value").length() - start) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                            + ") <= " + "value.length(" + value.length() + ')');
        }

        for (int i = 0, j = start; i < length; i++, j++) {
            this.value[i] = c2b(value.charAt(j));
        }
    }

    @Override
    public char charAt(int index) {
        return b2c(byteAt(index));
    }

    @Override
    public void arrayChanged() {
        string = null;
        super.arrayChanged();
    }

    private static byte c2b(char c) {
        if (c > MAX_CHAR_VALUE) {
            return '?';
        }
        return (byte) c;
    }

    private static char b2c(byte b) {
        return (char) (b & 0xFF);
    }

    private static byte toLowerCase(byte b) {
        if ('A' <= b && b <= 'Z') {
            return (byte) (b + UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET);
        }
        return b;
    }

    private static char toLowerCase(char c) {
        if ('A' <= c && c <= 'Z') {
            return (char) (c + UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET);
        }
        return c;
    }

    private static byte toUpperCase(byte b) {
        if ('a' <= b && b <= 'z') {
            return (byte) (b - UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET);
        }
        return b;
    }

    @Override
    public String toString(Charset charset, int start, int end) {
        if (start == 0 && end == length()) {
            if (string == null) {
                string = super.toString(charset, start, end);
            }
            return string;
        }

        return super.toString(charset, start, end);
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
     * Compares the specified string to this string using the ASCII values of the characters, ignoring case differences.
     * Returns 0 if the strings contain the same characters in the same order. Returns a negative integer if the first
     * non-equal character in this string has an ASCII value which is less than the ASCII value of the character at the
     * same position in the specified string, or if this string is a prefix of the specified string. Returns a positive
     * integer if the first non-equal character in this string has an ASCII value which is greater than the ASCII value
     * of the character at the same position in the specified string, or if the specified string is a prefix of this
     * string.
     *
     * @param string the string to compare.
     * @return 0 if the strings are equal, a negative integer if this string is before the specified string, or a
     *         positive integer if this string is after the specified string.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public int compareToIgnoreCase(CharSequence string) {
        return CHARSEQUENCE_CASE_INSENSITIVE_ORDER.compare(this, string);
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

        if (string instanceof AsciiString) {
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
    public boolean equalsIgnoreCase(CharSequence string) {
        if (string == this) {
            return true;
        }

        if (string == null) {
            return false;
        }

        final int thisLen = value.length;
        final int thatLen = string.length();
        if (thisLen != thatLen) {
            return false;
        }

        for (int i = 0, j = arrayOffset(); i < thisLen; i++, j++) {
            char c1 = b2c(value[j]);
            char c2 = string.charAt(i);
            if (c1 != c2 && toLowerCase(c1) != toLowerCase(c2)) {
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

        if (start < 0 || length > length() - start) {
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

        if (srcIdx < 0 || length > length() - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + length() + ')');
        }

        final int dstEnd = dstIdx + length;
        for (int i = dstIdx, j = srcIdx + arrayOffset(); i < dstEnd; i++, j++) {
            dst[i] = b2c(value[j]);
        }
    }

    @Override
    public AsciiString subSequence(int start) {
        return subSequence(start, length());
    }

    @Override
    public AsciiString subSequence(int start, int end) {
       return subSequence(start, end, true);
    }

    @Override
    public AsciiString subSequence(int start, int end, boolean copy) {
        return (AsciiString) super.subSequence(start, end, copy, DEFAULT_FACTORY);
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
        if (start < 0) {
            start = 0;
        }

        final int thisLen = length();

        int subCount = subString.length();
        if (subCount <= 0) {
            return start < thisLen ? start : thisLen;
        }
        if (subCount > thisLen - start) {
            return -1;
        }

        final char firstChar = subString.charAt(0);
        if (firstChar > MAX_CHAR_VALUE) {
            return -1;
        }
        ByteProcessor IndexOfVisitor = new IndexOfProcessor((byte) firstChar);
        try {
            for (;;) {
                int i = forEachByte(start, thisLen - start, IndexOfVisitor);
                if (i == -1 || subCount + i > thisLen) {
                    return -1; // handles subCount > count || start >= count
                }
                int o1 = i, o2 = 0;
                while (++o2 < subCount && b2c(value[++o1 + arrayOffset()]) == subString.charAt(o2)) {
                    // Intentionally empty
                }
                if (o2 == subCount) {
                    return i;
                }
                start = i + 1;
            }
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
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
        final int thisLen = length();
        final int subCount = subString.length();

        if (subCount > thisLen || start < 0) {
            return -1;
        }

        if (subCount <= 0) {
            return start < thisLen ? start : thisLen;
        }

        start = Math.min(start, thisLen - subCount);

        // count and subCount are both >= 1
        final char firstChar = subString.charAt(0);
        if (firstChar > MAX_CHAR_VALUE) {
            return -1;
        }
        ByteProcessor IndexOfVisitor = new IndexOfProcessor((byte) firstChar);
        try {
            for (;;) {
                int i = forEachByteDesc(start, thisLen - start, IndexOfVisitor);
                if (i == -1) {
                    return -1;
                }
                int o1 = i, o2 = 0;
                while (++o2 < subCount && b2c(value[++o1 + arrayOffset()]) == subString.charAt(o2)) {
                    // Intentionally empty
                }
                if (o2 == subCount) {
                    return i;
                }
                start = i - 1;
            }
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return -1;
        }
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
            char c1 = b2c(value[thisStart++]);
            char c2 = string.charAt(start++);
            if (c1 != c2 && toLowerCase(c1) != toLowerCase(c2)) {
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

        final int index;
        final byte oldCharByte = c2b(oldChar);
        try {
            index = forEachByte(new IndexOfProcessor(oldCharByte));
        } catch (Exception e) {
            PlatformDependent.throwException(e);
            return this;
        }
        if (index == -1) {
            return this;
        }

        final byte newCharByte = c2b(newChar);
        byte[] buffer = new byte[length()];
        for (int i = 0, j = arrayOffset(); i < buffer.length; i++, j++) {
            byte b = value[j];
            if (b == oldCharByte) {
                b = newCharByte;
            }
            buffer[i] = b;
        }

        return new AsciiString(buffer, false);
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
     * Copies this string removing white space characters from the beginning and end of the string.
     *
     * @return a new string with characters {@code <= \\u0020} removed from the beginning and the end.
     */
    public AsciiString trim() {
        int start = arrayOffset(), last = arrayOffset() + length();
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
     * @param cs the character sequence to compare to.
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
        } else if (length1 == 0) {
            return true; // since both are empty strings
        }

        return regionMatches(0, cs, 0, length2);
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

    private static AsciiString[] toAsciiStringArray(String[] jdkResult) {
        AsciiString[] res = new AsciiString[jdkResult.length];
        for (int i = 0; i < jdkResult.length; i++) {
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
     * Determines if this {@code String} contains the sequence of characters in the {@code CharSequence} passed.
     *
     * @param cs the character sequence to search for.
     * @return {@code true} if the sequence of characters are contained in this string, otherwise {@code false}.
     */
    public boolean contains(CharSequence cs) {
        if (cs == null) {
            throw new NullPointerException();
        }
        return indexOf(cs) >= 0;
    }
}
