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
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
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

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolverBuilder.class);
    static final SocketAddress DEFAULT_LOCAL_ADDRESS = new InetSocketAddress(0);

    volatile EventLoop eventLoop;
    private ChannelFactory<? extends DatagramChannel> datagramChannelFactory;
    private ChannelFactory<? extends SocketChannel> socketChannelFactory;
    private boolean retryOnTimeout;

    private DnsCache resolveCache;
    private DnsCnameCache cnameCache;
    private AuthoritativeDnsServerCache authoritativeDnsServerCache;
    private SocketAddress localAddress = DEFAULT_LOCAL_ADDRESS;
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
    private DnsServerAddressStream queryDnsServerAddressStream;
    private DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory =
            NoopDnsQueryLifecycleObserverFactory.INSTANCE;
    private String[] searchDomains;
    private int ndots = -1;
    private boolean decodeIdn = true;

    private int maxNumConsolidation;
    private DnsNameResolverChannelStrategy datagramChannelStrategy = DnsNameResolverChannelStrategy.ChannelPerResolver;

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

    @Deprecated
    protected ChannelFactory<? extends DatagramChannel> channelFactory() {
        return this.datagramChannelFactory;
    }

    ChannelFactory<? extends DatagramChannel> datagramChannelFactory() {
        return this.datagramChannelFactory;
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link DatagramChannel}.
     * <p>
     * If <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should be supported as well it is required
     * to call the {@link #socketChannelFactory(ChannelFactory) or {@link #socketChannelType(Class)}} method.
     *
     * @param datagramChannelFactory the {@link ChannelFactory}
     * @return {@code this}
     * @deprecated use {@link #datagramChannelFactory(ChannelFactory)}
     */
    @Deprecated
    public DnsNameResolverBuilder channelFactory(ChannelFactory<? extends DatagramChannel> datagramChannelFactory) {
        datagramChannelFactory(datagramChannelFactory);
        return this;
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link DatagramChannel}.
     * <p>
     * If <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should be supported as well it is required
     * to call the {@link #socketChannelFactory(ChannelFactory) or {@link #socketChannelType(Class)}} method.
     *
     * @param datagramChannelFactory the {@link ChannelFactory}
     * @return {@code this}
     */
    public DnsNameResolverBuilder datagramChannelFactory(
            ChannelFactory<? extends DatagramChannel> datagramChannelFactory) {
        this.datagramChannelFactory = datagramChannelFactory;
        return this;
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type.
     * Use as an alternative to {@link #channelFactory(ChannelFactory)}.
     * <p>
     * If <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should be supported as well it is required
     * to call the {@link #socketChannelFactory(ChannelFactory) or {@link #socketChannelType(Class)}} method.
     *
     * @param channelType the type
     * @return {@code this}
     * @deprecated use {@link #datagramChannelType(Class)}
     */
    @Deprecated
    public DnsNameResolverBuilder channelType(Class<? extends DatagramChannel> channelType) {
        return datagramChannelFactory(new ReflectiveChannelFactory<DatagramChannel>(channelType));
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type.
     * Use as an alternative to {@link #datagramChannelFactory(ChannelFactory)}.
     * <p>
     * If <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should be supported as well it is required
     * to call the {@link #socketChannelFactory(ChannelFactory) or {@link #socketChannelType(Class)}} method.
     *
     * @param channelType the type
     * @return {@code this}
     */
    public DnsNameResolverBuilder datagramChannelType(Class<? extends DatagramChannel> channelType) {
        return datagramChannelFactory(new ReflectiveChannelFactory<DatagramChannel>(channelType));
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link SocketChannel} for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     * <p>
     * TCP fallback is <strong>not</strong> enabled by default and must be enabled by providing a non-null
     * {@link ChannelFactory} for this method.
     *
     * @param channelFactory the {@link ChannelFactory} or {@code null}
     *                       if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should not be supported.
     *                       By default, TCP fallback is not enabled.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelFactory(ChannelFactory<? extends SocketChannel> channelFactory) {
        return socketChannelFactory(channelFactory, false);
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     * Use as an alternative to {@link #socketChannelFactory(ChannelFactory)}.
     * <p>
     * TCP fallback is <strong>not</strong> enabled by default and must be enabled by providing a non-null
     * {@code channelType} for this method.
     *
     * @param channelType the type or {@code null} if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a>
     *                    should not be supported. By default, TCP fallback is not enabled.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelType(Class<? extends SocketChannel> channelType) {
        return socketChannelType(channelType, false);
    }

    /**
     * Sets the {@link ChannelFactory} that will create a {@link SocketChannel} for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     * <p>
     * TCP fallback is <strong>not</strong> enabled by default and must be enabled by providing a non-null
     * {@link ChannelFactory} for this method.
     *
     * @param channelFactory the {@link ChannelFactory} or {@code null}
     *                       if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> should not be supported.
     *                       By default, TCP fallback is not enabled.
     * @param retryOnTimeout if {@code true} the {@link DnsNameResolver} will also fallback to TCP if a timeout
     *                       was detected, if {@code false} it will only try to use TCP if the response was marked
     *                       as truncated.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelFactory(
            ChannelFactory<? extends SocketChannel> channelFactory, boolean retryOnTimeout) {
        this.socketChannelFactory = channelFactory;
        this.retryOnTimeout = retryOnTimeout;
        return this;
    }

    /**
     * Sets the {@link ChannelFactory} as a {@link ReflectiveChannelFactory} of this type for
     * <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a> if needed.
     * Use as an alternative to {@link #socketChannelFactory(ChannelFactory)}.
     * <p>
     * TCP fallback is <strong>not</strong> enabled by default and must be enabled by providing a non-null
     * {@code channelType} for this method.
     *
     * @param channelType the type or {@code null} if <a href="https://tools.ietf.org/html/rfc7766">TCP fallback</a>
     *                    should not be supported. By default, TCP fallback is not enabled.
     * @param retryOnTimeout if {@code true} the {@link DnsNameResolver} will also fallback to TCP if a timeout
     *                       was detected, if {@code false} it will only try to use TCP if the response was marked
     *                       as truncated.
     * @return {@code this}
     */
    public DnsNameResolverBuilder socketChannelType(
            Class<? extends SocketChannel> channelType, boolean retryOnTimeout) {
        if (channelType == null) {
            return socketChannelFactory(null, retryOnTimeout);
        }
        return socketChannelFactory(new ReflectiveChannelFactory<SocketChannel>(channelType), retryOnTimeout);
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
     * {@code 0} disables the timeout. If not set or a negative number is set, the default timeout is used.
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
     *
     * @param internetProtocolFamilies a valid sequence of {@link InternetProtocolFamily}s
     * @return a {@link ResolvedAddressTypes}
     * @deprecated use {@link #toResolvedAddressTypes(SocketProtocolFamily...)}
     */
    @Deprecated
    public static ResolvedAddressTypes computeResolvedAddressTypes(InternetProtocolFamily... internetProtocolFamilies) {
        if (internetProtocolFamilies == null || internetProtocolFamilies.length == 0) {
            return DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
        }
        if (internetProtocolFamilies.length > 2) {
            throw new IllegalArgumentException("No more than 2 InternetProtocolFamilies");
        }
        return toResolvedAddressTypes(toSocketProtocolFamilies(internetProtocolFamilies));
    }

    private static SocketProtocolFamily[] toSocketProtocolFamilies(InternetProtocolFamily... internetProtocolFamilies) {
        if (internetProtocolFamilies == null || internetProtocolFamilies.length == 0) {
            return null;
        }
        SocketProtocolFamily[] socketProtocolFamilies = new SocketProtocolFamily[internetProtocolFamilies.length];
        for (int i = 0; i < internetProtocolFamilies.length; i++) {
            socketProtocolFamilies[i] = internetProtocolFamilies[i].toSocketProtocolFamily();
        }
        return socketProtocolFamilies;
    }

    /**
     * Compute a {@link ResolvedAddressTypes} from some {@link SocketProtocolFamily}s.
     * An empty input will return the default value, based on "java.net" System properties.
     * Valid inputs are (), (IPv4), (IPv6), (Ipv4, IPv6) and (IPv6, IPv4).
     * @param socketProtocolFamilies a valid sequence of {@link SocketProtocolFamily}s
     * @return a {@link ResolvedAddressTypes}
     */
    public static ResolvedAddressTypes toResolvedAddressTypes(SocketProtocolFamily... socketProtocolFamilies) {
        if (socketProtocolFamilies == null || socketProtocolFamilies.length == 0) {
            return DnsNameResolver.DEFAULT_RESOLVE_ADDRESS_TYPES;
        }
        if (socketProtocolFamilies.length > 2) {
            throw new IllegalArgumentException("No more than 2 socketProtocolFamilies");
        }

        switch(socketProtocolFamilies[0]) {
            case INET:
                return (socketProtocolFamilies.length >= 2
                        && socketProtocolFamilies[1] == SocketProtocolFamily.INET6) ?
                        ResolvedAddressTypes.IPV4_PREFERRED: ResolvedAddressTypes.IPV4_ONLY;
            case INET6:
                return (socketProtocolFamilies.length >= 2
                        && socketProtocolFamilies[1] == SocketProtocolFamily.INET) ?
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

    protected DnsServerAddressStream queryServerAddressStream() {
        return this.queryDnsServerAddressStream;
    }

    /**
     * Set the {@link DnsServerAddressStream} which provides the server address for DNS queries.
     * @return {@code this}.
     */
    public DnsNameResolverBuilder queryServerAddressStream(DnsServerAddressStream queryServerAddressStream) {
        this.queryDnsServerAddressStream =
                checkNotNull(queryServerAddressStream, "queryServerAddressStream");
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

        this.searchDomains = list.toArray(EmptyArrays.EMPTY_STRINGS);
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

   DnsCache getOrNewCache() {
        if (this.resolveCache != null) {
            return this.resolveCache;
        }
        return new DefaultDnsCache(intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE), intValue(negativeTtl, 0));
    }

   AuthoritativeDnsServerCache getOrNewAuthoritativeDnsServerCache() {
        if (this.authoritativeDnsServerCache != null) {
            return this.authoritativeDnsServerCache;
        }
        return new DefaultAuthoritativeDnsServerCache(
                intValue(minTtl, 0), intValue(maxTtl, Integer.MAX_VALUE),
                // Let us use the sane ordering as DnsNameResolver will be used when returning
                // nameservers from the cache.
                new NameServerComparator(DnsNameResolver.addressType(
                        DnsNameResolver.preferredAddressType(resolvedAddressTypes))));
    }

    private DnsServerAddressStream newQueryServerAddressStream(
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        return new ThreadLocalNameServerAddressStream(dnsServerAddressStreamProvider);
    }

   DnsCnameCache getOrNewCnameCache() {
        if (this.cnameCache != null) {
            return this.cnameCache;
        }
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
     * Set the maximum size of the cache that is used to consolidate lookups for different hostnames when in-flight.
     * This means if multiple lookups are done for the same hostname and still in-flight only one actual query will
     * be made and the result will be cascaded to the others.
     *
     * @param maxNumConsolidation the maximum lookups to consolidate (different hostnames), or {@code 0} if
     *                            no consolidation should be performed.
     * @return {@code this}
     */
    public DnsNameResolverBuilder consolidateCacheSize(int maxNumConsolidation) {
        this.maxNumConsolidation = ObjectUtil.checkPositiveOrZero(maxNumConsolidation, "maxNumConsolidation");
        return this;
    }

    /**
     * Set the strategy that is used to determine how a {@link DatagramChannel} is used by the resolver for sending
     * queries over UDP protocol.
     *
     * @param datagramChannelStrategy  the {@link DnsNameResolverChannelStrategy} to use when doing queries over
     *                                 UDP protocol.
     * @return {@code this}
     */
    public DnsNameResolverBuilder datagramChannelStrategy(DnsNameResolverChannelStrategy datagramChannelStrategy) {
        this.datagramChannelStrategy = ObjectUtil.checkNotNull(datagramChannelStrategy, "datagramChannelStrategy");
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
            logger.debug("resolveCache and TTLs are mutually exclusive. TTLs are ignored.");
        }

        if (cnameCache != null && (minTtl != null || maxTtl != null || negativeTtl != null)) {
            logger.debug("cnameCache and TTLs are mutually exclusive. TTLs are ignored.");
        }

        if (authoritativeDnsServerCache != null && (minTtl != null || maxTtl != null || negativeTtl != null)) {
            logger.debug("authoritativeDnsServerCache and TTLs are mutually exclusive. TTLs are ignored.");
        }

        DnsCache resolveCache = getOrNewCache();
        DnsCnameCache cnameCache = getOrNewCnameCache();
        AuthoritativeDnsServerCache authoritativeDnsServerCache = getOrNewAuthoritativeDnsServerCache();

        DnsServerAddressStream queryDnsServerAddressStream = this.queryDnsServerAddressStream != null ?
                this.queryDnsServerAddressStream : newQueryServerAddressStream(dnsServerAddressStreamProvider);

        return new DnsNameResolver(
                eventLoop,
                datagramChannelFactory,
                socketChannelFactory,
                retryOnTimeout,
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
                queryDnsServerAddressStream,
                searchDomains,
                ndots,
                decodeIdn,
                completeOncePreferredResolved,
                maxNumConsolidation,
                datagramChannelStrategy);
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

        if (datagramChannelFactory != null) {
            copiedBuilder.datagramChannelFactory(datagramChannelFactory);
        }

        copiedBuilder.socketChannelFactory(socketChannelFactory, retryOnTimeout);

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

        if (queryDnsServerAddressStream != null) {
            copiedBuilder.queryServerAddressStream(queryDnsServerAddressStream);
        }

        if (searchDomains != null) {
            copiedBuilder.searchDomains(Arrays.asList(searchDomains));
        }

        copiedBuilder.ndots(ndots);
        copiedBuilder.decodeIdn(decodeIdn);
        copiedBuilder.completeOncePreferredResolved(completeOncePreferredResolved);
        copiedBuilder.localAddress(localAddress);
        copiedBuilder.consolidateCacheSize(maxNumConsolidation);
        copiedBuilder.datagramChannelStrategy(datagramChannelStrategy);
        return copiedBuilder;
    }
}
