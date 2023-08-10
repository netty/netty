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

    static int firstIndexOf(byte[] bytes, int fromIndex, int toIndex, byte value) {
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

    private static int linearFirstIndexOf(byte[] bytes, int fromIndex, int toIndex, byte value) {
        for (int idx = fromIndex; idx < toIndex; ++idx) {
            if (bytes[idx] == value) {
                return idx;
            }
        }
        return -1;
    }

    private static int unrolledFirstIndexOf(byte[] bytes, int fromIndex, int byteCount, byte value) {
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

    static boolean isLowerCase(byte value) {
        return value >= 'a' && value <= 'z';
    }

    static boolean isUpperCase(byte value) {
        return value >= 'A' && value <= 'Z';
    }

    private static long toLowerCase(long word) {
        long mask = SWARByteUtil.applyPatternRange(word, SWARByteUtil.UPPER_CASE_PATTERN,
                                                   SWARByteUtil.UPPER_CASE_RANGE_PATTERN);
        return word | mask >>> 2;
    }

    private static long toUpperCase(long word) {
        long mask = SWARByteUtil.applyPatternRange(word, SWARByteUtil.LOWER_CASE_PATTERN,
                                                   SWARByteUtil.LOWER_CASE_RANGE_PATTERN);
        return word & ~(mask >>> 2);
    }

    static byte toUpperCase(byte value) {
        return isLowerCase(value)? (byte) (value & ~32) : value;
    }

    static byte toLowerCase(byte value) {
        return isUpperCase(value)? (byte) (value | 32) : value;
    }

    static boolean containsUpperCase(byte[] bytes, int fromIndex, int toIndex) {
        if (!PlatformDependent.isUnaligned()) {
            for (int idx = fromIndex; idx < toIndex; ++idx) {
                if (isUpperCase(bytes[idx])) {
                    return true;
                }
            }
            return false;
        }

        final int length = toIndex - fromIndex;
        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            final long mask = SWARByteUtil.applyPatternRange(word, SWARByteUtil.UPPER_CASE_PATTERN,
                                                             SWARByteUtil.UPPER_CASE_RANGE_PATTERN);
            if (mask != 0) {
                return true;
            }
            fromIndex += Long.BYTES;
        }

        for (; fromIndex < toIndex; ++fromIndex) {
            byte value = bytes[fromIndex];
            if (isUpperCase(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsLowerCase(byte[] bytes, int fromIndex, int toIndex) {
        if (!PlatformDependent.isUnaligned()) {
            for (int idx = fromIndex; idx < toIndex; ++idx) {
                if (isLowerCase(bytes[idx])) {
                    return true;
                }
            }
            return false;
        }

        final int length = toIndex - fromIndex;
        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            final long mask = SWARByteUtil.applyPatternRange(word, SWARByteUtil.LOWER_CASE_PATTERN,
                                                             SWARByteUtil.LOWER_CASE_RANGE_PATTERN);
            if (mask != 0) {
                return true;
            }
            fromIndex += Long.BYTES;
        }

        for (; fromIndex < toIndex; ++fromIndex) {
            if (isLowerCase(bytes[fromIndex])) {
                return true;
            }
        }
        return false;
    }

    static void toLowerCase(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (!PlatformDependent.isUnaligned()) {
            for (int i = 0; i < length; ++i) {
                dest[destPos++] = toLowerCase(src[srcPos++]);
            }
            return;
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(src, srcPos);
            PlatformDependent.putLong(dest, destPos, toLowerCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }

        final int byteCount = length & 7;
        for (int i = 0; i < byteCount; ++i) {
            dest[destPos++] = toLowerCase(src[srcPos++]);
        }
    }

    static void toUpperCase(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (!PlatformDependent.isUnaligned()) {
            for (int i = 0; i < length; ++i) {
                dest[destPos++] = toUpperCase(src[srcPos++]);
            }
            return;
        }

        final int longCount = length >>> 3;
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(src, srcPos);
            PlatformDependent.putLong(dest, destPos, toUpperCase(word));
            srcPos += Long.BYTES;
            destPos += Long.BYTES;
        }

        final int byteCount = length & 7;
        for (int i = 0; i < byteCount; ++i) {
            dest[destPos++] = toUpperCase(src[srcPos++]);
        }
    }

    static boolean equalsIgnoreCases(byte[] lhs, int lhsPos, byte[] rhs, int rhsPos, int length) {
        if (lhs == rhs && lhsPos == rhsPos && lhs.length >= lhsPos + length) {
            return true;
        }

        int longCount = length >>> 3;
        if (longCount > 0) {
            for (int i = 0; i < longCount; ++i) {
                final long lWord = PlatformDependent.getLong(lhs, lhsPos);
                final long rWord = PlatformDependent.getLong(rhs, rhsPos);
                if (toLowerCase(lWord) != toLowerCase(rWord)) {
                    return false;
                }
                lhsPos += Long.BYTES;
                rhsPos += Long.BYTES;
            }
        }
        int byteCount = length & 7;
        if (byteCount > 0) {
            for (int i = 0; i < byteCount; ++i) {
                if (toLowerCase(lhs[lhsPos++]) != toLowerCase(rhs[rhsPos++])) {
                    return false;
                }
            }
        }
        return true;
    }

    public static final class SWARByteUtil {
        static final long UPPER_CASE_PATTERN = compilePattern((byte) 'A');
        static final long UPPER_CASE_RANGE_PATTERN = compileRangePattern((byte) 'A', (byte) 'Z');

        static final long LOWER_CASE_PATTERN = compilePattern((byte) 'a');
        static final long LOWER_CASE_RANGE_PATTERN = compileRangePattern((byte) 'a', (byte) 'z');

        public static long compilePattern(byte byteToFind) {
            return (byteToFind & 0xFFL) * 0x101010101010101L;
        }

        private static long compileRangePattern(byte low, byte high) {
            assert low <= high && high - low <= 128;
            return (0x7F7F - high + low & 0xFFL) * 0x101010101010101L;
        }

        private static long applyPatternRange(long word, long lowPattern, long rangePattern) {
            long input = (word | 0x8080808080808080L) - lowPattern;
            input = ~((word | 0x7F7F7F7F7F7F7F7FL) ^ input);
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + rangePattern;
            return ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
        }

        private static long applyPattern(long word, long pattern) {
            long input = word ^ pattern;
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
            return ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
        }

        public static int getIndex(long mask, boolean isBigEndian) {
            return isBigEndian? Long.numberOfLeadingZeros(mask) >>> 3 : Long.numberOfTrailingZeros(mask) >>> 3;
        }

        public static int firstAnyPattern(long word, long pattern, boolean leading) {
            long input = word ^ pattern;
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
            tmp = ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
            final int binaryPosition = leading? Long.numberOfLeadingZeros(tmp) : Long.numberOfTrailingZeros(tmp);
            return binaryPosition >>> 3;
        }

        public static int firstAnyPatternInt(int word, int pattern, boolean leading) {
            int input = word ^ pattern;
            int tmp = (input & 0x7F7F7F7F) + 0x7F7F7F7F;
            tmp = ~(tmp | input | 0x7F7F7F7F);
            final int binaryPosition = leading? Integer.numberOfLeadingZeros(tmp) : Integer.numberOfTrailingZeros(tmp);
            return binaryPosition >>> 3;
        }

        private SWARByteUtil() {
        }

    }

    private AsciiStringUtil() {
    }

}
