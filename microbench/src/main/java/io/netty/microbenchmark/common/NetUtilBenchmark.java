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
package io.netty.microbenchmark.common;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.NetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Threads(4)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class NetUtilBenchmark extends AbstractMicrobenchmark {

    @Benchmark
    public int useGetByNameIpv4() {
        int invalidCount = 0;
        for (String testEntry : invalidIpV4Hosts) {
            if (NetUtil.getByName(testEntry) == null) {
                ++invalidCount;
            }
        }
        return invalidCount;
    }

    @Benchmark
    public int useGetByNameIpv6() {
        int invalidCount = 0;
        for (String testEntry : invalidIpV6Hosts) {
            if (NetUtil.getByName(testEntry) == null) {
                ++invalidCount;
            }
        }
        return invalidCount;
    }

    @Benchmark
    public int useIsValidIpv6() {
        int invalidCount = 0;
        for (String host : invalidIpV6Hosts) {
            if (!NetUtil.isValidIpV6Address(host)) {
                ++invalidCount;
            }
        }
        return invalidCount;
    }

    @Benchmark
    public int useIsValidIpv4() {
        int invalidCount = 0;
        for (String host : invalidIpV4Hosts) {
            if (!NetUtil.isValidIpV4Address(host)) {
                ++invalidCount;
            }
        }
        return invalidCount;
    }

    private static final String[] invalidIpV4Hosts = {
        "1.256.3.4",
        "256.0.0.1",
        "1.1.1.1.1",
        "x.255.255.255",
        "0.1:0.0",
        "0.1.0.0:",
        "127.0.0.",
        "1.2..4",
        "192.0.1",
        "192.0.1.1.1",
        "192.0.1.a",
        "19a.0.1.1",
        "a.0.1.1",
        ".0.1.1",
        "127.0.0",
        "192.0.1.256",
        "0.0.200.259",
        "1.1.-1.1",
        "1.1. 1.1",
        "1.1.1.1 ",
        "1.1.+1.1",
        "0.0x1.0.255",
        "0.01x.0.255",
        "0.x01.0.255",
        "0.-.0.0",
        "0..0.0",
        "0.A.0.0",
        "0.1111.0.0",
        "..."
    };

    private static final String[] invalidIpV6Hosts = {
        // Test method with garbage.
        "Obvious Garbage",
        // Test method with preferred style, too many :
        "0:1:2:3:4:5:6:7:8",
        // Test method with preferred style, not enough :
        "0:1:2:3:4:5:6",
        // Test method with preferred style, bad digits.
        "0:1:2:3:4:5:6:x",
        // Test method with preferred style, adjacent :
        "0:1:2:3:4:5:6::7",
        // Too many : separators trailing
        "0:1:2:3:4:5:6:7::",
        // Too many : separators leading
        "::0:1:2:3:4:5:6:7",
        // Too many : separators trailing
        "1:2:3:4:5:6:7:",
        // Too many : separators leading
        ":1:2:3:4:5:6:7",
        // Compression with : separators trailing
        "0:1:2:3:4:5::7:",
        "0:1:2:3:4::7:",
        "0:1:2:3::7:",
        "0:1:2::7:",
        "0:1::7:",
        "0::7:",
        // Compression at start with : separators trailing
        "::0:1:2:3:4:5:7:",
        "::0:1:2:3:4:7:",
        "::0:1:2:3:7:",
        "::0:1:2:7:",
        "::0:1:7:",
        "::7:",
        // The : separators leading and trailing
        ":1:2:3:4:5:6:7:",
        ":1:2:3:4:5:6:",
        ":1:2:3:4:5:",
        ":1:2:3:4:",
        ":1:2:3:",
        ":1:2:",
        ":1:",
        // Compression with : separators leading
        ":1::2:3:4:5:6:7",
        ":1::3:4:5:6:7",
        ":1::4:5:6:7",
        ":1::5:6:7",
        ":1::6:7",
        ":1::7",
        ":1:2:3:4:5:6::7",
        ":1:3:4:5:6::7",
        ":1:4:5:6::7",
        ":1:5:6::7",
        ":1:6::7",
        ":1::",
        // Compression trailing with : separators leading
        ":1:2:3:4:5:6:7::",
        ":1:3:4:5:6:7::",
        ":1:4:5:6:7::",
        ":1:5:6:7::",
        ":1:6:7::",
        ":1:7::",
        // Double compression
        "1::2:3:4:5:6::",
        "::1:2:3:4:5::6",
        "::1:2:3:4:5:6::",
        "::1:2:3:4:5::",
        "::1:2:3:4::",
        "::1:2:3::",
        "::1:2::",
        "::0::",
        "12::0::12",
        // Too many : separators leading 0
        "0::1:2:3:4:5:6:7",
        // Test method with preferred style, too many digits.
        "0:1:2:3:4:5:6:789abcdef",
        // Test method with compressed style, bad digits.
        "0:1:2:3::x",
        // Test method with compressed style, too many adjacent :
        "0:1:2:::3",
        // Test method with compressed style, too many digits.
        "0:1:2:3::abcde",
        // Test method with compressed style, not enough :
        "0:1",
        // Test method with ipv4 style, bad ipv6 digits.
        "0:0:0:0:0:x:10.0.0.1",
        // Test method with ipv4 style, bad ipv4 digits.
        "0:0:0:0:0:0:10.0.0.x",
        // Test method with ipv4 style, too many ipv6 digits.
        "0:0:0:0:0:00000:10.0.0.1",
        // Test method with ipv4 style, too many :
        "0:0:0:0:0:0:0:10.0.0.1",
        // Test method with ipv4 style, not enough :
        "0:0:0:0:0:10.0.0.1",
        // Test method with ipv4 style, too many .
        "0:0:0:0:0:0:10.0.0.0.1",
        // Test method with ipv4 style, not enough .
        "0:0:0:0:0:0:10.0.1",
        // Test method with ipv4 style, adjacent .
        "0:0:0:0:0:0:10..0.0.1",
        // Test method with ipv4 style, leading .
        "0:0:0:0:0:0:.0.0.1",
        // Test method with ipv4 style, leading .
        "0:0:0:0:0:0:.10.0.0.1",
        // Test method with ipv4 style, trailing .
        "0:0:0:0:0:0:10.0.0.",
        // Test method with ipv4 style, trailing .
        "0:0:0:0:0:0:10.0.0.1.",
        // Test method with compressed ipv4 style, bad ipv6 digits.
        "::fffx:192.168.0.1",
        // Test method with compressed ipv4 style, bad ipv4 digits.
        "::ffff:192.168.0.x",
        // Test method with compressed ipv4 style, too many adjacent :
        ":::ffff:192.168.0.1",
        // Test method with compressed ipv4 style, too many ipv6 digits.
        "::fffff:192.168.0.1",
        // Test method with compressed ipv4 style, too many ipv4 digits.
        "::ffff:1923.168.0.1",
        // Test method with compressed ipv4 style, not enough :
        ":ffff:192.168.0.1",
        // Test method with compressed ipv4 style, too many .
        "::ffff:192.168.0.1.2",
        // Test method with compressed ipv4 style, not enough .
        "::ffff:192.168.0",
        // Test method with compressed ipv4 style, adjacent .
        "::ffff:192.168..0.1",
        // Test method, bad ipv6 digits.
        "x:0:0:0:0:0:10.0.0.1",
        // Test method, bad ipv4 digits.
        "0:0:0:0:0:0:x.0.0.1",
        // Test method, too many ipv6 digits.
        "00000:0:0:0:0:0:10.0.0.1",
        // Test method, too many ipv4 digits.
        "0:0:0:0:0:0:10.0.0.1000",
        // Test method, too many :
        "0:0:0:0:0:0:0:10.0.0.1",
        // Test method, not enough :
        "0:0:0:0:0:10.0.0.1",
        // Test method, out of order trailing :
        "0:0:0:0:0:10.0.0.1:",
        // Test method, out of order leading :
        ":0:0:0:0:0:10.0.0.1",
        // Test method, out of order leading :
        "0:0:0:0::10.0.0.1:",
        // Test method, out of order trailing :
        ":0:0:0:0::10.0.0.1",
        // Test method, too many .
        "0:0:0:0:0:0:10.0.0.0.1",
        // Test method, not enough .
        "0:0:0:0:0:0:10.0.1",
        // Test method, adjacent .
        "0:0:0:0:0:0:10.0.0..1",
        // Empty contents
        "",
        // Invalid single compression
        ":",
        ":::",
        // Trailing : (max number of : = 8)
        "2001:0:4136:e378:8000:63bf:3fff:fdd2:",
        // Leading : (max number of : = 8)
        ":aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222",
        // Invalid character
        "1234:2345:3456:4567:5678:6789::X890",
        // Trailing . in IPv4
        "::ffff:255.255.255.255.",
        // To many characters in IPv4
        "::ffff:0.0.1111.0",
        // Test method, adjacent .
        "::ffff:0.0..0",
        // Not enough IPv4 entries trailing .
        "::ffff:127.0.0.",
        // Invalid trailing IPv4 character
        "::ffff:127.0.0.a",
        // Invalid leading IPv4 character
        "::ffff:a.0.0.1",
        // Invalid middle IPv4 character
        "::ffff:127.a.0.1",
        // Invalid middle IPv4 character
        "::ffff:127.0.a.1",
        // Not enough IPv4 entries no trailing .
        "::ffff:1.2.4",
        // Extra IPv4 entry
        "::ffff:192.168.0.1.255",
        // Not enough IPv6 content
        ":ffff:192.168.0.1.255",
        // Intermixed IPv4 and IPv6 symbols
        "::ffff:255.255:255.255.",
        // Invalid IPv4 mapped address - invalid ipv4 separator
        "0:0:0::0:0:00f.0.0.1",
        // Invalid IPv4 mapped address - not enough f's
        "0:0:0:0:0:fff:1.0.0.1",
        // Invalid IPv4 mapped address - not IPv4 mapped, not IPv4 compatible
        "0:0:0:0:0:ff00:1.0.0.1",
        // Invalid IPv4 mapped address - not IPv4 mapped, not IPv4 compatible
        "0:0:0:0:0:ff:1.0.0.1",
        // Invalid IPv4 mapped address - too many f's
        "0:0:0:0:0:fffff:1.0.0.1",
        // Invalid IPv4 mapped address - too many bytes (too many 0's)
        "0:0:0:0:0:0:ffff:1.0.0.1",
        // Invalid IPv4 mapped address - too many bytes (too many 0's)
        "::0:0:0:0:0:ffff:1.0.0.1",
        // Invalid IPv4 mapped address - too many bytes (too many 0's)
        "0:0:0:0:0:0::1.0.0.1",
        // Invalid IPv4 mapped address - too many bytes (too many 0's)
        "0:0:0:0:0:00000:1.0.0.1",
        // Invalid IPv4 mapped address - too few bytes (not enough 0's)
        "0:0:0:0:ffff:1.0.0.1",
        // Invalid IPv4 mapped address - too few bytes (not enough 0's)
        "ffff:192.168.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "0:0:0:0:0:ffff::10.0.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "0:0:0:0:ffff::10.0.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "0:0:0:ffff::10.0.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "0:0:ffff::10.0.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "0:ffff::10.0.0.1",
        // Invalid IPv4 mapped address - 0's after the mapped ffff indicator
        "ffff::10.0.0.1",
        // Invalid IPv4 mapped address - not all 0's before the mapped separator
        "1:0:0:0:0:ffff:10.0.0.1",
        // Address that is similar to IPv4 mapped, but is invalid
        "0:0:0:0:ffff:ffff:1.0.0.1",
        // Valid number of separators, but invalid IPv4 format
        "::1:2:3:4:5:6.7.8.9",
        // Too many digits
        "0:0:0:0:0:0:ffff:10.0.0.1",
        // Invalid IPv4 format
        ":1.2.3.4",
        // Invalid IPv4 format
        "::.2.3.4",
        // Invalid IPv4 format
        "::ffff:0.1.2."
    };
}
