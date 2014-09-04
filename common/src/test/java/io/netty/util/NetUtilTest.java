/*
 * Copyright 2012 The Netty Project
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
package io.netty.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

public class NetUtilTest {
    private static final Map<String, byte[]> validIpV4Hosts = new HashMap<String, byte[]>() {
        private static final long serialVersionUID = 2629792739366724032L;
        {
            put("192.168.1.0", new byte[]{
                    (byte) 0xc0, (byte) 0xa8, 0x01, 0x00}
            );
            put("10.255.255.254", new byte[]{
                    0x0a, (byte) 0xff, (byte) 0xff, (byte) 0xfe
            });
            put("172.18.5.4", new byte[]{
                    (byte) 0xac, 0x12, 0x05, 0x04
            });
            put("0.0.0.0", new byte[]{
                    0x00, 0x00, 0x00, 0x00
            });
            put("127.0.0.1", new byte[]{
                    0x7f, 0x00, 0x00, 0x01
            });
        }
    };
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
    private static final Map<String, byte[]> validIpV6Hosts = new HashMap<String, byte[]>() {
        private static final long serialVersionUID = 3999763170377573184L;
        {
            put("::ffff:5.6.7.8", new byte[]{
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, (byte) 0xff, (byte) 0xff,
                    0x05, 0x06, 0x07, 0x08}
            );
            put("fdf8:f53b:82e4::53", new byte[]{
                    (byte) 0xfd, (byte) 0xf8, (byte) 0xf5, 0x3b,
                    (byte) 0x82, (byte) 0xe4, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x53}
            );
            put("fe80::200:5aee:feaa:20a2", new byte[]{
                    (byte) 0xfe, (byte) 0x80, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x02, 0x00, 0x5a, (byte) 0xee,
                    (byte) 0xfe, (byte) 0xaa, 0x20, (byte) 0xa2}
            );
            put("2001::1", new byte[]{
                    0x20, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01}
            );
            put("2001:0000:4136:e378:8000:63bf:3fff:fdd2", new byte[]{
                    0x20, 0x01, 0x00, 0x00,
                    0x41, 0x36, (byte) 0xe3, 0x78,
                    (byte) 0x80, 0x00, 0x63, (byte) 0xbf,
                    0x3f, (byte) 0xff, (byte) 0xfd, (byte) 0xd2}
            );
            put("2001:0002:6c::430", new byte[]{
                    0x20, 0x01, 0x00, 0x02,
                    0x00, 0x6c, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x04, 0x30}
            );
            put("2001:10:240:ab::a", new byte[]{
                    0x20, 0x01, 0x00, 0x10,
                    0x02, 0x40, 0x00, (byte) 0xab,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x0a});
            put("2002:cb0a:3cdd:1::1", new byte[]{
                    0x20, 0x02, (byte) 0xcb, 0x0a,
                    0x3c, (byte) 0xdd, 0x00, 0x01,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01}
            );
            put("2001:db8:8:4::2", new byte[]{
                    0x20, 0x01, 0x0d, (byte) 0xb8,
                    0x00, 0x08, 0x00, 0x04,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x02}
            );
            put("ff01:0:0:0:0:0:0:2", new byte[]{
                    (byte) 0xff, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x02}
            );
            put("[fdf8:f53b:82e4::53]", new byte[]{
                    (byte) 0xfd, (byte) 0xf8, (byte) 0xf5, 0x3b,
                    (byte) 0x82, (byte) 0xe4, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x53}
            );
            put("[fe80::200:5aee:feaa:20a2]", new byte[]{
                    (byte) 0xfe, (byte) 0x80, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x02, 0x00, 0x5a, (byte) 0xee,
                    (byte) 0xfe, (byte) 0xaa, 0x20, (byte) 0xa2}
            );
            put("[2001::1]", new byte[]{
                    0x20, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01}
            );
            put("[2001:0000:4136:e378:8000:63bf:3fff:fdd2]", new byte[]{
                    0x20, 0x01, 0x00, 0x00,
                    0x41, 0x36, (byte) 0xe3, 0x78,
                    (byte) 0x80, 0x00, 0x63, (byte) 0xbf,
                    0x3f, (byte) 0xff, (byte) 0xfd, (byte) 0xd2}
            );
            put("0:1:2:3:4:5:6:789a", new byte[]{
                    0x00, 0x00, 0x00, 0x01,
                    0x00, 0x02, 0x00, 0x03,
                    0x00, 0x04, 0x00, 0x05,
                    0x00, 0x06, 0x78, (byte) 0x9a}
            );
            put("0:1:2:3::f", new byte[]{
                    0x00, 0x00, 0x00, 0x01,
                    0x00, 0x02, 0x00, 0x03,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x0f}
            );
            put("0:0:0:0:0:0:10.0.0.1", new byte[]{
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x0a, 0x00, 0x00, 0x01}
            );
            put("::ffff:192.168.0.1", new byte[]{
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, (byte) 0xff, (byte) 0xff,
                    (byte) 0xc0, (byte) 0xa8, 0x00, 0x01}
            );
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
    private static final Map<byte[], String> ipv6ToAddressStrings = new HashMap<byte[], String>() {
        private static final long serialVersionUID = 2999763170377573184L;
        {
            // From the RFC 5952 http://tools.ietf.org/html/rfc5952#section-4
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 1},
                    "2001:db8::1");
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 2, 0, 1},
                    "2001:db8::2:1");
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 1,
                0, 1, 0, 1,
                0, 1, 0, 1},
                    "2001:db8:0:1:1:1:1:1");

            // Other examples
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 2, 0, 1},
                    "2001:db8::2:1");
            put(new byte[]{
                32, 1, 0, 0,
                0, 0, 0, 1,
                0, 0, 0, 0,
                0, 0, 0, 1},
                    "2001:0:0:1::1");
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 0, 1},
                    "2001:db8::1:0:0:1");
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 0, 0},
                    "2001:db8:0:0:1::");
            put(new byte[]{
                32, 1, 13, -72,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 2, 0, 0},
                    "2001:db8::2:0");
            put(new byte[]{
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 1},
                    "::1");
            put(new byte[]{
                0, 0, 0, 0,
                0, 0, 0, 1,
                0, 0, 0, 0,
                0, 0, 0, 1},
                    "::1:0:0:0:1");
            put(new byte[]{
                0, 0, 0, 0,
                1, 0, 0, 1,
                0, 0, 0, 0,
                1, 0, 0, 0},
                    "::100:1:0:0:100:0");
            put(new byte[]{
                32, 1, 0, 0,
                65, 54, -29, 120,
                -128, 0, 99, -65,
                63, -1, -3, -46},
                    "2001:0:4136:e378:8000:63bf:3fff:fdd2");
            put(new byte[]{
                -86, -86, -69, -69,
                -52, -52, -35, -35,
                -18, -18, -1, -1,
                17, 17, 34, 34},
                    "aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222");
            put(new byte[]{
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0},
                    "::");
        }
    };

    private static final Map<String, String> ipv4MappedToIPv6AddressStrings = new HashMap<String, String>() {
        private static final long serialVersionUID = 1999763170377573184L;
        {
            // IPv4 addresses
            put("255.255.255.255", "::ffff:255.255.255.255");
            put("0.0.0.0", "::ffff:0.0.0.0");
            put("127.0.0.1", "::ffff:127.0.0.1");
            put("1.2.3.4", "::ffff:1.2.3.4");
            put("192.168.0.1", "::ffff:192.168.0.1");

            // IPv6 addresses
            // Fully specified
            put("2001:0:4136:e378:8000:63bf:3fff:fdd2", "2001:0:4136:e378:8000:63bf:3fff:fdd2");
            put("aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222", "aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222");
            put("0:0:0:0:0:0:0:0", "::");
            put("0:0:0:0:0:0:0:1", "::1");

            // Compressing at the beginning
            put("::1:0:0:0:1", "::1:0:0:0:1");
            put("::1:ffff:ffff", "::1:ffff:ffff");
            put("::", "::");
            put("::1", "::1");
            put("::ffff", "::ffff");
            put("::ffff:0", "::ffff:0");
            put("::ffff:ffff", "::ffff:ffff");
            put("::0987:9876:8765", "::987:9876:8765");
            put("::0987:9876:8765:7654", "::987:9876:8765:7654");
            put("::0987:9876:8765:7654:6543", "::987:9876:8765:7654:6543");
            put("::0987:9876:8765:7654:6543:5432", "::987:9876:8765:7654:6543:5432");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("::0987:9876:8765:7654:6543:5432:3210", "0:987:9876:8765:7654:6543:5432:3210");

            // Compressing at the end
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("2001:db8:abcd:bcde:cdef:def1:ef12::", "2001:db8:abcd:bcde:cdef:def1:ef12:0");
            put("2001:db8:abcd:bcde:cdef:def1::", "2001:db8:abcd:bcde:cdef:def1::");
            put("2001:db8:abcd:bcde:cdef::", "2001:db8:abcd:bcde:cdef::");
            put("2001:db8:abcd:bcde::", "2001:db8:abcd:bcde::");
            put("2001:db8:abcd::", "2001:db8:abcd::");
            put("2001:1234::", "2001:1234::");
            put("2001::", "2001::");
            put("0::", "::");

            // Compressing in the middle
            put("1234:2345::7890", "1234:2345::7890");
            put("1234::2345:7890", "1234::2345:7890");
            put("1234:2345:3456::7890", "1234:2345:3456::7890");
            put("1234:2345::3456:7890", "1234:2345::3456:7890");
            put("1234::2345:3456:7890", "1234::2345:3456:7890");
            put("1234:2345:3456:4567::7890", "1234:2345:3456:4567::7890");
            put("1234:2345:3456::4567:7890", "1234:2345:3456::4567:7890");
            put("1234:2345::3456:4567:7890", "1234:2345::3456:4567:7890");
            put("1234::2345:3456:4567:7890", "1234::2345:3456:4567:7890");
            put("1234:2345:3456:4567:5678::7890", "1234:2345:3456:4567:5678::7890");
            put("1234:2345:3456:4567::5678:7890", "1234:2345:3456:4567::5678:7890");
            put("1234:2345:3456::4567:5678:7890", "1234:2345:3456::4567:5678:7890");
            put("1234:2345::3456:4567:5678:7890", "1234:2345::3456:4567:5678:7890");
            put("1234::2345:3456:4567:5678:7890", "1234::2345:3456:4567:5678:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234:2345:3456:4567:5678:6789::7890", "1234:2345:3456:4567:5678:6789:0:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234:2345:3456:4567:5678::6789:7890", "1234:2345:3456:4567:5678:0:6789:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234:2345:3456:4567::5678:6789:7890", "1234:2345:3456:4567:0:5678:6789:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234:2345:3456::4567:5678:6789:7890", "1234:2345:3456:0:4567:5678:6789:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234:2345::3456:4567:5678:6789:7890", "1234:2345:0:3456:4567:5678:6789:7890");
            // Note the compression is removed (rfc 5952 section 4.2.2)
            put("1234::2345:3456:4567:5678:6789:7890", "1234:0:2345:3456:4567:5678:6789:7890");

            // IPv4 mapped addresses
            put("::ffff:255.255.255.255", "::ffff:255.255.255.255");
            put("::ffff:0.0.0.0", "::ffff:0.0.0.0");
            put("::ffff:127.0.0.1", "::ffff:127.0.0.1");
            put("::ffff:1.2.3.4", "::ffff:1.2.3.4");
            put("::ffff:192.168.0.1", "::ffff:192.168.0.1");
        }
    };

    @Test
    public void testLocalhost() {
        assertNotNull(NetUtil.LOCALHOST);
    }

    @Test
    public void testLoopback() {
        assertNotNull(NetUtil.LOOPBACK_IF);
    }

    @Test
    public void testIsValidIpV4Address() {
        for (String host : validIpV4Hosts.keySet()) {
            assertTrue(NetUtil.isValidIpV4Address(host));
        }
        for (String host : invalidIpV4Hosts.keySet()) {
            assertFalse(NetUtil.isValidIpV4Address(host));
        }
    }

    @Test
    public void testIsValidIpV6Address() {
        for (String host : validIpV6Hosts.keySet()) {
            assertTrue(NetUtil.isValidIpV6Address(host));
        }
        for (String host : invalidIpV6Hosts.keySet()) {
            assertFalse(NetUtil.isValidIpV6Address(host));
        }
    }

    @Test
    public void testCreateByteArrayFromIpAddressString() {
        for (Entry<String, byte[]> stringEntry : validIpV4Hosts.entrySet()) {
            assertArrayEquals(stringEntry.getValue(), NetUtil.createByteArrayFromIpAddressString(stringEntry.getKey()));
        }
        for (Entry<String, byte[]> stringEntry : invalidIpV4Hosts.entrySet()) {
            assertArrayEquals(stringEntry.getValue(), NetUtil.createByteArrayFromIpAddressString(stringEntry.getKey()));
        }
        for (Entry<String, byte[]> stringEntry : validIpV6Hosts.entrySet()) {
            assertArrayEquals(stringEntry.getValue(), NetUtil.createByteArrayFromIpAddressString(stringEntry.getKey()));
        }
        for (Entry<String, byte[]> stringEntry : invalidIpV6Hosts.entrySet()) {
            assertArrayEquals(stringEntry.getValue(), NetUtil.createByteArrayFromIpAddressString(stringEntry.getKey()));
        }
    }

    @Test
    public void testIp6AddressToString() throws UnknownHostException {
        for (Entry<byte[], String> testEntry : ipv6ToAddressStrings.entrySet()) {
            assertEquals(testEntry.getValue(),
                    NetUtil.toAddressString(InetAddress.getByAddress(testEntry.getKey())));
        }
    }

    @Test
    public void testIp4AddressToString() throws UnknownHostException {
        for (Entry<String, byte[]> stringEntry : validIpV4Hosts.entrySet()) {
            assertEquals(stringEntry.getKey(),
                    NetUtil.toAddressString(InetAddress.getByAddress(stringEntry.getValue())));
        }
    }

    @Test
    public void testIpv4MappedIp6GetByName() {
        for (Entry<String, String> testEntry : ipv4MappedToIPv6AddressStrings.entrySet()) {
            assertEquals(testEntry.getValue(),
                            NetUtil.toAddressString(NetUtil.getByName(testEntry.getKey(), true), true));
        }
    }

    @Test
    public void testinvalidIpv4MappedIp6GetByName() {
        for (String testEntry : invalidIpV4Hosts.keySet()) {
            assertNull(NetUtil.getByName(testEntry, true));
        }

        for (String testEntry : invalidIpV6Hosts.keySet()) {
            assertNull(NetUtil.getByName(testEntry, true));
        }
    }
}
