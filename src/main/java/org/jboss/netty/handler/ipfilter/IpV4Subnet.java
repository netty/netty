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
package org.jboss.netty.handler.ipfilter;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

/**
 * This class allows to check if an IP-V4-Address is contained in a subnet.<BR>
 * Supported Formats for the Subnets are: 1.1.1.1/255.255.255.255 or 1.1.1.1/32 (CIDR-Notation)
 * and (InetAddress,Mask) where Mask is a integer for CIDR-notation or a String for Standard Mask notation.<BR>
 * <BR><BR>Example1:<BR>
 * <tt>IpV4Subnet ips = new IpV4Subnet("192.168.1.0/24");</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains("192.168.1.123"));</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains(inetAddress2));</tt><BR>
 * <BR>Example1 bis:<BR>
 * <tt>IpV4Subnet ips = new IpV4Subnet(inetAddress, 24);</tt><BR>
 * where inetAddress is 192.168.1.0 and inetAddress2 is 192.168.1.123<BR>
 * <BR><BR>Example2:<BR>
 * <tt>IpV4Subnet ips = new IpV4Subnet("192.168.1.0/255.255.255.0");</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains("192.168.1.123"));</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains(inetAddress2));</tt><BR>
 * <BR>Example2 bis:<BR>
 * <tt>IpV4Subnet ips = new IpV4Subnet(inetAddress, "255.255.255.0");</tt><BR>
 * where inetAddress is 192.168.1.0 and inetAddress2 is 192.168.1.123<BR>
 */
public class IpV4Subnet implements IpSet, Comparable<IpV4Subnet> {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(IpV4Subnet.class);

    private static final int SUBNET_MASK = 0x80000000;

    private static final int BYTE_ADDRESS_MASK = 0xFF;

    private InetAddress inetAddress;

    private int subnet;

    private int mask;

    private int cidrMask;

    /** Create IpV4Subnet for ALL (used for ALLOW or DENY ALL) */
    public IpV4Subnet() {
        // ALLOW or DENY ALL
        mask = -1;
        // other will be ignored
        inetAddress = null;
        subnet = 0;
        cidrMask = 0;
    }

    /**
     * Create IpV4Subnet using the CIDR or normal Notation<BR>
     * i.e.:
     * IpV4Subnet subnet = new IpV4Subnet("10.10.10.0/24"); or
     * IpV4Subnet subnet = new IpV4Subnet("10.10.10.0/255.255.255.0");
     *
     * @param netAddress a network address as string.
     */
    public IpV4Subnet(String netAddress) throws UnknownHostException {
        setNetAddress(netAddress);
    }

    /** Create IpV4Subnet using the CIDR Notation */
    public IpV4Subnet(InetAddress inetAddress, int cidrNetMask) {
        setNetAddress(inetAddress, cidrNetMask);
    }

    /** Create IpV4Subnet using the normal Notation */
    public IpV4Subnet(InetAddress inetAddress, String netMask) {
        setNetAddress(inetAddress, netMask);
    }

    /**
     * Sets the Network Address in either CIDR or Decimal Notation.<BR>
     * i.e.: setNetAddress("1.1.1.1/24"); or<BR>
     * setNetAddress("1.1.1.1/255.255.255.0");<BR>
     *
     * @param netAddress a network address as string.
     */
    private void setNetAddress(String netAddress) throws UnknownHostException {
        String[] tokens = StringUtil.split(netAddress, '/');
        if (tokens.length != 2) {
            throw new IllegalArgumentException("netAddress: " + netAddress + " (expected: CIDR or Decimal Notation)");
        }

        if (tokens[1].length() < 3) {
            setNetId(tokens[0]);
            setCidrNetMask(Integer.parseInt(tokens[1]));
        } else {
            setNetId(tokens[0]);
            setNetMask(tokens[1]);
        }
    }

