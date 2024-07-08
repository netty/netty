/*
 * Copyright 2014 The Netty Project
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
package io.netty5.resolver.dns;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFactory;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.handler.codec.CorruptedFrameException;
import io.netty5.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty5.handler.codec.dns.DatagramDnsResponse;
import io.netty5.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty5.handler.codec.dns.DefaultDnsRawRecord;
import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsRawRecord;
import io.netty5.handler.codec.dns.DnsRecord;
import io.netty5.handler.codec.dns.DnsRecordType;
import io.netty5.handler.codec.dns.DnsResponse;
import io.netty5.resolver.DefaultHostsFileEntriesResolver;
import io.netty5.resolver.HostsFileEntries;
import io.netty5.resolver.HostsFileEntriesResolver;
import io.netty5.resolver.InetNameResolver;
import io.netty5.resolver.ResolvedAddressTypes;
import io.netty5.util.AttributeKey;
import io.netty5.util.NetUtil;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.EmptyArrays;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.netty5.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;
import static io.netty5.util.NetUtil.addressType;
import static io.netty5.util.NetUtil.isFamilySupported;
import static io.netty5.util.NetUtil.localHost;
import static io.netty5.util.internal.ObjectUtil.checkPositive;
import static java.util.Objects.requireNonNull;

/**
 * A DNS-based {@link InetNameResolver}.
 */
public class DnsNameResolver extends InetNameResolver {
    /**
     * An attribute used to mark all channels created by the {@link DnsNameResolver}.
     */
    public static final AttributeKey<Boolean> DNS_PIPELINE_ATTRIBUTE =
            AttributeKey.newInstance("io.netty5.resolver.dns.pipeline");

