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

package org.jboss.netty.channel.socket.httptunnel;

/**
 * This interface is used by the server end of an http tunnel to generate new
 * tunnel ids for accepted client connections.
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public interface TunnelIdGenerator
{

   /**
    * Generates the next tunnel ID to be used, which must be unique
    * (i.e. ensure with high probability that it will not clash with
    * an existing tunnel ID). This method must be thread safe, and
    * preferably lock free.
    */
   public String generateId();

}
