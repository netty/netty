/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.PlatformDependent.newConcurrentHashMap;

/**
 * A {@link AddressResolverGroup} of {@link DnsNameResolver}s.
 */
@UnstableApi
public class DnsAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    private final ChannelFactory<? extends DatagramChannel> channelFactory;
    private final DnsServerAddresses nameServerAddresses;

    private final ConcurrentMap<String, Promise<InetAddress>> resolvesInProgress = newConcurrentHashMap();
    private final ConcurrentMap<String, Promise<List<InetAddress>>> resolveAllsInProgress = newConcurrentHashMap();

    public DnsAddressResolverGroup(
            Class<? extends DatagramChannel> channelType,
            DnsServerAddresses nameServerAddresses) {
        this(new ReflectiveChannelFactory<DatagramChannel>(channelType), nameServerAddresses);
    }

    public DnsAddressResolverGroup(
            ChannelFactory<? extends DatagramChannel> channelFactory,
            DnsServerAddresses nameServerAddresses) {
        this.channelFactory = channelFactory;
        this.nameServerAddresses = nameServerAddresses;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected final AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        if (!(executor instanceof EventLoop)) {
            throw new IllegalStateException(
                    "unsupported executor type: " + StringUtil.simpleClassName(executor) +
                    " (expected: " + StringUtil.simpleClassName(EventLoop.class));
        }

        return newResolver((EventLoop) executor, channelFactory, nameServerAddresses);
    }

    /**
     * @deprecated Override {@link #newNameResolver(EventLoop, ChannelFactory, DnsServerAddresses)}.
     */
    @Deprecated
    protected AddressResolver<InetSocketAddress> newResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            DnsServerAddresses nameServerAddresses) throws Exception {

        final NameResolver<InetAddress> resolver = new InflightNameResolver<InetAddress>(
                eventLoop,
                newNameResolver(eventLoop, channelFactory, nameServerAddresses),
                resolvesInProgress,
                resolveAllsInProgress);

        return newAddressResolver(eventLoop, resolver);
    }

    /**
     * Creates a new {@link NameResolver}. Override this method to create an alternative {@link NameResolver}
     * implementation or override the default configuration.
     */
    protected NameResolver<InetAddress> newNameResolver(EventLoop eventLoop,
                                                        ChannelFactory<? extends DatagramChannel> channelFactory,
                                                        DnsServerAddresses nameServerAddresses) throws Exception {
        return new DnsNameResolverBuilder(eventLoop)
                .channelFactory(channelFactory)
                .nameServerAddresses(nameServerAddresses)
                .build();
    }

    /**
     * Creates a new {@link AddressResolver}. Override this method to create an alternative {@link AddressResolver}
     * implementation or override the default configuration.
     */
    protected AddressResolver<InetSocketAddress> newAddressResolver(EventLoop eventLoop,
                                                                    NameResolver<InetAddress> resolver)
            throws Exception {
        return new InetSocketAddressResolver(eventLoop, resolver);
    }
}
