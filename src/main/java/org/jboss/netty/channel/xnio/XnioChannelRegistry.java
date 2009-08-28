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
package org.jboss.netty.channel.xnio;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.jboss.netty.util.internal.ConcurrentIdentityHashMap;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
final class XnioChannelRegistry {

    private static final ConcurrentMap<SocketAddress, DefaultXnioServerChannel> serverChannels =
        new ConcurrentHashMap<SocketAddress, DefaultXnioServerChannel>();
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

    static void registerServerChannel(DefaultXnioServerChannel channel) {
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

    static DefaultXnioServerChannel getServerChannel(SocketAddress localAddress) {
        // XXX: More IPv4 <-> IPv6 address conversion
        DefaultXnioServerChannel answer = serverChannels.get(localAddress);
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
