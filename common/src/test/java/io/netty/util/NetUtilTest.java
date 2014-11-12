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

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.*;

public class NetUtilTest {

    private static final class TestMap extends HashMap<String, String> {
        private static final long serialVersionUID = -298642816998608473L;

        TestMap(String... values) {
            for (int i = 0; i < values.length; i += 2) {
                String key = values[i];
                String value = values[i + 1];
                put(key, value);
            }
        }
    }

    private static final Map<String, String> validIpV4Hosts = new TestMap(
            "192.168.1.0",    "c0a80100",
            "10.255.255.254", "0afffffe",
            "172.18.5.4",     "ac120504",
            "0.0.0.0",        "00000000",
            "127.0.0.1",      "7f000001");

    private static final Map<String, String> invalidIpV4Hosts = new TestMap(
            "1.256.3.4",     null,
            "256.0.0.1",     null,
            "1.1.1.1.1",     null,
            "x.255.255.255", null,
            "0.1:0.0",       null,
            "0.1.0.0:",      null,
            "127.0.0.",      null,
            "1.2..4",        null,
            "192.0.1",       null,
            "192.0.1.1.1",   null,
            "192.0.1.a",     null,
            "19a.0.1.1",     null,
            "a.0.1.1",       null,
            ".0.1.1",        null,
            "...",           null);

    private static final Map<String, String> validIpV6Hosts = new TestMap(
            "::ffff:5.6.7.8",                            "00000000000000000000ffff05060708",
            "fdf8:f53b:82e4::53",                        "fdf8f53b82e400000000000000000053",
            "fe80::200:5aee:feaa:20a2",                  "fe8000000000000002005aeefeaa20a2",
            "2001::1",                                   "20010000000000000000000000000001",
            "2001:0000:4136:e378:8000:63bf:3fff:fdd2",   "200100004136e378800063bf3ffffdd2",
            "2001:0002:6c::430",                         "20010002006c00000000000000000430",
            "2001:10:240:ab::a",                         "20010010024000ab000000000000000a",
            "2002:cb0a:3cdd:1::1",                       "2002cb0a3cdd00010000000000000001",
            "2001:db8:8:4::2",                           "20010db8000800040000000000000002",
            "ff01:0:0:0:0:0:0:2",                        "ff010000000000000000000000000002",
            "[fdf8:f53b:82e4::53]",                      "fdf8f53b82e400000000000000000053",
            "[fe80::200:5aee:feaa:20a2]",                "fe8000000000000002005aeefeaa20a2",
            "[2001::1]",                                 "20010000000000000000000000000001",
            "[2001:0000:4136:e378:8000:63bf:3fff:fdd2]", "200100004136e378800063bf3ffffdd2",
            "0:1:2:3:4:5:6:789a",                        "0000000100020003000400050006789a",
            "0:1:2:3::f",                                "0000000100020003000000000000000f",
            "0:0:0:0:0:0:10.0.0.1",                      "0000000000000000000000000a000001",
            "::ffff:192.168.0.1",                        "00000000000000000000ffffc0a80001",
            // Test if various interface names after the percent sign are recognized.
            "[::1%1]",                                   "00000000000000000000000000000001",
            "[::1%eth0]",                                "00000000000000000000000000000001",
            "[::1%%]",                                   "00000000000000000000000000000001",
            "::1%1",                                     "00000000000000000000000000000001",
            "::1%eth0",                                  "00000000000000000000000000000001",
            "::1%%",                                     "00000000000000000000000000000001");

