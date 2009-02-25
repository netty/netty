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
package org.jboss.netty.channel.xnio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.ConcurrentHashMap;
import org.jboss.netty.util.ConcurrentIdentityHashMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class XnioChannelRegistry {

    private static final ConcurrentMap<SocketAddress, XnioServerChannel> serverChannels =
        new ConcurrentHashMap<SocketAddress, XnioServerChannel>();
    private static final ConcurrentMap<java.nio.channels.Channel, BaseXnioChannel> mapping =
        new ConcurrentIdentityHashMap<java.nio.channels.Channel, BaseXnioChannel>();

    private static final InetAddress ANY_IPV4;
    private static final InetAddress ANY_IPV6;

    static {
        InetAddress any4 = null;
        try {
            any4 = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0 });
        } catch (Throwable t) {
            // Ignore
        } finally {
            ANY_IPV4 = any4;
        }

        InetAddress any6 = null;
        try {
            any6 = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        } catch (Throwable t) {
            // Ignore
        } finally {
            ANY_IPV6 = any6;
        }
    }

    static void registerServerChannel(XnioServerChannel channel) {
        SocketAddress localAddress = channel.getLocalAddress();
        if (localAddress == null) {
            throw new IllegalStateException("cannot register an unbound channel");
        }
        if (serverChannels.putIfAbsent(localAddress, channel) != null) {
            throw new IllegalStateException("duplicate local address: " + localAddress);
        }
    }

    static void unregisterServerChannel(SocketAddress localAddress) {
        if (localAddress == null) {
            return;
        }
        serverChannels.remove(localAddress);
    }

    static XnioServerChannel getServerChannel(SocketAddress localAddress) {
        // XXX: More IPv4 <-> IPv6 address conversion
        XnioServerChannel answer = serverChannels.get(localAddress);
        if (answer == null && localAddress instanceof InetSocketAddress) {
            InetSocketAddress a = (InetSocketAddress) localAddress;
            answer = serverChannels.get(new InetSocketAddress(ANY_IPV6, a.getPort()));
            if (answer == null) {
                answer = serverChannels.get(new InetSocketAddress(ANY_IPV4, a.getPort()));
            }
        }
        return answer;
    }

    static void registerChannelMapping(BaseXnioChannel channel) {
        if (mapping.putIfAbsent(channel.xnioChannel, channel) != null) {
            throw new IllegalStateException("duplicate mapping: " + channel);
        }
    }

    static void unregisterChannelMapping(BaseXnioChannel channel) {
        java.nio.channels.Channel xnioChannel = channel.xnioChannel;
        if (xnioChannel != null) {
            mapping.remove(xnioChannel);
        }
    }

    static BaseXnioChannel getChannel(java.nio.channels.Channel channel) {
        return mapping.get(channel);
    }

    private XnioChannelRegistry() {
        super();
    }
}
