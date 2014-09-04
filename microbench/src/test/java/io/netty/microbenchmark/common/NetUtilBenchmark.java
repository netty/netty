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

import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Threads(4)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class NetUtilBenchmark extends AbstractMicrobenchmark {

    @Benchmark
    public void useGetByNameIpv4() {
        for (String testEntry : invalidIpV4Hosts.keySet()) {
            if (NetUtil.getByName(testEntry, true) != null) {
                throw new RuntimeException("error");
            }
        }
    }

    @Benchmark
    public void useGetByNameIpv6() {
        for (String testEntry : invalidIpV6Hosts.keySet()) {
            if (NetUtil.getByName(testEntry, true) != null) {
                throw new RuntimeException("error");
            }
        }
    }

    @Benchmark
    public void useIsValidIpv6() {
        for (String host : invalidIpV6Hosts.keySet()) {
            if (NetUtil.isValidIpV6Address(host)) {
                throw new RuntimeException("error");
            }
        }
    }

    @Benchmark
    public void useIsValidIpv4() {
        for (String host : invalidIpV4Hosts.keySet()) {
            if (NetUtil.isValidIpV4Address(host)) {
                throw new RuntimeException("error");
            }
        }
    }

    private static final Map<String, byte[]> invalidIpV4Hosts = new HashMap<String, byte[]>() {
        private static final long serialVersionUID = 1299215199895717282L;
        {
            put("1.256.3.4", null);
            put("256.0.0.1", null);
            put("1.1.1.1.1", null);
            put("x.255.255.255", null);
            put("0.1:0.0", null);
            put("0.1.0.0:", null);
            put("127.0.0.", null);
            put("1.2..4", null);
            put("192.0.1", null);
            put("192.0.1.1.1", null);
            put("192.0.1.a", null);
            put("19a.0.1.1", null);
            put("a.0.1.1", null);
            put(".0.1.1", null);
            put("...", null);
        }
    };

    private static final Map<String, byte[]> invalidIpV6Hosts = new HashMap<String, byte[]>() {
        private static final long serialVersionUID = -5870810805409009696L;
        {
            // Test method with garbage.
            put("Obvious Garbage", null);
            // Test method with preferred style, too many :
            put("0:1:2:3:4:5:6:7:8", null);
            // Test method with preferred style, not enough :
            put("0:1:2:3:4:5:6", null);
            // Test method with preferred style, bad digits.
            put("0:1:2:3:4:5:6:x", null);
            // Test method with preferred style, adjacent :
            put("0:1:2:3:4:5:6::7", null);
            // Too many : separators trailing
            put("0:1:2:3:4:5:6:7::", null);
            // Too many : separators leading
            put("::0:1:2:3:4:5:6:7", null);
            // Too many : separators trailing
            put("1:2:3:4:5:6:7:", null);
            // Too many : separators leading
            put(":1:2:3:4:5:6:7", null);
            // Too many : separators leading 0
            put("0::1:2:3:4:5:6:7", null);
            // Test method with preferred style, too many digits.
            put("0:1:2:3:4:5:6:789abcdef", null);
            // Test method with compressed style, bad digits.
            put("0:1:2:3::x", null);
            // Test method with compressed style, too many adjacent :
            put("0:1:2:::3", null);
            // Test method with compressed style, too many digits.
            put("0:1:2:3::abcde", null);
            // Test method with preferred style, too many :
            put("0:1:2:3:4:5:6:7:8", null);
            // Test method with compressed style, not enough :
            put("0:1", null);
            // Test method with ipv4 style, bad ipv6 digits.
            put("0:0:0:0:0:x:10.0.0.1", null);
            // Test method with ipv4 style, bad ipv4 digits.
            put("0:0:0:0:0:0:10.0.0.x", null);
            // Test method with ipv4 style, adjacent :
            put("0:0:0:0:0::0:10.0.0.1", null);
            // Test method with ipv4 style, too many ipv6 digits.
            put("0:0:0:0:0:00000:10.0.0.1", null);
            // Test method with ipv4 style, too many :
            put("0:0:0:0:0:0:0:10.0.0.1", null);
            // Test method with ipv4 style, not enough :
            put("0:0:0:0:0:10.0.0.1", null);
            // Test method with ipv4 style, too many .
            put("0:0:0:0:0:0:10.0.0.0.1", null);
            // Test method with ipv4 style, not enough .
            put("0:0:0:0:0:0:10.0.1", null);
            // Test method with ipv4 style, adjacent .
            put("0:0:0:0:0:0:10..0.0.1", null);
            // Test method with ipv4 style, leading .
            put("0:0:0:0:0:0:.0.0.1", null);
            // Test method with ipv4 style, leading .
            put("0:0:0:0:0:0:.10.0.0.1", null);
            // Test method with ipv4 style, trailing .
            put("0:0:0:0:0:0:10.0.0.", null);
            // Test method with ipv4 style, trailing .
            put("0:0:0:0:0:0:10.0.0.1.", null);
            // Test method with compressed ipv4 style, bad ipv6 digits.
            put("::fffx:192.168.0.1", null);
            // Test method with compressed ipv4 style, bad ipv4 digits.
            put("::ffff:192.168.0.x", null);
            // Test method with compressed ipv4 style, too many adjacent :
            put(":::ffff:192.168.0.1", null);
            // Test method with compressed ipv4 style, too many ipv6 digits.
            put("::fffff:192.168.0.1", null);
            // Test method with compressed ipv4 style, too many ipv4 digits.
            put("::ffff:1923.168.0.1", null);
            // Test method with compressed ipv4 style, not enough :
            put(":ffff:192.168.0.1", null);
            // Test method with compressed ipv4 style, too many .
            put("::ffff:192.168.0.1.2", null);
            // Test method with compressed ipv4 style, not enough .
            put("::ffff:192.168.0", null);
            // Test method with compressed ipv4 style, adjacent .
            put("::ffff:192.168..0.1", null);
            // Test method, garbage.
            put("absolute, and utter garbage", null);
            // Test method, bad ipv6 digits.
            put("x:0:0:0:0:0:10.0.0.1", null);
            // Test method, bad ipv4 digits.
            put("0:0:0:0:0:0:x.0.0.1", null);
            // Test method, too many ipv6 digits.
            put("00000:0:0:0:0:0:10.0.0.1", null);
            // Test method, too many ipv4 digits.
            put("0:0:0:0:0:0:10.0.0.1000", null);
            // Test method, too many :
            put("0:0:0:0:0:0:0:10.0.0.1", null);
            // Test method, not enough :
            put("0:0:0:0:0:10.0.0.1", null);
            // Test method, out of order trailing :
            put("0:0:0:0:0:10.0.0.1:", null);
            // Test method, out of order leading :
            put(":0:0:0:0:0:10.0.0.1", null);
            // Test method, out of order leading :
            put("0:0:0:0::10.0.0.1:", null);
            // Test method, out of order trailing :
            put(":0:0:0:0::10.0.0.1", null);
            // Test method, too many .
            put("0:0:0:0:0:0:10.0.0.0.1", null);
            // Test method, not enough .
            put("0:0:0:0:0:0:10.0.1", null);
            // Test method, adjacent .
            put("0:0:0:0:0:0:10.0.0..1", null);
            // Double compression symbol
            put("::0::", null);
            // Empty contents
            put("", null);
            // Trailing : (max number of : = 8)
            put("2001:0:4136:e378:8000:63bf:3fff:fdd2:", null);
            // Leading : (max number of : = 8)
            put(":aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222", null);
            // Invalid character
            put("1234:2345:3456:4567:5678:6789::X890", null);
            // Trailing . in IPv4
            put("::ffff:255.255.255.255.", null);
            // To many characters in IPv4
            put("::ffff:0.0.1111.0", null);
            // Test method, adjacent .
            put("::ffff:0.0..0", null);
            // Not enough IPv4 entries trailing .
            put("::ffff:127.0.0.", null);
            // Not enough IPv4 entries no trailing .
            put("::ffff:1.2.4", null);
            // Extra IPv4 entry
            put("::ffff:192.168.0.1.255", null);
            // Not enough IPv6 content
            put(":ffff:192.168.0.1.255", null);
            // Intermixed IPv4 and IPv6 symbols
            put("::ffff:255.255:255.255.", null);
        }
    };
}