    /** Sets the Network Address in CIDR Notation. */
    private void setNetAddress(InetAddress inetAddress, int cidrNetMask) {
        setNetId(inetAddress);
        setCidrNetMask(cidrNetMask);
    }

    /** Sets the Network Address in Decimal Notation. */
    private void setNetAddress(InetAddress inetAddress, String netMask) {
        setNetId(inetAddress);
        setNetMask(netMask);
    }

    /**
     * Sets the BaseAdress of the Subnet.<BR>
     * i.e.: setNetId("192.168.1.0");
     *
     * @param netId a network ID
     */
    private void setNetId(String netId) throws UnknownHostException {
        InetAddress inetAddress1 = InetAddress.getByName(netId);
        setNetId(inetAddress1);
    }

    /**
     * Compute integer representation of InetAddress
     *
     * @return the integer representation
     */
    private static int toInt(InetAddress inetAddress1) {
        byte[] address = inetAddress1.getAddress();
        int net = 0;
        for (byte addres : address) {
            net <<= 8;
            net |= addres & BYTE_ADDRESS_MASK;
        }
        return net;
    }

    /** Sets the BaseAdress of the Subnet. */
    private void setNetId(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        subnet = toInt(inetAddress);
    }

    /**
     * Sets the Subnet's Netmask in Decimal format.<BR>
     * i.e.: setNetMask("255.255.255.0");
     *
     * @param netMask a network mask
     */
    private void setNetMask(String netMask) {
        StringTokenizer nm = new StringTokenizer(netMask, ".");
        int i = 0;
        int[] netmask = new int[4];
        while (nm.hasMoreTokens()) {
            netmask[i] = Integer.parseInt(nm.nextToken());
            i++;
        }
        int mask1 = 0;
        for (i = 0; i < 4; i++) {
            mask1 += Integer.bitCount(netmask[i]);
        }
        setCidrNetMask(mask1);
    }

    /**
     * Sets the CIDR Netmask<BR>
     * i.e.: setCidrNetMask(24);
     *
     * @param cidrNetMask a netmask in CIDR notation
     */
    private void setCidrNetMask(int cidrNetMask) {
        cidrMask = cidrNetMask;
        mask = SUBNET_MASK >> cidrMask - 1;
    }

    /**
     * Compares the given IP-Address against the Subnet and returns true if
     * the ip is in the subnet-ip-range and false if not.
     *
     * @param ipAddr an ipaddress
     * @return returns true if the given IP address is inside the currently
     *         set network.
     */
    public boolean contains(String ipAddr) throws UnknownHostException {
        InetAddress inetAddress1 = InetAddress.getByName(ipAddr);
        return contains(inetAddress1);
    }

    /**
     * Compares the given InetAddress against the Subnet and returns true if
     * the ip is in the subnet-ip-range and false if not.
     *
     * @return returns true if the given IP address is inside the currently
     *         set network.
     */
    public boolean contains(InetAddress inetAddress1) {
        if (mask == -1) {
            // ANY
            return true;
        }
        return (toInt(inetAddress1) & mask) == subnet;
    }

    @Override
    public String toString() {
        return inetAddress.getHostAddress() + '/' + cidrMask;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IpV4Subnet)) {
            return false;
        }
        IpV4Subnet ipV4Subnet = (IpV4Subnet) o;
        return ipV4Subnet.subnet == subnet && ipV4Subnet.cidrMask == cidrMask;
    }

    @Override
    public int hashCode() {
        return subnet;
    }

    /** Compare two IpV4Subnet */
    public int compareTo(IpV4Subnet o) {
        if (o.subnet == subnet && o.cidrMask == cidrMask) {
            return 0;
        }
        if (o.subnet < subnet) {
            return 1;
        }
        if (o.subnet > subnet) {
            return -1;
        }
        if (o.cidrMask < cidrMask) {
            // greater Mask means less IpAddresses so -1
            return -1;
        }
        return 1;
    }
}
