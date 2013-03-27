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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.*;

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
            // Test method, too many .
            put("0:0:0:0:0:0:10.0.0.0.1", null);
            // Test method, not enough .
            put("0:0:0:0:0:0:10.0.1", null);
            // Test method, adjacent .
            put("0:0:0:0:0:0:10.0.0..1", null);
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
}
