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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This class allows to check if an IP-Address is contained in a subnet.<BR>
 * Supported Formats for the Subnets are: 1.1.1.1/255.255.255.255 or 1.1.1.1/32 (CIDR-Notation)
 * and (InetAddress,Mask) where Mask is a integer for CIDR-notation or a String for Standard Mask notation<BR>
 * The first argument tells if this subnet is an ALLOW (TRUE) or a DENY (FALSE) subnet when used in access checking.<BR>
 * <BR><BR>Example1:<BR>
 * <tt>IpSubnet ips = new IpSubnet(true, "192.168.1.0/24");</tt><BR>
 * <tt>System.out.println("Result: "+ ips.accept("192.168.1.123"));</tt><BR>
 * <tt>System.out.println("Result: "+ ips.accept(inetAddress2));</tt><BR>
 * <BR>Example1 bis:<BR>
 * <tt>IpSubnet ips = new IpSubnet(true, inetAddress, 24);</tt><BR>
 * where inetAddress is 192.168.1.0 and inetAddress2 is 192.168.1.123<BR>
 * <BR><BR>Example2:<BR>
 * <tt>IpSubnet ips = new IpSubnet(rue, "192.168.1.0/255.255.255.0");</tt><BR>
 * <tt>System.out.println("Result: "+ ips.accept("192.168.1.123"));</tt><BR>
 * <tt>System.out.println("Result: "+ ips.accept(inetAddress2));</tt><BR>
 * <BR>Example2 bis:<BR>
 * <tt>IpSubnet ips = new IpSubnet(rue, inetAddress, "255.255.255.0");</tt><BR>
 * where inetAddress is 192.168.1.0 and inetAddress2 is 192.168.1.123<BR>

 * @author frederic bregier
 *
 */
public class IpSubnet implements IpFilterRule, Comparable<IpSubnet> { 
    private static final int SUBNET_MASK = 0x80000000;
    private static final int BYTE_ADDRESS_MASK = 0xFF;
    
    private InetAddress inetAddress;
    private int subnet;
    private int mask;
    private int cidrMask;
    private boolean isAllowed = true;
    
    /**
     * Create IpSubnet for ALLOW ALL (true) or DENY ALL (false)
     * @param allow
     */
    public IpSubnet(boolean allow) {
        // ALLOW or DENY ALL
        this.mask = -1;
        this.isAllowed = allow;
        // other will be ignored
        this.inetAddress = null;
        this.subnet = 0;
        this.cidrMask = 0;
    }
    /**
     * Create IpSubnet using the CIDR or normal Notation<BR>
     * i.e.: 
     * IpSubnet subnet = new IpSubnet("10.10.10.0/24"); or
     * IpSubnet subnet = new IpSubnet("10.10.10.0/255.255.255.0");
     * @param allow True if the given netAddress is to be allowed, False to be Denied
     * @param netAddress a network address as string. 
     * @throws UnknownHostException 
     * */
    public IpSubnet(boolean allow, String netAddress) throws UnknownHostException {
        this.isAllowed = allow;
        setNetAddress(netAddress);
    }
    /**
     * Create IpSubnet using the CIDR Notation
     * @param allow True if the given CIDR notation is to be allowed, False to be Denied
     * @param inetAddress
     * @param cidrNetMask
     */
    public IpSubnet(boolean allow, InetAddress inetAddress, int cidrNetMask) {
        this.isAllowed = allow;
        setNetAddress(inetAddress, cidrNetMask);
    }
    /**
     * Create IpSubnet using the normal Notation
     * @param allow True if the given standard netmask notation is to be allowed, False to be Denied
     * @param inetAddress
     * @param netMask
     */
    public IpSubnet(boolean allow, InetAddress inetAddress, String netMask) {
        this.isAllowed = allow;
        setNetAddress(inetAddress, netMask);
    }
    
    /**
     * Sets the Network Address in either CIDR or Decimal Notation.<BR>
     * i.e.: setNetAddress("1.1.1.1/24"); or<BR>
     * setNetAddress("1.1.1.1/255.255.255.0");<BR>
     * @param netAddress a network address as string. 
     * @throws UnknownHostException 
     * */
    private void setNetAddress(String netAddress) throws UnknownHostException {
        Vector<Object> vec = new Vector<Object>();
        StringTokenizer st = new StringTokenizer(netAddress, "/");
        while (st.hasMoreTokens()) {
            vec.add(st.nextElement());
        }

        if (vec.get(1).toString().length() < 3) {
            setNetId(vec.get(0).toString());
            setCidrNetMask(Integer.parseInt(vec.get(1).toString()));
        } else {
            setNetId(vec.get(0).toString());
            setNetMask(vec.get(1).toString());
        }
    }
    /**
     * Sets the Network Address in CIDR Notation.
     * @param inetAddress
     * @param cidrNetMask
     * */
    private void setNetAddress(InetAddress inetAddress, int cidrNetMask) {
        setNetId(inetAddress);
        setCidrNetMask(cidrNetMask);
    }
    /**
     * Sets the Network Address in Decimal Notation.
     * @param inetAddress
     * @param netMask 
     * */
    private void setNetAddress(InetAddress inetAddress, String netMask) {
        setNetId(inetAddress);
        setNetMask(netMask);
    }
    
