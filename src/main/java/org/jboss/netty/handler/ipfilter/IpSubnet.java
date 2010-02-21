/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ipfilter;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class allows to check if an IP V4 or V6 Address is contained in a subnet.<BR>
 *
 * Supported IP V4 Formats for the Subnets are: 1.1.1.1/255.255.255.255 or 1.1.1.1/32 (CIDR-Notation)
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
 * <BR>
 * Supported IP V6 Formats for the Subnets are: a:b:c:d:e:f:g:h/NN (CIDR-Notation)
 * or any IPV6 notations (like a:b:c:d::/NN, a:b:c:d:e:f:w.x.y.z/NN)
 * and (InetAddress,Mask) where Mask is a integer for CIDR-notation
 * and (InetAddress,subnet).<BR>
 * <BR><BR>Example1:<BR>
 * <tt>IpSubnet ips = new IpSubnet("1fff:0:0a88:85a3:0:0:0:0/24");</tt><BR>
 * <tt>IpSubnet ips = new IpSubnet("1fff:0:0a88:85a3::/24");</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains("1fff:0:0a88:85a3:0:0:ac1f:8001"));</tt><BR>
 * <tt>System.out.println("Result: "+ ips.contains(inetAddress2));</tt><BR>
 * <BR>Example1 bis:<BR>
 * <tt>IpSubnet ips = new IpSubnet(inetAddress, 24);</tt><BR>
 * where inetAddress2 is 1fff:0:0a88:85a3:0:0:ac1f:8001<BR>
 *
 * @author frederic bregier
 *
 */
public class IpSubnet implements IpSet, Comparable<IpSubnet>
{
   /**
    * Internal representation
    */
   private CIDR cidr = null;

   /**
    * Create IpSubnet for ALL (used for ALLOW or DENY ALL)
    */
   public IpSubnet()
   {
      // ALLOW or DENY ALL
      cidr = null;
   }

   /**
    * Create IpSubnet using the CIDR or normal Notation<BR>
    * i.e.:<br>
    * IpSubnet subnet = new IpSubnet("10.10.10.0/24"); or<br>
    * IpSubnet subnet = new IpSubnet("10.10.10.0/255.255.255.0"); or<br>
    * IpSubnet subnet = new IpSubnet("1fff:0:0a88:85a3:0:0:0:0/24");
    * @param netAddress a network address as string.
    * @throws UnknownHostException
    * */
   public IpSubnet(String netAddress) throws UnknownHostException
   {
      cidr = CIDR.newCIDR(netAddress);
   }

   /**
    * Create IpSubnet using the CIDR Notation
    * @param inetAddress
    * @param cidrNetMask
    * @throws UnknownHostException
    */
   public IpSubnet(InetAddress inetAddress, int cidrNetMask) throws UnknownHostException
   {
      cidr = CIDR.newCIDR(inetAddress, cidrNetMask);
   }

   /**
    * Create IpSubnet using the normal Notation
    * @param inetAddress
    * @param netMask
    * @throws UnknownHostException
    */
   public IpSubnet(InetAddress inetAddress, String netMask) throws UnknownHostException
   {
      cidr = CIDR.newCIDR(inetAddress, netMask);
   }

   /**
    * Compares the given IP-Address against the Subnet and returns true if
    * the ip is in the subnet-ip-range and false if not.
    * @param ipAddr an ipaddress
    * @return returns true if the given IP address is inside the currently
    * set network.
    * @throws UnknownHostException
    * */
   public boolean contains(String ipAddr) throws UnknownHostException
   {
      InetAddress inetAddress1 = InetAddress.getByName(ipAddr);
      return this.contains(inetAddress1);
   }

   /**
    * Compares the given InetAddress against the Subnet and returns true if
    * the ip is in the subnet-ip-range and false if not.
    * @param inetAddress
    * @return returns true if the given IP address is inside the currently
    * set network.
    * */
   public boolean contains(InetAddress inetAddress)
   {
      if (cidr == null)
      {
         // ANY
         return true;
      }
      return cidr.contains(inetAddress);
   }

   @Override
   public String toString()
   {
      return cidr.toString();
   }

   @Override
   public boolean equals(Object o)
   {
      if (!(o instanceof IpSubnet))
      {
         return false;
      }
      IpSubnet ipSubnet = (IpSubnet) o;
      return ipSubnet.cidr.equals(cidr);
   }

   /**
    * Compare two IpSubnet
    */
   public int compareTo(IpSubnet o)
   {
      return cidr.toString().compareTo(o.cidr.toString());
   }

   /**
    * Simple test functions
    * @param args
    *          where args[0] is the netmask (standard or CIDR notation) and optional args[1] is
    *          the inetAddress to test with this IpSubnet
    */
   public static void main(String[] args)
   {
      if (args.length != 0)
      {
         IpSubnet ipSubnet = null;
         try
         {
            ipSubnet = new IpSubnet(args[0]);
         }
         catch (UnknownHostException e)
         {
            return;
         }
         System.out.println("IpSubnet: " + ipSubnet.toString() + " from " + ipSubnet.cidr.getBaseAddress() + " to "
               + ipSubnet.cidr.getEndAddress() + " mask " + ipSubnet.cidr.getMask());
         if (args.length > 1)
         {
            try
            {
               System.out.println("Is IN: " + args[1] + " " + ipSubnet.contains(args[1]));
            }
            catch (UnknownHostException e)
            {
            }
         }
      }
   }
}
