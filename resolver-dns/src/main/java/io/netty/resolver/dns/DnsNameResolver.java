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
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.resolver.NameResolver;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A DNS-based {@link NameResolver}.
 */
public class DnsNameResolver extends SimpleNameResolver<InetSocketAddress> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);

    private static final InternetProtocolFamily[] DEFAULT_RESOLVE_ADDRESS_TYPES = new InternetProtocolFamily[2];

    static {
        // Note that we did not use SystemPropertyUtil.getBoolean() here to emulate the behavior of JDK.
        if ("true".equalsIgnoreCase(SystemPropertyUtil.get("java.net.preferIPv6Addresses"))) {
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
     * An array whose index is the ID of a DNS query and whose value is the promise of the corresponsing response. We
     * don't use {@link IntObjectHashMap} or map-like data structure here because 64k elements are fairly small, which
     * is only about 512KB.
     */
    final AtomicReferenceArray<DnsQueryContext> promises = new AtomicReferenceArray<DnsQueryContext>(65536);

    /**
     * Cache for {@link #doResolve(InetSocketAddress, Promise)} and {@link #doResolveAll(InetSocketAddress, Promise)}.
     */
    final ConcurrentMap<String, List<DnsCacheEntry>> resolveCache = PlatformDependent.newConcurrentHashMap();

    private final FastThreadLocal<DnsServerAddressStream> nameServerAddrStream =
            new FastThreadLocal<DnsServerAddressStream>() {
                @Override
                protected DnsServerAddressStream initialValue() throws Exception {
                    return nameServerAddresses.stream();
                }
            };

    private final DnsResponseHandler responseHandler = new DnsResponseHandler();

    private volatile long queryTimeoutMillis = 5000;

    // The default TTL values here respect the TTL returned by the DNS server and do not cache the negative response.
    private volatile int minTtl;
    private volatile int maxTtl = Integer.MAX_VALUE;
    private volatile int negativeTtl;
    private volatile int maxQueriesPerResolve = 3;
    private volatile boolean traceEnabled = true;

    private volatile InternetProtocolFamily[] resolveAddressTypes = DEFAULT_RESOLVE_ADDRESS_TYPES;
    private volatile boolean recursionDesired = true;

    private volatile int maxPayloadSize;

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new stream is created from
     *                            this to determine which DNS server should be contacted for the next retry in case
     *                            of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            DnsServerAddresses nameServerAddresses) {
        this(eventLoop, channelType, ANY_LOCAL_ADDR, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new stream is created from
     *                            this to determine which DNS server should be contacted for the next retry in case
     *                            of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress localAddress, DnsServerAddresses nameServerAddresses) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), localAddress, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new stream is created from
     *                            this to determine which DNS server should be contacted for the next retry in case
     *                            of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            DnsServerAddresses nameServerAddresses) {
        this(eventLoop, channelFactory, ANY_LOCAL_ADDR, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new stream is created from
     *                            this to determine which DNS server should be contacted for the next retry in case
     *                            of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress localAddress, DnsServerAddresses nameServerAddresses) {

        super(eventLoop);

        checkNotNull(channelFactory, "channelFactory");
        checkNotNull(nameServerAddresses, "nameServerAddresses");
        checkNotNull(localAddress, "localAddress");

        this.nameServerAddresses = nameServerAddresses;
        bindFuture = newChannel(channelFactory, localAddress);
        ch = (DatagramChannel) bindFuture.channel();

        setMaxPayloadSize(4096);
    }

    private ChannelFuture newChannel(
            ChannelFactory<? extends DatagramChannel> channelFactory, InetSocketAddress localAddress) {

        Bootstrap b = new Bootstrap();
        b.group(executor());
        b.channelFactory(channelFactory);
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
     * @see #setTtl(int, int)
     */
    public int minTtl() {
        return minTtl;
    }

    /**
     * Returns the maximum TTL of the cached DNS resource records (in seconds).
     *
     * @see #minTtl()
     * @see #setTtl(int, int)
     */
    public int maxTtl() {
        return maxTtl;
    }

    /**
     * Sets the minimum and maximum TTL of the cached DNS resource records (in seconds). If the TTL of the DNS resource
     * record returned by the DNS server is less than the minimum TTL or greater than the maximum TTL, this resolver
     * will ignore the TTL from the DNS server and use the minimum TTL or the maximum TTL instead respectively.
     * The default value is {@code 0} and {@link Integer#MAX_VALUE}, which practically tells this resolver to respect
     * the TTL from the DNS server.
     *
     * @return {@code this}
     *
     * @see #minTtl()
     * @see #maxTtl()
     */
    public DnsNameResolver setTtl(int minTtl, int maxTtl) {
        if (minTtl < 0) {
            throw new IllegalArgumentException("minTtl: " + minTtl + " (expected: >= 0)");
        }
        if (maxTtl < 0) {
            throw new IllegalArgumentException("maxTtl: " + maxTtl + " (expected: >= 0)");
        }
        if (minTtl > maxTtl) {
            throw new IllegalArgumentException(
                    "minTtl: " + minTtl + ", maxTtl: " + maxTtl + " (expected: 0 <= minTtl <= maxTtl)");
        }

        this.maxTtl = maxTtl;
        this.minTtl = minTtl;

        return this;
    }

    /**
     * Returns the TTL of the cache for the failed DNS queries (in seconds).  The default value is {@code 0}, which
     * disables the cache for negative results.
     *
     * @see #setNegativeTtl(int)
     */
    public int negativeTtl() {
        return negativeTtl;
    }

    /**
     * Sets the TTL of the cache for the failed DNS queries (in seconds).
     *
     * @return {@code this}
     *
     * @see #negativeTtl()
     */
    public DnsNameResolver setNegativeTtl(int negativeTtl) {
        if (negativeTtl < 0) {
            throw new IllegalArgumentException("negativeTtl: " + negativeTtl + " (expected: >= 0)");
        }

        this.negativeTtl = negativeTtl;

        return this;
    }

    /**
     * Returns the timeout of each DNS query performed by this resolver (in milliseconds).
     * The default value is 5 seconds.
     *
     * @see #setQueryTimeoutMillis(long)
     */
    public long queryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    /**
     * Sets the timeout of each DNS query performed by this resolver (in milliseconds).
     *
     * @return {@code this}
     *
     * @see #queryTimeoutMillis()
     */
    public DnsNameResolver setQueryTimeoutMillis(long queryTimeoutMillis) {
        if (queryTimeoutMillis < 0) {
            throw new IllegalArgumentException("queryTimeoutMillis: " + queryTimeoutMillis + " (expected: >= 0)");
        }

        this.queryTimeoutMillis = queryTimeoutMillis;

        return this;
    }

    /**
     * Returns the list of the protocol families of the address resolved by {@link #resolve(SocketAddress)}
     * in the order of preference.
     * The default value depends on the value of the system property {@code "java.net.preferIPv6Addresses"}.
     *
     * @see #setResolveAddressTypes(InternetProtocolFamily...)
     */
    public List<InternetProtocolFamily> resolveAddressTypes() {
        return Arrays.asList(resolveAddressTypes);
    }

    InternetProtocolFamily[] resolveAddressTypesUnsafe() {
        return resolveAddressTypes;
    }

    /**
     * Sets the list of the protocol families of the address resolved by {@link #resolve(SocketAddress)}.
     * Usually, both {@link InternetProtocolFamily#IPv4} and {@link InternetProtocolFamily#IPv6} are specified in the
     * order of preference.  To enforce the resolve to retrieve the address of a specific protocol family, specify
     * only a single {@link InternetProtocolFamily}.
     *
     * @return {@code this}
     *
     * @see #resolveAddressTypes()
     */
    public DnsNameResolver setResolveAddressTypes(InternetProtocolFamily... resolveAddressTypes) {
        checkNotNull(resolveAddressTypes, "resolveAddressTypes");

        final List<InternetProtocolFamily> list =
                new ArrayList<InternetProtocolFamily>(InternetProtocolFamily.values().length);

        for (InternetProtocolFamily f: resolveAddressTypes) {
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

        this.resolveAddressTypes = list.toArray(new InternetProtocolFamily[list.size()]);

        return this;
    }

    /**
     * Sets the list of the protocol families of the address resolved by {@link #resolve(SocketAddress)}.
     * Usually, both {@link InternetProtocolFamily#IPv4} and {@link InternetProtocolFamily#IPv6} are specified in the
     * order of preference.  To enforce the resolve to retrieve the address of a specific protocol family, specify
     * only a single {@link InternetProtocolFamily}.
     *
     * @return {@code this}
     *
     * @see #resolveAddressTypes()
     */
    public DnsNameResolver setResolveAddressTypes(Iterable<InternetProtocolFamily> resolveAddressTypes) {
        checkNotNull(resolveAddressTypes, "resolveAddressTypes");

        final List<InternetProtocolFamily> list =
                new ArrayList<InternetProtocolFamily>(InternetProtocolFamily.values().length);

        for (InternetProtocolFamily f: resolveAddressTypes) {
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

        this.resolveAddressTypes = list.toArray(new InternetProtocolFamily[list.size()]);

        return this;
    }

    /**
     * Returns {@code true} if and only if this resolver sends a DNS query with the RD (recursion desired) flag set.
     * The default value is {@code true}.
     *
     * @see #setRecursionDesired(boolean)
     */
    public boolean isRecursionDesired() {
        return recursionDesired;
    }

    /**
     * Sets if this resolver has to send a DNS query with the RD (recursion desired) flag set.
     *
     * @return {@code this}
     *
     * @see #isRecursionDesired()
     */
    public DnsNameResolver setRecursionDesired(boolean recursionDesired) {
        this.recursionDesired = recursionDesired;
        return this;
    }

    /**
     * Returns the maximum allowed number of DNS queries to send when resolving a host name.
     * The default value is {@code 8}.
     *
     * @see #setMaxQueriesPerResolve(int)
     */
    public int maxQueriesPerResolve() {
        return maxQueriesPerResolve;
    }

    /**
     * Sets the maximum allowed number of DNS queries to send when resolving a host name.
     *
     * @return {@code this}
     *
     * @see #maxQueriesPerResolve()
     */
    public DnsNameResolver setMaxQueriesPerResolve(int maxQueriesPerResolve) {
        if (maxQueriesPerResolve <= 0) {
            throw new IllegalArgumentException("maxQueriesPerResolve: " + maxQueriesPerResolve + " (expected: > 0)");
        }

        this.maxQueriesPerResolve = maxQueriesPerResolve;

        return this;
    }

    /**
     * Returns if this resolver should generate the detailed trace information in an exception message so that
     * it is easier to understand the cause of resolution failure. The default value if {@code true}.
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * Sets if this resolver should generate the detailed trace information in an exception message so that
     * it is easier to understand the cause of resolution failure.
     */
    public DnsNameResolver setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        return this;
    }

    /**
     * Returns the capacity of the datagram packet buffer (in bytes).  The default value is {@code 4096} bytes.
     *
     * @see #setMaxPayloadSize(int)
     */
    public int maxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Sets the capacity of the datagram packet buffer (in bytes).  The default value is {@code 4096} bytes.
     *
     * @return {@code this}
     *
     * @see #maxPayloadSize()
     */
    public DnsNameResolver setMaxPayloadSize(int maxPayloadSize) {
        if (maxPayloadSize <= 0) {
            throw new IllegalArgumentException("maxPayloadSize: " + maxPayloadSize + " (expected: > 0)");
        }

        if (this.maxPayloadSize == maxPayloadSize) {
            // Same value; no need to instantiate DnsClass and RecvByteBufAllocator again.
            return this;
        }

        this.maxPayloadSize = maxPayloadSize;
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));

        return this;
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

    @Override
    protected boolean doIsResolved(InetSocketAddress address) {
        return !address.isUnresolved();
    }

    @Override
    protected void doResolve(InetSocketAddress unresolvedAddress, Promise<InetSocketAddress> promise) throws Exception {
        final byte[] bytes = NetUtil.createByteArrayFromIpAddressString(unresolvedAddress.getHostName());
        if (bytes != null) {
            // The unresolvedAddress was created via a String that contains an ipaddress.
            promise.setSuccess(new InetSocketAddress(InetAddress.getByAddress(bytes), unresolvedAddress.getPort()));
            return;
        }

        final String hostname = hostname(unresolvedAddress);
        final int port = unresolvedAddress.getPort();

        if (!doResolveCached(hostname, port, promise)) {
            doResolveUncached(hostname, port, promise);
        }
    }

    private boolean doResolveCached(String hostname, int port, Promise<InetSocketAddress> promise) {
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
                for (InternetProtocolFamily f : resolveAddressTypes) {
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
            setSuccess(promise, new InetSocketAddress(address, port));
        } else if (cause != null) {
            if (!promise.tryFailure(cause)) {
                logger.warn("Failed to notify failure to a promise: {}", promise, cause);
            }
        } else {
            return false;
        }

        return true;
    }

    private static void setSuccess(Promise<InetSocketAddress> promise, InetSocketAddress result) {
        if (!promise.trySuccess(result)) {
            logger.warn("Failed to notify success ({}) to a promise: {}", result, promise);
        }
    }

    private void doResolveUncached(String hostname, final int port, Promise<InetSocketAddress> promise) {
        final DnsNameResolverContext<InetSocketAddress> ctx =
                new DnsNameResolverContext<InetSocketAddress>(this, hostname, promise) {
                    @Override
                    protected boolean finishResolve(
                            Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries) {

                        final int numEntries = resolvedEntries.size();
                        for (int i = 0; i < numEntries; i++) {
                            final InetAddress a = resolvedEntries.get(i).address();
                            if (addressType.isInstance(a)) {
                                setSuccess(promise(), new InetSocketAddress(a, port));
                                return true;
                            }
                        }
                        return false;
                    }
                };

        ctx.resolve();
    }

    @Override
    protected void doResolveAll(
            InetSocketAddress unresolvedAddress, Promise<List<InetSocketAddress>> promise) throws Exception {

        final byte[] bytes = NetUtil.createByteArrayFromIpAddressString(unresolvedAddress.getHostName());
        if (bytes != null) {
            // The unresolvedAddress was created via a String that contains an ipaddress.
            promise.setSuccess(Collections.singletonList(
                    new InetSocketAddress(InetAddress.getByAddress(bytes), unresolvedAddress.getPort())));
            return;
        }

        final String hostname = hostname(unresolvedAddress);
        final int port = unresolvedAddress.getPort();

        if (!doResolveAllCached(hostname, port, promise)) {
            doResolveAllUncached(hostname, port, promise);
        }
    }

    private boolean doResolveAllCached(String hostname, int port, Promise<List<InetSocketAddress>> promise) {
        final List<DnsCacheEntry> cachedEntries = resolveCache.get(hostname);
        if (cachedEntries == null) {
            return false;
        }

        List<InetSocketAddress> result = null;
        Throwable cause = null;
        synchronized (cachedEntries) {
            final int numEntries = cachedEntries.size();
            assert numEntries > 0;

            if (cachedEntries.get(0).cause() != null) {
                cause = cachedEntries.get(0).cause();
            } else {
                for (InternetProtocolFamily f : resolveAddressTypes) {
                    for (int i = 0; i < numEntries; i++) {
                        final DnsCacheEntry e = cachedEntries.get(i);
                        if (f.addressType().isInstance(e.address())) {
                            if (result == null) {
                                result = new ArrayList<InetSocketAddress>(numEntries);
                            }
                            result.add(new InetSocketAddress(e.address(), port));
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

    private void doResolveAllUncached(final String hostname, final int port,
                                      final Promise<List<InetSocketAddress>> promise) {
        final DnsNameResolverContext<List<InetSocketAddress>> ctx =
                new DnsNameResolverContext<List<InetSocketAddress>>(this, hostname, promise) {
                    @Override
                    protected boolean finishResolve(
                            Class<? extends InetAddress> addressType, List<DnsCacheEntry> resolvedEntries) {

                        List<InetSocketAddress> result = null;
                        final int numEntries = resolvedEntries.size();
                        for (int i = 0; i < numEntries; i++) {
                            final InetAddress a = resolvedEntries.get(i).address();
                            if (addressType.isInstance(a)) {
                                if (result == null) {
                                    result = new ArrayList<InetSocketAddress>(numEntries);
                                }
                                result.add(new InetSocketAddress(a, port));
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

    private static String hostname(InetSocketAddress addr) {
        // InetSocketAddress.getHostString() is available since Java 7.
        final String hostname;
        if (PlatformDependent.javaVersion() < 7) {
            hostname = addr.getHostName();
        } else {
            hostname = addr.getHostString();
        }

        return IDN.toASCII(hostname);
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

                final DnsQueryContext qCtx = promises.get(queryId);
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
