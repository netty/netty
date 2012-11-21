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

import static org.junit.Assert.*;

public class NetUtilTest {
    private final static String[] validIpV4Hosts = {
            "192.168.1.0",
            "10.255.255.254",
            "172.18.5.4",
            "0.0.0.0",
            "127.0.0.1"
    };
    private final static String[] invalidIpV4Hosts = {
            "1.256.3.4",
            "256.0.0.1",
            "1.1.1.1.1"
    };
    private final static String[] validIpV6Hosts = {
            // for i in `cat chkip.t | grep -B2 CODE | grep ::ipv6 | sed 's/.*(//' | sed 's/);//'`; do echo $i"," // `cat chkip.t| grep -B1 $i| head -n1`; done
            "::ffff:5.6.7.8",
            "fdf8:f53b:82e4::53",
            "fe80::200:5aee:feaa:20a2",
            "2001::1",
            "2001:0000:4136:e378:8000:63bf:3fff:fdd2",
            "2001:0002:6c::430",
            "2001:10:240:ab::a",
            "2002:cb0a:3cdd:1::1",
            "2001:db8:8:4::2",
            "ff01:0:0:0:0:0:0:2",
            "[fdf8:f53b:82e4::53]",
            "[fe80::200:5aee:feaa:20a2]",
            "[2001::1]",
            "[2001:0000:4136:e378:8000:63bf:3fff:fdd2]",
            "0:1:2:3:4:5:6:789a", // Test isValidIpV6Address with preferred style.
            "0:1:2:3::f", // Test isValidIpV6Address with compressed style.
            "0:0:0:0:0:0:10.0.0.1", // Test isValidIpV6Address with ipv4 style.
            "::ffff:192.168.0.1", // Test isValidIpV6Address with compressed ipv4 style.
    };
    private final static String[] invalidIpV6Hosts = {
            // for i in `cat chkip.t | grep -B2 defined | grep ::ipv6 | sed 's/.*(//' | sed 's/);//'`; do echo $i"," // `cat chkip.t| grep -B1 $i| head -n1`; done
            "Obvious Garbage", // Test isValidIpV6Address with garbage.
            "0:1:2:3:4:5:6:7:8", // Test isValidIpV6Address with preferred style, too many :
            "0:1:2:3:4:5:6", // Test isValidIpV6Address with preferred style, not enough :
            "0:1:2:3:4:5:6:x", // Test isValidIpV6Address with preferred style, bad digits.
            "0:1:2:3:4:5:6::7", // Test isValidIpV6Address with preferred style, adjacent :
            "0:1:2:3:4:5:6:789abcdef", // Test isValidIpV6Address with preferred style, too many digits.
            "0:1:2:3::x", // Test isValidIpV6Address with compressed style, bad digits.
            "0:1:2:::3", // Test isValidIpV6Address with compressed style, too many adjacent :
            "0:1:2:3::abcde", // Test isValidIpV6Address with compressed style, too many digits.
            "0:1:2:3:4:5:6:7:8", // Test isValidIpV6Address with preferred style, too many :
            "0:1", // Test isValidIpV6Address with compressed style, not enough :
            "0:0:0:0:0:x:10.0.0.1", // Test isValidIpV6Address with ipv4 style, bad ipv6 digits.
            "0:0:0:0:0:0:10.0.0.x", // Test isValidIpV6Address with ipv4 style, bad ipv4 digits.
//            "0:0:0:0:0::0:10.0.0.1", // Test isValidIpV6Address with ipv4 style, adjacent :
            "0:0:0:0:0:00000:10.0.0.1", // Test isValidIpV6Address with ipv4 style, too many ipv6 digits.
            "0:0:0:0:0:0:0:10.0.0.1", // Test isValidIpV6Address with ipv4 style, too many :
            "0:0:0:0:0:10.0.0.1", // Test isValidIpV6Address with ipv4 style, not enough :
            "0:0:0:0:0:0:10.0.0.0.1", // Test isValidIpV6Address with ipv4 style, too many .
            "0:0:0:0:0:0:10.0.1", // Test isValidIpV6Address with ipv4 style, not enough .
            "0:0:0:0:0:0:10..0.0.1", // Test isValidIpV6Address with ipv4 style, adjacent .
            "::fffx:192.168.0.1", // Test isValidIpV6Address with compressed ipv4 style, bad ipv6 digits.
            "::ffff:192.168.0.x", // Test isValidIpV6Address with compressed ipv4 style, bad ipv4 digits.
            ":::ffff:192.168.0.1", // Test isValidIpV6Address with compressed ipv4 style, too many adjacent :
            "::fffff:192.168.0.1", // Test isValidIpV6Address with compressed ipv4 style, too many ipv6 digits.
            "::ffff:1923.168.0.1", // Test isValidIpV6Address with compressed ipv4 style, too many ipv4 digits.
            ":ffff:192.168.0.1", // Test isValidIpV6Address with compressed ipv4 style, not enough :
            "::ffff:192.168.0.1.2", // Test isValidIpV6Address with compressed ipv4 style, too many .
            "::ffff:192.168.0", // Test isValidIpV6Address with compressed ipv4 style, not enough .
            "::ffff:192.168..0.1", // Test isValidIpV6Address with compressed ipv4 style, adjacent .
            // for i in `cat  ipv4.t  | grep -B3 invalid | grep ::ipv6 | sed 's/.*(//' | sed 's/);.*//'`; do echo $i"," // ` cat ipv4.t | grep -B1 $i | head -n1`; done
            "absolute, and utter garbage", // Test ipv6_parse_ipv4, garbage.
            "x:0:0:0:0:0:10.0.0.1", // Test ipv6_parse_ipv4, bad ipv6 digits.
            "0:0:0:0:0:0:x.0.0.1", // Test ipv6_parse_ipv4, bad ipv4 digits.
//            "0:0:0:0:0::0:10.0.0.1", // Test ipv6_parse_ipv4, adjacent :
            "00000:0:0:0:0:0:10.0.0.1", // Test ipv6_parse_ipv4, too many ipv6 digits.
            "0:0:0:0:0:0:10.0.0.1000", // Test ipv6_parse_ipv4, too many ipv4 digits.
            "0:0:0:0:0:0:0:10.0.0.1", // Test ipv6_parse_ipv4, too many :
            "0:0:0:0:0:10.0.0.1", // Test ipv6_parse_ipv4, not enough :
            "0:0:0:0:0:0:10.0.0.0.1", // Test ipv6_parse_ipv4, too many .
            "0:0:0:0:0:0:10.0.1", // Test ipv6_parse_ipv4, not enough .
            "0:0:0:0:0:0:10.0.0..1", // Test ipv6_parse_ipv4, adjacent .
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
        for (String host : validIpV4Hosts) {
            assertTrue(NetUtil.isValidIpV4Address(host));
        }
        for (String host : invalidIpV4Hosts) {
            assertFalse(NetUtil.isValidIpV4Address(host));
        }
    }


    @Test
    public void testIsValidIpV6Address() {
        for (String host : validIpV6Hosts) {
            assertTrue(NetUtil.isValidIpV6Address(host));
        }
        for (String host : invalidIpV6Hosts) {
            assertFalse(NetUtil.isValidIpV6Address(host));
        }
    }

    @Test
    public void testCreateByteArrayFromIpAddressString(){
        for (String host: validIpV4Hosts){
            assertNotNull(NetUtil.createByteArrayFromIpAddressString(host));
        }
        for (String host: invalidIpV4Hosts){
            assertNull(NetUtil.createByteArrayFromIpAddressString(host));
        }
        for (String host: validIpV6Hosts){
            assertNotNull(NetUtil.createByteArrayFromIpAddressString(host));
        }
        for (String host: invalidIpV4Hosts){
            assertNull(NetUtil.createByteArrayFromIpAddressString(host));
        }
    }
}
