/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.intValue;

/**
 * A {@link DnsNameResolver} builder.
 */
public final class DnsNameResolverBuilder {
    volatile EventLoop eventLoop;
    private ChannelFactory<? extends DatagramChannel> channelFactory;
    private ChannelFactory<? extends SocketChannel> socketChannelFactory;
    private DnsCache resolveCache;
    private DnsCnameCache cnameCache;
    private AuthoritativeDnsServerCache authoritativeDnsServerCache;
    private SocketAddress localAddress;
    private Integer minTtl;
    private Integer maxTtl;
    private Integer negativeTtl;
    private long queryTimeoutMillis = -1;
    private ResolvedAddressTypes resolvedAddressTypes = DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
    private boolean completeOncePreferredResolved;
    private boolean recursionDesired = true;
    private int maxQueriesPerResolve = -1;
    private boolean traceEnabled;
    private int maxPayloadSize = 4096;
    private boolean optResourceEnabled = true;
    private HostsFileEntriesResolver hostsFileEntriesResolver = HostsFileEntriesResolver.DEFAULT;
    private DnsServerAddressStreamProvider dnsServerAddressStreamProvider =
            DnsServerAddressStreamProviders.platformDefault();
    private DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory =
            NoopDnsQueryLifecycleObserverFactory.INSTANCE;
    private String[] searchDomains;
    private int ndots = -1;
    private boolean decodeIdn = true;

    /**
     * Creates a new builder.
     */
    public DnsNameResolverBuilder() {
    }

    /**
     * Creates a new builder.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS
     * servers.
     */
    public DnsNameResolverBuilder(EventLoop eventLoop) {
        eventLoop(eventLoop);
    }

    /**
     * Sets the {@link EventLoop} which will perform the communication with the DNS servers.
     *
     * @param eventLoop the {@link EventLoop}
     * @return {@code this}
     */
    public DnsNameResolverBuilder eventLoop(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
        return this;
    }

