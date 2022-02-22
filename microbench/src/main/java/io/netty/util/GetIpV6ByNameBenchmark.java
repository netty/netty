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
package io.netty.util;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GetIpV6ByNameBenchmark extends AbstractMicrobenchmark {

    @Param({
        "::",
        "::1234:2345",
        "1234:2345::3456:7890",
        "fe80::bdad:7a67:6fcd:fa89",
        "fe80:bdad:7a67:6fcd::fa89",
        "1234:2345:3456:4567:5678:6789:0:7890"
    })
    private String ip;

    @Benchmark
    public byte[] getNameOld() {
        return GetByNameHolder.getIPv6ByNameOld(ip, true);
    }

    @Benchmark
    public byte[] getNameNew() {
        return NetUtil.getIPv6ByName(ip, true);
    }
}
/*
* this is the original implementation
* I only changed line 225 - 250, and removed some unused statements.
* */
final class GetByNameHolder {
    private GetByNameHolder() {
    }

    private static final int IPV6_BYTE_COUNT = 16;
    private static final int IPV6_MAX_CHAR_BETWEEN_SEPARATOR = 4;
    private static final int IPV6_MIN_SEPARATORS = 2;
    private static final int IPV6_MAX_SEPARATORS = 8;
    private static final int IPV4_MAX_CHAR_BETWEEN_SEPARATOR = 3;
    private static final int IPV4_SEPARATORS = 3;

