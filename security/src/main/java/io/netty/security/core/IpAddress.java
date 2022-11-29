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

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.format.IPAddressRange;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

/**
 * {@link IpAddress} contains IPv4 or IPv6 address and Subnet Mask of them.
 */
public final class IpAddress implements Address {
    private final InetAddress inetAddress;
    private final Version version;
    private int v4NetworkAddress = -1;
    private int v4SubnetMask = -1;
    private BigInteger v6NetworkAddress;
    private BigInteger v6SubnetMask;

    private IpAddress(InetAddress inetAddress, int subnetMask) {
        this.inetAddress = ObjectUtil.checkNotNull(inetAddress, "InetAddress");

        if (inetAddress instanceof Inet4Address) {
            version = Version.v4;
            v4SubnetMask = Util.prefixToSubnetMaskV4(ObjectUtil.checkInRange(subnetMask, 0, 32, "SubnetMask"));
            v4NetworkAddress = NetUtil.ipv4AddressToInt((Inet4Address) inetAddress) & v4SubnetMask;
        } else if (inetAddress instanceof Inet6Address) {
            version = Version.v6;
            v6SubnetMask = Util.prefixToSubnetMaskV6(ObjectUtil.checkInRange(subnetMask, 0, 128, "SubnetMask"));
            v6NetworkAddress = new BigInteger(inetAddress.getAddress()).add(v6SubnetMask);
        } else {
            throw new IllegalArgumentException("Invalid InetAddress");
        }
    }

    /**
     * Create a new {@link IpAddress} instance with /32 or /128 subnet mask
     *
     * @param ipAddress IP address
     * @return {@link IpAddress} instance
     * @throws AssertionError If IP address is invalid
     */
    public static IpAddress of(String ipAddress) {
        InetAddress address = NetUtil.createInetAddressFromIpAddressString(ipAddress);
        assert address != null : "Invalid IP Address"; // Guard against invalid IP address
        return new IpAddress(address, address instanceof Inet4Address ? 32 : 128);
    }

    /**
     * Create a new {@link IpAddress} instance with /32 or /128 subnet mask
     *
     * @param inetAddress InetAddress
     * @return {@link IpAddress} instance
     * @throws AssertionError If IP address is invalid
     */
    public static IpAddress of(InetAddress inetAddress) {
        InetAddress address = NetUtil.createInetAddressFromIpAddressString(inetAddress.getHostAddress());
        assert address != null : "Invalid IP Address"; // Guard against invalid IP address
        return new IpAddress(address, address instanceof Inet4Address ? 32 : 128);
    }

    /**
     * Create a new {@link IpAddress} instance with specified subnet mask
     *
     * @param ipAddress  IP address
     * @param subnetMask Subnet mask
     * @return {@link IpAddress} instance
     * @throws AssertionError If IP address is invalid
     */
    public static IpAddress of(String ipAddress, int subnetMask) {
        InetAddress inetAddress = NetUtil.createInetAddressFromIpAddressString(ipAddress);
        assert inetAddress != null : "Invalid IP Address"; // Guard against invalid IP address
        return new IpAddress(inetAddress, subnetMask);
    }

    /**
     * Create a new {@link IpAddress} instance with specified subnet mask
     *
     * @param inetAddress InetAddress
     * @param subnetMask  Subnet mask
     * @return {@link IpAddress} instance
     * @throws AssertionError If IP address is invalid
     */
    public static IpAddress of(InetAddress inetAddress, int subnetMask) {
        InetAddress address = NetUtil.createInetAddressFromIpAddressString(inetAddress.getHostAddress());
        assert address != null : "Invalid IP Address"; // Guard against invalid IP address
        return new IpAddress(address, subnetMask);
    }

    /**
     * Create a new {@link IpAddress} instance with /32 or /128 subnet mask
     *
     * @param ipAddress IP address byte array
     * @return {@link IpAddress} instance
     * @throws UnknownHostException If IP address is invalid
     */
    public static IpAddress of(byte[] ipAddress) throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(ipAddress);
        return new IpAddress(address, address instanceof Inet4Address ? 32 : 128);
    }

    /**
     * Create a new {@link IpAddress} instance with specified subnet mask
     *
     * @param ipAddress  IP address byte array
     * @param subnetMask Subnet mask
     * @return {@link IpAddress} instance
     * @throws UnknownHostException If IP address is invalid
     */
    public static IpAddress of(byte[] ipAddress, int subnetMask) throws UnknownHostException {
        return new IpAddress(InetAddress.getByAddress(ipAddress), subnetMask);
    }

    /**
     * Create a new {@link IpAddress} instances with specified IP range
     *
     * @param ipStart Start of IP range
     * @param ipEnd   End of IP range
     * @return {@link List} containing {@link IpAddress} instances
     */
    public static List<IpAddress> from(String ipStart, String ipEnd) {
        IPAddress address1 = new IPAddressString(ipStart).getAddress();
        IPAddress address2 = new IPAddressString(ipEnd).getAddress();
        IPAddressRange range = address1.spanWithRange(address2);
        IPAddress[] blocks = range.spanWithPrefixBlocks();

        assert blocks.length > 0 : "There must be at least one block";

        List<IpAddress> ipAddresses = new ArrayList<IpAddress>();
        for (IPAddress ipAddress : blocks) {
            ipAddresses.add(new IpAddress(ipAddress.toInetAddress(), ipAddress.getPrefixLength()));
        }

        return ipAddresses;
    }

    @Override
    public InetAddress address() {
        return inetAddress;
    }

    @Override
    public int v4AddressAsInt() {
        return v4NetworkAddress;
    }

    @Override
    public int v4SubnetMaskAsInt() {
        return v4SubnetMask;
    }

    @Override
    public BigInteger v6NetworkAddressAsBigInt() {
        return v6NetworkAddress;
    }

    @Override
    public BigInteger v6SubnetMaskAsBigInt() {
        return v6SubnetMask;
    }

    /**
     * Returns {@link Version} of this IP address
     */
    @Override
    public Version version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IpAddress ipAddress = (IpAddress) o;
        return hashCode() == ipAddress.hashCode();
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
        return hash(inetAddress, version);
    }

    @Override
    public String toString() {
        return "IpAddress{address=" + inetAddress + ", version=" + version + '}';
    }
}
