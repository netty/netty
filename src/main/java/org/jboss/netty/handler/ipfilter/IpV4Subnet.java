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
import java.util.StringTokenizer;
import java.util.Vector;

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

 * @author frederic bregier
 *
 */
public class IpV4Subnet implements IpSet, Comparable<IpV4Subnet>
{
   private static final int SUBNET_MASK = 0x80000000;

   private static final int BYTE_ADDRESS_MASK = 0xFF;

   private InetAddress inetAddress;

   private int subnet;

   private int mask;

   private int cidrMask;

   /**
    * Create IpV4Subnet for ALL (used for ALLOW or DENY ALL)
    */
   public IpV4Subnet()
   {
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
    * @param netAddress a network address as string.
    * @throws UnknownHostException
    * */
   public IpV4Subnet(String netAddress) throws UnknownHostException
   {
      setNetAddress(netAddress);
   }

   /**
    * Create IpV4Subnet using the CIDR Notation
    * @param inetAddress
    * @param cidrNetMask
    */
   public IpV4Subnet(InetAddress inetAddress, int cidrNetMask)
   {
      setNetAddress(inetAddress, cidrNetMask);
   }

   /**
    * Create IpV4Subnet using the normal Notation
    * @param inetAddress
    * @param netMask
    */
   public IpV4Subnet(InetAddress inetAddress, String netMask)
   {
      setNetAddress(inetAddress, netMask);
   }

   /**
    * Sets the Network Address in either CIDR or Decimal Notation.<BR>
    * i.e.: setNetAddress("1.1.1.1/24"); or<BR>
    * setNetAddress("1.1.1.1/255.255.255.0");<BR>
    * @param netAddress a network address as string.
    * @throws UnknownHostException
    * */
   private void setNetAddress(String netAddress) throws UnknownHostException
   {
      Vector<Object> vec = new Vector<Object>();
      StringTokenizer st = new StringTokenizer(netAddress, "/");
      while (st.hasMoreTokens())
      {
         vec.add(st.nextElement());
      }

      if (vec.get(1).toString().length() < 3)
      {
         setNetId(vec.get(0).toString());
         setCidrNetMask(Integer.parseInt(vec.get(1).toString()));
      }
      else
      {
         setNetId(vec.get(0).toString());
         setNetMask(vec.get(1).toString());
      }
   }

   /**
    * Sets the Network Address in CIDR Notation.
    * @param inetAddress
    * @param cidrNetMask
    * */
   private void setNetAddress(InetAddress inetAddress, int cidrNetMask)
   {
      setNetId(inetAddress);
      setCidrNetMask(cidrNetMask);
   }

   /**
    * Sets the Network Address in Decimal Notation.
    * @param inetAddress
    * @param netMask
    * */
   private void setNetAddress(InetAddress inetAddress, String netMask)
   {
      setNetId(inetAddress);
      setNetMask(netMask);
   }

   /**
    * Sets the BaseAdress of the Subnet.<BR>
    * i.e.: setNetId("192.168.1.0");
    * @param netId a network ID
    * @throws UnknownHostException
    * */
   private void setNetId(String netId) throws UnknownHostException
   {
      InetAddress inetAddress1 = InetAddress.getByName(netId);
      this.setNetId(inetAddress1);
   }

   /**
    * Compute integer representation of InetAddress
    * @param inetAddress1
    * @return the integer representation
    */
   private int toInt(InetAddress inetAddress1)
   {
      byte[] address = inetAddress1.getAddress();
      int net = 0;
      for (byte addres : address)
      {
         net <<= 8;
         net |= addres & BYTE_ADDRESS_MASK;
      }
      return net;
   }

   /**
    * Sets the BaseAdress of the Subnet.
    * @param inetAddress
    * */
   private void setNetId(InetAddress inetAddress)
   {
      this.inetAddress = inetAddress;
      subnet = toInt(inetAddress);
   }

   /**
    * Sets the Subnet's Netmask in Decimal format.<BR>
    * i.e.: setNetMask("255.255.255.0");
    * @param netMask a network mask
    * */
   private void setNetMask(String netMask)
   {
      StringTokenizer nm = new StringTokenizer(netMask, ".");
      int i = 0;
      int[] netmask = new int[4];
      while (nm.hasMoreTokens())
      {
         netmask[i] = Integer.parseInt(nm.nextToken());
         i++;
      }
      int mask1 = 0;
      for (i = 0; i < 4; i++)
      {
         mask1 += Integer.bitCount(netmask[i]);
      }
      setCidrNetMask(mask1);
   }

   /**
    * Sets the CIDR Netmask<BR>
    * i.e.: setCidrNetMask(24);
    * @param cidrNetMask a netmask in CIDR notation
    * */
   private void setCidrNetMask(int cidrNetMask)
   {
      cidrMask = cidrNetMask;
      mask = SUBNET_MASK >> cidrMask - 1;
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
    * @param inetAddress1
    * @return returns true if the given IP address is inside the currently
    * set network.
    * */
   public boolean contains(InetAddress inetAddress1)
   {
      if (mask == -1)
      {
         // ANY
         return true;
      }
      return (toInt(inetAddress1) & mask) == subnet;
   }

   @Override
   public String toString()
   {
      return inetAddress.getHostAddress() + "/" + cidrMask;
   }

   @Override
   public boolean equals(Object o)
   {
      if (!(o instanceof IpV4Subnet))
      {
         return false;
      }
      IpV4Subnet ipV4Subnet = (IpV4Subnet) o;
      return ipV4Subnet.subnet == subnet && ipV4Subnet.cidrMask == cidrMask;
   }

   /**
    * Compare two IpV4Subnet
    */
   public int compareTo(IpV4Subnet o)
   {
      if (o.subnet == subnet && o.cidrMask == cidrMask)
      {
         return 0;
      }
      if (o.subnet < subnet)
      {
         return 1;
      }
      else if (o.subnet > subnet)
      {
         return -1;
      }
      else if (o.cidrMask < cidrMask)
      {
         // greater Mask means less IpAddresses so -1
         return -1;
      }
      return 1;
   }

   /**
    * Simple test functions
    * @param args
    *          where args[0] is the netmask (standard or CIDR notation) and optional args[1] is
    *          the inetAddress to test with this IpV4Subnet
    */
   public static void main(String[] args)
   {
      if (args.length != 0)
      {
         IpV4Subnet ipV4Subnet = null;
         try
         {
            ipV4Subnet = new IpV4Subnet(args[0]);
         }
         catch (UnknownHostException e)
         {
            return;
         }
         if (args.length > 1)
         {
            try
            {
               System.out.println("Is IN: " + args[1] + " " + ipV4Subnet.contains(args[1]));
            }
            catch (UnknownHostException e)
            {
            }
         }
      }
   }
}
