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

/**
 * IpV4 only Filter Rule
 * @author frederic bregier
 *
 */
public class IpV4SubnetFilterRule extends IpV4Subnet implements IpFilterRule {
    /**
     * Is this IpV4Subnet an ALLOW or DENY rule
     */
    private boolean isAllowRule = true;

    /**
     * Constructor for a ALLOW or DENY ALL
     * @param allow True for ALLOW, False for DENY
     */
    public IpV4SubnetFilterRule(boolean allow) {
        super();
        isAllowRule = allow;
    }

    /**
     * @param allow True for ALLOW, False for DENY
     * @param inetAddress
     * @param cidrNetMask
     */
    public IpV4SubnetFilterRule(boolean allow, InetAddress inetAddress,
            int cidrNetMask) {
        super(inetAddress, cidrNetMask);
        isAllowRule = allow;
    }

    /**
     * @param allow True for ALLOW, False for DENY
     * @param inetAddress
     * @param netMask
     */
    public IpV4SubnetFilterRule(boolean allow, InetAddress inetAddress,
            String netMask) {
        super(inetAddress, netMask);
        isAllowRule = allow;
    }

    /**
     * @param allow True for ALLOW, False for DENY
     * @param netAddress
     * @throws UnknownHostException
     */
    public IpV4SubnetFilterRule(boolean allow, String netAddress)
            throws UnknownHostException {
        super(netAddress);
        isAllowRule = allow;
    }

    public boolean isAllowRule() {
        return isAllowRule;
    }

    public boolean isDenyRule() {
        return !isAllowRule;
    }

}
