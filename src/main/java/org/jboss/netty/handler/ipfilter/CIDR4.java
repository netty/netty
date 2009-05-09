/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.ipfilter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author frederic bregier
 */
public class CIDR4 extends CIDR {
    /**
     * The integer for the base address
     */
    private int addressInt;

    /**
     * The integer for the end address
     */
    private final int addressEndInt;

    /**
     * @param newaddr
     * @param mask
     */
    protected CIDR4(Inet4Address newaddr, int mask) {
        cidrMask = mask;
        addressInt = ipv4AddressToInt(newaddr);
        int newmask = ipv4PrefixLengthToMask(mask);
        addressInt &= newmask;
        try {
            baseAddress = intToIPv4Address(addressInt);
        } catch (UnknownHostException e) {
            // this should never happen
        }
        addressEndInt = addressInt + ipv4PrefixLengthToLength(cidrMask) - 1;
    }

    @Override
    public InetAddress getEndAddress() {
        try {
            return intToIPv4Address(addressEndInt);
        } catch (UnknownHostException e) {
            // this should never happen
            return null;
        }
    }

    public int compareTo(CIDR arg) {
        if (arg instanceof CIDR6) {
            byte[] address = getIpV4FromIpV6((Inet6Address) arg.baseAddress);
            int net = ipv4AddressToInt(address);
            if (net == addressInt && arg.cidrMask == cidrMask) {
                return 0;
            }
            if (net < addressInt) {
                return 1;
            } else if (net > addressInt) {
                return -1;
            } else if (arg.cidrMask < cidrMask) {
                return -1;
            }
            return 1;
        }
        CIDR4 o = (CIDR4) arg;
        if (o.addressInt == addressInt && o.cidrMask == cidrMask) {
            return 0;
        }
        if (o.addressInt < addressInt) {
            return 1;
        } else if (o.addressInt > addressInt) {
            return -1;
        } else if (o.cidrMask < cidrMask) {
            // greater Mask means less IpAddresses so -1
            return -1;
        }
        return 1;
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.ipfilter.CIDR#contains(java.net.InetAddress)
     */
    @Override
    public boolean contains(InetAddress inetAddress) {
        int search = ipv4AddressToInt(inetAddress);
        return search >= addressInt && search <= addressEndInt;
    }

    /** Given an IPv4 baseAddress length, return the block length.  I.e., a
     *  baseAddress length of 24 will return 256. */
    private static int ipv4PrefixLengthToLength(int prefix_length) {
        return 1 << 32 - prefix_length;
    }

    /** Given a baseAddress length, return a netmask.  I.e, a baseAddress length
     *  of 24 will return 0xFFFFFF00. */
    private static int ipv4PrefixLengthToMask(int prefix_length) {
        return ~((1 << 32 - prefix_length) - 1);
    }

    /** Convert an integer into an (IPv4) InetAddress.
     * @param addr
     * @return the created InetAddress
     * @throws UnknownHostException
     * @throws UnknownHostException
     */
    private static InetAddress intToIPv4Address(int addr)
            throws UnknownHostException {
        byte[] a = new byte[4];
        a[0] = (byte) (addr >> 24 & 0xFF);
        a[1] = (byte) (addr >> 16 & 0xFF);
        a[2] = (byte) (addr >> 8 & 0xFF);
        a[3] = (byte) (addr & 0xFF);
        return InetAddress.getByAddress(a);
    }

    /** Given an IPv4 address, convert it into an integer.
     * @param addr
     * @return the integer representation of the InetAddress
     *
     *  @throws IllegalArgumentException if the address is really an
     *  IPv6 address.
     */
    private static int ipv4AddressToInt(InetAddress addr) {
        byte[] address = null;
        if (addr instanceof Inet6Address) {
            address = getIpV4FromIpV6((Inet6Address) addr);
        } else {
            address = addr.getAddress();
        }
        return ipv4AddressToInt(address);
    }

    /** Given an IPv4 address as array of bytes, convert it into an integer.
     * @param address
     * @return the integer representation of the InetAddress
     *
     *  @throws IllegalArgumentException if the address is really an
     *  IPv6 address.
     */
    private static int ipv4AddressToInt(byte[] address) {
        int net = 0;
        for (byte addres: address) {
            net <<= 8;
            net |= addres & 0xFF;
        }
        return net;
    }
}
