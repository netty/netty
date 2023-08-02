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

    public static int firstIndexOf(byte[] bytes, int fromIndex, int toIndex, byte value) {
        if (!PlatformDependent.isUnaligned()) {
            for (int idx = fromIndex; idx < toIndex; ++idx) {
                if (bytes[idx] == value) {
                    return idx;
                }
            }
            return -1;
        }

        final int length = toIndex - fromIndex;
        final int byteCount = length & 7;
        if (byteCount > 0) {
            final int index = unrolledFirstIndexOf(bytes, fromIndex, byteCount, value);
            if (index >= 0) {
                return index;
            }
            fromIndex += byteCount;
            if (fromIndex == toIndex) {
                return -1;
            }
        }

        final int longCount = length >>> 3;
        final boolean isNative = PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;
        final long pattern = SWARByteSearch.compilePattern(value);
        for (int i = 0; i < longCount; ++i) {
            final long word = PlatformDependent.getLong(bytes, fromIndex);
            final int mask = SWARByteSearch.firstAnyPattern(word, pattern, isNative);
            if (mask < Long.BYTES) {
                return fromIndex + mask;
            }
            fromIndex += Long.BYTES;
        }
        return -1;
    }

    public static final class SWARByteSearch {
        public static long compilePattern(byte byteToFind) {
            return (byteToFind & 0xFFL) * 0x101010101010101L;
        }

        public static int firstAnyPattern(long word, long pattern, boolean leading) {
            long input = word ^ pattern;
            long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
            tmp = ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
            final int binaryPosition = leading? Long.numberOfLeadingZeros(tmp) : Long.numberOfTrailingZeros(tmp);
            return binaryPosition >>> 3;
        }

        private SWARByteSearch() {
        }
    }

    private static int unrolledFirstIndexOf(byte[] bytes, int fromIndex, int length, byte target) {
        assert length > 0 && length < 8;
        if (bytes[fromIndex] == target) {
            return fromIndex;
        }
        if (length == 1) {
            return -1;
        }
        if (bytes[fromIndex + 1] == target) {
            return fromIndex + 1;
        }
        if (length == 2) {
            return -1;
        }
        if (bytes[fromIndex + 2] == target) {
            return fromIndex + 2;
        }
        if (length == 3) {
            return -1;
        }
        if (bytes[fromIndex + 3] == target) {
            return fromIndex + 3;
        }
        if (length == 4) {
            return -1;
        }
        if (bytes[fromIndex + 4] == target) {
            return fromIndex + 4;
        }
        if (length == 5) {
            return -1;
        }
        if (bytes[fromIndex + 5] == target) {
            return fromIndex + 5;
        }
        if (length == 6) {
            return -1;
        }
        if (bytes[fromIndex + 6] == target) {
            return fromIndex + 6;
        }
        return -1;
    }

    private AsciiStringUtil() {
    }

}
