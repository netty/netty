/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.security.core;

import io.netty.buffer.ByteBuf;

/**
 * Backward port of Netty5's InternalBufferUtils
 */
public final class InternalBufferUtils {

    private InternalBufferUtils() {
        // Prevent outside initialization
    }

    public interface UncheckedLoadByte {
        byte load(ByteBuf buffer, int offset);
    }

    public static final UncheckedLoadByte UNCHECKED_LOAD_BYTE_BUFFER = new UncheckedLoadByte() {
        @Override
        public byte load(ByteBuf buffer, int offset) {
            return buffer.getByte(offset);
        }
    };

    private static boolean equalsInner(ByteBuf a, int aStartIndex, ByteBuf b, int bStartIndex, int length) {
        final int longCount = length >>> 3;
        final int byteCount = length & 7;

        for (int i = longCount; i > 0; i--) {
            if (a.getLong(aStartIndex) != b.getLong(bStartIndex)) {
                return false;
            }
            aStartIndex += 8;
            bStartIndex += 8;
        }

        for (int i = byteCount; i > 0; i--) {
            if (a.getByte(aStartIndex) != b.getByte(bStartIndex)) {
                return false;
            }
            aStartIndex++;
            bStartIndex++;
        }

        return true;
    }

    public static int bytesBefore(ByteBuf haystack, UncheckedLoadByte hl, ByteBuf needle, UncheckedLoadByte nl) {
        if (haystack.refCnt() == 0) {
            throw new IllegalStateException("Haystack is not accessible");
        }
        if (needle.refCnt() == 0) {
            throw new IllegalStateException("Needle is not accessible");
        }

        if (needle.readableBytes() > haystack.readableBytes()) {
            return -1;
        }

        if (hl == null) {
            hl = UNCHECKED_LOAD_BYTE_BUFFER;
        }

        if (nl == null) {
            nl = UNCHECKED_LOAD_BYTE_BUFFER;
        }

        int haystackLen = haystack.readableBytes();
        int needleLen = needle.readableBytes();
        if (needleLen == 0) {
            return 0;
        }

        // When the needle has only one byte that can be read,
        // the Buffer.bytesBefore() method can be used
        if (needleLen == 1) {
            return haystack.bytesBefore(needle.getByte(needle.readerIndex()));
        }

        int needleStart = needle.readerIndex();
        int haystackStart = haystack.readerIndex();
        long suffixes = maxFixes(needle, nl, needleLen, needleStart, true);
        long prefixes = maxFixes(needle, nl, needleLen, needleStart, false);
        int maxSuffix = Math.max((int) (suffixes >> 32), (int) (prefixes >> 32));
        int period = Math.max((int) suffixes, (int) prefixes);
        int length = Math.min(needleLen - period, maxSuffix + 1);

        if (equalsInner(needle, needleStart, needle, needleStart + period, length)) {
            return bytesBeforeInnerPeriodic(
                    haystack, hl, needle, nl, haystackLen, needleLen, needleStart, haystackStart, maxSuffix, period);
        }
        return bytesBeforeInnerNonPeriodic(
                haystack, hl, needle, nl, haystackLen, needleLen, needleStart, haystackStart, maxSuffix);
    }

    private static int bytesBeforeInnerPeriodic(ByteBuf haystack, UncheckedLoadByte hl,
                                                ByteBuf needle, UncheckedLoadByte nl,
                                                int haystackLen, int needleLen, int needleStart, int haystackStart,
                                                int maxSuffix, int period) {
        int j = 0;
        int memory = -1;
        while (j <= haystackLen - needleLen) {
            int i = Math.max(maxSuffix, memory) + 1;
            while (i < needleLen && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                ++i;
            }
            if (i > haystackLen) {
                return -1;
            }
            if (i >= needleLen) {
                i = maxSuffix;
                while (i > memory && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                    --i;
                }
                if (i <= memory) {
                    return j;
                }
                j += period;
                memory = needleLen - period - 1;
            } else {
                j += i - maxSuffix;
                memory = -1;
            }
        }
        return -1;
    }

    private static int bytesBeforeInnerNonPeriodic(ByteBuf haystack, UncheckedLoadByte hl,
                                                   ByteBuf needle, UncheckedLoadByte nl,
                                                   int haystackLen, int needleLen, int needleStart, int haystackStart,
                                                   int maxSuffix) {
        int j = 0;
        int period = Math.max(maxSuffix + 1, needleLen - maxSuffix - 1) + 1;
        while (j <= haystackLen - needleLen) {
            int i = maxSuffix + 1;
            while (i < needleLen && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                ++i;
            }
            if (i > haystackLen) {
                return -1;
            }
            if (i >= needleLen) {
                i = maxSuffix;
                while (i >= 0 && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                    --i;
                }
                if (i < 0) {
                    return j;
                }
                j += period;
            } else {
                j += i - maxSuffix;
            }
        }
        return -1;
    }

    private static long maxFixes(ByteBuf needle, UncheckedLoadByte nl, int needleLen, int start, boolean isSuffix) {
        int period = 1;
        int maxSuffix = -1;
        int lastRest = start;
        int k = 1;
        while (lastRest + k < needleLen) {
            byte a = nl.load(needle, lastRest + k);
            byte b = nl.load(needle, maxSuffix + k);
            boolean suffix = isSuffix ? a < b : a > b;
            if (suffix) {
                lastRest += k;
                k = 1;
                period = lastRest - maxSuffix;
            } else if (a == b) {
                if (k != period) {
                    ++k;
                } else {
                    lastRest += period;
                    k = 1;
                }
            } else {
                maxSuffix = lastRest;
                lastRest = maxSuffix + 1;
                k = period = 1;
            }
        }
        return ((long) maxSuffix << 32) + period;
    }
}
