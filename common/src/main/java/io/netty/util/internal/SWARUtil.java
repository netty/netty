/*
 * Copyright 2024 The Netty Project
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
package io.netty.util.internal;


/**
 * Utility class for SWAR (SIMD within a register) operations.
 */
public final class SWARUtil {

    /**
     * Compiles given byte into a long pattern suitable for SWAR operations.
     */
    public static long compilePattern(byte byteToFind) {
        return (byteToFind & 0xFFL) * 0x101010101010101L;
    }

    /**
     * Applies a compiled pattern to given word.
     * Returns a word where each byte that matches the pattern has the highest bit set.
     *
     * @param word    the word to apply the pattern to
     * @param pattern the pattern to apply
     * @return a word where each byte that matches the pattern has the highest bit set
     */
    public static long applyPattern(final long word, final long pattern) {
        long input = word ^ pattern;
        long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
        return ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
    }

    /**
     * Returns the index of the first occurrence of byte that specificied in the pattern.
     * If no pattern is found, returns 8.
     *
     * @param word     the return value of {@link #applyPattern(long, long)}
     * @param isBigEndian if true, if given word is big endian
     *                 if false, if given word is little endian
     * @return the index of the first occurrence of the specified pattern in the specified word.
     * If no pattern is found, returns 8.
     */
    public static int getIndex(final long word, final boolean isBigEndian) {
        final int zeros = isBigEndian? Long.numberOfLeadingZeros(word) : Long.numberOfTrailingZeros(word);
        return zeros >>> 3;
    }

    /**
     * Returns a word where each ASCII uppercase byte has the highest bit set.
     */
    private static long applyUpperCasePattern(final long word) {
        // Inspired by https://github.com/facebook/folly/blob/add4049dd6c2371eac05b92b6fd120fd6dd74df5/folly/String.cpp
        long rotated = word & 0x7F7F7F7F7F7F7F7FL;
        rotated += 0x2525252525252525L;
        rotated &= 0x7F7F7F7F7F7F7F7FL;
        rotated += 0x1A1A1A1A1A1A1A1AL;
        rotated &= ~word;
        rotated &= 0x8080808080808080L;
        return rotated;
    }

    /**
     * Returns a word where each ASCII uppercase byte has the highest bit set.
     */
    private static int applyUpperCasePattern(final int word) {
        int rotated = word & 0x7F7F7F7F;
        rotated += 0x25252525;
        rotated &= 0x7F7F7F7F;
        rotated += 0x1A1A1A1A;
        rotated &= ~word;
        rotated &= 0x80808080;
        return rotated;
    }

    /**
     * Returns a word where each ASCII lowercase byte has the highest bit set.
     */
    private static long applyLowerCasePattern(final long word) {
        long rotated = word & 0x7F7F7F7F7F7F7F7FL;
        rotated += 0x0505050505050505L;
        rotated &= 0x7F7F7F7F7F7F7F7FL;
        rotated += 0x1A1A1A1A1A1A1A1AL;
        rotated &= ~word;
        rotated &= 0x8080808080808080L;
        return rotated;
    }

    /**
     * Returns a word where each lowercase ASCII byte has the highest bit set.
     */
    private static int applyLowerCasePattern(final int word) {
        int rotated = word & 0x7F7F7F7F;
        rotated += 0x05050505;
        rotated &= 0x7F7F7F7F;
        rotated += 0x1A1A1A1A;
        rotated &= ~word;
        rotated &= 0x80808080;
        return rotated;
    }

    /**
     * Returns true if the given word contains at least one ASCII uppercase byte.
     */
    public static boolean containsUpperCase(final long word) {
        return applyUpperCasePattern(word) != 0;
    }

    /**
     * Returns true if the given word contains at least one ASCII uppercase byte.
     */
    public static boolean containsUpperCase(final int word) {
        return applyUpperCasePattern(word) != 0;
    }

    /**
     * Returns true if the given word contains at least one ASCII lowercase byte.
     */
    public static boolean containsLowerCase(final long word) {
        return applyLowerCasePattern(word) != 0;
    }

    /**
     * Returns true if the given word contains at least one ASCII lowercase byte.
     */
    public static boolean containsLowerCase(final int word) {
        return applyLowerCasePattern(word) != 0;
    }

    /**
     * Returns a word with all bytes converted to lowercase ASCII.
     */
    public static long toLowerCase(final long word) {
        final long mask = applyUpperCasePattern(word) >>> 2;
        return word | mask;
    }

    /**
     * Returns a word with all bytes converted to lowercase ASCII.
     */
    public static int toLowerCase(final int word) {
        final int mask = applyUpperCasePattern(word) >>> 2;
        return word | mask;
    }

    /**
     * Returns a word with all bytes converted to uppercase ASCII.
     */
    public static long toUpperCase(final long word) {
        final long mask = applyLowerCasePattern(word) >>> 2;
        return word & ~mask;
    }

    /**
     * Returns a word with all bytes converted to uppercase ASCII.
     */
    public static int toUpperCase(final int word) {
        final int mask = applyLowerCasePattern(word) >>> 2;
        return word & ~mask;
    }

    private SWARUtil() {
        // Utility
    }

}
