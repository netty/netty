/*
 * Copyright 2015 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.intValue;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.resolver.HostsFileEntriesResolver;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A {@link DnsNameResolver} builder.
 */
public abstract class AbstractDnsNameResolverBuilder<T extends AbstractDnsNameResolverBuilder<T>> {

    protected final EventLoop eventLoop;
    protected ChannelFactory<? extends DatagramChannel> channelFactory;
    protected InetSocketAddress localAddress = DnsNameResolver.ANY_LOCAL_ADDR;
    protected DnsServerAddresses nameServerAddresses = DnsServerAddresses.defaultAddresses();
    protected DnsCache resolveCache;
    protected Integer minTtl;
    protected Integer maxTtl;
    protected Integer negativeTtl;
    protected long queryTimeoutMillis = 5000;
    protected InternetProtocolFamily[] resolvedAddressTypes = DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
    protected boolean recursionDesired = true;
    protected int maxQueriesPerResolve = 3;
    protected boolean traceEnabled;
    protected int maxPayloadSize = 4096;
    protected boolean optResourceEnabled = true;
    protected HostsFileEntriesResolver hostsFileEntriesResolver = HostsFileEntriesResolver.DEFAULT;

    /**
     * Creates a new builder.
     *
     * @param eventLoop the {@link EventLoop} the {@link EventLoop} which will perform the communication with the DNS
     * servers.
     */
    public AbstractDnsNameResolverBuilder(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link DatagramChannel}.
     *
     * @param channelFactory the {@link ChannelFactory}
     * @return {@code this}
     */
    public T channelFactory(ChannelFactory<? extends DatagramChannel> channelFactory) {
        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type.
     * Use as an alternative to {@link #channelFactory(ChannelFactory)}.
     *
     * @param channelType
     * @return {@code this}
     */
    public T channelType(Class<? extends DatagramChannel> channelType) {
        return channelFactory(new ReflectiveChannelFactory<DatagramChannel>(channelType));
    }

    /**
     * Sets the local address of the {@link DatagramChannel}
     *
     * @param localAddress the local address
     * @return {@code this}
     */
    public T localAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * Sets the addresses of the DNS server.
     *
     * @param nameServerAddresses the DNS server addresses
     * @return {@code this}
     */
    public T nameServerAddresses(DnsServerAddresses nameServerAddresses) {
        this.nameServerAddresses = nameServerAddresses;
        return self();
    }

    /**
     * Sets the cache for resolution results.
     *
     * @param resolveCache the DNS resolution results cache
     * @return {@code this}
     */
    public T resolveCache(DnsCache resolveCache) {
        this.resolveCache  = resolveCache;
        return self();
    }

    /**
     * Sets the minimum and maximum TTL of the cached DNS resource records (in seconds). If the TTL of the DNS
     * resource record returned by the DNS server is less than the minimum TTL or greater than the maximum TTL,
     * this resolver will ignore the TTL from the DNS server and use the minimum TTL or the maximum TTL instead
     * respectively.
     * The default value is {@code 0} and {@link Integer#MAX_VALUE}, which practically tells this resolver to
     * respect the TTL from the DNS server.
     *
     * @param minTtl the minimum TTL
     * @param maxTtl the maximum TTL
     * @return {@code this}
     */
    public T ttl(int minTtl, int maxTtl) {
        this.maxTtl = maxTtl;
        this.minTtl = minTtl;
        return self();
    }

    /**
     * Sets the TTL of the cache for the failed DNS queries (in seconds).
     *
     * @param negativeTtl the TTL for failed cached queries
     * @return {@code this}
     */
    public T negativeTtl(int negativeTtl) {
        this.negativeTtl = negativeTtl;
        return self();
    }

    /**
     * Sets the timeout of each DNS query performed by this resolver (in milliseconds).
     *
     * @param queryTimeoutMillis the query timeout
     * @return {@code this}
     */
    public T queryTimeoutMillis(long queryTimeoutMillis) {
        this.queryTimeoutMillis = queryTimeoutMillis;
        return self();
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     * Usually, both {@link InternetProtocolFamily#IPv4} and {@link InternetProtocolFamily#IPv6} are specified in
     * the order of preference.  To enforce the resolve to retrieve the address of a specific protocol family,
     * specify only a single {@link InternetProtocolFamily}.
     *
     * @param resolvedAddressTypes the address types
     * @return {@code this}
     */
    public T resolvedAddressTypes(InternetProtocolFamily... resolvedAddressTypes) {
        checkNotNull(resolvedAddressTypes, "resolvedAddressTypes");

        final List<InternetProtocolFamily> list =
                new ArrayList<InternetProtocolFamily>(InternetProtocolFamily.values().length);

        for (InternetProtocolFamily f : resolvedAddressTypes) {
            if (f == null) {
                break;
            }

            // Avoid duplicate entries.
            if (list.contains(f)) {
                continue;
            }

            list.add(f);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("no protocol family specified");
        }

        this.resolvedAddressTypes = list.toArray(new InternetProtocolFamily[list.size()]);

        return self();
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     * Usually, both {@link InternetProtocolFamily#IPv4} and {@link InternetProtocolFamily#IPv6} are specified in
     * the order of preference.  To enforce the resolve to retrieve the address of a specific protocol family,
     * specify only a single {@link InternetProtocolFamily}.
     *
     * @param resolvedAddressTypes the address types
     * @return {@code this}
     */
    public T resolvedAddressTypes(Iterable<InternetProtocolFamily> resolvedAddressTypes) {
        checkNotNull(resolvedAddressTypes, "resolveAddressTypes");

        final List<InternetProtocolFamily> list =
                new ArrayList<InternetProtocolFamily>(InternetProtocolFamily.values().length);

        for (InternetProtocolFamily f : resolvedAddressTypes) {
            if (f == null) {
                break;
            }

            // Avoid duplicate entries.
            if (list.contains(f)) {
                continue;
            }

            list.add(f);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("no protocol family specified");
        }

        this.resolvedAddressTypes = list.toArray(new InternetProtocolFamily[list.size()]);

        return self();
    }

    /**
     * Sets if this resolver has to send a DNS query with the RD (recursion desired) flag set.
     *
     * @param recursionDesired true if recursion is desired
     * @return {@code this}
     */
    public T recursionDesired(boolean recursionDesired) {
        this.recursionDesired = recursionDesired;
        return self();
    }

    /**
     * Sets the maximum allowed number of DNS queries to send when resolving a host name.
     *
     * @param maxQueriesPerResolve the max number of queries
     * @return {@code this}
     */
    public T maxQueriesPerResolve(int maxQueriesPerResolve) {
        this.maxQueriesPerResolve = maxQueriesPerResolve;
        return self();
    }

    /**
     * Sets if this resolver should generate the detailed trace information in an exception message so that
     * it is easier to understand the cause of resolution failure.
     *
     * @param traceEnabled true if trace is enabled
     * @return {@code this}
     */
    public T traceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        return self();
    }

    /**
     * Sets the capacity of the datagram packet buffer (in bytes).  The default value is {@code 4096} bytes.
     *
     * @param maxPayloadSize the capacity of the datagram packet buffer
     * @return {@code this}
     */
    public T maxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
        return self();
    }

    /**
     * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
     * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
     * queries. If you find problems you may want to disable this.
     *
     * @param optResourceEnabled if optional records inclusion is enabled
     * @return {@code this}
     */
    public T optResourceEnabled(boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return self();
    }

    /**
     * @param hostsFileEntriesResolver the {@link HostsFileEntriesResolver} used to first check
     *                                 if the hostname is locally aliased.
     * @return {@code this}
     */
    public T hostsFileEntriesResolver(HostsFileEntriesResolver hostsFileEntriesResolver) {
        this.hostsFileEntriesResolver = hostsFileEntriesResolver;
        return self();
    }

    /**
     * Returns a new {@link DnsNameResolver} instance.
     *
     * @return a {@link DnsNameResolver}
     */
    public DnsNameResolver build() {

        if (resolveCache != null && (minTtl != null || maxTtl != null || negativeTtl != null)) {
            throw new IllegalStateException("resolveCache and TTLs are mutually exclusive");
        }

        DnsCache cache = resolveCache != null ? resolveCache :
                new DefaultDnsCache(intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE), intValue(negativeTtl, 0));

        return build0(cache);
    }

    protected abstract DnsNameResolver build0(DnsCache cache);
}