    private static final Logger logger = LoggerFactory.getLogger(DnsNameResolver.class);
    private static final String LOCALHOST = "localhost";
    private static final String WINDOWS_HOST_NAME;
    private static final InetAddress LOCALHOST_ADDRESS;
    private static final DnsRecord[] EMPTY_ADDITIONALS = new DnsRecord[0];
    private static final DnsRecordType[] IPV4_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A};
    private static final ProtocolFamily[] IPV4_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {StandardProtocolFamily.INET};
    private static final DnsRecordType[] IPV4_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A, DnsRecordType.AAAA};
    private static final ProtocolFamily[] IPV4_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {StandardProtocolFamily.INET, StandardProtocolFamily.INET6};
    private static final DnsRecordType[] IPV6_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA};
    private static final ProtocolFamily[] IPV6_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {StandardProtocolFamily.INET6};
    private static final DnsRecordType[] IPV6_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA, DnsRecordType.A};
    private static final ProtocolFamily[] IPV6_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {StandardProtocolFamily.INET6, StandardProtocolFamily.INET};

    private static final ChannelHandler NOOP_HANDLER = new ChannelHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    static final ResolvedAddressTypes DEFAULT_RESOLVE_ADDRESS_TYPES;
    static final String[] DEFAULT_SEARCH_DOMAINS;
    private static final UnixResolverOptions DEFAULT_OPTIONS;

    static {
        if (NetUtil.isIpV4StackPreferred() || !anyInterfaceSupportsIpV6()) {
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
        logger.debug("Default ResolvedAddressTypes: {}", DEFAULT_RESOLVE_ADDRESS_TYPES);
        logger.debug("Localhost address: {}", LOCALHOST_ADDRESS);

        String hostName;
        try {
            hostName = PlatformDependent.isWindows() ? InetAddress.getLocalHost().getHostName() : null;
        } catch (Exception ignore) {
            hostName = null;
        }
        WINDOWS_HOST_NAME = hostName;
        logger.debug("Windows hostname: {}", WINDOWS_HOST_NAME);

        String[] searchDomains;
        try {
            searchDomains = EmptyArrays.EMPTY_STRINGS;
            if (!PlatformDependent.isWindows()) {
                List<String> list = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains();
                searchDomains = list.toArray(EmptyArrays.EMPTY_STRINGS);
            }
        } catch (Exception ignore) {
            // Failed to get the system name search domain list.
            searchDomains = EmptyArrays.EMPTY_STRINGS;
        }
        DEFAULT_SEARCH_DOMAINS = searchDomains;
        logger.debug("Default search domains: {}", Arrays.toString(DEFAULT_SEARCH_DOMAINS));

        UnixResolverOptions options;
        try {
            options = UnixResolverDnsServerAddressStreamProvider.parseEtcResolverOptions();
        } catch (Exception ignore) {
            options = UnixResolverOptions.newBuilder().build();
        }
        DEFAULT_OPTIONS = options;
        logger.debug("Default {}", DEFAULT_OPTIONS);
    }

    /**
     * Returns {@code true} if any {@link NetworkInterface} supports {@code IPv6}, {@code false} otherwise.
     */
    private static boolean anyInterfaceSupportsIpV6() {
        for (NetworkInterface iface : NetUtil.NETWORK_INTERFACES) {
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress inetAddress = addresses.nextElement();
                if (inetAddress instanceof Inet6Address && !inetAddress.isAnyLocalAddress() &&
                        !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final DatagramDnsResponseDecoder DATAGRAM_DECODER = new DatagramDnsResponseDecoder() {
        @Override
        protected DnsResponse decodeResponse(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            DnsResponse response = super.decodeResponse(ctx, packet);
            if (packet.content().readableBytes() > 0) {
                // If there is still something to read we did stop parsing because of a truncated message.
                // This can happen if we enabled EDNS0 but our MTU is not big enough to handle all the
                // data.
                response.setTruncated(true);

                if (logger.isDebugEnabled()) {
                    logger.debug("{} RECEIVED: UDP [{}: {}] truncated packet received, consider adjusting "
                                    + "maxPayloadSize for the {}.", ctx.channel(), response.id(), packet.sender(),
                            StringUtil.simpleClassName(DnsNameResolver.class));
                }
            }
            return response;
        }
    };
    private static final DatagramDnsQueryEncoder DATAGRAM_ENCODER = new DatagramDnsQueryEncoder();

    private final Promise<Channel> channelReadyPromise;
    private final Channel ch;

    // Comparator that ensures we will try first to use the nameservers that use our preferred address type.
    private final Comparator<InetSocketAddress> nameServerComparator;
    /**
     * Manages the {@link DnsQueryContext}s in progress and their query IDs.
     */
    private final DnsQueryContextManager queryContextManager = new DnsQueryContextManager();

    /**
     * Cache for {@link #doResolve(String, Promise)} and {@link #doResolveAll(String, Promise)}.
     */
    private final DnsCache resolveCache;
    private final AuthoritativeDnsServerCache authoritativeDnsServerCache;
    private final DnsCnameCache cnameCache;
    private final DnsServerAddressStream queryDnsServerAddressStream;
    private final long queryTimeoutMillis;
    private final int maxQueriesPerResolve;
    private final ResolvedAddressTypes resolvedAddressTypes;
    private final ProtocolFamily[] resolvedProtocolFamilies;
    private final boolean recursionDesired;
    private final int maxPayloadSize;
    private final boolean optResourceEnabled;
    private final HostsFileEntriesResolver hostsFileEntriesResolver;
    private final DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private final String[] searchDomains;
    private final int ndots;
    private final boolean supportsAAAARecords;
    private final boolean supportsARecords;
    private final ProtocolFamily preferredAddressType;
    private final DnsRecordType[] resolveRecordTypes;
    private final boolean decodeIdn;
    private final DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory;
    private final boolean completeOncePreferredResolved;
    private final Bootstrap socketBootstrap;
    private final boolean retryWithTcpOnTimeout;

    private final int maxNumConsolidation;
    private final Map<String, Future<List<InetAddress>>> inflightLookups;

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param resolveCache the DNS resolved entries cache
     * @param authoritativeDnsServerCache the cache used to find the authoritative DNS server for a domain
     * @param dnsQueryLifecycleObserverFactory used to generate new instances of {@link DnsQueryLifecycleObserver} which
     *                                         can be used to track metrics for DNS servers.
     * @param queryTimeoutMillis timeout of each DNS query in millis. {@code 0} disables the timeout. If not set or a
     *                           negative number is set, the default timeout is used.
     * @param resolvedAddressTypes the preferred address types
     * @param recursionDesired if recursion desired flag must be set
     * @param maxQueriesPerResolve the maximum allowed number of DNS queries for a given name resolution
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
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn) {
        this(eventLoop, channelFactory, resolveCache,
             new AuthoritativeDnsServerCacheAdapter(authoritativeDnsServerCache), dnsQueryLifecycleObserverFactory,
             queryTimeoutMillis, resolvedAddressTypes, recursionDesired, maxQueriesPerResolve,
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
     * @param queryTimeoutMillis timeout of each DNS query in millis. {@code 0} disables the timeout. If not set or a
     *                           negative number is set, the default timeout is used.
     * @param resolvedAddressTypes the preferred address types
     * @param recursionDesired if recursion desired flag must be set
     * @param maxQueriesPerResolve the maximum allowed number of DNS queries for a given name resolution
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
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn) {
        this(eventLoop, channelFactory, null, false, resolveCache,
                NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache, null,
             dnsQueryLifecycleObserverFactory, queryTimeoutMillis, resolvedAddressTypes, recursionDesired,
             maxQueriesPerResolve, maxPayloadSize, optResourceEnabled, hostsFileEntriesResolver,
             dnsServerAddressStreamProvider, new ThreadLocalNameServerAddressStream(dnsServerAddressStreamProvider),
                searchDomains, ndots, decodeIdn, false, 0);
    }

    DnsNameResolver(
            EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory,
            ChannelFactory<? extends SocketChannel> socketChannelFactory,
            boolean retryWithTcpOnTimeout,
            final DnsCache resolveCache,
            final DnsCnameCache cnameCache,
            final AuthoritativeDnsServerCache authoritativeDnsServerCache,
            SocketAddress localAddress,
            DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory,
            long queryTimeoutMillis,
            ResolvedAddressTypes resolvedAddressTypes,
            boolean recursionDesired,
            int maxQueriesPerResolve,
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            DnsServerAddressStream queryDnsServerAddressStream,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn,
            boolean completeOncePreferredResolved,
            int maxNumConsolidation) {
        super(eventLoop);
        this.queryTimeoutMillis = queryTimeoutMillis >= 0
            ? queryTimeoutMillis
            : TimeUnit.SECONDS.toMillis(DEFAULT_OPTIONS.timeout());
        this.resolvedAddressTypes = resolvedAddressTypes != null ? resolvedAddressTypes : DEFAULT_RESOLVE_ADDRESS_TYPES;
        this.recursionDesired = recursionDesired;
        this.maxQueriesPerResolve = maxQueriesPerResolve > 0 ? maxQueriesPerResolve : DEFAULT_OPTIONS.attempts();
        this.maxPayloadSize = checkPositive(maxPayloadSize, "maxPayloadSize");
        this.optResourceEnabled = optResourceEnabled;
        this.hostsFileEntriesResolver = requireNonNull(hostsFileEntriesResolver, "hostsFileEntriesResolver");
        this.dnsServerAddressStreamProvider =
                requireNonNull(dnsServerAddressStreamProvider, "dnsServerAddressStreamProvider");
        this.queryDnsServerAddressStream = requireNonNull(queryDnsServerAddressStream, "queryDnsServerAddressStream");
        this.resolveCache = requireNonNull(resolveCache, "resolveCache");
        this.cnameCache = requireNonNull(cnameCache, "cnameCache");
        this.dnsQueryLifecycleObserverFactory =
                requireNonNull(dnsQueryLifecycleObserverFactory, "dnsQueryLifecycleObserverFactory");

        this.searchDomains = searchDomains != null ? searchDomains.clone() : DEFAULT_SEARCH_DOMAINS;
        this.ndots = ndots >= 0 ? ndots : DEFAULT_OPTIONS.ndots();
        this.decodeIdn = decodeIdn;
        this.completeOncePreferredResolved = completeOncePreferredResolved;
        this.retryWithTcpOnTimeout = retryWithTcpOnTimeout;
        if (socketChannelFactory == null) {
            socketBootstrap = null;
        } else {
            socketBootstrap = new Bootstrap();
            socketBootstrap.option(ChannelOption.SO_REUSEADDR, true)
                    .group(executor())
                    .channelFactory(socketChannelFactory)
                    .attr(DNS_PIPELINE_ATTRIBUTE, Boolean.TRUE)
                    .handler(NOOP_HANDLER);
            if (queryTimeoutMillis > 0 && queryTimeoutMillis <= Integer.MAX_VALUE) {
                // Set the connect timeout to the same as queryTimeout as otherwise it might take a long
                // time for the query to fail in case of a connection timeout.
                socketBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) queryTimeoutMillis);
            }
        }
        switch (this.resolvedAddressTypes) {
            case IPV4_ONLY:
                supportsAAAARecords = false;
                supportsARecords = true;
                resolveRecordTypes = IPV4_ONLY_RESOLVED_RECORD_TYPES;
                resolvedProtocolFamilies = IPV4_ONLY_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV4_PREFERRED:
                supportsAAAARecords = true;
                supportsARecords = true;
                resolveRecordTypes = IPV4_PREFERRED_RESOLVED_RECORD_TYPES;
                resolvedProtocolFamilies = IPV4_PREFERRED_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV6_ONLY:
                supportsAAAARecords = true;
                supportsARecords = false;
                resolveRecordTypes = IPV6_ONLY_RESOLVED_RECORD_TYPES;
                resolvedProtocolFamilies = IPV6_ONLY_RESOLVED_PROTOCOL_FAMILIES;
                break;
            case IPV6_PREFERRED:
                supportsAAAARecords = true;
                supportsARecords = true;
                resolveRecordTypes = IPV6_PREFERRED_RESOLVED_RECORD_TYPES;
                resolvedProtocolFamilies = IPV6_PREFERRED_RESOLVED_PROTOCOL_FAMILIES;
                break;
            default:
                throw new IllegalArgumentException("Unknown ResolvedAddressTypes " + resolvedAddressTypes);
        }
        preferredAddressType = preferredAddressType(this.resolvedAddressTypes);
        this.authoritativeDnsServerCache = requireNonNull(authoritativeDnsServerCache, "authoritativeDnsServerCache");
        Class<? extends InetAddress> addressType = addressType(preferredAddressType);
        if (addressType == null) {
            throw new IllegalArgumentException(preferredAddressType + " not supported");
        }
        nameServerComparator = new NameServerComparator(addressType);
        this.maxNumConsolidation = maxNumConsolidation;
        if (maxNumConsolidation > 0) {
            inflightLookups = new HashMap<>();
        } else {
            inflightLookups = null;
        }
        Bootstrap b = new Bootstrap()
                .group(executor())
                .channelFactory(channelFactory)
                .attr(DNS_PIPELINE_ATTRIBUTE, Boolean.TRUE);
        channelReadyPromise = executor().newPromise();
        final DnsResponseHandler responseHandler = new DnsResponseHandler(channelReadyPromise);
        b.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) {
                ch.pipeline().addLast(DATAGRAM_ENCODER, DATAGRAM_DECODER, responseHandler);
                ch.closeFuture().addListener(closeFuture -> {
                    resolveCache.clear();
                    cnameCache.clear();
                    authoritativeDnsServerCache.clear();
                });
            }
        });
        b.option(ChannelOption.READ_HANDLE_FACTORY, new FixedReadHandleFactory(maxPayloadSize));
        if (localAddress == null) {
            localAddress = new InetSocketAddress(0);
        }
        try {
            ch = b.createUnregistered();
            SocketAddress local = localAddress;
            Future<Void> future = ch.register().flatMap(__ -> ch.bind(local));
            if (future.isFailed()) {
                throw future.cause();
            } else if (!future.isSuccess()) {
                future.addListener(f -> {
                    Throwable cause = f.cause();
                    if (cause != null) {
                        channelReadyPromise.tryFailure(cause);
                    }
                });
            }
        } catch (Error | RuntimeException e) {
            throw e;
        } catch (Throwable cause) {
            throw new IllegalStateException("Unable to create / register Channel", cause);
        }
    }

    static ProtocolFamily preferredAddressType(ResolvedAddressTypes resolvedAddressTypes) {
        switch (resolvedAddressTypes) {
        case IPV4_ONLY:
        case IPV4_PREFERRED:
            return StandardProtocolFamily.INET;
        case IPV6_ONLY:
        case IPV6_PREFERRED:
            return StandardProtocolFamily.INET6;
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
            nameservers.sort(nameServerComparator);
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
    public DnsCnameCache cnameCache() {
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
     * Returns the dns server address stream used for DNS queries (not resolve).
     */
    public DnsServerAddressStream queryDnsServerAddressStream() {
        return queryDnsServerAddressStream;
    }

    /**
     * Returns the {@link ResolvedAddressTypes} resolved by {@link #resolve(String)}.
     * The default value depends on the value of the system property {@code "java.net.preferIPv6Addresses"}.
     */
    public ResolvedAddressTypes resolvedAddressTypes() {
        return resolvedAddressTypes;
    }

    ProtocolFamily[] resolvedProtocolFamiliesUnsafe() {
        return resolvedProtocolFamilies;
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

    final ProtocolFamily preferredAddressType() {
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
        }
        InetAddress address = hostsFileEntriesResolver.address(hostname, resolvedAddressTypes);
        return address == null && isLocalWindowsHost(hostname) ? LOCALHOST_ADDRESS : address;
    }

    private List<InetAddress> resolveHostsFileEntries(String hostname) {
        if (hostsFileEntriesResolver == null) {
            return null;
        }
        List<InetAddress> addresses;
        if (hostsFileEntriesResolver instanceof DefaultHostsFileEntriesResolver) {
            addresses = ((DefaultHostsFileEntriesResolver) hostsFileEntriesResolver)
                    .addresses(hostname, resolvedAddressTypes);
        } else {
            InetAddress address = hostsFileEntriesResolver.address(hostname, resolvedAddressTypes);
            addresses = address != null ? Collections.singletonList(address) : null;
        }
        return addresses == null && isLocalWindowsHost(hostname) ?
                Collections.singletonList(LOCALHOST_ADDRESS) : addresses;
    }

    /**
     * Checks whether the given hostname is the localhost/host (computer) name on Windows OS.
     * Windows OS removed the localhost/host (computer) name information from the hosts file in the later versions
     * and such hostname cannot be resolved from hosts file.
     * See https://github.com/netty/netty/issues/5386
     * See https://github.com/netty/netty/issues/11142
     */
    private static boolean isLocalWindowsHost(String hostname) {
        return PlatformDependent.isWindows() &&
                (LOCALHOST.equalsIgnoreCase(hostname) ||
                 WINDOWS_HOST_NAME != null && WINDOWS_HOST_NAME.equalsIgnoreCase(hostname));
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
        return resolve(inetHost, additionals, executor().newPromise());
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
        requireNonNull(promise, "promise");
        DnsRecord[] additionalsArray = toArray(additionals, true);
        try {
            doResolve(inetHost, additionalsArray, promise, resolveCache);
        } catch (Exception e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
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
        return resolveAll(inetHost, additionals, executor().newPromise());
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
        requireNonNull(promise, "promise");
        DnsRecord[] additionalsArray = toArray(additionals, true);
        try {
            doResolveAll(inetHost, additionalsArray, promise, resolveCache);
        } catch (Exception e) {
            promise.setFailure(e);
        }
        return promise.asFuture();
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
        return resolveAll(question, EMPTY_ADDITIONALS, executor().newPromise());
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
        return resolveAll(question, additionals, executor().newPromise());
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
        requireNonNull(question, "question");
        requireNonNull(promise, "promise");

        // Respect /etc/hosts as well if the record type is A or AAAA.
        final DnsRecordType type = question.type();
        final String hostname = question.name();

        if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
            final List<InetAddress> hostsFileEntries = resolveHostsFileEntries(hostname);
            if (hostsFileEntries != null) {
                List<DnsRecord> result = new ArrayList<>();
                for (InetAddress hostsFileEntry : hostsFileEntries) {
                    Buffer content = null;
                    if (hostsFileEntry instanceof Inet4Address) {
                        if (type == DnsRecordType.A) {
                            content = ch.bufferAllocator().copyOf(hostsFileEntry.getAddress());
                        }
                    } else if (hostsFileEntry instanceof Inet6Address) {
                        if (type == DnsRecordType.AAAA) {
                            content = ch.bufferAllocator().copyOf(hostsFileEntry.getAddress());
                        }
                    }
                    if (content != null) {
                        // Our current implementation does not support reloading the hosts file,
                        // so use a fairly large TTL (1 day, i.e. 86400 seconds).
                        result.add(new DefaultDnsRawRecord(hostname, type, 86400, content));
                    }
                }

                if (!result.isEmpty()) {
                    if (!trySuccess(promise, result)) {
                        // We were not able to transfer ownership, release the records to prevent leaks.
                        for (DnsRecord r: result) {
                            Resource.dispose(r);
                        }
                    }
                    return promise.asFuture();
                }
            }
        }

        // It was not A/AAAA question or there was no entry in /etc/hosts.
        final DnsServerAddressStream nameServerAddrs =
                dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        new DnsRecordResolveContext(this, ch, channelReadyPromise.asFuture(),
                promise, question, additionals, nameServerAddrs, maxQueriesPerResolve)
                .resolve(promise);
        return promise.asFuture();
    }

    private static DnsRecord[] toArray(Iterable<DnsRecord> additionals, boolean validateType) {
        requireNonNull(additionals, "additionals");
        if (additionals instanceof Collection) {
            Collection<DnsRecord> records = (Collection<DnsRecord>) additionals;
            for (DnsRecord r: additionals) {
                validateAdditional(r, validateType);
            }
            return records.toArray(new DnsRecord[0]);
        }

        Iterator<DnsRecord> additionalsIt = additionals.iterator();
        if (!additionalsIt.hasNext()) {
            return EMPTY_ADDITIONALS;
        }
        List<DnsRecord> records = new ArrayList<>();
        do {
            DnsRecord r = additionalsIt.next();
            validateAdditional(r, validateType);
            records.add(r);
        } while (additionalsIt.hasNext());

        return records.toArray(new DnsRecord[0]);
    }

    private static void validateAdditional(DnsRecord record, boolean validateType) {
        requireNonNull(record, "record");
        if (validateType && record instanceof DnsRawRecord) {
            throw new IllegalArgumentException("DnsRawRecord implementations not allowed: " + record);
        }
    }

    private InetAddress loopbackAddress() {
        return localHost(preferredAddressType());
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
        final InetAddress address = NetUtil.createInetAddressFromIpAddressString(inetHost);
        if (address != null) {
            // The inetHost is actually an ipaddress.
            promise.setSuccess(address);
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
            for (ProtocolFamily f : resolvedProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    if (NetUtil.isFamilySupported(e.address(), f)) {
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

    static <T> boolean trySuccess(Promise<T> promise, T result) {
        final boolean notifiedRecords = promise.trySuccess(result);
        if (!notifiedRecords) {
            // There is nothing really wrong with not be able to notify the promise as we may have raced here because
            // of multiple queries that have been executed. Log it with trace level anyway just in case the user
            // wants to better understand what happened.
            logger.trace("Failed to notify success ({}) to a promise: {}", result, promise);
        }
        return notifiedRecords;
    }

    private static void tryFailure(Promise<?> promise, Throwable cause) {
        if (!promise.tryFailure(cause)) {
            // There is nothing really wrong with not be able to notify the promise as we may have raced here because
            // of multiple queries that have been executed. Log it with trace level anyway just in case the user
            // wants to better understand what happened.
            logger.trace("Failed to notify failure to a promise: {}", promise, cause);
        }
    }

    private void doResolveUncached(String hostname,
                                   DnsRecord[] additionals,
                                   final Promise<InetAddress> promise,
                                   DnsCache resolveCache) {
        final Promise<List<InetAddress>> allPromise = executor().newPromise();
        doResolveAllUncached(hostname, additionals, promise, allPromise, resolveCache, completeOncePreferredResolved);
        allPromise.asFuture().addListener(future -> {
            if (future.isSuccess()) {
                trySuccess(promise, future.getNow().get(0));
            } else {
                tryFailure(promise, future.cause());
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
        final InetAddress address = NetUtil.createInetAddressFromIpAddressString(inetHost);
        if (address != null) {
            // The unresolvedAddress was created via a String that contains an ipaddress.
            promise.setSuccess(Collections.singletonList(address));
            return;
        }

        final String hostname = hostname(inetHost);

        List<InetAddress> hostsFileEntries = resolveHostsFileEntries(hostname);
        if (hostsFileEntries != null) {
            promise.setSuccess(hostsFileEntries);
            return;
        }

        if (!doResolveAllCached(hostname, additionals, promise, resolveCache, this.searchDomains(),
                ndots(), resolvedProtocolFamilies)) {
            doResolveAllUncached(hostname, additionals, promise, promise,
                                 resolveCache, completeOncePreferredResolved);
        }
    }

    private static boolean hasEntries(List<? extends DnsCacheEntry> cachedEntries) {
        return cachedEntries != null && !cachedEntries.isEmpty();
    }

    static boolean doResolveAllCached(String hostname,
                                      DnsRecord[] additionals,
                                      Promise<List<InetAddress>> promise,
                                      DnsCache resolveCache,
                                      String[] searchDomains,
                                      int ndots,
                                      ProtocolFamily[] resolvedInternetProtocolFamilies) {
        List<? extends DnsCacheEntry> cachedEntries = resolveCache.get(hostname, additionals);
        if (!hasEntries(cachedEntries) && searchDomains != null && ndots != 0
                && !StringUtil.endsWith(hostname, '.')) {
            for (String searchDomain : searchDomains) {
                final String initialHostname = hostname + '.' + searchDomain;
                cachedEntries = resolveCache.get(initialHostname, additionals);
                if (hasEntries(cachedEntries)) {
                    break;
                }
            }
        }
        if (!hasEntries(cachedEntries)) {
            return false;
        }

        Throwable cause = cachedEntries.get(0).cause();
        if (cause == null) {
            List<InetAddress> result = null;
            final int numEntries = cachedEntries.size();
            for (ProtocolFamily f : resolvedInternetProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    if (isFamilySupported(e.address(), f)) {
                        if (result == null) {
                            result = new ArrayList<>(numEntries);
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
                                      final Promise<?> originalPromise,
                                      final Promise<List<InetAddress>> promise,
                                      final DnsCache resolveCache,
                                      final boolean completeEarlyIfPossible) {
        // Call doResolveUncached0(...) in the EventLoop as we may need to submit multiple queries which would need
        // to submit multiple Runnable at the end if we are not already on the EventLoop.
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            doResolveAllUncached0(hostname, additionals, originalPromise,
                                  promise, resolveCache, completeEarlyIfPossible);
        } else {
            executor.execute(() ->
                    doResolveAllUncached0(hostname, additionals, originalPromise,
                            promise, resolveCache, completeEarlyIfPossible));
        }
    }

    private void doResolveAllUncached0(final String hostname,
                                       final DnsRecord[] additionals,
                                       final Promise<?> originalPromise,
                                       final Promise<List<InetAddress>> promise,
                                       final DnsCache resolveCache,
                                       final boolean completeEarlyIfPossible) {

        assert executor().inEventLoop();

        if (inflightLookups != null && (additionals == null || additionals.length == 0)) {
            Future<List<InetAddress>> inflightFuture = inflightLookups.get(hostname);
            if (inflightFuture != null) {
                inflightFuture.addListener(future -> {
                        if (future.isSuccess()) {
                            promise.setSuccess(future.getNow());
                        } else {
                            Throwable cause = future.cause();
                            if (isTimeoutError(cause)) {
                                // The failure was caused by a timeout. This might be happening as a result of
                                // the remote server be overloaded for some short amount of time or because
                                // UDP packets were dropped on the floor. In this case lets try to just do the
                                // query explicit and don't cascade this possible temporary failure.
                                resolveNow(hostname, additionals, originalPromise, promise,
                                        resolveCache, completeEarlyIfPossible);
                            } else {
                                promise.setFailure(cause);
                            }
                        }
                });
                return;
            // Check if we have space left in the map.
            } else if (inflightLookups.size() < maxNumConsolidation) {
                Future<List<InetAddress>> f = promise.asFuture();
                inflightLookups.put(hostname, f);
                f.addListener(future -> inflightLookups.remove(hostname));
            }
        }
        resolveNow(hostname, additionals, originalPromise, promise, resolveCache, completeEarlyIfPossible);
    }

    private void resolveNow(final String hostname,
                            final DnsRecord[] additionals,
                            final Promise<?> originalPromise,
                            final Promise<List<InetAddress>> promise,
                            final DnsCache resolveCache,
                            final boolean completeEarlyIfPossible) {
        final DnsServerAddressStream nameServerAddrs =
                dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        DnsAddressResolveContext ctx = new DnsAddressResolveContext(this, ch, channelReadyPromise.asFuture(),
                originalPromise, hostname, additionals, nameServerAddrs, maxQueriesPerResolve, resolveCache,
                authoritativeDnsServerCache, completeEarlyIfPossible);
        ctx.resolve(promise);
    }

    private static String hostname(String inetHost) {
        String hostname = IDN.toASCII(inetHost);
        // Check for https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6894622
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
        return query(nextNameServerAddress(), question, Collections.emptyList(), promise);
    }

    private InetSocketAddress nextNameServerAddress() {
        return queryDnsServerAddressStream.next();
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question) {
        return doQuery(ch, channelReadyPromise.asFuture(), nameServerAddr, question,
                NoopDnsQueryLifecycleObserver.INSTANCE, EMPTY_ADDITIONALS, true, ch.newPromise());
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question, Iterable<DnsRecord> additionals) {

        return doQuery(ch, channelReadyPromise.asFuture(), nameServerAddr, question,
                NoopDnsQueryLifecycleObserver.INSTANCE, toArray(additionals, false), true, ch.newPromise());
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return doQuery(ch, channelReadyPromise.asFuture(), nameServerAddr, question,
                NoopDnsQueryLifecycleObserver.INSTANCE, EMPTY_ADDITIONALS, true, promise);
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Iterable<DnsRecord> additionals,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return doQuery(ch, channelReadyPromise.asFuture(), nameServerAddr, question,
                NoopDnsQueryLifecycleObserver.INSTANCE, toArray(additionals, false), true, promise);
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

    final Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> doQuery(
            Channel channel, Future<? extends Channel> channelReadyFuture,
            InetSocketAddress nameServerAddr, DnsQuestion question,
            final DnsQueryLifecycleObserver queryLifecycleObserver,
            DnsRecord[] additionals, boolean flush,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> castPromise = cast(
                requireNonNull(promise, "promise"));
        final int payloadSize = isOptResourceEnabled() ? maxPayloadSize() : 0;
        try {
            DnsQueryContext queryContext = new DatagramDnsQueryContext(channel,
                    channelReadyFuture, nameServerAddr,
                    queryContextManager, payloadSize, isRecursionDesired(), queryTimeoutMillis(), question, additionals,
                    castPromise, socketBootstrap, retryWithTcpOnTimeout);
            Future<Void> future = queryContext.writeQuery(flush);
            queryLifecycleObserver.queryWritten(nameServerAddr, future);
        } catch (Exception e) {
            castPromise.setFailure(e);
        }
        return castPromise.asFuture();
    }

    @SuppressWarnings("unchecked")
    private static Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> cast(Promise<?> promise) {
        return (Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>>) promise;
    }

    final DnsServerAddressStream newNameServerAddressStream(String hostname) {
        return dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
    }

    private final class DnsResponseHandler implements ChannelHandler {

        private final Promise<Channel> channelActivePromise;

        DnsResponseHandler(Promise<Channel> channelActivePromise) {
            this.channelActivePromise = channelActivePromise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final Channel qCh = ctx.channel();
            final DatagramDnsResponse res = (DatagramDnsResponse) msg;
            final int queryId = res.id();
            logger.debug("{} RECEIVED: UDP [{}: {}], {}", qCh, queryId, res.sender(), res);

            final DnsQueryContext qCtx = queryContextManager.get(res.sender(), queryId);
            if (qCtx == null) {
                logger.debug("{} Received a DNS response with an unknown ID: UDP [{}: {}]",
                        qCh, queryId, res.sender());
                res.release();
                return;
            } else if (qCtx.isDone()) {
                logger.debug("{} Received a DNS response for a query that was timed out or cancelled: UDP [{}: {}]",
                        qCh, queryId, res.sender());
                res.release();
                return;
            }
            // The context will handle truncation itself.
            qCtx.finishSuccess(res, res.isTruncated());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            channelActivePromise.trySuccess(ctx.channel());
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof CorruptedFrameException) {
                logger.debug("{} Unable to decode DNS response: UDP", ctx.channel(), cause);
            } else {
                logger.warn("{} Unexpected exception: UDP", ctx.channel(), cause);
            }
        }
    }
}