    protected ChannelFactory<? extends DatagramChannel> channelFactory() {
        return this.channelFactory;
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link DatagramChannel}.
     *
     * @param channelFactory the {@link ChannelFactory}
     * @return {@code this}
     */
    public DnsNameResolverBuilder channelFactory(ChannelFactory<? extends DatagramChannel> channelFactory) {
        this.channelFactory = channelFactory;
        return this;
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type.
     * Use as an alternative to {@link #channelFactory(ChannelFactory)}.
     *
     * @param channelType the type
     * @return {@code this}
     */
    public DnsNameResolverBuilder channelType(Class<? extends DatagramChannel> channelType) {
        return channelFactory(new ReflectiveChannelFactory<DatagramChannel>(channelType));
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link SocketChannel} for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     *
     * @param channelFactory the {@link ChannelFactory} or {@code null}
     *                       if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should not be supported.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelFactory(ChannelFactory<? extends SocketChannel> channelFactory) {
        this.socketChannelFactory = channelFactory;
        return this;
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     * Use as an alternative to {@link #socketChannelFactory(ChannelFactory)}.
     *
     * @param channelType the type or {@code null} if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a>
     *                    should not be supported.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelType(Class<? extends SocketChannel> channelType) {
        if (channelType == null) {
            return socketChannelFactory(null);
        }
        return socketChannelFactory(new ReflectiveChannelFactory<SocketChannel>(channelType));
    }

    /**
     * Sets the cache for resolution results.
     *
     * @param resolveCache the DNS resolution results cache
     * @return {@code this}
     */
    public DnsNameResolverBuilder resolveCache(DnsCache resolveCache) {
        this.resolveCache  = resolveCache;
        return this;
    }

    /**
     * Sets the cache for {@code CNAME} mappings.
     *
     * @param cnameCache the cache used to cache {@code CNAME} mappings for a domain.
     * @return {@code this}
     */
    public DnsNameResolverBuilder cnameCache(DnsCnameCache cnameCache) {
        this.cnameCache  = cnameCache;
        return this;
    }

    /**
     * Set the factory used to generate objects which can observe individual DNS queries.
     * @param lifecycleObserverFactory the factory used to generate objects which can observe individual DNS queries.
     * @return {@code this}
     */
    public DnsNameResolverBuilder dnsQueryLifecycleObserverFactory(DnsQueryLifecycleObserverFactory
                                                                           lifecycleObserverFactory) {
        this.dnsQueryLifecycleObserverFactory = checkNotNull(lifecycleObserverFactory, "lifecycleObserverFactory");
        return this;
    }

    /**
     * Sets the cache for authoritative NS servers
     *
     * @param authoritativeDnsServerCache the authoritative NS servers cache
     * @return {@code this}
     * @deprecated Use {@link #authoritativeDnsServerCache(AuthoritativeDnsServerCache)}
     */
    @Deprecated
    public DnsNameResolverBuilder authoritativeDnsServerCache(DnsCache authoritativeDnsServerCache) {
        this.authoritativeDnsServerCache = new AuthoritativeDnsServerCacheAdapter(authoritativeDnsServerCache);
        return this;
    }

    /**
     * Sets the cache for authoritative NS servers
     *
     * @param authoritativeDnsServerCache the authoritative NS servers cache
     * @return {@code this}
     */
    public DnsNameResolverBuilder authoritativeDnsServerCache(AuthoritativeDnsServerCache authoritativeDnsServerCache) {
        this.authoritativeDnsServerCache = authoritativeDnsServerCache;
        return this;
    }

    /**
     * Configure the address that will be used to bind too. If {@code null} the default will be used.
     * @param localAddress the bind address
     * @return {@code this}
     */
    public DnsNameResolverBuilder localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return this;
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
    public DnsNameResolverBuilder ttl(int minTtl, int maxTtl) {
        this.maxTtl = maxTtl;
        this.minTtl = minTtl;
        return this;
    }

    /**
     * Sets the TTL of the cache for the failed DNS queries (in seconds).
     *
     * @param negativeTtl the TTL for failed cached queries
     * @return {@code this}
     */
    public DnsNameResolverBuilder negativeTtl(int negativeTtl) {
        this.negativeTtl = negativeTtl;
        return this;
    }

    /**
     * Sets the timeout of each DNS query performed by this resolver (in milliseconds).
     *
     * @param queryTimeoutMillis the query timeout
     * @return {@code this}
     */
    public DnsNameResolverBuilder queryTimeoutMillis(long queryTimeoutMillis) {
        this.queryTimeoutMillis = queryTimeoutMillis;
        return this;
    }

    /**
     * Compute a {@link ResolvedAddressTypes} from some {@link InternetProtocolFamily}s.
     * An empty input will return the default value, based on "java.net" System properties.
     * Valid inputs are (), (IPv4), (IPv6), (Ipv4, IPv6) and (IPv6, IPv4).
     * @param internetProtocolFamilies a valid sequence of {@link InternetProtocolFamily}s
     * @return a {@link ResolvedAddressTypes}
     */
    public static ResolvedAddressTypes computeResolvedAddressTypes(InternetProtocolFamily... internetProtocolFamilies) {
        if (internetProtocolFamilies == null || internetProtocolFamilies.length == 0) {
            return DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
        }
        if (internetProtocolFamilies.length > 2) {
            throw new IllegalArgumentException("No more than 2 InternetProtocolFamilies");
        }

        switch(internetProtocolFamilies[0]) {
            case IPv4:
                return (internetProtocolFamilies.length >= 2
                        && internetProtocolFamilies[1] == InternetProtocolFamily.IPv6) ?
                        ResolvedAddressTypes.IPV4_PREFERRED: ResolvedAddressTypes.IPV4_ONLY;
            case IPv6:
                return (internetProtocolFamilies.length >= 2
                        && internetProtocolFamilies[1] == InternetProtocolFamily.IPv4) ?
                        ResolvedAddressTypes.IPV6_PREFERRED: ResolvedAddressTypes.IPV6_ONLY;
            default:
                throw new IllegalArgumentException(
                        "Couldn't resolve ResolvedAddressTypes from InternetProtocolFamily array");
        }
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     * You can use {@link DnsNameResolverBuilder#computeResolvedAddressTypes(InternetProtocolFamily...)}
     * to get a {@link ResolvedAddressTypes} out of some {@link InternetProtocolFamily}s.
     *
     * @param resolvedAddressTypes the address types
     * @return {@code this}
     */
    public DnsNameResolverBuilder resolvedAddressTypes(ResolvedAddressTypes resolvedAddressTypes) {
        this.resolvedAddressTypes = resolvedAddressTypes;
        return this;
    }

    /**
     * If {@code true} {@link DnsNameResolver#resolveAll(String)} will notify the returned {@link Future} as
     * soon as all queries for the preferred address-type are complete.
     *
     * @param completeOncePreferredResolved {@code true} to enable, {@code false} to disable.
     * @return {@code this}
     */
    public DnsNameResolverBuilder completeOncePreferredResolved(boolean completeOncePreferredResolved) {
        this.completeOncePreferredResolved = completeOncePreferredResolved;
        return this;
    }

    /**
     * Sets if this resolver has to send a DNS query with the RD (recursion desired) flag set.
     *
     * @param recursionDesired true if recursion is desired
     * @return {@code this}
     */
    public DnsNameResolverBuilder recursionDesired(boolean recursionDesired) {
        this.recursionDesired = recursionDesired;
        return this;
    }

    /**
     * Sets the maximum allowed number of DNS queries to send when resolving a host name.
     *
     * @param maxQueriesPerResolve the max number of queries
     * @return {@code this}
     */
    public DnsNameResolverBuilder maxQueriesPerResolve(int maxQueriesPerResolve) {
        this.maxQueriesPerResolve = maxQueriesPerResolve;
        return this;
    }

    /**
     * Sets if this resolver should generate the detailed trace information in an exception message so that
     * it is easier to understand the cause of resolution failure.
     *
     * @param traceEnabled true if trace is enabled
     * @return {@code this}
     * @deprecated Prefer to {@linkplain #dnsQueryLifecycleObserverFactory(DnsQueryLifecycleObserverFactory) configure}
     * a {@link LoggingDnsQueryLifeCycleObserverFactory} instead.
     */
    @Deprecated
    public DnsNameResolverBuilder traceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        return this;
    }

    /**
     * Sets the capacity of the datagram packet buffer (in bytes).  The default value is {@code 4096} bytes.
     *
     * @param maxPayloadSize the capacity of the datagram packet buffer
     * @return {@code this}
     */
    public DnsNameResolverBuilder maxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
        return this;
    }

    /**
     * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
     * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
     * queries. If you find problems you may want to disable this.
     *
     * @param optResourceEnabled if optional records inclusion is enabled
     * @return {@code this}
     */
    public DnsNameResolverBuilder optResourceEnabled(boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return this;
    }

    /**
     * @param hostsFileEntriesResolver the {@link HostsFileEntriesResolver} used to first check
     *                                 if the hostname is locally aliased.
     * @return {@code this}
     */
    public DnsNameResolverBuilder hostsFileEntriesResolver(HostsFileEntriesResolver hostsFileEntriesResolver) {
        this.hostsFileEntriesResolver = hostsFileEntriesResolver;
        return this;
    }

    protected DnsServerAddressStreamProvider nameServerProvider() {
        return this.dnsServerAddressStreamProvider;
    }

    /**
     * Set the {@link DnsServerAddressStreamProvider} which is used to determine which DNS server is used to resolve
     * each hostname.
     * @return {@code this}.
     */
    public DnsNameResolverBuilder nameServerProvider(DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        this.dnsServerAddressStreamProvider =
                checkNotNull(dnsServerAddressStreamProvider, "dnsServerAddressStreamProvider");
        return this;
    }

    /**
     * Set the list of search domains of the resolver.
     *
     * @param searchDomains the search domains
     * @return {@code this}
     */
    public DnsNameResolverBuilder searchDomains(Iterable<String> searchDomains) {
        checkNotNull(searchDomains, "searchDomains");

        final List<String> list = new ArrayList<String>(4);

        for (String f : searchDomains) {
            if (f == null) {
                break;
            }

            // Avoid duplicate entries.
            if (list.contains(f)) {
                continue;
            }

            list.add(f);
        }

        this.searchDomains = list.toArray(new String[0]);
        return this;
    }

  /**
   * Set the number of dots which must appear in a name before an initial absolute query is made.
   * The default value is {@code 1}.
   *
   * @param ndots the ndots value
   * @return {@code this}
   */
    public DnsNameResolverBuilder ndots(int ndots) {
        this.ndots = ndots;
        return this;
    }

    private DnsCache newCache() {
        return new DefaultDnsCache(intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE), intValue(negativeTtl, 0));
    }

    private AuthoritativeDnsServerCache newAuthoritativeDnsServerCache() {
        return new DefaultAuthoritativeDnsServerCache(
                intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE),
                // Let us use the sane ordering as DnsNameResolver will be used when returning
                // nameservers from the cache.
                new NameServerComparator(DnsNameResolver.preferredAddressType(resolvedAddressTypes).addressType()));
    }

    private DnsCnameCache newCnameCache() {
        return new DefaultDnsCnameCache(
                intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE));
    }

    /**
     * Set if domain / host names should be decoded to unicode when received.
     * See <a href="https://tools.ietf.org/html/rfc3492">rfc3492</a>.
     *
     * @param decodeIdn if should get decoded
     * @return {@code this}
     */
    public DnsNameResolverBuilder decodeIdn(boolean decodeIdn) {
        this.decodeIdn = decodeIdn;
        return this;
    }

    /**
     * Returns a new {@link DnsNameResolver} instance.
     *
     * @return a {@link DnsNameResolver}
     */
    public DnsNameResolver build() {
        if (eventLoop == null) {
            throw new IllegalStateException("eventLoop should be specified to build a DnsNameResolver.");
        }

        if (resolveCache != null && (minTtl != null || maxTtl != null || negativeTtl != null)) {
            throw new IllegalStateException("resolveCache and TTLs are mutually exclusive");
        }

        if (authoritativeDnsServerCache != null && (minTtl != null || maxTtl != null || negativeTtl != null)) {
            throw new IllegalStateException("authoritativeDnsServerCache and TTLs are mutually exclusive");
        }

        DnsCache resolveCache = this.resolveCache != null ? this.resolveCache : newCache();
        DnsCnameCache cnameCache = this.cnameCache != null ? this.cnameCache : newCnameCache();
        AuthoritativeDnsServerCache authoritativeDnsServerCache = this.authoritativeDnsServerCache != null ?
                this.authoritativeDnsServerCache : newAuthoritativeDnsServerCache();
        return new DnsNameResolver(
                eventLoop,
                channelFactory,
                socketChannelFactory,
                resolveCache,
                cnameCache,
                authoritativeDnsServerCache,
                localAddress,
                dnsQueryLifecycleObserverFactory,
                queryTimeoutMillis,
                resolvedAddressTypes,
                recursionDesired,
                maxQueriesPerResolve,
                traceEnabled,
                maxPayloadSize,
                optResourceEnabled,
                hostsFileEntriesResolver,
                dnsServerAddressStreamProvider,
                searchDomains,
                ndots,
                decodeIdn,
                completeOncePreferredResolved);
    }

    /**
     * Creates a copy of this {@link DnsNameResolverBuilder}
     *
     * @return {@link DnsNameResolverBuilder}
     */
    public DnsNameResolverBuilder copy() {
        DnsNameResolverBuilder copiedBuilder = new DnsNameResolverBuilder();

        if (eventLoop != null) {
            copiedBuilder.eventLoop(eventLoop);
        }

        if (channelFactory != null) {
            copiedBuilder.channelFactory(channelFactory);
        }

        if (socketChannelFactory != null) {
            copiedBuilder.socketChannelFactory(socketChannelFactory);
        }

        if (resolveCache != null) {
            copiedBuilder.resolveCache(resolveCache);
        }

        if (cnameCache != null) {
            copiedBuilder.cnameCache(cnameCache);
        }
        if (maxTtl != null && minTtl != null) {
            copiedBuilder.ttl(minTtl, maxTtl);
        }

        if (negativeTtl != null) {
            copiedBuilder.negativeTtl(negativeTtl);
        }

        if (authoritativeDnsServerCache != null) {
            copiedBuilder.authoritativeDnsServerCache(authoritativeDnsServerCache);
        }

        if (dnsQueryLifecycleObserverFactory != null) {
            copiedBuilder.dnsQueryLifecycleObserverFactory(dnsQueryLifecycleObserverFactory);
        }

        copiedBuilder.queryTimeoutMillis(queryTimeoutMillis);
        copiedBuilder.resolvedAddressTypes(resolvedAddressTypes);
        copiedBuilder.recursionDesired(recursionDesired);
        copiedBuilder.maxQueriesPerResolve(maxQueriesPerResolve);
        copiedBuilder.traceEnabled(traceEnabled);
        copiedBuilder.maxPayloadSize(maxPayloadSize);
        copiedBuilder.optResourceEnabled(optResourceEnabled);
        copiedBuilder.hostsFileEntriesResolver(hostsFileEntriesResolver);

        if (dnsServerAddressStreamProvider != null) {
            copiedBuilder.nameServerProvider(dnsServerAddressStreamProvider);
        }

        if (searchDomains != null) {
            copiedBuilder.searchDomains(Arrays.asList(searchDomains));
        }

        copiedBuilder.ndots(ndots);
        copiedBuilder.decodeIdn(decodeIdn);
        copiedBuilder.completeOncePreferredResolved(completeOncePreferredResolved);
        copiedBuilder.localAddress(localAddress);
        return copiedBuilder;
    }
}
