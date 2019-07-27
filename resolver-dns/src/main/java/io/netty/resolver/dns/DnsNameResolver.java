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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.resolver.HostsFileEntries;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;
import static io.netty.resolver.dns.UnixResolverDnsServerAddressStreamProvider.parseEtcResolverFirstNdots;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * A DNS-based {@link InetNameResolver}.
 */
@UnstableApi
public class DnsNameResolver extends InetNameResolver {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);
    private static final String LOCALHOST = "localhost";
    private static final InetAddress LOCALHOST_ADDRESS;
    private static final DnsRecord[] EMPTY_ADDITIONALS = new DnsRecord[0];
    private static final DnsRecordType[] IPV4_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A};
    private static final InternetProtocolFamily[] IPV4_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {InternetProtocolFamily.IPv4};
    private static final DnsRecordType[] IPV4_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A, DnsRecordType.AAAA};
    private static final InternetProtocolFamily[] IPV4_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {InternetProtocolFamily.IPv4, InternetProtocolFamily.IPv6};
    private static final DnsRecordType[] IPV6_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA};
    private static final InternetProtocolFamily[] IPV6_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {InternetProtocolFamily.IPv6};
    private static final DnsRecordType[] IPV6_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA, DnsRecordType.A};
    private static final InternetProtocolFamily[] IPV6_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {InternetProtocolFamily.IPv6, InternetProtocolFamily.IPv4};

    static final ResolvedAddressTypes DEFAULT_RESOLVE_ADDRESS_TYPES;
    static final String[] DEFAULT_SEARCH_DOMAINS;
    private static final int DEFAULT_NDOTS;

    static {
        if (NetUtil.isIpV4StackPreferred()) {
            DEFAULT_RESOLVE_ADDRESS_TYPES = ResolvedAddressTypes.IPV4_ONLY;
            LOCALHOST_ADDRESS = NetUtil.LOCALHOST4;
        } else {
            if (NetUtil.isIpV6AddressesPreferred()) {
                DEFAULT_RESOLVE_ADDRESS_TYPES = ResolvedAddressTypes.IPV6_PREFERRED;
                LOCALHOST_ADDRESS = NetUtil.LOCALHOST6;
            } else {
                DEFAULT_RESOLVE_ADDRESS_TYPES = ResolvedAddressTypes.IPV4_PREFERRED;
                LOCALHOST_ADDRESS = NetUtil.LOCALHOST4;
            }
        }
    }

    static {
        String[] searchDomains;
        try {
            List<String> list = PlatformDependent.isWindows()
                    ? getSearchDomainsHack()
                    : UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains();
            searchDomains = list.toArray(new String[0]);
        } catch (Exception ignore) {
            // Failed to get the system name search domain list.
            searchDomains = EmptyArrays.EMPTY_STRINGS;
        }
        DEFAULT_SEARCH_DOMAINS = searchDomains;

        int ndots;
        try {
            ndots = parseEtcResolverFirstNdots();
        } catch (Exception ignore) {
            ndots = UnixResolverDnsServerAddressStreamProvider.DEFAULT_NDOTS;
        }
        DEFAULT_NDOTS = ndots;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getSearchDomainsHack() throws Exception {
        // This code on Java 9+ yields a warning about illegal reflective access that will be denied in
        // a future release. There doesn't seem to be a better way to get search domains for Windows yet.
        Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
        Method open = configClass.getMethod("open");
        Method nameservers = configClass.getMethod("searchlist");
        Object instance = open.invoke(null);

        return (List<String>) nameservers.invoke(instance);
    }

    private static final DatagramDnsResponseDecoder DECODER = new DatagramDnsResponseDecoder();
    private static final DatagramDnsQueryEncoder ENCODER = new DatagramDnsQueryEncoder();

    final Future<Channel> channelFuture;
    final Channel ch;

    // Comparator that ensures we will try first to use the nameservers that use our preferred address type.
    private final Comparator<InetSocketAddress> nameServerComparator;
    /**
     * Manages the {@link DnsQueryContext}s in progress and their query IDs.
     */
    final DnsQueryContextManager queryContextManager = new DnsQueryContextManager();

    /**
     * Cache for {@link #doResolve(String, Promise)} and {@link #doResolveAll(String, Promise)}.
     */
    private final DnsCache resolveCache;
    private final AuthoritativeDnsServerCache authoritativeDnsServerCache;
    private final DnsCnameCache cnameCache;

    private final FastThreadLocal<DnsServerAddressStream> nameServerAddrStream =
            new FastThreadLocal<DnsServerAddressStream>() {
                @Override
                protected DnsServerAddressStream initialValue() {
                    return dnsServerAddressStreamProvider.nameServerAddressStream("");
                }
            };

    private final long queryTimeoutMillis;
    private final int maxQueriesPerResolve;
    private final ResolvedAddressTypes resolvedAddressTypes;
    private final InternetProtocolFamily[] resolvedInternetProtocolFamilies;
    private final boolean recursionDesired;
    private final int maxPayloadSize;
    private final boolean optResourceEnabled;
    private final HostsFileEntriesResolver hostsFileEntriesResolver;
    private final DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private final String[] searchDomains;
    private final int ndots;
    private final boolean supportsAAAARecords;
    private final boolean supportsARecords;
    private final InternetProtocolFamily preferredAddressType;
    private final DnsRecordType[] resolveRecordTypes;
    private final boolean decodeIdn;
    private final DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory;

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param resolveCache the DNS resolved entries cache
     * @param authoritativeDnsServerCache the cache used to find the authoritative DNS server for a domain
     * @param dnsQueryLifecycleObserverFactory used to generate new instances of {@link DnsQueryLifecycleObserver} which
     *                                         can be used to track metrics for DNS servers.
     * @param queryTimeoutMillis timeout of each DNS query in millis
     * @param resolvedAddressTypes the preferred address types
     * @param recursionDesired if recursion desired flag must be set
     * @param maxQueriesPerResolve the maximum allowed number of DNS queries for a given name resolution
     * @param traceEnabled if trace is enabled
     * @param maxPayloadSize the capacity of the datagram packet buffer
     * @param optResourceEnabled if automatic inclusion of a optional records is enabled
     * @param hostsFileEntriesResolver the {@link HostsFileEntriesResolver} used to check for local aliases
     * @param dnsServerAddressStreamProvider The {@link DnsServerAddressStreamProvider} used to determine the name
     *                                       servers for each hostname lookup.
     * @param searchDomains the list of search domain
     *                      (can be null, if so, will try to default to the underlying platform ones)
     * @param ndots the ndots value
     * @param decodeIdn {@code true} if domain / host names should be decoded to unicode when received.
     *                        See <a href="https://tools.ietf.org/html/rfc3492">rfc3492</a>.
     * @deprecated Use {@link DnsNameResolverBuilder}.
     */
    @Deprecated
    public DnsNameResolver(
            EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory,
            final DnsCache resolveCache,
            final DnsCache authoritativeDnsServerCache,
            DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory,
            long queryTimeoutMillis,
            ResolvedAddressTypes resolvedAddressTypes,
            boolean recursionDesired,
            int maxQueriesPerResolve,
            boolean traceEnabled,
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn) {
        this(eventLoop, channelFactory, resolveCache,
             new AuthoritativeDnsServerCacheAdapter(authoritativeDnsServerCache), dnsQueryLifecycleObserverFactory,
             queryTimeoutMillis, resolvedAddressTypes, recursionDesired, maxQueriesPerResolve, traceEnabled,
             maxPayloadSize, optResourceEnabled, hostsFileEntriesResolver, dnsServerAddressStreamProvider,
             searchDomains, ndots, decodeIdn);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param resolveCache the DNS resolved entries cache
     * @param authoritativeDnsServerCache the cache used to find the authoritative DNS server for a domain
     * @param dnsQueryLifecycleObserverFactory used to generate new instances of {@link DnsQueryLifecycleObserver} which
     *                                         can be used to track metrics for DNS servers.
     * @param queryTimeoutMillis timeout of each DNS query in millis
     * @param resolvedAddressTypes the preferred address types
     * @param recursionDesired if recursion desired flag must be set
     * @param maxQueriesPerResolve the maximum allowed number of DNS queries for a given name resolution
     * @param traceEnabled if trace is enabled
     * @param maxPayloadSize the capacity of the datagram packet buffer
     * @param optResourceEnabled if automatic inclusion of a optional records is enabled
     * @param hostsFileEntriesResolver the {@link HostsFileEntriesResolver} used to check for local aliases
     * @param dnsServerAddressStreamProvider The {@link DnsServerAddressStreamProvider} used to determine the name
     *                                       servers for each hostname lookup.
     * @param searchDomains the list of search domain
     *                      (can be null, if so, will try to default to the underlying platform ones)
     * @param ndots the ndots value
     * @param decodeIdn {@code true} if domain / host names should be decoded to unicode when received.
     *                        See <a href="https://tools.ietf.org/html/rfc3492">rfc3492</a>.
     * @deprecated Use {@link DnsNameResolverBuilder}.
     */
    @Deprecated
    public DnsNameResolver(
            EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory,
            final DnsCache resolveCache,
            final AuthoritativeDnsServerCache authoritativeDnsServerCache,
            DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory,
            long queryTimeoutMillis,
            ResolvedAddressTypes resolvedAddressTypes,
            boolean recursionDesired,
            int maxQueriesPerResolve,
            boolean traceEnabled,
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn) {
        this(eventLoop, channelFactory, resolveCache, NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache,
             dnsQueryLifecycleObserverFactory, queryTimeoutMillis, resolvedAddressTypes, recursionDesired,
             maxQueriesPerResolve, traceEnabled, maxPayloadSize, optResourceEnabled, hostsFileEntriesResolver,
             dnsServerAddressStreamProvider, searchDomains, ndots, decodeIdn);
    }

    DnsNameResolver(
            EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory,
            final DnsCache resolveCache,
            final DnsCnameCache cnameCache,
            final AuthoritativeDnsServerCache authoritativeDnsServerCache,
            DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory,
            long queryTimeoutMillis,
            ResolvedAddressTypes resolvedAddressTypes,
            boolean recursionDesired,
            int maxQueriesPerResolve,
            boolean traceEnabled,
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn) {
        super(eventLoop);
        this.queryTimeoutMillis = checkPositive(queryTimeoutMillis, "queryTimeoutMillis");
        this.resolvedAddressTypes = resolvedAddressTypes != null ? resolvedAddressTypes : DEFAULT_RESOLVE_ADDRESS_TYPES;
        this.recursionDesired = recursionDesired;
        this.maxQueriesPerResolve = checkPositive(maxQueriesPerResolve, "maxQueriesPerResolve");
        this.maxPayloadSize = checkPositive(maxPayloadSize, "maxPayloadSize");
        this.optResourceEnabled = optResourceEnabled;
        this.hostsFileEntriesResolver = checkNotNull(hostsFileEntriesResolver, "hostsFileEntriesResolver");
        this.dnsServerAddressStreamProvider =
                checkNotNull(dnsServerAddressStreamProvider, "dnsServerAddressStreamProvider");
        this.resolveCache = checkNotNull(resolveCache, "resolveCache");
        this.cnameCache = checkNotNull(cnameCache, "cnameCache");
        this.dnsQueryLifecycleObserverFactory = traceEnabled ?
                dnsQueryLifecycleObserverFactory instanceof NoopDnsQueryLifecycleObserverFactory ?
                        new TraceDnsQueryLifeCycleObserverFactory() :
                        new BiDnsQueryLifecycleObserverFactory(new TraceDnsQueryLifeCycleObserverFactory(),
                                                               dnsQueryLifecycleObserverFactory) :
                checkNotNull(dnsQueryLifecycleObserverFactory, "dnsQueryLifecycleObserverFactory");
        this.searchDomains = searchDomains != null ? searchDomains.clone() : DEFAULT_SEARCH_DOMAINS;
        this.ndots = ndots >= 0 ? ndots : DEFAULT_NDOTS;
        this.decodeIdn = decodeIdn;

        switch (this.resolvedAddressTypes) {
            case IPV4_ONLY:
                supportsAAAARecords = false;
                supportsARecords = true;
                resolveRecordTypes = IPV4_ONLY_RESOLVED_RECORD_TYPES;
                resolvedInternetProtocolFamilies = IPV4_ONLY_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV4_PREFERRED:
                supportsAAAARecords = true;
                supportsARecords = true;
                resolveRecordTypes = IPV4_PREFERRED_RESOLVED_RECORD_TYPES;
                resolvedInternetProtocolFamilies = IPV4_PREFERRED_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV6_ONLY:
                supportsAAAARecords = true;
                supportsARecords = false;
                resolveRecordTypes = IPV6_ONLY_RESOLVED_RECORD_TYPES;
                resolvedInternetProtocolFamilies = IPV6_ONLY_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV6_PREFERRED:
                supportsAAAARecords = true;
                supportsARecords = true;
                resolveRecordTypes = IPV6_PREFERRED_RESOLVED_RECORD_TYPES;
                resolvedInternetProtocolFamilies = IPV6_PREFERRED_RESOLVED_PROTOCOL_FAMILIES;
                break;
            default:
                throw new IllegalArgumentException("Unknown ResolvedAddressTypes " + resolvedAddressTypes);
        }
        preferredAddressType = preferredAddressType(this.resolvedAddressTypes);
        this.authoritativeDnsServerCache = checkNotNull(authoritativeDnsServerCache, "authoritativeDnsServerCache");
        nameServerComparator = new NameServerComparator(preferredAddressType.addressType());

        Bootstrap b = new Bootstrap();
        b.group(executor());
        b.channelFactory(channelFactory);
        b.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
        final DnsResponseHandler responseHandler = new DnsResponseHandler(executor().<Channel>newPromise());
        b.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) throws Exception {
                ch.pipeline().addLast(DECODER, ENCODER, responseHandler);
            }
        });

        channelFuture = responseHandler.channelActivePromise;
        ChannelFuture future = b.register();
        Throwable cause = future.cause();
        if (cause != null) {
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unable to create / register Channel", cause);
        }
        ch = future.channel();
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));

        ch.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                resolveCache.clear();
                cnameCache.clear();
                authoritativeDnsServerCache.clear();
            }
        });
    }

    static InternetProtocolFamily preferredAddressType(ResolvedAddressTypes resolvedAddressTypes) {
        switch (resolvedAddressTypes) {
        case IPV4_ONLY:
        case IPV4_PREFERRED:
            return InternetProtocolFamily.IPv4;
        case IPV6_ONLY:
        case IPV6_PREFERRED:
            return InternetProtocolFamily.IPv6;
        default:
            throw new IllegalArgumentException("Unknown ResolvedAddressTypes " + resolvedAddressTypes);
        }
    }

    // Only here to override in unit tests.
    InetSocketAddress newRedirectServerAddress(InetAddress server) {
        return new InetSocketAddress(server, DNS_PORT);
    }

    final DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory() {
        return dnsQueryLifecycleObserverFactory;
    }

    /**
     * Creates a new {@link DnsServerAddressStream} to following a redirected DNS query. By overriding this
     * it provides the opportunity to sort the name servers before following a redirected DNS query.
     *
     * @param hostname the hostname.
     * @param nameservers The addresses of the DNS servers which are used in the event of a redirect. This may
     *                    contain resolved and unresolved addresses so the used {@link DnsServerAddressStream} must
     *                    allow unresolved addresses if you want to include these as well.
     * @return A {@link DnsServerAddressStream} which will be used to follow the DNS redirect or {@code null} if
     *         none should be followed.
     */
    protected DnsServerAddressStream newRedirectDnsServerStream(
            @SuppressWarnings("unused") String hostname, List<InetSocketAddress> nameservers) {
        DnsServerAddressStream cached = authoritativeDnsServerCache().get(hostname);
        if (cached == null || cached.size() == 0) {
            // If there is no cache hit (which may be the case for example when a NoopAuthoritativeDnsServerCache
            // is used), we will just directly use the provided nameservers.
            Collections.sort(nameservers, nameServerComparator);
            return new SequentialDnsServerAddressStream(nameservers, 0);
        }
        return cached;
    }

    /**
     * Returns the resolution cache.
     */
    public DnsCache resolveCache() {
        return resolveCache;
    }

    /**
     * Returns the {@link DnsCnameCache}.
     */
    DnsCnameCache cnameCache() {
        return cnameCache;
    }

    /**
     * Returns the cache used for authoritative DNS servers for a domain.
     */
    public AuthoritativeDnsServerCache authoritativeDnsServerCache() {
        return authoritativeDnsServerCache;
    }

    /**
     * Returns the timeout of each DNS query performed by this resolver (in milliseconds).
     * The default value is 5 seconds.
     */
    public long queryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    /**
     * Returns the {@link ResolvedAddressTypes} resolved by {@link #resolve(String)}.
     * The default value depends on the value of the system property {@code "java.net.preferIPv6Addresses"}.
     */
    public ResolvedAddressTypes resolvedAddressTypes() {
        return resolvedAddressTypes;
    }

    InternetProtocolFamily[] resolvedInternetProtocolFamiliesUnsafe() {
        return resolvedInternetProtocolFamilies;
    }

    final String[] searchDomains() {
        return searchDomains;
    }

    final int ndots() {
        return ndots;
    }

    final boolean supportsAAAARecords() {
        return supportsAAAARecords;
    }

    final boolean supportsARecords() {
        return supportsARecords;
    }

    final InternetProtocolFamily preferredAddressType() {
        return preferredAddressType;
    }

    final DnsRecordType[] resolveRecordTypes() {
        return resolveRecordTypes;
    }

    final boolean isDecodeIdn() {
        return decodeIdn;
    }

    /**
     * Returns {@code true} if and only if this resolver sends a DNS query with the RD (recursion desired) flag set.
     * The default value is {@code true}.
     */
    public boolean isRecursionDesired() {
        return recursionDesired;
    }

    /**
     * Returns the maximum allowed number of DNS queries to send when resolving a host name.
     * The default value is {@code 8}.
     */
    public int maxQueriesPerResolve() {
        return maxQueriesPerResolve;
    }

    /**
     * Returns the capacity of the datagram packet buffer (in bytes).  The default value is {@code 4096} bytes.
     */
    public int maxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Returns the automatic inclusion of a optional records that tries to give the remote DNS server a hint about how
     * much data the resolver can read per response is enabled.
     */
    public boolean isOptResourceEnabled() {
        return optResourceEnabled;
    }

    /**
     * Returns the component that tries to resolve hostnames against the hosts file prior to asking to
     * remotes DNS servers.
     */
    public HostsFileEntriesResolver hostsFileEntriesResolver() {
        return hostsFileEntriesResolver;
    }

    /**
     * Closes the internal datagram channel used for sending and receiving DNS messages, and clears all DNS resource
     * records from the cache. Attempting to send a DNS query or to resolve a domain name will fail once this method
     * has been called.
     */
    @Override
    public void close() {
        if (ch.isOpen()) {
            ch.close();
        }
    }

    @Override
    protected EventLoop executor() {
        return (EventLoop) super.executor();
    }

    private InetAddress resolveHostsFileEntry(String hostname) {
        if (hostsFileEntriesResolver == null) {
            return null;
        } else {
            InetAddress address = hostsFileEntriesResolver.address(hostname, resolvedAddressTypes);
            if (address == null && PlatformDependent.isWindows() && LOCALHOST.equalsIgnoreCase(hostname)) {
                // If we tried to resolve localhost we need workaround that windows removed localhost from its
                // hostfile in later versions.
                // See https://github.com/netty/netty/issues/5386
                return LOCALHOST_ADDRESS;
            }
            return address;
        }
    }

    /**
     * Resolves the specified name into an address.
     *
     * @param inetHost the name to resolve
     * @param additionals additional records ({@code OPT})
     *
     * @return the address as the result of the resolution
     */
    public final Future<InetAddress> resolve(String inetHost, Iterable<DnsRecord> additionals) {
        return resolve(inetHost, additionals, executor().<InetAddress>newPromise());
    }

    /**
     * Resolves the specified name into an address.
     *
     * @param inetHost the name to resolve
     * @param additionals additional records ({@code OPT})
     * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
     *
     * @return the address as the result of the resolution
     */
    public final Future<InetAddress> resolve(String inetHost, Iterable<DnsRecord> additionals,
                                             Promise<InetAddress> promise) {
        checkNotNull(promise, "promise");
        DnsRecord[] additionalsArray = toArray(additionals, true);
        try {
            doResolve(inetHost, additionalsArray, promise, resolveCache);
            return promise;
        } catch (Exception e) {
            return promise.setFailure(e);
        }
    }

    /**
     * Resolves the specified host name and port into a list of address.
     *
     * @param inetHost the name to resolve
     * @param additionals additional records ({@code OPT})
     *
     * @return the list of the address as the result of the resolution
     */
    public final Future<List<InetAddress>> resolveAll(String inetHost, Iterable<DnsRecord> additionals) {
        return resolveAll(inetHost, additionals, executor().<List<InetAddress>>newPromise());
    }

    /**
     * Resolves the specified host name and port into a list of address.
     *
     * @param inetHost the name to resolve
     * @param additionals additional records ({@code OPT})
     * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
     *
     * @return the list of the address as the result of the resolution
     */
    public final Future<List<InetAddress>> resolveAll(String inetHost, Iterable<DnsRecord> additionals,
                                                Promise<List<InetAddress>> promise) {
        checkNotNull(promise, "promise");
        DnsRecord[] additionalsArray = toArray(additionals, true);
        try {
            doResolveAll(inetHost, additionalsArray, promise, resolveCache);
            return promise;
        } catch (Exception e) {
            return promise.setFailure(e);
        }
    }

    @Override
    protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
        doResolve(inetHost, EMPTY_ADDITIONALS, promise, resolveCache);
    }

    /**
     * Resolves the {@link DnsRecord}s that are matched by the specified {@link DnsQuestion}. Unlike
     * {@link #query(DnsQuestion)}, this method handles redirection, CNAMEs and multiple name servers.
     * If the specified {@link DnsQuestion} is {@code A} or {@code AAAA}, this method looks up the configured
     * {@link HostsFileEntries} before sending a query to the name servers. If a match is found in the
     * {@link HostsFileEntries}, a synthetic {@code A} or {@code AAAA} record will be returned.
     *
     * @param question the question
     *
     * @return the list of the {@link DnsRecord}s as the result of the resolution
     */
    public final Future<List<DnsRecord>> resolveAll(DnsQuestion question) {
        return resolveAll(question, EMPTY_ADDITIONALS, executor().<List<DnsRecord>>newPromise());
    }

    /**
     * Resolves the {@link DnsRecord}s that are matched by the specified {@link DnsQuestion}. Unlike
     * {@link #query(DnsQuestion)}, this method handles redirection, CNAMEs and multiple name servers.
     * If the specified {@link DnsQuestion} is {@code A} or {@code AAAA}, this method looks up the configured
     * {@link HostsFileEntries} before sending a query to the name servers. If a match is found in the
     * {@link HostsFileEntries}, a synthetic {@code A} or {@code AAAA} record will be returned.
     *
     * @param question the question
     * @param additionals additional records ({@code OPT})
     *
     * @return the list of the {@link DnsRecord}s as the result of the resolution
     */
    public final Future<List<DnsRecord>> resolveAll(DnsQuestion question, Iterable<DnsRecord> additionals) {
        return resolveAll(question, additionals, executor().<List<DnsRecord>>newPromise());
    }

    /**
     * Resolves the {@link DnsRecord}s that are matched by the specified {@link DnsQuestion}. Unlike
     * {@link #query(DnsQuestion)}, this method handles redirection, CNAMEs and multiple name servers.
     * If the specified {@link DnsQuestion} is {@code A} or {@code AAAA}, this method looks up the configured
     * {@link HostsFileEntries} before sending a query to the name servers. If a match is found in the
     * {@link HostsFileEntries}, a synthetic {@code A} or {@code AAAA} record will be returned.
     *
     * @param question the question
     * @param additionals additional records ({@code OPT})
     * @param promise the {@link Promise} which will be fulfilled when the resolution is finished
     *
     * @return the list of the {@link DnsRecord}s as the result of the resolution
     */
    public final Future<List<DnsRecord>> resolveAll(DnsQuestion question, Iterable<DnsRecord> additionals,
                                                    Promise<List<DnsRecord>> promise) {
        final DnsRecord[] additionalsArray = toArray(additionals, true);
        return resolveAll(question, additionalsArray, promise);
    }

    private Future<List<DnsRecord>> resolveAll(DnsQuestion question, DnsRecord[] additionals,
                                               Promise<List<DnsRecord>> promise) {
        checkNotNull(question, "question");
        checkNotNull(promise, "promise");

        // Respect /etc/hosts as well if the record type is A or AAAA.
        final DnsRecordType type = question.type();
        final String hostname = question.name();

        if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
            final InetAddress hostsFileEntry = resolveHostsFileEntry(hostname);
            if (hostsFileEntry != null) {
                ByteBuf content = null;
                if (hostsFileEntry instanceof Inet4Address) {
                    if (type == DnsRecordType.A) {
                        content = Unpooled.wrappedBuffer(hostsFileEntry.getAddress());
                    }
                } else if (hostsFileEntry instanceof Inet6Address) {
                    if (type == DnsRecordType.AAAA) {
                        content = Unpooled.wrappedBuffer(hostsFileEntry.getAddress());
                    }
                }

                if (content != null) {
                    // Our current implementation does not support reloading the hosts file,
                    // so use a fairly large TTL (1 day, i.e. 86400 seconds).
                    trySuccess(promise, Collections.<DnsRecord>singletonList(
                            new DefaultDnsRawRecord(hostname, type, 86400, content)));
                    return promise;
                }
            }
        }

        // It was not A/AAAA question or there was no entry in /etc/hosts.
        final DnsServerAddressStream nameServerAddrs =
                dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        new DnsRecordResolveContext(this, question, additionals, nameServerAddrs).resolve(promise);
        return promise;
    }

    private static DnsRecord[] toArray(Iterable<DnsRecord> additionals, boolean validateType) {
        checkNotNull(additionals, "additionals");
        if (additionals instanceof Collection) {
            Collection<DnsRecord> records = (Collection<DnsRecord>) additionals;
            for (DnsRecord r: additionals) {
                validateAdditional(r, validateType);
            }
            return records.toArray(new DnsRecord[records.size()]);
        }

        Iterator<DnsRecord> additionalsIt = additionals.iterator();
        if (!additionalsIt.hasNext()) {
            return EMPTY_ADDITIONALS;
        }
        List<DnsRecord> records = new ArrayList<DnsRecord>();
        do {
            DnsRecord r = additionalsIt.next();
            validateAdditional(r, validateType);
            records.add(r);
        } while (additionalsIt.hasNext());

        return records.toArray(new DnsRecord[records.size()]);
    }

    private static void validateAdditional(DnsRecord record, boolean validateType) {
        checkNotNull(record, "record");
        if (validateType && record instanceof DnsRawRecord) {
            throw new IllegalArgumentException("DnsRawRecord implementations not allowed: " + record);
        }
    }

    private InetAddress loopbackAddress() {
        return preferredAddressType().localhost();
    }

    /**
     * Hook designed for extensibility so one can pass a different cache on each resolution attempt
     * instead of using the global one.
     */
    protected void doResolve(String inetHost,
                             DnsRecord[] additionals,
                             Promise<InetAddress> promise,
                             DnsCache resolveCache) throws Exception {
        if (inetHost == null || inetHost.isEmpty()) {
            // If an empty hostname is used we should use "localhost", just like InetAddress.getByName(...) does.
            promise.setSuccess(loopbackAddress());
            return;
        }
        final byte[] bytes = NetUtil.createByteArrayFromIpAddressString(inetHost);
        if (bytes != null) {
            // The inetHost is actually an ipaddress.
            promise.setSuccess(InetAddress.getByAddress(bytes));
            return;
        }

        final String hostname = hostname(inetHost);

        InetAddress hostsFileEntry = resolveHostsFileEntry(hostname);
        if (hostsFileEntry != null) {
            promise.setSuccess(hostsFileEntry);
            return;
        }

        if (!doResolveCached(hostname, additionals, promise, resolveCache)) {
            doResolveUncached(hostname, additionals, promise, resolveCache);
        }
    }

    private boolean doResolveCached(String hostname,
                                    DnsRecord[] additionals,
                                    Promise<InetAddress> promise,
                                    DnsCache resolveCache) {
        final List<? extends DnsCacheEntry> cachedEntries = resolveCache.get(hostname, additionals);
        if (cachedEntries == null || cachedEntries.isEmpty()) {
            return false;
        }

        Throwable cause = cachedEntries.get(0).cause();
        if (cause == null) {
            final int numEntries = cachedEntries.size();
            // Find the first entry with the preferred address type.
            for (InternetProtocolFamily f : resolvedInternetProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    if (f.addressType().isInstance(e.address())) {
                        trySuccess(promise, e.address());
                        return true;
                    }
                }
            }
            return false;
        } else {
            tryFailure(promise, cause);
            return true;
        }
    }

    static <T> void trySuccess(Promise<T> promise, T result) {
        if (!promise.trySuccess(result)) {
            logger.warn("Failed to notify success ({}) to a promise: {}", result, promise);
        }
    }

    private static void tryFailure(Promise<?> promise, Throwable cause) {
        if (!promise.tryFailure(cause)) {
            logger.warn("Failed to notify failure to a promise: {}", promise, cause);
        }
    }

    private void doResolveUncached(String hostname,
                                   DnsRecord[] additionals,
                                   final Promise<InetAddress> promise,
                                   DnsCache resolveCache) {
        final Promise<List<InetAddress>> allPromise = executor().newPromise();
        doResolveAllUncached(hostname, additionals, allPromise, resolveCache);
        allPromise.addListener(new FutureListener<List<InetAddress>>() {
            @Override
            public void operationComplete(Future<List<InetAddress>> future) {
                if (future.isSuccess()) {
                    trySuccess(promise, future.getNow().get(0));
                } else {
                    tryFailure(promise, future.cause());
                }
            }
        });
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
        doResolveAll(inetHost, EMPTY_ADDITIONALS, promise, resolveCache);
    }

    /**
     * Hook designed for extensibility so one can pass a different cache on each resolution attempt
     * instead of using the global one.
     */
    protected void doResolveAll(String inetHost,
                                DnsRecord[] additionals,
                                Promise<List<InetAddress>> promise,
                                DnsCache resolveCache) throws Exception {
        if (inetHost == null || inetHost.isEmpty()) {
            // If an empty hostname is used we should use "localhost", just like InetAddress.getAllByName(...) does.
            promise.setSuccess(Collections.singletonList(loopbackAddress()));
            return;
        }
        final byte[] bytes = NetUtil.createByteArrayFromIpAddressString(inetHost);
        if (bytes != null) {
            // The unresolvedAddress was created via a String that contains an ipaddress.
            promise.setSuccess(Collections.singletonList(InetAddress.getByAddress(bytes)));
            return;
        }

        final String hostname = hostname(inetHost);

        InetAddress hostsFileEntry = resolveHostsFileEntry(hostname);
        if (hostsFileEntry != null) {
            promise.setSuccess(Collections.singletonList(hostsFileEntry));
            return;
        }

        if (!doResolveAllCached(hostname, additionals, promise, resolveCache, resolvedInternetProtocolFamilies)) {
            doResolveAllUncached(hostname, additionals, promise, resolveCache);
        }
    }

    static boolean doResolveAllCached(String hostname,
                                      DnsRecord[] additionals,
                                      Promise<List<InetAddress>> promise,
                                      DnsCache resolveCache,
                                      InternetProtocolFamily[] resolvedInternetProtocolFamilies) {
        final List<? extends DnsCacheEntry> cachedEntries = resolveCache.get(hostname, additionals);
        if (cachedEntries == null || cachedEntries.isEmpty()) {
            return false;
        }

        Throwable cause = cachedEntries.get(0).cause();
        if (cause == null) {
            List<InetAddress> result = null;
            final int numEntries = cachedEntries.size();
            for (InternetProtocolFamily f : resolvedInternetProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    if (f.addressType().isInstance(e.address())) {
                        if (result == null) {
                            result = new ArrayList<InetAddress>(numEntries);
                        }
                        result.add(e.address());
                    }
                }
            }
            if (result != null) {
                trySuccess(promise, result);
                return true;
            }
            return false;
        } else {
            tryFailure(promise, cause);
            return true;
        }
    }

    private void doResolveAllUncached(final String hostname,
                                      final DnsRecord[] additionals,
                                      final Promise<List<InetAddress>> promise,
                                      final DnsCache resolveCache) {
        // Call doResolveUncached0(...) in the EventLoop as we may need to submit multiple queries which would need
        // to submit multiple Runnable at the end if we are not already on the EventLoop.
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            doResolveAllUncached0(hostname, additionals, promise, resolveCache);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    doResolveAllUncached0(hostname, additionals, promise, resolveCache);
                }
            });
        }
    }

    private void doResolveAllUncached0(String hostname,
                                      DnsRecord[] additionals,
                                      Promise<List<InetAddress>> promise,
                                      DnsCache resolveCache) {

        assert executor().inEventLoop();

        final DnsServerAddressStream nameServerAddrs =
                dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        new DnsAddressResolveContext(this, hostname, additionals, nameServerAddrs,
                                     resolveCache, authoritativeDnsServerCache).resolve(promise);
    }

    private static String hostname(String inetHost) {
        String hostname = IDN.toASCII(inetHost);
        // Check for http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6894622
        if (StringUtil.endsWith(inetHost, '.') && !StringUtil.endsWith(hostname, '.')) {
            hostname += ".";
        }
        return hostname;
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(DnsQuestion question) {
        return query(nextNameServerAddress(), question);
    }

    /**
     * Sends a DNS query with the specified question with additional records.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            DnsQuestion question, Iterable<DnsRecord> additionals) {
        return query(nextNameServerAddress(), question, additionals);
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            DnsQuestion question, Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {
        return query(nextNameServerAddress(), question, Collections.<DnsRecord>emptyList(), promise);
    }

    private InetSocketAddress nextNameServerAddress() {
        return nameServerAddrStream.get().next();
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question) {

        return query0(nameServerAddr, question, EMPTY_ADDITIONALS, true, ch.newPromise(),
                      ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question, Iterable<DnsRecord> additionals) {

        return query0(nameServerAddr, question, toArray(additionals, false), true, ch.newPromise(),
                     ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return query0(nameServerAddr, question, EMPTY_ADDITIONALS, true, ch.newPromise(), promise);
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Iterable<DnsRecord> additionals,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return query0(nameServerAddr, question, toArray(additionals, false), true, ch.newPromise(), promise);
    }

    /**
     * Returns {@code true} if the {@link Throwable} was caused by an timeout or transport error.
     * These methods can be used on the {@link Future#cause()} that is returned by the various methods exposed by this
     * {@link DnsNameResolver}.
     */
    public static boolean isTransportOrTimeoutError(Throwable cause) {
        return cause != null && cause.getCause() instanceof DnsNameResolverException;
    }

    /**
     * Returns {@code true} if the {@link Throwable} was caused by an timeout.
     * These methods can be used on the {@link Future#cause()} that is returned by the various methods exposed by this
     * {@link DnsNameResolver}.
     */
    public static boolean isTimeoutError(Throwable cause) {
        return cause != null && cause.getCause() instanceof DnsNameResolverTimeoutException;
    }

    final void flushQueries() {
        ch.flush();
    }

    final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query0(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            DnsRecord[] additionals,
            boolean flush,
            ChannelPromise writePromise,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {
        assert !writePromise.isVoid();

        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> castPromise = cast(
                checkNotNull(promise, "promise"));
        try {
            new DnsQueryContext(this, nameServerAddr, question, additionals, castPromise)
                    .query(flush, writePromise);
            return castPromise;
        } catch (Exception e) {
            return castPromise.setFailure(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> cast(Promise<?> promise) {
        return (Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>>) promise;
    }

    final DnsServerAddressStream newNameServerAddressStream(String hostname) {
        return dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {

        private final Promise<Channel> channelActivePromise;

        DnsResponseHandler(Promise<Channel> channelActivePromise) {
            this.channelActivePromise = channelActivePromise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                final DatagramDnsResponse res = (DatagramDnsResponse) msg;
                final int queryId = res.id();

                if (logger.isDebugEnabled()) {
                    logger.debug("{} RECEIVED: [{}: {}], {}", ch, queryId, res.sender(), res);
                }

                final DnsQueryContext qCtx = queryContextManager.get(res.sender(), queryId);
                if (qCtx == null) {
                    logger.warn("{} Received a DNS response with an unknown ID: {}", ch, queryId);
                    return;
                }

                qCtx.finish(res);
            } finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            channelActivePromise.setSuccess(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warn("{} Unexpected exception: ", ch, cause);
        }
    }
}
