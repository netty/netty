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

/**
 * This Interface defines an IpSet object.
 *
 * @author frederic bregier
 *
 */
public interface IpSet
{
   /**
    * Compares the given InetAddress against the IpSet and returns true if
    * the InetAddress is contained in this Rule and false if not.
    * @param inetAddress1
    * @return returns true if the given IP address is contained in the current
    * IpSet.
    */
   public boolean contains(InetAddress inetAddress1);
}