    /**
     * Sets the BaseAdress of the Subnet.<BR>
     * i.e.: setNetId("192.168.1.0");
     * @param netId a network ID
     * @throws UnknownHostException 
     * */
    private void setNetId(String netId) throws UnknownHostException {
        InetAddress inetAddress1 = InetAddress.getByName(netId);
        this.setNetId(inetAddress1);
    }
    /**
     * Compute integer representation of InetAddress
     * @param inetAddress1
     * @return the integer representation
     */
    private int toInt(InetAddress inetAddress1) {
        byte [] address = inetAddress1.getAddress();
        int net = 0;
        for (int i = 0; i < address.length ; i++) {
            net <<= 8;
            net |= address[i] & BYTE_ADDRESS_MASK;
        }
        return net;
    }
    /**
     * Sets the BaseAdress of the Subnet.
     * @param inetAddress
     * */
    private void setNetId(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        this.subnet = toInt(inetAddress);
    }

    /**
     * Sets the Subnet's Netmask in Decimal format.<BR>
     * i.e.: setNetMask("255.255.255.0");
     * @param netMask a network mask
     * */
    private void setNetMask(String netMask) {
        StringTokenizer nm = new StringTokenizer(netMask, ".");
        int i = 0;
        int []netmask = new int[4];
        while (nm.hasMoreTokens()) {
            netmask[i] = Integer.parseInt(nm.nextToken());
            i ++;
        }
        int mask1 = 0;
        for (i = 0; i < 4 ; i++) {
            mask1 += Integer.bitCount(netmask[i]);
        }
        this.setCidrNetMask(mask1);
    }

    /**
     * Sets the CIDR Netmask<BR>
     * i.e.: setCidrNetMask(24);
     * @param cidrNetMask a netmask in CIDR notation 
     * */
    private void setCidrNetMask(int cidrNetMask) {
        this.cidrMask = cidrNetMask;
        this.mask = SUBNET_MASK >> (this.cidrMask - 1);
    }

    /**
     * Compares the given IP-Address against the Subnet and returns true if
     * the ip is in the subnet-ip-range and false if not.
     * @param ipAddr an ipaddress
     * @return returns true if the given IP address is inside the currently
     * set network.
     * @throws UnknownHostException 
     * */
    public boolean contains(String ipAddr) throws UnknownHostException {
        InetAddress inetAddress1 = InetAddress.getByName(ipAddr);
        return this.contains(inetAddress1);
    }    
    /**
     * Compares the given InetAddress against the Subnet and returns true if
     * the ip is in the subnet-ip-range and false if not.
     * @param inetAddress1
     * @return returns true if the given IP address is inside the currently
     * set network.
     * */
    public boolean contains(InetAddress inetAddress1) {
        if (this.mask == -1) {
            // ANY
            return true;
        }
        return ((toInt(inetAddress1) & this.mask) == this.subnet);
    }

    @Override
    public String toString() {
        return this.inetAddress.getHostAddress()+"/"+this.cidrMask;
    }
    @Override
    public boolean equals(Object o) {
        if (! (o instanceof IpSubnet)) {
            return false;
        }
        IpSubnet ipSubnet = (IpSubnet) o;
        return (ipSubnet.subnet == this.subnet) && (ipSubnet.cidrMask == this.cidrMask);
    }
    /**
     * Compare two IpSubnet
     */
    public int compareTo(IpSubnet o) {
        if ((o.subnet == this.subnet) && (o.cidrMask == this.cidrMask)) {
            return 0;
        }
        if (o.subnet < this.subnet) {
            return 1;
        } else if (o.subnet > this.subnet) {
            return -1;
        } else if (o.cidrMask < this.cidrMask) {
            // greater Mask means less IpAddresses so -1
            return -1;
        }
        return 1;
    }
    public boolean isAllowRule() {
        return this.isAllowed;
    }
    public boolean isDenyRule() {
        return (!this.isAllowed);
    }
    /**
     * Simple test functions
     * @param args 
     *          where args[0] is the netmask (standard or CIDR notation) and optional args[1] is
     *          the inetAddress to test with this IpSubnet
     */
    public static void main(String [] args) {
        if (args.length != 0) {
            IpSubnet ipSubnet = null;
            try {
                ipSubnet = new IpSubnet(true, args[0]);
            } catch (UnknownHostException e) {
                return;
            }
            if (args.length > 1) {
                try {
                    System.out.println("Is IN: "+args[1]+" "+ipSubnet.contains(args[1]));
                } catch (UnknownHostException e) {
                }
            }
        }
    }
}
