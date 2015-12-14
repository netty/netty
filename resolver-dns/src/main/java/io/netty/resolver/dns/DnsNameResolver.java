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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.InetNameResolver;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A DNS-based {@link InetNameResolver}.
 */
public class DnsNameResolver extends InetNameResolver {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);

    static final InternetProtocolFamily[] DEFAULT_RESOLVE_ADDRESS_TYPES = new InternetProtocolFamily[2];

    static {
        // Note that we did not use SystemPropertyUtil.getBoolean() here to emulate the behavior of JDK.
        if (Boolean.getBoolean("java.net.preferIPv6Addresses")) {
            DEFAULT_RESOLVE_ADDRESS_TYPES[0] = InternetProtocolFamily.IPv6;
            DEFAULT_RESOLVE_ADDRESS_TYPES[1] = InternetProtocolFamily.IPv4;
            logger.debug("-Djava.net.preferIPv6Addresses: true");
        } else {
            DEFAULT_RESOLVE_ADDRESS_TYPES[0] = InternetProtocolFamily.IPv4;
            DEFAULT_RESOLVE_ADDRESS_TYPES[1] = InternetProtocolFamily.IPv6;
            logger.debug("-Djava.net.preferIPv6Addresses: false");
        }
    }

    private static final DatagramDnsResponseDecoder DECODER = new DatagramDnsResponseDecoder();
    private static final DatagramDnsQueryEncoder ENCODER = new DatagramDnsQueryEncoder();

    final DnsServerAddresses nameServerAddresses;
    final ChannelFuture bindFuture;
    final DatagramChannel ch;

    /**
     * Manages the {@link DnsQueryContext}s in progress and their query IDs.
     */
    final DnsQueryContextManager queryContextManager = new DnsQueryContextManager();

    /**
     * Cache for {@link #doResolve(String, Promise)} and {@link #doResolveAll(String, Promise)}.
     */
    private final ConcurrentMap<String, List<DnsCacheEntry>> resolveCache = PlatformDependent.newConcurrentHashMap();

    private final FastThreadLocal<DnsServerAddressStream> nameServerAddrStream =
            new FastThreadLocal<DnsServerAddressStream>() {
                @Override
                protected DnsServerAddressStream initialValue() throws Exception {
                    return nameServerAddresses.stream();
                }
            };

    private final long queryTimeoutMillis;
    // The default TTL values here respect the TTL returned by the DNS server and do not cache the negative response.
    private final int minTtl;
    private final int maxTtl;
    private final int negativeTtl;
    private final int maxQueriesPerResolve;
    private final boolean traceEnabled;
    private final InternetProtocolFamily[] resolvedAddressTypes;
    private final boolean recursionDesired;
    private final int maxPayloadSize;
    private final boolean optResourceEnabled;
    private final HostsFileEntriesResolver hostsFileEntriesResolver;

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new stream is created from
     *                            this to determine which DNS server should be contacted for the next retry in case
     *                            of failure.
     * @param minTtl the minimum TTL of cached DNS records
     * @param maxTtl the maximum TTL of cached DNS records
     * @param negativeTtl the TTL for failed cached queries
     * @param queryTimeoutMillis timeout of each DNS query in millis
     * @param resolvedAddressTypes list of the protocol families
     * @param recursionDesired if recursion desired flag must be set
     * @param maxQueriesPerResolve the maximum allowed number of DNS queries for a given name resolution
     * @param traceEnabled if trace is enabled
     * @param maxPayloadSize the capacity of the datagram packet buffer
     * @param optResourceEnabled if automatic inclusion of a optional records is enabled
     * @param hostsFileEntriesResolver the {@link HostsFileEntriesResolver} used to check for local aliases
     */
    public DnsNameResolver(
            EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress localAddress,
            DnsServerAddresses nameServerAddresses,
            int minTtl,
            int maxTtl,
            int negativeTtl,
            long queryTimeoutMillis,
            InternetProtocolFamily[] resolvedAddressTypes,
            boolean recursionDesired,
            int maxQueriesPerResolve,
            boolean traceEnabled,
            int maxPayloadSize,
            boolean optResourceEnabled,
            HostsFileEntriesResolver hostsFileEntriesResolver) {

        super(eventLoop);
        checkNotNull(channelFactory, "channelFactory");
        checkNotNull(localAddress, "localAddress");
        this.nameServerAddresses = checkNotNull(nameServerAddresses, "nameServerAddresses");
        this.minTtl = checkPositiveOrZero(minTtl, "minTtl");
        this.maxTtl = checkPositiveOrZero(maxTtl, "maxTtl");
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }
        this.negativeTtl = checkPositiveOrZero(negativeTtl, "negativeTtl");
        this.queryTimeoutMillis = checkPositive(queryTimeoutMillis, "queryTimeoutMillis");
        this.resolvedAddressTypes = checkNonEmpty(resolvedAddressTypes, "resolvedAddressTypes");
        this.recursionDesired = recursionDesired;
        this.maxQueriesPerResolve = checkPositive(maxQueriesPerResolve, "maxQueriesPerResolve");
        this.traceEnabled = traceEnabled;
        this.maxPayloadSize = checkPositive(maxPayloadSize, "maxPayloadSize");
        this.optResourceEnabled = optResourceEnabled;
        this.hostsFileEntriesResolver = checkNotNull(hostsFileEntriesResolver, "hostsFileEntriesResolver");

        bindFuture = newChannel(channelFactory, localAddress);
        ch = (DatagramChannel) bindFuture.channel();
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));
    }

    private ChannelFuture newChannel(
            ChannelFactory<? extends DatagramChannel> channelFactory, InetSocketAddress localAddress) {

        Bootstrap b = new Bootstrap();
        b.group(executor());
        b.channelFactory(channelFactory);
        final DnsResponseHandler responseHandler = new DnsResponseHandler();
        b.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) throws Exception {
                ch.pipeline().addLast(DECODER, ENCODER, responseHandler);
            }
        });

        ChannelFuture bindFuture = b.bind(localAddress);
        bindFuture.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                clearCache();
            }
        });

        return bindFuture;
    }

    /**
     * Returns the minimum TTL of the cached DNS resource records (in seconds).
     *
     * @see #maxTtl()
     */
    public int minTtl() {
        return minTtl;
    }

    /**
     * Returns the maximum TTL of the cached DNS resource records (in seconds).
     *
     * @see #minTtl()
     */
    public int maxTtl() {
        return maxTtl;
    }

    /**
     * Returns the TTL of the cache for the failed DNS queries (in seconds).  The default value is {@code 0}, which
     * disables the cache for negative results.
     */
    public int negativeTtl() {
        return negativeTtl;
    }

    /**
     * Returns the timeout of each DNS query performed by this resolver (in milliseconds).
     * The default value is 5 seconds.
     */
    public long queryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    /**
     * Returns the list of the protocol families of the address resolved by {@link #resolve(String)}
     * in the order of preference.
     * The default value depends on the value of the system property {@code "java.net.preferIPv6Addresses"}.
     */
    public List<InternetProtocolFamily> resolvedAddressTypes() {
        return Arrays.asList(resolvedAddressTypes);
    }

    InternetProtocolFamily[] resolveAddressTypesUnsafe() {
        return resolvedAddressTypes;
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
     * Returns if this resolver should generate the detailed trace information in an exception message so that
     * it is easier to understand the cause of resolution failure. The default value if {@code true}.
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
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
     * Clears all the resolved addresses cached by this resolver.
     *
     * @return {@code this}
     *
     * @see #clearCache(String)
     */
    public DnsNameResolver clearCache() {
        for (Iterator<Entry<String, List<DnsCacheEntry>>> i = resolveCache.entrySet().iterator(); i.hasNext();) {
            final Entry<String, List<DnsCacheEntry>> e = i.next();
            i.remove();
            cancelExpiration(e);
        }
        return this;
    }

    /**
     * Clears the resolved addresses of the specified host name from the cache of this resolver.
     *
     * @return {@code true} if and only if there was an entry for the specified host name in the cache and
     *         it has been removed by this method
     */
    public boolean clearCache(String hostname) {
        boolean removed = false;
        for (Iterator<Entry<String, List<DnsCacheEntry>>> i = resolveCache.entrySet().iterator(); i.hasNext();) {
            final Entry<String, List<DnsCacheEntry>> e = i.next();
            if (e.getKey().equals(hostname)) {
                i.remove();
                cancelExpiration(e);
                removed = true;
            }
        }
        return removed;
    }

    private static void cancelExpiration(Entry<String, List<DnsCacheEntry>> e) {
        final List<DnsCacheEntry> entries = e.getValue();
        final int numEntries = entries.size();
        for (int i = 0; i < numEntries; i++) {
            entries.get(i).cancelExpiration();
        }
    }

    /**
     * Closes the internal datagram channel used for sending and receiving DNS messages, and clears all DNS resource
     * records from the cache. Attempting to send a DNS query or to resolve a domain name will fail once this method
     * has been called.
     */
    @Override
    public void close() {
        ch.close();
    }

    @Override
    protected EventLoop executor() {
        return (EventLoop) super.executor();
    }

    private InetAddress resolveHostsFileEntry(String hostname) {
        return hostsFileEntriesResolver != null ? hostsFileEntriesResolver.address(hostname) : null;
    }

    @Override
    protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
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

        if (!doResolveCached(hostname, promise)) {
            doResolveUncached(hostname, promise);
        }
    }

    private boolean doResolveCached(String hostname, Promise<InetAddress> promise) {
        final List<DnsCacheEntry> cachedEntries = resolveCache.get(hostname);
        if (cachedEntries == null) {
            return false;
        }

        InetAddress address = null;
        Throwable cause = null;
        synchronized (cachedEntries) {
            final int numEntries = cachedEntries.size();
            assert numEntries > 0;

            if (cachedEntries.get(0).cause() != null) {
                cause = cachedEntries.get(0).cause();
            } else {
                // Find the first entry with the preferred address type.
                for (InternetProtocolFamily f : resolvedAddressTypes) {
                    for (int i = 0; i < numEntries; i++) {
                        final DnsCacheEntry e = cachedEntries.get(i);
                        if (f.addressType().isInstance(e.address())) {
                            address = e.address();
                            break;
                        }
                    }
                }
            }
        }

        if (address != null) {
            setSuccess(promise, address);
        } else if (cause != null) {
            if (!promise.tryFailure(cause)) {
                logger.warn("Failed to notify failure to a promise: {}", promise, cause);
            }
        } else {
            return false;
        }

        return true;
    }

    private static void setSuccess(Promise<InetAddress> promise, InetAddress result) {
        if (!promise.trySuccess(result)) {
            logger.warn("Failed to notify success ({}) to a promise: {}", result, promise);
        }
    }

    private void doResolveUncached(String hostname, Promise<InetAddress> promise) {
        final DnsNameResolverContext<InetAddress> ctx =
                new DnsNameResolverContext<InetAddress>(this, hostname, promise) {
                    @Override
                    protected boolean finishResolve(
                            Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries) {

                        final int numEntries = resolvedEntries.size();
                        for (int i = 0; i < numEntries; i++) {
                            final InetAddress a = resolvedEntries.get(i).address();
                            if (addressType.isInstance(a)) {
                                setSuccess(promise(), a);
                                return true;
                            }
                        }
                        return false;
                    }
                };

        ctx.resolve();
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {

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

        if (!doResolveAllCached(hostname, promise)) {
            doResolveAllUncached(hostname, promise);
        }
    }

    private boolean doResolveAllCached(String hostname, Promise<List<InetAddress>> promise) {
        final List<DnsCacheEntry> cachedEntries = resolveCache.get(hostname);
        if (cachedEntries == null) {
            return false;
        }

        List<InetAddress> result = null;
        Throwable cause = null;
        synchronized (cachedEntries) {
            final int numEntries = cachedEntries.size();
            assert numEntries > 0;

            if (cachedEntries.get(0).cause() != null) {
                cause = cachedEntries.get(0).cause();
            } else {
                for (InternetProtocolFamily f : resolvedAddressTypes) {
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
            }
        }

        if (result != null) {
            promise.trySuccess(result);
        } else if (cause != null) {
            promise.tryFailure(cause);
        } else {
            return false;
        }

        return true;
    }

    private void doResolveAllUncached(final String hostname, final Promise<List<InetAddress>> promise) {
        final DnsNameResolverContext<List<InetAddress>> ctx =
                new DnsNameResolverContext<List<InetAddress>>(this, hostname, promise) {
                    @Override
                    protected boolean finishResolve(
                            Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries) {

                        List<InetAddress> result = null;
                        final int numEntries = resolvedEntries.size();
                        for (int i = 0; i < numEntries; i++) {
                            final InetAddress a = resolvedEntries.get(i).address();
                            if (addressType.isInstance(a)) {
                                if (result == null) {
                                    result = new ArrayList<InetAddress>(numEntries);
                                }
                                result.add(a);
                            }
                        }

                        if (result != null) {
                            promise().trySuccess(result);
                            return true;
                        }
                        return false;
                    }
                };

        ctx.resolve();
    }

    private static String hostname(String inetHost) {
        return IDN.toASCII(inetHost);
    }

    void cache(String hostname, InetAddress address, long originalTtl) {
        final int maxTtl = maxTtl();
        if (maxTtl == 0) {
            return;
        }

        final int ttl = Math.max(minTtl(), (int) Math.min(maxTtl, originalTtl));
        final List<DnsCacheEntry> entries = cachedEntries(hostname);
        final DnsCacheEntry e = new DnsCacheEntry(hostname, address);

        synchronized (entries) {
            if (!entries.isEmpty()) {
                final DnsCacheEntry firstEntry = entries.get(0);
                if (firstEntry.cause() != null) {
                    assert entries.size() == 1;
                    firstEntry.cancelExpiration();
                    entries.clear();
                }
            }
            entries.add(e);
        }

        scheduleCacheExpiration(entries, e, ttl);
    }

    void cache(String hostname, Throwable cause) {
        final int negativeTtl = negativeTtl();
        if (negativeTtl == 0) {
            return;
        }

        final List<DnsCacheEntry> entries = cachedEntries(hostname);
        final DnsCacheEntry e = new DnsCacheEntry(hostname, cause);

        synchronized (entries) {
            final int numEntries = entries.size();
            for (int i = 0; i < numEntries; i ++) {
                entries.get(i).cancelExpiration();
            }
            entries.clear();
            entries.add(e);
        }

        scheduleCacheExpiration(entries, e, negativeTtl);
    }

    private List<DnsCacheEntry> cachedEntries(String hostname) {
        List<DnsCacheEntry> oldEntries = resolveCache.get(hostname);
        final List<DnsCacheEntry> entries;
        if (oldEntries == null) {
            List<DnsCacheEntry> newEntries = new ArrayList<DnsCacheEntry>();
            oldEntries = resolveCache.putIfAbsent(hostname, newEntries);
            entries = oldEntries != null? oldEntries : newEntries;
        } else {
            entries = oldEntries;
        }
        return entries;
    }

    private void scheduleCacheExpiration(final List<DnsCacheEntry> entries, final DnsCacheEntry e, int ttl) {
        e.scheduleExpiration(
                ch.eventLoop(),
                new OneTimeTask() {
                    @Override
                    public void run() {
                        synchronized (entries) {
                            entries.remove(e);
                            if (entries.isEmpty()) {
                                resolveCache.remove(e.hostname());
                            }
                        }
                    }
                }, ttl, TimeUnit.SECONDS);
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(DnsQuestion question) {
        return query(nextNameServerAddress(), question);
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            DnsQuestion question, Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {
        return query(nextNameServerAddress(), question, promise);
    }

    private InetSocketAddress nextNameServerAddress() {
        return nameServerAddrStream.get().next();
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question) {

        return query0(checkNotNull(nameServerAddr, "nameServerAddr"),
                      checkNotNull(question, "question"),
                      ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return query0(checkNotNull(nameServerAddr, "nameServerAddr"),
                      checkNotNull(question, "question"),
                      checkNotNull(promise, "promise"));
    }

    private Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query0(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> castPromise = cast(promise);
        try {
            new DnsQueryContext(this, nameServerAddr, question, castPromise).query();
            return castPromise;
        } catch (Exception e) {
            return castPromise.setFailure(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> cast(Promise<?> promise) {
        return (Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>>) promise;
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                final DatagramDnsResponse res = (DatagramDnsResponse) msg;
                final int queryId = res.id();

                if (logger.isDebugEnabled()) {
                    logger.debug("{} RECEIVED: [{}: {}], {}", ch, queryId, res.sender(), res);
                }

                final DnsQueryContext qCtx = queryContextManager.get(res.sender(), queryId);
                if (qCtx == null) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("{} Received a DNS response with an unknown ID: {}", ch, queryId);
                    }
                    return;
                }

                qCtx.finish(res);
            } finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn("{} Unexpected exception: ", ch, cause);
        }
    }
}
