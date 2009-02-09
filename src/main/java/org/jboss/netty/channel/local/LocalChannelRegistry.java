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
package org.jboss.netty.channel.local;

import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.ConcurrentIdentityHashMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class LocalChannelRegistry {

    private static final ConcurrentMap<LocalAddress, Channel> map =
        new ConcurrentIdentityHashMap<LocalAddress, Channel>();

    static boolean isRegistered(LocalAddress address) {
        return map.containsKey(address);
    }

    static Channel getChannel(LocalAddress address) {
        return map.get(address);
    }

    static boolean register(LocalAddress address, Channel channel) {
        return map.putIfAbsent(address, channel) == null;
    }

    static boolean unregister(LocalAddress address) {
        return map.remove(address) != null;
    }

    private LocalChannelRegistry() {
        // Unused
    }
}
