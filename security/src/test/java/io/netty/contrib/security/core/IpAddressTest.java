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
package io.netty.contrib.security.core;

import io.netty.security.core.Address;
import io.netty.security.core.IpAddress;
import io.netty.security.core.StaticIpAddress;
import io.netty.security.core.Util;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressTest {

    @Test
    void ofString() {
        assertEquals(Address.Version.v4, IpAddress.of("10.10.10.10").version());
        assertEquals(Address.Version.v6, IpAddress.of("0000:0000:0000:0000:0000:0000:0000:0001").version());

        assertEquals(Util.prefixToSubnetMaskV4(32), IpAddress.of("10.10.10.10").v4SubnetMaskAsInt());
        assertEquals(Util.prefixToSubnetMaskV6(128),
                IpAddress.of("0000:0000:0000:0000:0000:0000:0000:0001").v6SubnetMaskAsBigInt());
    }

    @Test
    void ofStringWithMask() {
        IpAddress v4 = IpAddress.of("10.10.10.10", 16);
        IpAddress v6 = IpAddress.of("0000:0000:0000:0000:0000:0000:0000:0001", 64);
        assertEquals(Address.Version.v4, v4.version());
        assertEquals(Address.Version.v6, v6.version());

        assertEquals(Util.prefixToSubnetMaskV4(16), v4.v4SubnetMaskAsInt());
        assertEquals(Util.prefixToSubnetMaskV6(64), v6.v6SubnetMaskAsBigInt());
    }

    @Test
    void ofByteArray() throws Exception {
        InetAddress v4Address = InetAddress.getByName("10.10.10.10");
        InetAddress v6Address = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");

        IpAddress v4 = IpAddress.of(v4Address.getAddress());
        IpAddress v6 = IpAddress.of(v6Address.getAddress());

        assertEquals(v4Address, v4.address());
        assertEquals(v6Address, v6.address());
    }

    @Test
    void ofByteArrayWithMask() throws Exception {
        InetAddress v4Address = InetAddress.getByName("10.10.10.10");
        InetAddress v6Address = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");

        IpAddress v4 = IpAddress.of(v4Address.getAddress(), 16);
        IpAddress v6 = IpAddress.of(v6Address.getAddress(), 64);

        assertEquals(v4Address, v4.address());
        assertEquals(v6Address, v6.address());
    }

    @Test
    void fromRange() {
        List<IpAddress> v4 = IpAddress.from("192.168.1.0", "192.168.1.25");
        List<IpAddress> v6 = IpAddress.from("2001:4860:4860::8888", "2001:4860:4860::8844");

        assertEquals(3, v4.size());
        assertEquals(6, v6.size());
    }

    @Test
    void address() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("192.168.1.100");
        Address address = IpAddress.of(inetAddress.getAddress());
        assertEquals(inetAddress, address.address());

        inetAddress = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");
        address = IpAddress.of(inetAddress.getAddress());
        assertEquals(inetAddress, address.address());
    }

    @Test
    void networkAddressV4() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("192.168.1.100");
        Address address = IpAddress.of(inetAddress.getAddress());
        assertEquals(NetUtil.ipv4AddressToInt((Inet4Address) inetAddress), address.v4AddressAsInt());
    }

    @Test
    void subnetMaskV4() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("192.168.1.100");
        Address address = IpAddress.of(inetAddress);
        assertEquals(Util.prefixToSubnetMaskV4(32), address.v4SubnetMaskAsInt());
    }

    @Test
    void networkAddressV6() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");
        Address address = StaticIpAddress.of(inetAddress);
        assertEquals(new BigInteger(inetAddress.getAddress()), address.v6NetworkAddressAsBigInt());
    }

    @Test
    void subnetMaskV6() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");
        Address address = StaticIpAddress.of(inetAddress);
        assertEquals(Util.prefixToSubnetMaskV6(128), address.v6SubnetMaskAsBigInt());
    }

    @Test
    void version() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("192.168.1.100");
        Address address = StaticIpAddress.of(inetAddress);
        assertEquals(Address.Version.v4, address.version());

        inetAddress = InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001");
        address = StaticIpAddress.of(inetAddress);
        assertEquals(Address.Version.v6, address.version());
    }
}