    private static final Map<String, String> invalidIpV6Hosts = new TestMap(
            // Test method with garbage.
            "Obvious Garbage",          null,
            // Test method with preferred style, too many :
            "0:1:2:3:4:5:6:7:8",        null,
            // Test method with preferred style, not enough :
            "0:1:2:3:4:5:6",            null,
            // Test method with preferred style, bad digits.
            "0:1:2:3:4:5:6:x",          null,
            // Test method with preferred style, adjacent :
            "0:1:2:3:4:5:6::7",         null,
            // Too many : separators trailing
            "0:1:2:3:4:5:6:7::",        null,
            // Too many : separators leading
            "::0:1:2:3:4:5:6:7",        null,
            // Too many : separators trailing
            "1:2:3:4:5:6:7:",           null,
            // Too many : separators leading
            ":1:2:3:4:5:6:7",           null,
            // Too many : separators leading 0
            "0::1:2:3:4:5:6:7",         null,
            // Test method with preferred style, too many digits.
            "0:1:2:3:4:5:6:789abcdef",  null,
            // Test method with compressed style, bad digits.
            "0:1:2:3::x",               null,
            // Test method with compressed style, too many adjacent :
            "0:1:2:::3",                null,
            // Test method with compressed style, too many digits.
            "0:1:2:3::abcde",           null,
            // Test method with preferred style, too many :
            "0:1:2:3:4:5:6:7:8",        null,
            // Test method with compressed style, not enough :
            "0:1",                      null,
            // Test method with ipv4 style, bad ipv6 digits.
            "0:0:0:0:0:x:10.0.0.1",     null,
            // Test method with ipv4 style, bad ipv4 digits.
            "0:0:0:0:0:0:10.0.0.x",     null,
            // Test method with ipv4 style, adjacent :
            "0:0:0:0:0::0:10.0.0.1",    null,
            // Test method with ipv4 style, too many ipv6 digits.
            "0:0:0:0:0:00000:10.0.0.1", null,
            // Test method with ipv4 style, too many :
            "0:0:0:0:0:0:0:10.0.0.1",   null,
            // Test method with ipv4 style, not enough :
            "0:0:0:0:0:10.0.0.1",       null,
            // Test method with ipv4 style, too many .
            "0:0:0:0:0:0:10.0.0.0.1",   null,
            // Test method with ipv4 style, not enough .
            "0:0:0:0:0:0:10.0.1",       null,
            // Test method with ipv4 style, adjacent .
            "0:0:0:0:0:0:10..0.0.1",    null,
            // Test method with ipv4 style, leading .
            "0:0:0:0:0:0:.0.0.1",       null,
            // Test method with ipv4 style, leading .
            "0:0:0:0:0:0:.10.0.0.1",    null,
            // Test method with ipv4 style, trailing .
            "0:0:0:0:0:0:10.0.0.",      null,
            // Test method with ipv4 style, trailing .
            "0:0:0:0:0:0:10.0.0.1.",    null,
            // Test method with compressed ipv4 style, bad ipv6 digits.
            "::fffx:192.168.0.1",       null,
            // Test method with compressed ipv4 style, bad ipv4 digits.
            "::ffff:192.168.0.x",       null,
            // Test method with compressed ipv4 style, too many adjacent :
            ":::ffff:192.168.0.1",      null,
            // Test method with compressed ipv4 style, too many ipv6 digits.
            "::fffff:192.168.0.1",      null,
            // Test method with compressed ipv4 style, too many ipv4 digits.
            "::ffff:1923.168.0.1",      null,
            // Test method with compressed ipv4 style, not enough :
            ":ffff:192.168.0.1",        null,
            // Test method with compressed ipv4 style, too many .
            "::ffff:192.168.0.1.2",     null,
            // Test method with compressed ipv4 style, not enough .
            "::ffff:192.168.0",         null,
            // Test method with compressed ipv4 style, adjacent .
            "::ffff:192.168..0.1",      null,
            // Test method, garbage.
            "absolute, and utter garbage", null,
            // Test method, bad ipv6 digits.
            "x:0:0:0:0:0:10.0.0.1",     null,
            // Test method, bad ipv4 digits.
            "0:0:0:0:0:0:x.0.0.1",      null,
            // Test method, too many ipv6 digits.
            "00000:0:0:0:0:0:10.0.0.1", null,
            // Test method, too many ipv4 digits.
            "0:0:0:0:0:0:10.0.0.1000",  null,
            // Test method, too many :
            "0:0:0:0:0:0:0:10.0.0.1",   null,
            // Test method, not enough :
            "0:0:0:0:0:10.0.0.1",       null,
            // Test method, out of order trailing :
            "0:0:0:0:0:10.0.0.1:",      null,
            // Test method, out of order leading :
            ":0:0:0:0:0:10.0.0.1",      null,
            // Test method, out of order leading :
            "0:0:0:0::10.0.0.1:",       null,
            // Test method, out of order trailing :
            ":0:0:0:0::10.0.0.1",       null,
            // Test method, too many .
            "0:0:0:0:0:0:10.0.0.0.1",   null,
            // Test method, not enough .
            "0:0:0:0:0:0:10.0.1",       null,
            // Test method, adjacent .
            "0:0:0:0:0:0:10.0.0..1",    null,
            // Double compression symbol
            "::0::",                    null,
            // Empty contents
            "",                         null,
            // Trailing : (max number of : = 8)
            "2001:0:4136:e378:8000:63bf:3fff:fdd2:", null,
            // Leading : (max number of : = 8)
            ":aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222", null,
            // Invalid character
            "1234:2345:3456:4567:5678:6789::X890", null,
            // Trailing . in IPv4
            "::ffff:255.255.255.255.",  null,
            // To many characters in IPv4
            "::ffff:0.0.1111.0",        null,
            // Test method, adjacent .
            "::ffff:0.0..0",            null,
            // Not enough IPv4 entries trailing .
            "::ffff:127.0.0.",          null,
            // Not enough IPv4 entries no trailing .
            "::ffff:1.2.4",             null,
            // Extra IPv4 entry
            "::ffff:192.168.0.1.255",   null,
            // Not enough IPv6 content
            ":ffff:192.168.0.1.255",    null,
            // Intermixed IPv4 and IPv6 symbols
            "::ffff:255.255:255.255.",  null);

