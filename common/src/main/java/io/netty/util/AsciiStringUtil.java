/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SWARUtil;

/**
 * A collection of utility methods that is related with handling {@link AsciiString}
 */
final class AsciiStringUtil {

    /**
     * Returns index of the first occurrence of the specified byte in the specified array.
     * this method utilizes SWAR technique to accelerate indexOf operation.
     */
    static int firstIndexOf(final byte[] bytes, int fromIndex, final int toIndex, final byte value) {
        if (!PlatformDependent.isUnaligned()) {
            return linearFirstIndexOf(bytes, fromIndex, toIndex, value);
        }
        final int length = toIndex - fromIndex;
        final int longCount = length >>> 3;
        final long pattern = SWARUtil.compilePattern(value);
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            final long mask = SWARUtil.applyPattern(word, pattern);
            if (mask != 0) {
                return fromIndex + SWARUtil.getIndex(mask, PlatformDependent.BIG_ENDIAN_NATIVE_ORDER);
            }
            fromIndex += Long.BYTES;
        }
        return unrolledFirstIndexOf(bytes, fromIndex, length & 7, value);
    }

    private static int linearFirstIndexOf(final byte[] bytes, final int fromIndex,
                                          final int toIndex, final byte value) {
        for (int idx = fromIndex; idx < toIndex; ++idx) {
            if (bytes[idx] == value) {
                return idx;
            }
        }
        return -1;
    }

    private static int unrolledFirstIndexOf(final byte[] bytes, final int fromIndex,
                                            final int byteCount, final byte value) {
        assert byteCount >= 0 && byteCount < 8;
        if (byteCount == 0) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex) == value) {
            return fromIndex;
        }
        if (byteCount == 1) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 1) == value) {
            return fromIndex + 1;
        }
        if (byteCount == 2) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 2) == value) {
            return fromIndex + 2;
        }
        if (byteCount == 3) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 3) == value) {
            return fromIndex + 3;
        }
        if (byteCount == 4) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 4) == value) {
            return fromIndex + 4;
        }
        if (byteCount == 5) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 5) == value) {
            return fromIndex + 5;
        }
        if (byteCount == 6) {
            return -1;
        }
        if (PlatformDependent.getByte(bytes, fromIndex + 6) == value) {
            return fromIndex + 6;
        }
        return -1;
    }

    /**
     * Returns true if given byte is ascii lower case alphabet character. false otherwise.
     */
    static boolean isLowerCase(final byte value) {
        return value >= 'a' && value <= 'z';
    }

    /**
     * Returns true if given byte is ascii upper case alphabet character. false otherwise.
     */
    static boolean isUpperCase(final byte value) {
        return value >= 'A' && value <= 'Z';
    }

    /**
     * If the character is lowercase - converts the character to uppercase,
     * otherwise returns the character as it is. Only for ASCII characters.
     */
    static byte toUpperCase(final byte value) {
        return isLowerCase(value)? (byte) (value & ~32) : value;
    }

    /**
     * If the character is uppercase - converts the character to lowercase,
     * otherwise returns the character as it is. Only for ASCII characters.
     */
    static byte toLowerCase(final byte value) {
        return isUpperCase(value)? (byte) (value | 32) : value;
    }

    /**
     * Returns true if the given byte array contains at least one upper case character. false otherwise.
     * This method utilizes SWAR technique to accelerate containsUpperCase operation.
     */
    static boolean containsUpperCase(final byte[] bytes, int index, final int length) {
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsUpperCase(bytes, index, length);
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, index);
            if (SWARUtil.containsUpperCase(word)) {
                return true;
            }
            index += Long.BYTES;
        }
        return unrolledConstainsUpperCase(bytes, index, length & 7);
    }

    private static boolean linearContainsUpperCase(final byte[] bytes, final int index, final int length) {
        final int end = index + length;
        for (int idx = index; idx < end; ++idx) {
            if (isUpperCase(bytes[idx])) {
                return true;
            }
        }
        return false;
    }

    private static boolean unrolledConstainsUpperCase(final byte[] bytes, int index, final int byteCount) {
        assert byteCount >= 0 && byteCount < 8;
        if ((byteCount & 4) != 0) {
            final int word = PlatformDependent.getInt(bytes, index);
            if (SWARUtil.containsUpperCase(word)) {
                return true;
            }
            index += Integer.BYTES;
        }
        if ((byteCount & 2) != 0) {
            if (isUpperCase(PlatformDependent.getByte(bytes, index))) {
                return true;
            }
            if (isUpperCase(PlatformDependent.getByte(bytes, index + 1))) {
                return true;
            }
            index += 2;
        }
        if ((byteCount & 1) != 0) {
            return isUpperCase(PlatformDependent.getByte(bytes, index));
        }
        return false;
    }

    /**
     * Returns true if the given byte array contains at least one lower case character. false otherwise.
     * This method utilizes SWAR technique to accelerate containsLowerCase operation.
     */
    static boolean containsLowerCase(final byte[] bytes, int index, final int length) {
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsLowerCase(bytes, index, length);
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, index);
            if (SWARUtil.containsLowerCase(word)) {
                return true;
            }
            index += Long.BYTES;
        }
        return unrolledContainsLowerCase(bytes, index, length & 7);
    }

    private static boolean linearContainsLowerCase(final byte[] bytes, final int index, final int length) {
        final int end = index + length;
        for (int idx = index; idx < end; ++idx) {
            if (isLowerCase(bytes[idx])) {
                return true;
            }
        }
        return false;
    }

    private static boolean unrolledContainsLowerCase(final byte[] bytes, int index, final int byteCount) {
        assert byteCount >= 0 && byteCount < 8;
        if ((byteCount & 4) != 0) {
            final int word = PlatformDependent.getInt(bytes, index);
            if (SWARUtil.containsLowerCase(word)) {
                return true;
            }
            index += Integer.BYTES;
        }
        if ((byteCount & 2) != 0) {
            if (isLowerCase(PlatformDependent.getByte(bytes, index))) {
                return true;
            }
            if (isLowerCase(PlatformDependent.getByte(bytes, index + 1))) {
                return true;
            }
            index += 2;
        }
        if ((byteCount & 1) != 0) {
            return isLowerCase(PlatformDependent.getByte(bytes, index));
        }
        return false;
    }

    /*
     * Copies source byte array to a destination byte array, converting the characters to their
     * corresponding lowercase. Only for ASCII characters.
     */

    static void toLowerCase(final byte[] src, int srcPos, final byte[] dest, int destPos, final int length) {
        if (!PlatformDependent.isUnaligned()) {
            linearToLowerCase(src, srcPos, dest, destPos, length);
            return;
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(src, srcPos);
            PlatformDependent.putLong(dest, destPos, SWARUtil.toLowerCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }
        unrolledToLowerCase(src, srcPos, dest, destPos, length & 7);
    }

    private static void linearToLowerCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        for (int i = 0; i < length; ++i) {
            dest[destPos++] = toLowerCase(src[srcPos++]);
        }
    }

    private static void unrolledToLowerCase(final byte[] src, int srcPos,
                                            final byte[] dest, int destPos, final int byteCount) {
        assert byteCount >= 0 && byteCount < 8;
        if ((byteCount & 4) != 0) {
            final int word = PlatformDependent.getInt(src, srcPos);
            PlatformDependent.putInt(dest, destPos, SWARUtil.toLowerCase(word));
            srcPos += Integer.BYTES;
            destPos += Integer.BYTES;
        }
        if ((byteCount & 2) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toLowerCase(PlatformDependent.getByte(src, srcPos)));
            PlatformDependent.putByte(dest, destPos + 1,
                                      toLowerCase(PlatformDependent.getByte(src, srcPos + 1)));
            srcPos += 2;
            destPos += 2;
        }
        if ((byteCount & 1) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toLowerCase(PlatformDependent.getByte(src, srcPos)));
        }
    }

    /*
     * Copies source byte array to a destination byte array, converting the characters to their
     * corresponding uppercase. Only for ASCII characters.
     */
    static void toUpperCase(final byte[] src, int srcPos, final byte[] dest, int destPos, final int length) {
        if (!PlatformDependent.isUnaligned()) {
            linearToUpperCase(src, srcPos, dest, destPos, length);
            return;
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(src, srcPos);
            PlatformDependent.putLong(dest, destPos, SWARUtil.toUpperCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }

        unrolledtoUpperCase(src, srcPos, dest, destPos, length & 7);
    }

    private static void linearToUpperCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        for (int i = 0; i < length; ++i) {
            dest[destPos++] = toUpperCase(src[srcPos++]);
        }
    }

    private static void unrolledtoUpperCase(final byte[] src, int srcPos,
                                            final byte[] dest, int destPos, final int byteCount) {
        assert byteCount >= 0 && byteCount < 8;
        if ((byteCount & 4) != 0) {
            final int word = PlatformDependent.getInt(src, srcPos);
            PlatformDependent.putInt(dest, destPos, SWARUtil.toUpperCase(word));
            srcPos += Integer.BYTES;
            destPos += Integer.BYTES;
        }
        if ((byteCount & 2) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toUpperCase(PlatformDependent.getByte(src, srcPos)));
            PlatformDependent.putByte(dest, destPos + 1,
                                      toUpperCase(PlatformDependent.getByte(src, srcPos + 1)));
            srcPos += 2;
            destPos += 2;
        }
        if ((byteCount & 1) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toUpperCase(PlatformDependent.getByte(src, srcPos)));
        }
    }

    /**
     * Compares a portion of two source byte arrays for equality while ignoring case. This method compares the
     * characters in a case-insensitive manner and returns true if the specified portions of the arrays are equal,
     * false otherwise.
     */
    static boolean equalsIgnoreCases(final byte[] lhs, int lhsPos, final byte[] rhs, int rhsPos, final int length) {
        if (lhs == rhs && lhsPos == rhsPos && lhs.length >= lhsPos + length) {
            return true;
        }

        if (!PlatformDependent.isUnaligned()) {
            return linearEqualsIgnoreCase(lhs, lhsPos, rhs, rhsPos, length);
        }

        final int longCount = length >>> 3;
        if (longCount > 0) {
            for (int i = 0; i < longCount; ++i) {
                final long lWord = PlatformDependent.getLong(lhs, lhsPos);
                final long rWord = PlatformDependent.getLong(rhs, rhsPos);
                if (SWARUtil.toLowerCase(lWord) != SWARUtil.toLowerCase(rWord)) {
                    return false;
                }
                lhsPos += Long.BYTES;
                rhsPos += Long.BYTES;
            }
        }
        return unrolledEqualsIgnoreCase(lhs, lhsPos, rhs, rhsPos, length & 7);
    }

    private static boolean linearEqualsIgnoreCase(final byte[] lhs, int lhsPos,
                                                  final byte[] rhs, int rhsPos, final int length) {
        for (int i = 0; i < length; ++i) {
            if (toLowerCase(lhs[lhsPos + i]) != toLowerCase(rhs[rhsPos + i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean unrolledEqualsIgnoreCase(final byte[] lhs, int lhsPos,
                                                    final byte[] rhs, int rhsPos, final int byteCount) {
        assert byteCount >= 0 && byteCount < 8;
        if ((byteCount & 4) != 0) {
            final int lWord = PlatformDependent.getInt(lhs, lhsPos);
            final int rWord = PlatformDependent.getInt(rhs, rhsPos);
            if (SWARUtil.toLowerCase(lWord) != SWARUtil.toLowerCase(rWord)) {
                return false;
            }
            lhsPos += Integer.BYTES;
            rhsPos += Integer.BYTES;
        }
        if ((byteCount & 2) != 0) {
            if (toLowerCase(PlatformDependent.getByte(lhs, lhsPos)) !=
                toLowerCase(PlatformDependent.getByte(rhs, rhsPos))) {
                return false;
            }
            if (toLowerCase(PlatformDependent.getByte(lhs, lhsPos + 1)) !=
                toLowerCase(PlatformDependent.getByte(rhs, rhsPos + 1))) {
                return false;
            }
            lhsPos += 2;
            rhsPos += 2;
        }
        if ((byteCount & 1) != 0) {
            return toLowerCase(PlatformDependent.getByte(lhs, lhsPos)) ==
                   toLowerCase(PlatformDependent.getByte(rhs, rhsPos));
        }
        return true;
    }

    private AsciiStringUtil() {
    }

}
