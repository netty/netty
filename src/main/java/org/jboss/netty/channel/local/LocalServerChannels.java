/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.local;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author Trustin Lee (tlee@redhat.com)
 */
public final class LocalServerChannels {
    private static final Map<String, LocalServerChannelFactory> factoryMap = new HashMap<String, LocalServerChannelFactory>();

    public static LocalServerChannelFactory registerServerChannel(String channelName) {
        if (factoryMap.keySet().contains(channelName)) {
            throw new IllegalArgumentException("server channel already registered: " + channelName);
        }
        LocalServerChannelFactory factory = new LocalServerChannelFactory(channelName);
        factoryMap.put(channelName, factory);
        return factory;
    }

    public static LocalClientChannelFactory getClientChannelFactory(String channelName) {
       LocalServerChannelFactory localServerChannelFactory = factoryMap.get(channelName);
       return localServerChannelFactory == null?null:new LocalClientChannelFactory(localServerChannelFactory);
    }

    public static void unregisterServerChannel(String channelName) {
        factoryMap.remove(channelName);
    }

    private LocalServerChannels() {
        // Unused.
    }
}
