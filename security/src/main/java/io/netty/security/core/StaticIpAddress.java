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

import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

/**
 * This is a simple implementation handle of {@link Address} which cache
 * 32-bit {@link Integer} IPv4 address or 128-bit {@link BigInteger} IPv6 Address.
 */
public final class StaticIpAddress implements Address {
    private static final int subnetMaskV4 = Util.prefixToSubnetMaskV4(32);
    private static final BigInteger subnetMaskV6 = Util.prefixToSubnetMaskV6(128);

    private final InetAddress address;
    private int addressV4 = -1;
    private BigInteger addressV6;

    private StaticIpAddress(InetAddress address) {
        this.address = ObjectUtil.checkNotNull(address, "InetAddress");

        if (address instanceof Inet4Address) {
            addressV4 = NetUtil.ipv4AddressToInt((Inet4Address) address);
        } else if (address instanceof Inet6Address) {
            addressV6 = new BigInteger(address.getAddress());
        } else {
            throw new IllegalArgumentException("Invalid InetAddress");
        }
    }

    /**
     * Create a new {@link StaticIpAddress} instance with specified {@link InetAddress}
     *
     * @param address {@link InetAddress} to use
     * @return {@link StaticIpAddress} instance
     */
    public static StaticIpAddress of(InetAddress address) {
        return new StaticIpAddress(address);
    }

    /**
     * Create a new {@link StaticIpAddress} instance with specified {@link String} IP address
     *
     * @param address {@link String} IP address
     * @return {@link StaticIpAddress} instance
     */
    public static StaticIpAddress of(String address) throws UnknownHostException {
        ObjectUtil.checkNotNull(address, "Address");
        return new StaticIpAddress(InetAddress.getByName(address));
    }

    /**
     * Create a new {@link StaticIpAddress} instance with specified {@link Byte} array IP address
     *
     * @param address {@link Byte} array IP address
     * @return {@link StaticIpAddress} instance
     */
    public static StaticIpAddress of(byte[] address) throws UnknownHostException {
        ObjectUtil.checkNotNull(address, "Address");
        return new StaticIpAddress(InetAddress.getByAddress(address));
    }

    @Override
    public InetAddress address() {
        return address;
    }

    @Override
    public int v4AddressAsInt() {
        return addressV4;
    }

    @Override
    public int v4SubnetMaskAsInt() {
        return subnetMaskV4;
    }

    @Override
    public BigInteger v6NetworkAddressAsBigInt() {
        return addressV6;
    }

    @Override
    public BigInteger v6SubnetMaskAsBigInt() {
        return subnetMaskV6;
    }

    @Override
    public Version version() {
        return address instanceof Inet4Address ? Version.v4 : Version.v6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StaticIpAddress that = (StaticIpAddress) o;

        // Hashcode matching is enough because hashcode is generated
        // using InetAddress instance only.
        return hashCode() == that.hashCode();
    }

    @Override
    public int compareTo(Address address) {
        if (address.version() == Version.v4) {
            return compareIntegers(address.v4AddressAsInt() & v4SubnetMaskAsInt(), v4AddressAsInt());
        } else if (address.version() == Version.v6) {
            return v6NetworkAddressAsBigInt().and(v6SubnetMaskAsBigInt()).compareTo(address.v6NetworkAddressAsBigInt());
        } else {
            // Unsupported IP version. So let's return -1
            // because we have no idea at all about it.
            return -1;
        }
    }

    @Override
    public int hashCode() {
        return hash(address);
    }

    @Override
    public String toString() {
        return "StaticIpAddress{" +
                "address=" + address +
                ", addressV4=" + addressV4 +
                ", addressV6=" + addressV6 +
                '}';
    }
}