    private static final Map<byte[], String> ipv6ToAddressStrings = new HashMap<byte[], String>() {
        private static final long serialVersionUID = 2999763170377573184L;
        {
            // From the RFC 5952 http://tools.ietf.org/html/rfc5952#section-4
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 1
                },
                "2001:db8::1");
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 2, 0, 1
                },
                "2001:db8::2:1");
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 1,
                        0, 1, 0, 1,
                        0, 1, 0, 1
                },
                "2001:db8:0:1:1:1:1:1");

            // Other examples
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 2, 0, 1
                },
                "2001:db8::2:1");
            put(new byte[] {
                        32, 1, 0, 0,
                        0, 0, 0, 1,
                        0, 0, 0, 0,
                        0, 0, 0, 1
                },
                "2001:0:0:1::1");
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 0, 1
                },
                "2001:db8::1:0:0:1");
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 0, 0
                },
                "2001:db8:0:0:1::");
            put(new byte[] {
                        32, 1, 13, -72,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 2, 0, 0
                },
                "2001:db8::2:0");
            put(new byte[] {
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 1
                },
                "::1");
            put(new byte[] {
                        0, 0, 0, 0,
                        0, 0, 0, 1,
                        0, 0, 0, 0,
                        0, 0, 0, 1
                },
                "::1:0:0:0:1");
            put(new byte[] {
                        0, 0, 0, 0,
                        1, 0, 0, 1,
                        0, 0, 0, 0,
                        1, 0, 0, 0
                },
                "::100:1:0:0:100:0");
            put(new byte[] {
                        32, 1, 0, 0,
                        65, 54, -29, 120,
                        -128, 0, 99, -65,
                        63, -1, -3, -46
                },
                "2001:0:4136:e378:8000:63bf:3fff:fdd2");
            put(new byte[] {
                        -86, -86, -69, -69,
                        -52, -52, -35, -35,
                        -18, -18, -1, -1,
                        17, 17, 34, 34
                },
                "aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222");
            put(new byte[] {
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0
                },
                "::");
        }
    };

    private static final Map<String, String> ipv4MappedToIPv6AddressStrings = new TestMap(
            // IPv4 addresses
            "255.255.255.255", "::ffff:255.255.255.255",
            "0.0.0.0", "::ffff:0.0.0.0",
            "127.0.0.1", "::ffff:127.0.0.1",
            "1.2.3.4", "::ffff:1.2.3.4",
            "192.168.0.1", "::ffff:192.168.0.1",

            // IPv6 addresses
            // Fully specified
            "2001:0:4136:e378:8000:63bf:3fff:fdd2", "2001:0:4136:e378:8000:63bf:3fff:fdd2",
            "aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222", "aaaa:bbbb:cccc:dddd:eeee:ffff:1111:2222",
            "0:0:0:0:0:0:0:0", "::",
            "0:0:0:0:0:0:0:1", "::1",

            // Compressing at the beginning
            "::1:0:0:0:1", "::1:0:0:0:1",
            "::1:ffff:ffff", "::1:ffff:ffff",
            "::", "::",
            "::1", "::1",
            "::ffff", "::ffff",
            "::ffff:0", "::ffff:0",
            "::ffff:ffff", "::ffff:ffff",
            "::0987:9876:8765", "::987:9876:8765",
            "::0987:9876:8765:7654", "::987:9876:8765:7654",
            "::0987:9876:8765:7654:6543", "::987:9876:8765:7654:6543",
            "::0987:9876:8765:7654:6543:5432", "::987:9876:8765:7654:6543:5432",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "::0987:9876:8765:7654:6543:5432:3210", "0:987:9876:8765:7654:6543:5432:3210",

            // Compressing at the end
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "2001:db8:abcd:bcde:cdef:def1:ef12::", "2001:db8:abcd:bcde:cdef:def1:ef12:0",
            "2001:db8:abcd:bcde:cdef:def1::", "2001:db8:abcd:bcde:cdef:def1::",
            "2001:db8:abcd:bcde:cdef::", "2001:db8:abcd:bcde:cdef::",
            "2001:db8:abcd:bcde::", "2001:db8:abcd:bcde::",
            "2001:db8:abcd::", "2001:db8:abcd::",
            "2001:1234::", "2001:1234::",
            "2001::", "2001::",
            "0::", "::",

            // Compressing in the middle
            "1234:2345::7890", "1234:2345::7890",
            "1234::2345:7890", "1234::2345:7890",
            "1234:2345:3456::7890", "1234:2345:3456::7890",
            "1234:2345::3456:7890", "1234:2345::3456:7890",
            "1234::2345:3456:7890", "1234::2345:3456:7890",
            "1234:2345:3456:4567::7890", "1234:2345:3456:4567::7890",
            "1234:2345:3456::4567:7890", "1234:2345:3456::4567:7890",
            "1234:2345::3456:4567:7890", "1234:2345::3456:4567:7890",
            "1234::2345:3456:4567:7890", "1234::2345:3456:4567:7890",
            "1234:2345:3456:4567:5678::7890", "1234:2345:3456:4567:5678::7890",
            "1234:2345:3456:4567::5678:7890", "1234:2345:3456:4567::5678:7890",
            "1234:2345:3456::4567:5678:7890", "1234:2345:3456::4567:5678:7890",
            "1234:2345::3456:4567:5678:7890", "1234:2345::3456:4567:5678:7890",
            "1234::2345:3456:4567:5678:7890", "1234::2345:3456:4567:5678:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234:2345:3456:4567:5678:6789::7890", "1234:2345:3456:4567:5678:6789:0:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234:2345:3456:4567:5678::6789:7890", "1234:2345:3456:4567:5678:0:6789:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234:2345:3456:4567::5678:6789:7890", "1234:2345:3456:4567:0:5678:6789:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234:2345:3456::4567:5678:6789:7890", "1234:2345:3456:0:4567:5678:6789:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234:2345::3456:4567:5678:6789:7890", "1234:2345:0:3456:4567:5678:6789:7890",
            // Note the compression is removed (rfc 5952 section 4.2.2)
            "1234::2345:3456:4567:5678:6789:7890", "1234:0:2345:3456:4567:5678:6789:7890",

            // IPv4 mapped addresses
            "::ffff:255.255.255.255", "::ffff:255.255.255.255",
            "::ffff:0.0.0.0", "::ffff:0.0.0.0",
            "::ffff:127.0.0.1", "::ffff:127.0.0.1",
            "::ffff:1.2.3.4", "::ffff:1.2.3.4",
            "::ffff:192.168.0.1", "::ffff:192.168.0.1");

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
        for (Entry<String, String> e : validIpV4Hosts.entrySet()) {
            assertHexDumpEquals(e.getValue(), NetUtil.createByteArrayFromIpAddressString(e.getKey()));
        }
        for (Entry<String, String> e : invalidIpV4Hosts.entrySet()) {
            assertHexDumpEquals(e.getValue(), NetUtil.createByteArrayFromIpAddressString(e.getKey()));
        }
        for (Entry<String, String> e : validIpV6Hosts.entrySet()) {
            assertHexDumpEquals(e.getValue(), NetUtil.createByteArrayFromIpAddressString(e.getKey()));
        }
        for (Entry<String, String> e : invalidIpV6Hosts.entrySet()) {
            assertHexDumpEquals(e.getValue(), NetUtil.createByteArrayFromIpAddressString(e.getKey()));
        }
    }

    @Test
    public void testIp6AddressToString() throws UnknownHostException {
        for (Entry<byte[], String> testEntry : ipv6ToAddressStrings.entrySet()) {
            assertEquals(testEntry.getValue(), NetUtil.toAddressString(InetAddress.getByAddress(testEntry.getKey())));
        }
    }

    @Test
    public void testIp4AddressToString() throws UnknownHostException {
        for (Entry<String, String> e : validIpV4Hosts.entrySet()) {
            assertEquals(e.getKey(), NetUtil.toAddressString(InetAddress.getByAddress(unhex(e.getValue()))));
        }
    }

    @Test
    public void testIpv4MappedIp6GetByName() {
        for (Entry<String, String> testEntry : ipv4MappedToIPv6AddressStrings.entrySet()) {
            assertEquals(
                    testEntry.getValue(),
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

    private static void assertHexDumpEquals(String expected, byte[] actual) {
        assertEquals(expected, hex(actual));
    }

    private static String hex(byte[] value) {
        if (value == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder(value.length << 1);
        for (byte b: value) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                buf.append('0');
            }
            buf.append(hex);
        }
        return buf.toString();
    }

    private static byte[] unhex(String value) {
        if (value == null) {
            return null;
        }

        byte[] buf = new byte[value.length() >>> 1];
        for (int i = 0; i < buf.length; i ++) {
            buf[i] = (byte) Integer.parseInt(value.substring(i << 1, i + 1 << 1), 16);
        }

        return buf;
    }
}
