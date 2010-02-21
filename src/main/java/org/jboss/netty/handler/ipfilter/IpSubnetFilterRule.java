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
 * Ip V4 and Ip V6 filter rule.<br>
 * <br>
 * Note that mix of IPV4 and IPV6 is allowed but it is not recommended. So it is preferable to not
 * mix IPV4 addresses with IPV6 rules, even if it should work.
 * @author frederic bregier
 *
 */
public class IpSubnetFilterRule extends IpSubnet implements IpFilterRule
{
   /**
    * Is this IpV4Subnet an ALLOW or DENY rule
    */
   private boolean isAllowRule = true;

   /**
    * Constructor for a ALLOW or DENY ALL
    * @param allow True for ALLOW, False for DENY
    */
   public IpSubnetFilterRule(boolean allow)
   {
      super();
      isAllowRule = allow;
   }

   /**
    * @param allow True for ALLOW, False for DENY
    * @param inetAddress
    * @param cidrNetMask
    * @throws UnknownHostException
    */
   public IpSubnetFilterRule(boolean allow, InetAddress inetAddress, int cidrNetMask) throws UnknownHostException
   {
      super(inetAddress, cidrNetMask);
      isAllowRule = allow;
   }

   /**
    * @param allow True for ALLOW, False for DENY
    * @param inetAddress
    * @param netMask
    * @throws UnknownHostException
    */
   public IpSubnetFilterRule(boolean allow, InetAddress inetAddress, String netMask) throws UnknownHostException
   {
      super(inetAddress, netMask);
      isAllowRule = allow;
   }

   /**
    * @param allow True for ALLOW, False for DENY
    * @param netAddress
    * @throws UnknownHostException
    */
   public IpSubnetFilterRule(boolean allow, String netAddress) throws UnknownHostException
   {
      super(netAddress);
      isAllowRule = allow;
   }

   public boolean isAllowRule()
   {
      return isAllowRule;
   }

   public boolean isDenyRule()
   {
      return !isAllowRule;
   }

}
