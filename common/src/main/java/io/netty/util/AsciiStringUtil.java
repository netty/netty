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

/**
 * A collection of utility methods that is related with handling {@link AsciiString}
 */
public final class AsciiStringUtil {

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
        final long pattern = SWARByteUtil.compilePattern(value);
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            final long mask = SWARByteUtil.applyPattern(word, pattern);
            if (mask != 0) {
                return fromIndex + SWARByteUtil.getIndex(mask, PlatformDependent.BIG_ENDIAN_NATIVE_ORDER);
            }
            fromIndex += Long.BYTES;
        }
        final int byteCount = length & 7;
        return unrolledFirstIndexOf(bytes, fromIndex, byteCount, value);
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
    static boolean containsUpperCase(final byte[] bytes, int fromIndex, final int toIndex) {
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsUpperCase(bytes, fromIndex, toIndex);
        }

        final int length = toIndex - fromIndex;
        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            if (SWARByteUtil.containsUpperCase(word)) {
                return true;
            }
            fromIndex += Long.BYTES;
        }
        final int byteCount = length & 7;
        return unrolledConstainsUpperCase(bytes, fromIndex, byteCount);
    }

    private static boolean linearContainsUpperCase(final byte[] bytes, final int fromIndex, final int toIndex) {
        for (int idx = fromIndex; idx < toIndex; ++idx) {
            if (isUpperCase(bytes[idx])) {
                return true;
            }
        }
        return false;
    }

    private static boolean unrolledConstainsUpperCase(final byte[] bytes, int fromIndex, final int length) {
        if ((length & 4) != 0) {
            final int word = PlatformDependent.getInt(bytes, fromIndex);
            if (SWARByteUtil.containsUpperCase(word)) {
                return true;
            }
            fromIndex += Integer.BYTES;
        }
        if ((length & 2) != 0) {
            if (isUpperCase(PlatformDependent.getByte(bytes, fromIndex))) {
                return true;
            }
            if (isUpperCase(PlatformDependent.getByte(bytes, fromIndex + 1))) {
                return true;
            }
            fromIndex += 2;
        }
        if ((length & 1) != 0) {
            return isUpperCase(PlatformDependent.getByte(bytes, fromIndex));
        }
        return false;
    }

    /**
     * Returns true if the given byte array contains at least one lower case character. false otherwise.
     * This method utilizes SWAR technique to accelerate containsLowerCase operation.
     */
    static boolean containsLowerCase(final byte[] bytes, int fromIndex, final int toIndex) {
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsLowerCase(bytes, fromIndex, toIndex);
        }

        final int length = toIndex - fromIndex;
        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            if (SWARByteUtil.containsLowerCase(word)) {
                return true;
            }
            fromIndex += Long.BYTES;
        }
        return unrolledContainsLowerCase(bytes, fromIndex, length & 7);
    }

    private static boolean linearContainsLowerCase(final byte[] bytes, final int fromIndex, final int toIndex) {
        for (int idx = fromIndex; idx < toIndex; ++idx) {
            if (isLowerCase(bytes[idx])) {
                return true;
            }
        }
        return false;
    }

    private static boolean unrolledContainsLowerCase(final byte[] bytes, int fromIndex, final int length) {
        if ((length & 4) != 0) {
            final int word = PlatformDependent.getInt(bytes, fromIndex);
            if (SWARByteUtil.containsLowerCase(word)) {
                return true;
            }
            fromIndex += Integer.BYTES;
        }
        if ((length & 2) != 0) {
            if (isLowerCase(PlatformDependent.getByte(bytes, fromIndex))) {
                return true;
            }
            if (isLowerCase(PlatformDependent.getByte(bytes, fromIndex + 1))) {
                return true;
            }
            fromIndex += 2;
        }
        if ((length & 1) != 0) {
            return isLowerCase(PlatformDependent.getByte(bytes, fromIndex));
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
            PlatformDependent.putLong(dest, destPos, SWARByteUtil.toLowerCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }
        unrollToLowerCase(src, srcPos, dest, destPos, length & 7);
    }

    private static void linearToLowerCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        for (int i = 0; i < length; ++i) {
            dest[destPos++] = toLowerCase(src[srcPos++]);
        }
    }

    private static void unrollToLowerCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        if ((length & 4) != 0) {
            final int word = PlatformDependent.getInt(src, srcPos);
            PlatformDependent.putInt(dest, destPos, SWARByteUtil.toLowerCase(word));
            srcPos += Integer.BYTES;
            destPos += Integer.BYTES;
        }
        if ((length & 2) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toLowerCase(PlatformDependent.getByte(src, srcPos)));
            PlatformDependent.putByte(dest, destPos + 1,
                                      toLowerCase(PlatformDependent.getByte(src, srcPos + 1)));
            srcPos += 2;
            destPos += 2;
        }
        if ((length & 1) != 0) {
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
            PlatformDependent.putLong(dest, destPos, SWARByteUtil.toUpperCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }

        final int byteCount = length & 7;
        unrolltoUpperCase(src, srcPos, dest, destPos, byteCount);
    }

    private static void linearToUpperCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        for (int i = 0; i < length; ++i) {
            dest[destPos++] = toUpperCase(src[srcPos++]);
        }
    }

    private static void unrolltoUpperCase(final byte[] src, int srcPos,
                                          final byte[] dest, int destPos, final int length) {
        if ((length & 4) != 0) {
            final int word = PlatformDependent.getInt(src, srcPos);
            PlatformDependent.putInt(dest, destPos, SWARByteUtil.toUpperCase(word));
            srcPos += Integer.BYTES;
            destPos += Integer.BYTES;
        }
        if ((length & 2) != 0) {
            PlatformDependent.putByte(dest, destPos,
                                      toUpperCase(PlatformDependent.getByte(src, srcPos)));
            PlatformDependent.putByte(dest, destPos + 1,
                                      toUpperCase(PlatformDependent.getByte(src, srcPos + 1)));
            srcPos += 2;
            destPos += 2;
        }
        if ((length & 1) != 0) {
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
                if (SWARByteUtil.toLowerCase(lWord) != SWARByteUtil.toLowerCase(rWord)) {
                    return false;
                }
                lhsPos += Long.BYTES;
                rhsPos += Long.BYTES;
            }
        }
        final int byteCount = length & 7;
        return unrollEqualsIgnoreCase(lhs, lhsPos, rhs, rhsPos, byteCount);
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

    private static boolean unrollEqualsIgnoreCase(final byte[] lhs, int lhsPos,
                                                  final byte[] rhs, int rhsPos, final int length) {
        if ((length & 4) != 0) {
            final int lWord = PlatformDependent.getInt(lhs, lhsPos);
            final int rWord = PlatformDependent.getInt(rhs, rhsPos);
            if (SWARByteUtil.toLowerCase(lWord) != SWARByteUtil.toLowerCase(rWord)) {
                return false;
            }
            lhsPos += Integer.BYTES;
            rhsPos += Integer.BYTES;
        }
        if ((length & 2) != 0) {
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
        if ((length & 1) != 0) {
            return toLowerCase(PlatformDependent.getByte(lhs, lhsPos)) ==
                   toLowerCase(PlatformDependent.getByte(rhs, rhsPos));
        }
        return true;
    }

    public static final class SWARByteUtil {
        public static long compilePattern(final byte byteToFind) {
            return (byteToFind & 0xFFL) * 0x101010101010101L;
        }

        private static long applyPattern(final long word, final long pattern) {
            long input = word ^ pattern;
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
            return ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
        }

        public static int getIndex(final long mask, final boolean isBigEndian) {
            return isBigEndian? Long.numberOfLeadingZeros(mask) >>> 3 : Long.numberOfTrailingZeros(mask) >>> 3;
        }

        public static int firstAnyPattern(final long word, final long pattern, final boolean leading) {
            long input = word ^ pattern;
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
            tmp = ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
            final int binaryPosition = leading? Long.numberOfLeadingZeros(tmp) : Long.numberOfTrailingZeros(tmp);
            return binaryPosition >>> 3;
        }

        private static long applyUpperCasePattern(final long word) {
            long rotated = word & 0x7F7F7F7F7F7F7F7FL;
            rotated += 0x2525252525252525L;
            rotated &= 0x7F7F7F7F7F7F7F7FL;
            rotated += 0x1A1A1A1A1A1A1A1AL;
            rotated &= ~word;
            rotated &= 0x8080808080808080L;
            return rotated;
        }

        private static int applyUpperCasePattern(final int word) {
            int rotated = word & 0x7F7F7F7F;
            rotated += 0x25252525;
            rotated &= 0x7F7F7F7F;
            rotated += 0x1A1A1A1A;
            rotated &= ~word;
            rotated &= 0x80808080;
            return rotated;
        }

        private static long applyLowerCasePattern(final long word) {
            long rotated = word & 0x7F7F7F7F7F7F7F7FL;
            rotated += 0x0505050505050505L;
            rotated &= 0x7F7F7F7F7F7F7F7FL;
            rotated += 0x1A1A1A1A1A1A1A1AL;
            rotated &= ~word;
            rotated &= 0x8080808080808080L;
            return rotated;
        }

        private static int applyLowerCasePattern(final int word) {
            int rotated = word & 0x7F7F7F7F;
            rotated += 0x05050505;
            rotated &= 0x7F7F7F7F;
            rotated += 0x1A1A1A1A;
            rotated &= ~word;
            rotated &= 0x80808080;
            return rotated;
        }

        static boolean containsUpperCase(final long word) {
            return applyUpperCasePattern(word) != 0;
        }

        static boolean containsUpperCase(final int word) {
            return applyUpperCasePattern(word) != 0;
        }

        static boolean containsLowerCase(final long word) {
            return applyLowerCasePattern(word) != 0;
        }

        static boolean containsLowerCase(final int word) {
            return applyLowerCasePattern(word) != 0;
        }

        static long toLowerCase(final long word) {
            final long mask = applyUpperCasePattern(word) >>> 2;
            return word | mask;
        }

        static int toLowerCase(final int word) {
            final int mask = applyUpperCasePattern(word) >>> 2;
            return word | mask;
        }

        static long toUpperCase(final long word) {
            final long mask = applyLowerCasePattern(word) >>> 2;
            return word & ~mask;
        }

        static int toUpperCase(final int word) {
            final int mask = applyLowerCasePattern(word) >>> 2;
            return word & ~mask;
        }

        private SWARByteUtil() {
        }
    }

    private AsciiStringUtil() {
    }

}
