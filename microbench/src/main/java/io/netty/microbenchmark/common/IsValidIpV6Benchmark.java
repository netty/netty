/*
 * Copyright 2017 The Netty Project
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
package io.netty.microbenchmark.common;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.NetUtil;
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
public class IsValidIpV6Benchmark extends AbstractMicrobenchmark {

    @Param({
            "127.0.0.1", "fdf8:f53b:82e4::53", "2001::1",
            "2001:0000:4136:e378:8000:63bf:3fff:fdd2", "0:0:0:0:0:0:10.0.0.1"
    })
    private String ip;

    private static boolean isValidIp4Word(String word) {
        char c;
        if (word.length() < 1 || word.length() > 3) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            c = word.charAt(i);
            if (!(c >= '0' && c <= '9')) {
                return false;
            }
        }
        return Integer.parseInt(word) <= 255;
    }

    private static boolean isValidHexChar(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
    }

    private static boolean isValidIPv4MappedChar(char c) {
        return c == 'f' || c == 'F';
    }

    public static boolean isValidIpV6AddressOld(String ipAddress) {
        boolean doubleColon = false;
        int numberOfColons = 0;
        int numberOfPeriods = 0;
        StringBuilder word = new StringBuilder();
        char c = 0;
        char prevChar;
        int startOffset = 0; // offset for [] ip addresses
        int endOffset = ipAddress.length();

        if (endOffset < 2) {
            return false;
        }

        // Strip []
        if (ipAddress.charAt(0) == '[') {
            if (ipAddress.charAt(endOffset - 1) != ']') {
                return false; // must have a close ]
            }

            startOffset = 1;
            endOffset--;
        }

        // Strip the interface name/index after the percent sign.
        int percentIdx = ipAddress.indexOf('%', startOffset);
        if (percentIdx >= 0) {
            endOffset = percentIdx;
        }

        for (int i = startOffset; i < endOffset; i++) {
            prevChar = c;
            c = ipAddress.charAt(i);
            switch (c) {
            // case for the last 32-bits represented as IPv4 x:x:x:x:x:x:d.d.d.d
            case '.':
                numberOfPeriods++;
                if (numberOfPeriods > 3) {
                    return false;
                }
                if (numberOfPeriods == 1) {
                    // Verify this address is of the correct structure to contain an IPv4 address.
                    // It must be IPv4-Mapped or IPv4-Compatible
                    // (see https://tools.ietf.org/html/rfc4291#section-2.5.5).
                    int j = i - word.length() - 2; // index of character before the previous ':'.
                    final int beginColonIndex = ipAddress.lastIndexOf(':', j);
                    if (beginColonIndex == -1) {
                        return false;
                    }
                    char tmpChar = ipAddress.charAt(j);
                    if (isValidIPv4MappedChar(tmpChar)) {
                        if (j - beginColonIndex != 4 ||
                            !isValidIPv4MappedChar(ipAddress.charAt(j - 1)) ||
                            !isValidIPv4MappedChar(ipAddress.charAt(j - 2)) ||
                            !isValidIPv4MappedChar(ipAddress.charAt(j - 3))) {
                            return false;
                        }
                        j -= 5;
                    } else if (tmpChar == '0' || tmpChar == ':') {
                        --j;
                    } else {
                        return false;
                    }

                    // a special case ::1:2:3:4:5:d.d.d.d allows 7 colons with an
                    // IPv4 ending, otherwise 7 :'s is bad
                    if ((numberOfColons != 6 && !doubleColon) || numberOfColons > 7 ||
                        (numberOfColons == 7 && (ipAddress.charAt(startOffset) != ':' ||
                                                 ipAddress.charAt(1 + startOffset) != ':'))) {
                        return false;
                    }

                    for (; j >= startOffset; --j) {
                        tmpChar = ipAddress.charAt(j);
                        if (tmpChar != '0' && tmpChar != ':') {
                            return false;
                        }
                    }
                }

                if (!isValidIp4Word(word.toString())) {
                    return false;
                }
                word.delete(0, word.length());
                break;

            case ':':
                // FIX "IP6 mechanism syntax #ip6-bad1"
                // An IPV6 address cannot start with a single ":".
                // Either it can start with "::" or with a number.
                if (i == startOffset && (endOffset <= i || ipAddress.charAt(i + 1) != ':')) {
                    return false;
                }
                // END FIX "IP6 mechanism syntax #ip6-bad1"
                numberOfColons++;
                if (numberOfColons > 8) {
                    return false;
                }
                if (numberOfPeriods > 0) {
                    return false;
                }
                if (prevChar == ':') {
                    if (doubleColon) {
                        return false;
                    }
                    doubleColon = true;
                }
                word.delete(0, word.length());
                break;

            default:
                if (word != null && word.length() > 3) {
                    return false;
                }
                if (!isValidHexChar(c)) {
                    return false;
                }
                word.append(c);
            }
        }

        // Check if we have an IPv4 ending
        if (numberOfPeriods > 0) {
            // There is a test case with 7 colons and valid ipv4 this should resolve it
            return numberOfPeriods == 3 &&
                    (isValidIp4Word(word.toString()) && (numberOfColons < 7 || doubleColon));
        } else {
            // If we're at then end and we haven't had 7 colons then there is a
            // problem unless we encountered a doubleColon
            if (numberOfColons != 7 && !doubleColon) {
                return false;
            }

            if (word.length() == 0) {
                // If we have an empty word at the end, it means we ended in either
                // a : or a .
                // If we did not end in :: then this is invalid
                return ipAddress.charAt(endOffset - 1) != ':' ||
                        ipAddress.charAt(endOffset - 2) == ':';
            } else {
                return numberOfColons != 8 || ipAddress.charAt(startOffset) == ':';
            }
        }
    }

    // Tests

    @Benchmark
    public boolean isValidIpV6AddressOld() {
        return isValidIpV6AddressOld(ip);
    }

    @Benchmark
    public boolean isValidIpV6AddressNew() {
        return NetUtil.isValidIpV6Address(ip);
    }
}
