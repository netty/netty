/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.resolver.dns;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.RoundRobinInetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A {@link AddressResolverGroup} of {@link DnsNameResolver}s that supports random selection of destination addresses if
 * multiple are provided by the nameserver. This is ideal for use in applications that use a pool of connections, for
 * which connecting to a single resolved address would be inefficient.
 */
@UnstableApi
public class RoundRobinDnsAddressResolverGroup extends DnsAddressResolverGroup {

    public RoundRobinDnsAddressResolverGroup(
            Class<? extends DatagramChannel> channelType,
            DnsServerAddresses nameServerAddresses) {
        super(channelType, nameServerAddresses);
    }

    public RoundRobinDnsAddressResolverGroup(
            ChannelFactory<? extends DatagramChannel> channelFactory,
            DnsServerAddresses nameServerAddresses) {
        super(channelFactory, nameServerAddresses);
    }

    @Override
    protected final AddressResolver<InetSocketAddress> newAddressResolver(EventLoop eventLoop,
                                                                          NameResolver<InetAddress> resolver)
            throws Exception {
        return new RoundRobinInetSocketAddressResolver(eventLoop, resolver);
    }
}