    public static byte[] getIPv6ByNameOld(CharSequence ip, boolean ipv4Mapped) {
        final byte[] bytes = new byte[IPV6_BYTE_COUNT];
        final int ipLength = ip.length();
        int compressBegin = 0;
        int compressLength = 0;
        int currentIndex = 0;
        int value = 0;
        int begin = -1;
        int i = 0;
        int ipv6Separators = 0;
        int ipv4Separators = 0;
        int tmp;
        boolean needsShift = false;
        for (; i < ipLength; ++i) {
            final char c = ip.charAt(i);
            switch (c) {
                case ':':
                    ++ipv6Separators;
                    if (i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR ||
                        ipv4Separators > 0 || ipv6Separators > IPV6_MAX_SEPARATORS ||
                        currentIndex + 1 >= bytes.length) {
                        return null;
                    }
                    value <<= (IPV6_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) << 2;

                    if (compressLength > 0) {
                        compressLength -= 2;
                    }

                    // The value integer holds at most 4 bytes from right (most significant) to left (least significant)
                    // The following bit shifting is used to extract and re-order the individual bytes to achieve a
                    // left (most significant) to right (least significant) ordering.
                    bytes[currentIndex++] = (byte) (((value & 0xf) << 4) | ((value >> 4) & 0xf));
                    bytes[currentIndex++] = (byte) ((((value >> 8) & 0xf) << 4) | ((value >> 12) & 0xf));
                    tmp = i + 1;
                    if (tmp < ipLength && ip.charAt(tmp) == ':') {
                        ++tmp;
                        if (compressBegin != 0 || (tmp < ipLength && ip.charAt(tmp) == ':')) {
                            return null;
                        }
                        ++ipv6Separators;
                        needsShift = ipv6Separators == 2 && value == 0;
                        compressBegin = currentIndex;
                        compressLength = bytes.length - compressBegin - 2;
                        ++i;
                    }
                    value = 0;
                    begin = -1;
                    break;
                case '.':
                    ++ipv4Separators;
                    tmp = i - begin; // tmp is the length of the current segment.
                    if (tmp > IPV4_MAX_CHAR_BETWEEN_SEPARATOR
                        || begin < 0
                        || ipv4Separators > IPV4_SEPARATORS
                        || (ipv6Separators > 0 && (currentIndex + compressLength < 12))
                        || i + 1 >= ipLength
                        || currentIndex >= bytes.length
                        || ipv4Separators == 1 &&
                        // We also parse pure IPv4 addresses as IPv4-Mapped for ease of use.
                        ((!ipv4Mapped || currentIndex != 0 && !isValidIPv4Mapped(bytes, currentIndex,
                            compressBegin, compressLength)) ||
                            (tmp == 3 && (!isValidNumericChar(ip.charAt(i - 1)) ||
                                !isValidNumericChar(ip.charAt(i - 2)) ||
                                !isValidNumericChar(ip.charAt(i - 3))) ||
                                tmp == 2 && (!isValidNumericChar(ip.charAt(i - 1)) ||
                                    !isValidNumericChar(ip.charAt(i - 2))) ||
                                tmp == 1 && !isValidNumericChar(ip.charAt(i - 1))))) {
                        return null;
                    }
                    value <<= (IPV4_MAX_CHAR_BETWEEN_SEPARATOR - tmp) << 2;

                    // The value integer holds at most 3 bytes from right (most significant) to left (least significant)
                    // The following bit shifting is to restructure the bytes to be left (most significant) to
                    // right (least significant) while also accounting for each IPv4 digit is base 10.
                    begin = (value & 0xf) * 100 + ((value >> 4) & 0xf) * 10 + ((value >> 8) & 0xf);
                    if (begin < 0 || begin > 255) {
                        return null;
                    }
                    bytes[currentIndex++] = (byte) begin;
                    value = 0;
                    begin = -1;
                    break;
                default:
                    if (!isValidHexChar(c) || (ipv4Separators > 0 && !isValidNumericChar(c))) {
                        return null;
                    }
                    if (begin < 0) {
                        begin = i;
                    } else if (i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR) {
                        return null;
                    }
                    // The value is treated as a sort of array of numbers because we are dealing with
                    // at most 4 consecutive bytes we can use bit shifting to accomplish this.
                    // The most significant byte will be encountered first, and reside in the right most
                    // position of the following integer
                    value += StringUtil.decodeHexNibble(c) << ((i - begin) << 2);
                    break;
            }
        }

        final boolean isCompressed = compressBegin > 0;
        // Finish up last set of data that was accumulated in the loop (or before the loop)
        if (ipv4Separators > 0) {
            if (begin > 0 && i - begin > IPV4_MAX_CHAR_BETWEEN_SEPARATOR ||
                ipv4Separators != IPV4_SEPARATORS ||
                currentIndex >= bytes.length) {
                return null;
            }
            if (ipv6Separators == 0) {
                compressLength = 12;
            } else if (ipv6Separators >= IPV6_MIN_SEPARATORS &&
                (!isCompressed && (ipv6Separators == 6 && ip.charAt(0) != ':') ||
                    isCompressed && (ipv6Separators < IPV6_MAX_SEPARATORS &&
                        (ip.charAt(0) != ':' || compressBegin <= 2)))) {
                compressLength -= 2;
            } else {
                return null;
            }
            value <<= (IPV4_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) << 2;

            // The value integer holds at most 3 bytes from right (most significant) to left (least significant).
            // The following bit shifting is to restructure the bytes to be left (most significant) to
            // right (least significant) while also accounting for each IPv4 digit is base 10.
            begin = (value & 0xf) * 100 + ((value >> 4) & 0xf) * 10 + ((value >> 8) & 0xf);
            if (begin < 0 || begin > 255) {
                return null;
            }
            bytes[currentIndex++] = (byte) begin;
        } else {
            tmp = ipLength - 1;
            if (begin > 0 && i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR ||
                ipv6Separators < IPV6_MIN_SEPARATORS ||
                !isCompressed && (ipv6Separators + 1 != IPV6_MAX_SEPARATORS ||
                    ip.charAt(0) == ':' || ip.charAt(tmp) == ':') ||
                isCompressed && (ipv6Separators > IPV6_MAX_SEPARATORS ||
                    (ipv6Separators == IPV6_MAX_SEPARATORS &&
                        (compressBegin <= 2 && ip.charAt(0) != ':' ||
                            compressBegin >= 14 && ip.charAt(tmp) != ':'))) ||
                currentIndex + 1 >= bytes.length ||
                begin < 0 && ip.charAt(tmp - 1) != ':' ||
                compressBegin > 2 && ip.charAt(0) == ':') {
                return null;
            }
            if (begin >= 0 && i - begin <= IPV6_MAX_CHAR_BETWEEN_SEPARATOR) {
                value <<= (IPV6_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) << 2;
            }
            // The value integer holds at most 4 bytes from right (most significant) to left (least significant).
            // The following bit shifting is used to extract and re-order the individual bytes to achieve a
            // left (most significant) to right (least significant) ordering.
            bytes[currentIndex++] = (byte) (((value & 0xf) << 4) | ((value >> 4) & 0xf));
            bytes[currentIndex++] = (byte) ((((value >> 8) & 0xf) << 4) | ((value >> 12) & 0xf));
        }

        i = currentIndex + compressLength;
        if (needsShift || i >= bytes.length) {
            // Right shift array
            if (i >= bytes.length) {
                ++compressBegin;
            }
            for (i = currentIndex; i < bytes.length; ++i) {
                for (begin = bytes.length - 1; begin >= compressBegin; --begin) {
                    bytes[begin] = bytes[begin - 1];
                }
                bytes[begin] = 0;
                ++compressBegin;
            }
        } else {
            // Selectively move elements
            for (i = 0; i < compressLength; ++i) {
                begin = i + compressBegin;
                currentIndex = begin + compressLength;
                if (currentIndex < bytes.length) {
                    bytes[currentIndex] = bytes[begin];
                    bytes[begin] = 0;
                } else {
                    break;
                }
            }
        }

        if (ipv4Separators > 0) {
            // We only support IPv4-Mapped addresses [1] because IPv4-Compatible addresses are deprecated [2].
            // [1] https://tools.ietf.org/html/rfc4291#section-2.5.5.2
            // [2] https://tools.ietf.org/html/rfc4291#section-2.5.5.1
            bytes[10] = bytes[11] = (byte) 0xff;
        }

        return bytes;
    }

    private static boolean isValidHexChar(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
    }

    private static boolean isValidNumericChar(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isValidIPv4MappedSeparators(byte b0, byte b1, boolean mustBeZero) {
        // We allow IPv4 Mapped (https://tools.ietf.org/html/rfc4291#section-2.5.5.1)
        // and IPv4 compatible (https://tools.ietf.org/html/rfc4291#section-2.5.5.1).
        // The IPv4 compatible is deprecated, but it allows parsing of plain IPv4 addressed into IPv6-Mapped addresses.
        return b0 == b1 && (b0 == 0 || !mustBeZero && b1 == -1);
    }

    private static boolean isValidIPv4Mapped(byte[] bytes, int currentIndex, int compressBegin, int compressLength) {
        final boolean mustBeZero = compressBegin + compressLength >= 14;
        return currentIndex <= 12 && currentIndex >= 2 && (!mustBeZero || compressBegin < 12) &&
            isValidIPv4MappedSeparators(bytes[currentIndex - 1], bytes[currentIndex - 2], mustBeZero) &&
            PlatformDependent.isZero(bytes, 0, currentIndex - 3);
    }
}
