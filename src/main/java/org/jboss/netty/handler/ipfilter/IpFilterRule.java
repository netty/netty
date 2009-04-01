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

/**
 * This Interface defines an Ip Filter Rule.
 * 
 * @author frederic bregier
 *
 */
public interface IpFilterRule {
    /**
     * 
     * @return True if this Rule is an ALLOW rule
     */
    public boolean isAllowRule();
    /**
     * 
     * @return True if this Rule is a DENY rule
     */
    public boolean isDenyRule();
    /**
     * Compares the given InetAddress against the IpFilterRule and returns true if
     * the InetAddress is contained in this Rule and false if not (whatever ALLOW or DENY status).
     * @param inetAddress1
     * @return returns true if the given IP address is contained in the current
     * Ip Filter Rule.
     */
    public boolean contains(InetAddress inetAddress1);
}
