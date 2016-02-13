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
import io.netty.handler.codec.dns.DnsRecord;
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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private final DnsCache resolveCache;

    private final FastThreadLocal<DnsServerAddressStream> nameServerAddrStream =
            new FastThreadLocal<DnsServerAddressStream>() {
                @Override
                protected DnsServerAddressStream initialValue() throws Exception {
                    return nameServerAddresses.stream();
                }
            };

    private final long queryTimeoutMillis;
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
     * @param resolveCache the DNS resolved entries cache
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
            DnsCache resolveCache,
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
        this.queryTimeoutMillis = checkPositive(queryTimeoutMillis, "queryTimeoutMillis");
        this.resolvedAddressTypes = checkNonEmpty(resolvedAddressTypes, "resolvedAddressTypes");
        this.recursionDesired = recursionDesired;
        this.maxQueriesPerResolve = checkPositive(maxQueriesPerResolve, "maxQueriesPerResolve");
        this.traceEnabled = traceEnabled;
        this.maxPayloadSize = checkPositive(maxPayloadSize, "maxPayloadSize");
        this.optResourceEnabled = optResourceEnabled;
        this.hostsFileEntriesResolver = checkNotNull(hostsFileEntriesResolver, "hostsFileEntriesResolver");
        this.resolveCache = resolveCache;

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
                resolveCache.clear();
            }
        });

        return bindFuture;
    }

    /**
     * Returns the resolution cache.
     */
    public DnsCache resolveCache() {
        return resolveCache;
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
        doResolve(inetHost, promise, resolveCache);
    }

    /**
     * Hook designed for extensibility so one can pass a different cache on each resolution attempt
     * instead of using the global one.
     */
    protected void doResolve(String inetHost,
                             Promise<InetAddress> promise,
                             DnsCache resolveCache) throws Exception {
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

        if (!doResolveCached(hostname, promise, resolveCache)) {
            doResolveUncached(hostname, promise, resolveCache);
        }
    }

    private boolean doResolveCached(String hostname,
                                    Promise<InetAddress> promise,
                                    DnsCache resolveCache) {
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

    private void doResolveUncached(String hostname,
                                   Promise<InetAddress> promise,
                                   DnsCache resolveCache) {
        final DnsNameResolverContext<InetAddress> ctx =
                new DnsNameResolverContext<InetAddress>(this, hostname, promise, resolveCache) {
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
        doResolveAll(inetHost, promise, resolveCache);
    }

    /**
     * Hook designed for extensibility so one can pass a different cache on each resolution attempt
     * instead of using the global one.
     */
    protected void doResolveAll(String inetHost,
                                Promise<List<InetAddress>> promise,
                                DnsCache resolveCache) throws Exception {

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

        if (!doResolveAllCached(hostname, promise, resolveCache)) {
            doResolveAllUncached(hostname, promise, resolveCache);
        }
    }

    private boolean doResolveAllCached(String hostname,
                                       Promise<List<InetAddress>> promise,
                                       DnsCache resolveCache) {
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

    private void doResolveAllUncached(final String hostname,
                                      final Promise<List<InetAddress>> promise,
                                      DnsCache resolveCache) {
        final DnsNameResolverContext<List<InetAddress>> ctx =
                new DnsNameResolverContext<List<InetAddress>>(this, hostname, promise, resolveCache) {
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
            DnsQuestion question, Iterable<DnsRecord> additional) {
        return query(nextNameServerAddress(), question, additional);
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

        return query0(nameServerAddr, question, Collections.<DnsRecord>emptyList(),
                ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question, Iterable<DnsRecord> additional) {

        return query0(nameServerAddr, question, additional,
                ch.eventLoop().<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>>newPromise());
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return query0(nameServerAddr, question, Collections.<DnsRecord>emptyList(), promise);
    }

    /**
     * Sends a DNS query with the specified question with additional records using the specified name server list.
     */
    public Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Iterable<DnsRecord> additional,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        return query0(nameServerAddr, question, additional, promise);
    }

    private Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> query0(
            InetSocketAddress nameServerAddr, DnsQuestion question,
            Iterable<DnsRecord> additional,
            Promise<AddressedEnvelope<? extends DnsResponse, InetSocketAddress>> promise) {

        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> castPromise = cast(
                checkNotNull(promise, "promise"));
        try {
            new DnsQueryContext(this, nameServerAddr, question, additional, castPromise).query();
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
                    logger.warn("{} Received a DNS response with an unknown ID: {}", ch, queryId);
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
