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

import java.security.SecureRandom;

/**
 * Default implementation of TunnelIdGenerator, which uses a
 * {@link java.security.SecureRandom SecureRandom} generator
 * to produce 32-bit tunnel identifiers.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class DefaultTunnelIdGenerator implements TunnelIdGenerator
{

   private SecureRandom generator;

   public DefaultTunnelIdGenerator()
   {
      this(new SecureRandom());
   }

   public DefaultTunnelIdGenerator(SecureRandom generator)
   {
      this.generator = generator;
   }

   public synchronized String generateId()
   {
      // synchronized to ensure that this code is thread safe. The Sun
      // standard implementations seem to be synchronized or lock free
      // but are not documented as guaranteeing this
      return Integer.toHexString(generator.nextInt());
   }

}
