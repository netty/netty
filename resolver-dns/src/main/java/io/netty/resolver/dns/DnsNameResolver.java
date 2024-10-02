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
package io.netty.resolver.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.resolver.DefaultHostsFileEntriesResolver;
import io.netty.resolver.HostsFileEntries;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.AttributeKey;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.UnknownHostException;
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

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * A DNS-based {@link InetNameResolver}.
 */
public class DnsNameResolver extends InetNameResolver {
    /**
     * An attribute used to mark all channels created by the {@link DnsNameResolver}.
     */
    public static final AttributeKey<Boolean> DNS_PIPELINE_ATTRIBUTE =
            AttributeKey.newInstance("io.netty.resolver.dns.pipeline");

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);
    private static final String LOCALHOST = "localhost";
    private static final String WINDOWS_HOST_NAME;
    private static final InetAddress LOCALHOST_ADDRESS;
    private static final DnsRecord[] EMPTY_ADDITIONALS = new DnsRecord[0];
    private static final DnsRecordType[] IPV4_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A};
    private static final SocketProtocolFamily[] IPV4_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {SocketProtocolFamily.INET};
    private static final DnsRecordType[] IPV4_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.A, DnsRecordType.AAAA};
    private static final SocketProtocolFamily[] IPV4_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {SocketProtocolFamily.INET, SocketProtocolFamily.INET6};
    private static final DnsRecordType[] IPV6_ONLY_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA};
    private static final SocketProtocolFamily[] IPV6_ONLY_RESOLVED_PROTOCOL_FAMILIES =
            {SocketProtocolFamily.INET6};
    private static final DnsRecordType[] IPV6_PREFERRED_RESOLVED_RECORD_TYPES =
            {DnsRecordType.AAAA, DnsRecordType.A};
    private static final SocketProtocolFamily[] IPV6_PREFERRED_RESOLVED_PROTOCOL_FAMILIES =
            {SocketProtocolFamily.INET6, SocketProtocolFamily.INET};

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
            List<String> list = PlatformDependent.isWindows()
                    ? getSearchDomainsHack()
                    : UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains();
            searchDomains = list.toArray(EmptyArrays.EMPTY_STRINGS);
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

    @SuppressWarnings("unchecked")
    private static List<String> getSearchDomainsHack() throws Exception {
        // Only try if not using Java9 and later
        // See https://github.com/netty/netty/issues/9500
        if (PlatformDependent.javaVersion() < 9) {
            // This code on Java 9+ yields a warning about illegal reflective access that will be denied in
            // a future release. There doesn't seem to be a better way to get search domains for Windows yet.
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("searchlist");
            Object instance = open.invoke(null);

            return (List<String>) nameservers.invoke(instance);
        }
        return Collections.emptyList();
    }

    private static final DatagramDnsResponseDecoder DATAGRAM_DECODER = new DatagramDnsResponseDecoder() {
        @Override
        protected DnsResponse decodeResponse(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            DnsResponse response = super.decodeResponse(ctx, packet);
            if (packet.content().isReadable()) {
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
    private final SocketProtocolFamily[] resolvedInternetProtocolFamilies;
    private final boolean recursionDesired;
    private final int maxPayloadSize;
    private final boolean optResourceEnabled;
    private final HostsFileEntriesResolver hostsFileEntriesResolver;
    private final DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private final String[] searchDomains;
    private final int ndots;
    private final boolean supportsAAAARecords;
    private final boolean supportsARecords;
    private final SocketProtocolFamily preferredAddressType;
    private final DnsRecordType[] resolveRecordTypes;
    private final boolean decodeIdn;
    private final DnsQueryLifecycleObserverFactory dnsQueryLifecycleObserverFactory;
    private final boolean completeOncePreferredResolved;
    private final DnsResolveChannelProvider resolveChannelProvider;
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
     * @param queryTimeoutMillis timeout of each DNS query in millis. {@code 0} disables the timeout. If not set or a
     *                           negative number is set, the default timeout is used.
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
        this(eventLoop, channelFactory, null, false, resolveCache,
                NoopDnsCnameCache.INSTANCE, authoritativeDnsServerCache, null,
             dnsQueryLifecycleObserverFactory, queryTimeoutMillis, resolvedAddressTypes, recursionDesired,
             maxQueriesPerResolve, traceEnabled, maxPayloadSize, optResourceEnabled, hostsFileEntriesResolver,
             dnsServerAddressStreamProvider, new ThreadLocalNameServerAddressStream(dnsServerAddressStreamProvider),
             searchDomains, ndots, decodeIdn, false, 0, DnsNameResolverChannelStrategy.ChannelPerResolver);
    }

    @SuppressWarnings("deprecation")
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
            boolean traceEnabled,
            final int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver,
            DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
            DnsServerAddressStream queryDnsServerAddressStream,
            String[] searchDomains,
            int ndots,
            boolean decodeIdn,
            boolean completeOncePreferredResolved,
            int maxNumConsolidation, DnsNameResolverChannelStrategy datagramChannelStrategy) {
        super(eventLoop);
        this.queryTimeoutMillis = queryTimeoutMillis >= 0
            ? queryTimeoutMillis
            : TimeUnit.SECONDS.toMillis(DEFAULT_OPTIONS.timeout());
        this.resolvedAddressTypes = resolvedAddressTypes != null ? resolvedAddressTypes : DEFAULT_RESOLVE_ADDRESS_TYPES;
        this.recursionDesired = recursionDesired;
        this.maxQueriesPerResolve = maxQueriesPerResolve > 0 ? maxQueriesPerResolve : DEFAULT_OPTIONS.attempts();
        this.maxPayloadSize = checkPositive(maxPayloadSize, "maxPayloadSize");
        this.optResourceEnabled = optResourceEnabled;
        this.hostsFileEntriesResolver = checkNotNull(hostsFileEntriesResolver, "hostsFileEntriesResolver");
        this.dnsServerAddressStreamProvider =
                checkNotNull(dnsServerAddressStreamProvider, "dnsServerAddressStreamProvider");
        this.queryDnsServerAddressStream = checkNotNull(queryDnsServerAddressStream, "queryDnsServerAddressStream");
        this.resolveCache = checkNotNull(resolveCache, "resolveCache");
        this.cnameCache = checkNotNull(cnameCache, "cnameCache");
        this.dnsQueryLifecycleObserverFactory = traceEnabled ?
                dnsQueryLifecycleObserverFactory instanceof NoopDnsQueryLifecycleObserverFactory ?
                        new LoggingDnsQueryLifeCycleObserverFactory() :
                        new BiDnsQueryLifecycleObserverFactory(new LoggingDnsQueryLifeCycleObserverFactory(),
                                                               dnsQueryLifecycleObserverFactory) :
                checkNotNull(dnsQueryLifecycleObserverFactory, "dnsQueryLifecycleObserverFactory");
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
        nameServerComparator = new NameServerComparator(addressType(preferredAddressType));
        this.maxNumConsolidation = maxNumConsolidation;
        if (maxNumConsolidation > 0) {
            inflightLookups = new HashMap<String, Future<List<InetAddress>>>();
        } else {
            inflightLookups = null;
        }

        final DnsResponseHandler responseHandler = new DnsResponseHandler(queryContextManager);
        Bootstrap bootstrap = new Bootstrap()
                .channelFactory(channelFactory)
                .group(eventLoop)
                .attr(DNS_PIPELINE_ATTRIBUTE, Boolean.TRUE)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));
                        ch.pipeline().addLast(DATAGRAM_ENCODER, DATAGRAM_DECODER, responseHandler);
                    }
                });
        if (localAddress == null) {
            bootstrap.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
        }
        this.resolveChannelProvider = newProvider(datagramChannelStrategy, bootstrap, localAddress);
    }

    private static DnsResolveChannelProvider newProvider(DnsNameResolverChannelStrategy channelStrategy,
                                                         Bootstrap bootstrap, SocketAddress localAddress) {
        switch (channelStrategy) {
            case ChannelPerResolver:
                return new DnsResolveChannelPerResolverProvider(bootstrap, localAddress);
            case ChannelPerResolution:
                return new DnsResolveChannelPerResolutionProvider(bootstrap, localAddress);
            default:
                throw new IllegalArgumentException("Unknown DnsNameResolverChannelStrategy: " + channelStrategy);
        }
    }

    static SocketProtocolFamily preferredAddressType(ResolvedAddressTypes resolvedAddressTypes) {
        switch (resolvedAddressTypes) {
        case IPV4_ONLY:
        case IPV4_PREFERRED:
            return SocketProtocolFamily.INET;
        case IPV6_ONLY:
        case IPV6_PREFERRED:
            return SocketProtocolFamily.INET6;
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

    SocketProtocolFamily[] resolvedInternetProtocolFamiliesUnsafe() {
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

    final SocketProtocolFamily preferredAddressType() {
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
        resolveChannelProvider.close();
        resolveCache.clear();
        cnameCache.clear();
        authoritativeDnsServerCache.clear();
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
                        (WINDOWS_HOST_NAME != null && WINDOWS_HOST_NAME.equalsIgnoreCase(hostname)));
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

    private Future<List<DnsRecord>> resolveAll(final DnsQuestion question, final DnsRecord[] additionals,
                                               final Promise<List<DnsRecord>> promise) {
        checkNotNull(question, "question");
        checkNotNull(promise, "promise");

        // Respect /etc/hosts as well if the record type is A or AAAA.
        final DnsRecordType type = question.type();
        final String hostname = question.name();

        if (type == DnsRecordType.A || type == DnsRecordType.AAAA) {
            final List<InetAddress> hostsFileEntries = resolveHostsFileEntries(hostname);
            if (hostsFileEntries != null) {
                List<DnsRecord> result = new ArrayList<DnsRecord>();
                for (InetAddress hostsFileEntry : hostsFileEntries) {
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
                        result.add(new DefaultDnsRawRecord(hostname, type, 86400, content));
                    }
                }

                if (!result.isEmpty()) {
                    if (!trySuccess(promise, result)) {
                        // We were not able to transfer ownership, release the records to prevent leaks.
                        for (DnsRecord r: result) {
                            ReferenceCountUtil.safeRelease(r);
                        }
                    }
                    return promise;
                }
            }
        }

        ChannelFuture f = resolveChannelProvider.nextResolveChannel(promise);
        if (f.isDone()) {
            resolveAllNow(f, hostname, question, additionals, promise);
        } else {
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) {
                    resolveAllNow(f, hostname, question, additionals, promise);
                }
            });
        }
        return promise;
    }

    private void resolveAllNow(ChannelFuture f, String hostname, final DnsQuestion question,
                               final DnsRecord[] additionals, final Promise<List<DnsRecord>> promise) {
        if (f.isSuccess()) {
            // It was not A/AAAA question or there was no entry in /etc/hosts.
            final DnsServerAddressStream nameServerAddrs =
                    dnsServerAddressStreamProvider.nameServerAddressStream(hostname);

            new DnsRecordResolveContext(DnsNameResolver.this, f.channel(), promise, question, additionals,
                    nameServerAddrs, maxQueriesPerResolve).resolve(promise);
        } else {
            UnknownHostException e = toException(f, hostname, question, additionals);
            promise.setFailure(e);
        }
    }

    private static UnknownHostException toException(
            ChannelFuture f, String hostname, DnsQuestion question, DnsRecord[] additionals) {
        UnknownHostException e = new UnknownHostException(
                "Failed to resolve '" + hostname + "', couldn't setup transport: " + f.channel());
        e.initCause(f.cause());

        if (question != null) {
            ReferenceCountUtil.release(question);
        }
        for (DnsRecord record : additionals) {
            ReferenceCountUtil.release(record);
        }
        return e;
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
        switch (preferredAddressType()) {
            case INET:
                return NetUtil.LOCALHOST4;
            case INET6:
                return NetUtil.LOCALHOST6;
            default:
                throw new UnsupportedOperationException("Only INET and INET6 are supported");
        }
    }

    /**
     * Hook designed for extensibility so one can pass a different cache on each resolution attempt
     * instead of using the global one.
     */
    protected void doResolve(String inetHost,
                             final DnsRecord[] additionals,
                             final Promise<InetAddress> promise,
                             final DnsCache resolveCache) throws Exception {
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
            ChannelFuture f = resolveChannelProvider.nextResolveChannel(promise);
            if (f.isDone()) {
                doResolveNow(f, hostname, additionals, promise, resolveCache);
            } else {
                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) {
                        doResolveNow(f, hostname, additionals, promise, resolveCache);
                    }
                });
            }
        }
    }

    private void doResolveNow(ChannelFuture f, final String hostname, final DnsRecord[] additionals,
                              final Promise<InetAddress> promise,
                              final DnsCache resolveCache) {
        if (f.isSuccess()) {
            doResolveUncached(f.channel(), hostname, additionals, promise,
                    resolveCache, completeOncePreferredResolved);
        } else {
            UnknownHostException e = toException(f, hostname, null, additionals);
            promise.setFailure(e);
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
            for (SocketProtocolFamily f : resolvedInternetProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    final Class<? extends InetAddress> addressType = addressType(f);
                    if (addressType != null && addressType.isInstance(e.address())) {
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

    static Class<? extends InetAddress> addressType(SocketProtocolFamily f) {
        switch (f) {
            case INET:
                return Inet4Address.class;
            case INET6:
                return Inet6Address.class;
            default:
                return null;
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

    private void doResolveUncached(Channel channel,
                                   String hostname,
                                   DnsRecord[] additionals,
                                   final Promise<InetAddress> promise,
                                   DnsCache resolveCache, boolean completeEarlyIfPossible) {
        final Promise<List<InetAddress>> allPromise = executor().newPromise();
        doResolveAllUncached(channel, hostname, additionals, promise, allPromise,
                resolveCache, completeEarlyIfPossible);
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
                                final DnsRecord[] additionals,
                                final Promise<List<InetAddress>> promise,
                                final DnsCache resolveCache) throws Exception {
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
                ndots(), resolvedInternetProtocolFamilies)) {
            ChannelFuture f = resolveChannelProvider.nextResolveChannel(promise);
            if (f.isDone()) {
                doResolveAllNow(f, hostname, additionals, promise, resolveCache);
            } else {
                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture f) {
                        doResolveAllNow(f, hostname, additionals, promise, resolveCache);
                    }
                });
            }
        }
    }

    private void doResolveAllNow(ChannelFuture f, final String hostname, final DnsRecord[] additionals,
                              final Promise<List<InetAddress>> promise,
                              final DnsCache resolveCache) {
        if (f.isSuccess()) {
            doResolveAllUncached(f.channel(), hostname, additionals, promise, promise,
                    resolveCache, completeOncePreferredResolved);
        } else {
            UnknownHostException e = toException(f, hostname, null, additionals);
            promise.setFailure(e);
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
                                      SocketProtocolFamily[] resolvedInternetProtocolFamilies) {
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
            for (SocketProtocolFamily f : resolvedInternetProtocolFamilies) {
                for (int i = 0; i < numEntries; i++) {
                    final DnsCacheEntry e = cachedEntries.get(i);
                    Class<? extends InetAddress> addressType = addressType(f);
                    if (addressType != null && addressType.isInstance(e.address())) {
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

    private void doResolveAllUncached(final Channel channel,
                                      final String hostname,
                                      final DnsRecord[] additionals,
                                      final Promise<?> originalPromise,
                                      final Promise<List<InetAddress>> promise,
                                      final DnsCache resolveCache,
                                      final boolean completeEarlyIfPossible) {
        // Call doResolveUncached0(...) in the EventLoop as we may need to submit multiple queries which would need
        // to submit multiple Runnable at the end if we are not already on the EventLoop.
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            doResolveAllUncached0(channel, hostname, additionals, originalPromise,
                                  promise, resolveCache, completeEarlyIfPossible);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    doResolveAllUncached0(channel, hostname, additionals, originalPromise,
                                          promise, resolveCache, completeEarlyIfPossible);
                }
            });
        }
    }

    private void doResolveAllUncached0(final Channel channel,
                                       final String hostname,
                                       final DnsRecord[] additionals,
                                       final Promise<?> originalPromise,
                                       final Promise<List<InetAddress>> promise,
                                       final DnsCache resolveCache,
                                       final boolean completeEarlyIfPossible) {

        assert executor().inEventLoop();

        if (inflightLookups != null && (additionals == null || additionals.length == 0)) {
            Future<List<InetAddress>> inflightFuture = inflightLookups.get(hostname);
            if (inflightFuture != null) {
                inflightFuture.addListener(new GenericFutureListener<Future<? super List<InetAddress>>>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void operationComplete(Future<? super List<InetAddress>> future) {
                        if (future.isSuccess()) {
                            promise.setSuccess((List<InetAddress>) future.getNow());
                        } else {
                            Throwable cause = future.cause();
                            if (isTimeoutError(cause)) {
                                // The failure was caused by a timeout. This might be happening as a result of
                                // the remote server be overloaded for some short amount of time or because
                                // UDP packets were dropped on the floor. In this case lets try to just do the
                                // query explicit and don't cascade this possible temporary failure.
                                resolveNow(channel, hostname, additionals, originalPromise, promise,
                                        resolveCache, completeEarlyIfPossible);
                            } else {
                                promise.setFailure(cause);
                            }
                        }
                    }
                });
                return;
            // Check if we have space left in the map.
            } else if (inflightLookups.size() < maxNumConsolidation) {
                inflightLookups.put(hostname, promise);
                promise.addListener(new GenericFutureListener<Future<? super List<InetAddress>>>() {
                    @Override
                    public void operationComplete(Future<? super List<InetAddress>> future) {
                        inflightLookups.remove(hostname);
                    }
                });
            }
        }
        resolveNow(channel, hostname, additionals, originalPromise, promise,
                resolveCache, completeEarlyIfPossible);
    }

    private void resolveNow(final Channel channel,
                            final String hostname,
                            final DnsRecord[] additionals,
                            final Promise<?> originalPromise,
                            final Promise<List<InetAddress>> promise,
                            final DnsCache resolveCache,
                            final boolean completeEarlyIfPossible) {
        final DnsServerAddressStream nameServerAddrs =
                dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
        DnsAddressResolveContext ctx = new DnsAddressResolveContext(this, channel,
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
        return query(nextNameServerAddress(), question, Collections.<DnsRecord>emptyList(), promise);
    }

    private InetSocketAddress nextNameServerAddress() {
        return queryDnsServerAddressStream.next();
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            final InetSocketAddress nameServerAddr, final DnsQuestion question) {
        return query(nameServerAddr, question, Collections.<DnsRecord>emptyList());
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            final InetSocketAddress nameServerAddr, final DnsQuestion question, final Iterable<DnsRecord> additionals) {
        return query(nameServerAddr, question, additionals,
                executor().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            final InetSocketAddress nameServerAddr, final DnsQuestion question,
            final Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {
        return query(nameServerAddr, question, Collections.<DnsRecord>emptyList(), promise);
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            final InetSocketAddress nameServerAddr, final DnsQuestion question,
            final Iterable<DnsRecord> additionals,
            final Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        ChannelFuture f = resolveChannelProvider.nextResolveChannel(promise);
        final DnsRecord[] additionalsArray = toArray(additionals, false);
        if (f.isDone()) {
            if (f.isSuccess()) {
                return doQuery(f.channel(), nameServerAddr, question,
                        NoopDnsQueryLifecycleObserver.INSTANCE, additionalsArray,
                        true, promise);
            } else {
                UnknownHostException e = toException(f, question.name(), question, additionalsArray);
                promise.setFailure(e);
                return executor().newFailedFuture(e);
            }
        } else {
            final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> p = executor().newPromise();
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) {
                    if (f.isSuccess()) {
                        Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> qf = doQuery(
                                f.channel(), nameServerAddr, question, NoopDnsQueryLifecycleObserver.INSTANCE,
                                additionalsArray, true, promise);
                        PromiseNotifier.cascade(qf, p);
                    } else {
                        UnknownHostException e = toException(f, question.name(), question, additionalsArray);
                        promise.setFailure(e);
                        p.setFailure(e);
                    }
                }
            });
            return p;
        }
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
            Channel channel,
            InetSocketAddress nameServerAddr, DnsQuestion question,
            final DnsQueryLifecycleObserver queryLifecycleObserver,
            DnsRecord[] additionals, boolean flush,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> castPromise = cast(
                checkNotNull(promise, "promise"));
        final int payloadSize = isOptResourceEnabled() ? maxPayloadSize() : 0;
        try {
            DnsQueryContext queryContext = new DatagramDnsQueryContext(channel, nameServerAddr,
                    queryContextManager, payloadSize, isRecursionDesired(), queryTimeoutMillis(), question, additionals,
                    castPromise, socketBootstrap, retryWithTcpOnTimeout);
            ChannelFuture future = queryContext.writeQuery(flush);
            queryLifecycleObserver.queryWritten(nameServerAddr, future);
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

    private static final class DnsResponseHandler extends ChannelInboundHandlerAdapter {

        private final DnsQueryContextManager queryContextManager;

        DnsResponseHandler(DnsQueryContextManager queryContextManager) {
            this.queryContextManager = queryContextManager;
        }

        @Override
        public boolean isSharable() {
            return true;
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
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof CorruptedFrameException) {
                logger.debug("{} Unable to decode DNS response: UDP", ctx.channel(), cause);
            } else {
                logger.warn("{} Unexpected exception: UDP", ctx.channel(), cause);
            }
        }
    }

    private interface DnsResolveChannelProvider {

        /**
         * Return the next {@link ChannelFuture} that contains the {@link Channel} that should be used for resolving
         * a chain of queries.
         *
         * @param resolutionFuture  the {@link Future} that will be notified once th resolution completes.
         * @return                  the {@link ChannelFuture}
         */
        <T> ChannelFuture nextResolveChannel(Future<T> resolutionFuture);

        /**
         * Close the {@link DnsResolveChannelProvider} and so cleanup resources if needed.
         */
        void close();
    }

    private static ChannelFuture registerOrBind(Bootstrap bootstrap, SocketAddress localAddress) {
        return localAddress == null ? bootstrap.register() : bootstrap.bind(localAddress);
    }

    private static final class DnsResolveChannelPerResolverProvider implements DnsResolveChannelProvider {

        private final ChannelFuture resolveChannelFuture;

        DnsResolveChannelPerResolverProvider(Bootstrap bootstrap, SocketAddress localAddress) {
            resolveChannelFuture = registerOrBind(bootstrap, localAddress);
        }

        @Override
        public <T> ChannelFuture nextResolveChannel(Future<T> resolutionFuture) {
            return resolveChannelFuture;
        }

        @Override
        public void close() {
            resolveChannelFuture.channel().close();
        }
    }

    private static final class DnsResolveChannelPerResolutionProvider implements DnsResolveChannelProvider {

        private final Bootstrap bootstrap;
        private final SocketAddress localAddress;

        DnsResolveChannelPerResolutionProvider(Bootstrap bootstrap, SocketAddress localAddress) {
            this.bootstrap = bootstrap;
            this.localAddress = localAddress;
        }

        @Override
        public <T> ChannelFuture nextResolveChannel(Future<T> resolutionFuture) {
            final ChannelFuture f = registerOrBind(bootstrap, localAddress);
            resolutionFuture.addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) {
                    // Always just close the Channel once the resolution is considered complete.
                    f.channel().close();
                }
            });
            return f;
        }

        @Override
        public void close() {
            // NOOP
        }
    }
}
