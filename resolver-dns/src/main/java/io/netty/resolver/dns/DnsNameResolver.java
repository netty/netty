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
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.resolver.NameResolver;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

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

    private static final DnsResponseDecoder DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder ENCODER = new DnsQueryEncoder();

    final Iterable<InetSocketAddress> nameServerAddresses;
    final DatagramChannel ch;

    /**
     * An array whose index is the ID of a DNS query and whose value is the promise of the corresponsing response. We
     * don't use {@link IntObjectHashMap} or map-like data structure here because 64k elements are fairly small, which
     * is only about 512KB.
     */
    final AtomicReferenceArray<DnsQueryContext> promises = new AtomicReferenceArray<DnsQueryContext>(65536);

    /**
     * The cache for {@link #query(DnsQuestion)}
     */
    final ConcurrentMap<DnsQuestion, DnsCacheEntry> queryCache = PlatformDependent.newConcurrentHashMap();

    private final DnsResponseHandler responseHandler = new DnsResponseHandler();

    private volatile long queryTimeoutMillis = 5000;

    // The default TTL values here respect the TTL returned by the DNS server and do not cache the negative response.
    private volatile int minTtl;
    private volatile int maxTtl = Integer.MAX_VALUE;
    private volatile int negativeTtl;
    private volatile int maxTriesPerQuery = 2;

    private volatile InternetProtocolFamily[] resolveAddressTypes = DEFAULT_RESOLVE_ADDRESS_TYPES;
    private volatile boolean recursionDesired = true;
    private volatile int maxQueriesPerResolve = 8;

    private volatile int maxPayloadSize;
    private volatile DnsClass maxPayloadSizeClass; // EDNS uses the CLASS field as the payload size field.

    /**
     * Creates a new DNS-based name resolver that communicates with a single DNS server.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param nameServerAddress the address of the DNS server
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelType, ANY_LOCAL_ADDR, nameServerAddress);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with a single DNS server.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddress the address of the DNS server
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress localAddress, InetSocketAddress nameServerAddress) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), localAddress, nameServerAddress);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with a single DNS server.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param nameServerAddress the address of the DNS server
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelFactory, ANY_LOCAL_ADDR, nameServerAddress);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with a single DNS server.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddress the address of the DNS server
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress localAddress, InetSocketAddress nameServerAddress) {
        this(eventLoop, channelFactory, localAddress, DnsServerAddresses.singleton(nameServerAddress));
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new {@link Iterator} is
     *                            created from this {@link Iterable} to determine which DNS server should be contacted
     *                            for the next retry in case of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, channelType, ANY_LOCAL_ADDR, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelType the type of the {@link DatagramChannel} to create
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new {@link Iterator} is
     *                            created from this {@link Iterable} to determine which DNS server should be contacted
     *                            for the next retry in case of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress localAddress, Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), localAddress, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new {@link Iterator} is
     *                            created from this {@link Iterable} to determine which DNS server should be contacted
     *                            for the next retry in case of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, channelFactory, ANY_LOCAL_ADDR, nameServerAddresses);
    }

    /**
     * Creates a new DNS-based name resolver that communicates with the specified list of DNS servers.
     *
     * @param eventLoop the {@link EventLoop} which will perform the communication with the DNS servers
     * @param channelFactory the {@link ChannelFactory} that will create a {@link DatagramChannel}
     * @param localAddress the local address of the {@link DatagramChannel}
     * @param nameServerAddresses the addresses of the DNS server. For each DNS query, a new {@link Iterator} is
     *                            created from this {@link Iterable} to determine which DNS server should be contacted
     *                            for the next retry in case of failure.
     */
    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress localAddress, Iterable<InetSocketAddress> nameServerAddresses) {

        super(eventLoop);

        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (nameServerAddresses == null) {
            throw new NullPointerException("nameServerAddresses");
        }
        if (!nameServerAddresses.iterator().hasNext()) {
            throw new NullPointerException("nameServerAddresses is empty");
        }
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }

        this.nameServerAddresses = nameServerAddresses;
        ch = newChannel(channelFactory, localAddress);

        setMaxPayloadSize(4096);
    }

    private DatagramChannel newChannel(
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

        DatagramChannel ch = (DatagramChannel) b.bind(localAddress).channel();
        ch.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                clearCache();
            }
        });

        return ch;
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
     * Returns the maximum number of tries for each query. The default value is 2 times.
     *
     * @see #setMaxTriesPerQuery(int)
     */
    public int maxTriesPerQuery() {
        return maxTriesPerQuery;
    }

    /**
     * Sets the maximum number of tries for each query.
     *
     * @return {@code this}
     *
     * @see #maxTriesPerQuery()
     */
    public DnsNameResolver setMaxTriesPerQuery(int maxTriesPerQuery) {
        if (maxTriesPerQuery < 1) {
            throw new IllegalArgumentException("maxTries: " + maxTriesPerQuery + " (expected: > 0)");
        }

        this.maxTriesPerQuery = maxTriesPerQuery;

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
        if (resolveAddressTypes == null) {
            throw new NullPointerException("resolveAddressTypes");
        }

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
        if (resolveAddressTypes == null) {
            throw new NullPointerException("resolveAddressTypes");
        }

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
        maxPayloadSizeClass = DnsClass.valueOf(maxPayloadSize);
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));

        return this;
    }

    DnsClass maxPayloadSizeClass() {
        return maxPayloadSizeClass;
    }

    /**
     * Clears all the DNS resource records cached by this resolver.
     *
     * @return {@code this}
     *
     * @see #clearCache(DnsQuestion)
     */
    public DnsNameResolver clearCache() {
        for (Iterator<Entry<DnsQuestion, DnsCacheEntry>> i = queryCache.entrySet().iterator(); i.hasNext();) {
            Entry<DnsQuestion, DnsCacheEntry> e = i.next();
            i.remove();
            e.getValue().release();
        }

        return this;
    }

    /**
     * Clears the DNS resource record of the specified DNS question from the cache of this resolver.
     */
    public boolean clearCache(DnsQuestion question) {
        DnsCacheEntry e = queryCache.remove(question);
        if (e != null) {
            e.release();
            return true;
        } else {
            return false;
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
        final String hostname = IDN.toASCII(unresolvedAddress.getHostString());
        final int port = unresolvedAddress.getPort();

        final DnsNameResolverContext ctx = new DnsNameResolverContext(this, hostname, port, promise);

        ctx.resolve();
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<DnsResponse> query(DnsQuestion question) {
        return query(nameServerAddresses, question);
    }

    /**
     * Sends a DNS query with the specified question.
     */
    public Future<DnsResponse> query(DnsQuestion question, Promise<DnsResponse> promise) {
        return query(nameServerAddresses, question, promise);
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<DnsResponse> query(Iterable<InetSocketAddress> nameServerAddresses, DnsQuestion question) {
        if (nameServerAddresses == null) {
            throw new NullPointerException("nameServerAddresses");
        }
        if (question == null) {
            throw new NullPointerException("question");
        }

        final EventLoop eventLoop = ch.eventLoop();
        final DnsCacheEntry cachedResult = queryCache.get(question);
        if (cachedResult != null) {
            if (cachedResult.response != null) {
                return eventLoop.newSucceededFuture(cachedResult.response.retain());
            } else {
                return eventLoop.newFailedFuture(cachedResult.cause);
            }
        } else {
            return query0(nameServerAddresses, question, eventLoop.<DnsResponse>newPromise());
        }
    }

    /**
     * Sends a DNS query with the specified question using the specified name server list.
     */
    public Future<DnsResponse> query(
            Iterable<InetSocketAddress> nameServerAddresses, DnsQuestion question, Promise<DnsResponse> promise) {

        if (nameServerAddresses == null) {
            throw new NullPointerException("nameServerAddresses");
        }
        if (question == null) {
            throw new NullPointerException("question");
        }
        if (promise == null) {
            throw new NullPointerException("promise");
        }

        final DnsCacheEntry cachedResult = queryCache.get(question);
        if (cachedResult != null) {
            if (cachedResult.response != null) {
                return promise.setSuccess(cachedResult.response.retain());
            } else {
                return promise.setFailure(cachedResult.cause);
            }
        } else {
            return query0(nameServerAddresses, question, promise);
        }
    }

    private Future<DnsResponse> query0(
            Iterable<InetSocketAddress> nameServerAddresses, DnsQuestion question, Promise<DnsResponse> promise) {

        try {
            new DnsQueryContext(this, nameServerAddresses, question, promise).query();
            return promise;
        } catch (Exception e) {
            return promise.setFailure(e);
        }
    }

    void cache(final DnsQuestion question, DnsCacheEntry entry, long delaySeconds) {
        queryCache.put(question, entry);
        boolean scheduled = false;
        try {
            entry.expirationFuture = ch.eventLoop().schedule(new OneTimeTask() {
                @Override
                public void run() {
                    Object response = queryCache.remove(question);
                    ReferenceCountUtil.safeRelease(response);
                }
            }, delaySeconds, TimeUnit.SECONDS);

            scheduled = true;
        } finally {
            if (!scheduled) {
                // If failed to schedule the expiration task,
                // remove the entry from the cache so that it does not leak.
                queryCache.remove(question);
                entry.release();
            }
        }
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean success = false;
            try {
                final DnsResponse res = (DnsResponse) msg;
                final int queryId = res.header().id();

                if (logger.isDebugEnabled()) {
                    logger.debug("{} RECEIVED: [{}: {}], {}", ch, queryId, res.sender(), res);
                }

                final DnsQueryContext qCtx = promises.get(queryId);

                if (qCtx == null) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Received a DNS response with an unknown ID: {}", queryId);
                    }
                    return;
                }

                final List<DnsQuestion> questions = res.questions();
                if (questions.size() != 1) {
                    logger.warn("Received a DNS response with invalid number of questions: {}", res);
                    return;
                }

                final DnsQuestion q = qCtx.question();
                if (!q.equals(questions.get(0))) {
                    logger.warn("Received a mismatching DNS response: {}", res);
                    return;
                }

                // Cancel the timeout task.
                final ScheduledFuture<?> timeoutFuture = qCtx.timeoutFuture();
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }

                if (res.header().responseCode() == DnsResponseCode.NOERROR) {
                    cache(q, res);
                    promises.set(queryId, null);
                    qCtx.promise().trySuccess(res);
                    success = true;
                } else {
                    qCtx.retry(res.sender(),
                            "response code: " + res.header().responseCode() +
                            " with " + res.answers().size() + " answer(s) and " +
                            res.authorityResources().size() + " authority resource(s)");
                }
            } finally {
                if (!success) {
                    ReferenceCountUtil.safeRelease(msg);
                }
            }
        }

        private void cache(DnsQuestion question, DnsResponse res) {
            final int maxTtl = maxTtl();
            if (maxTtl == 0) {
                return;
            }

            long ttl = Long.MAX_VALUE;
            // Find the smallest TTL value returned by the server.
            for (DnsResource r: res.answers()) {
                long rTtl = r.timeToLive();
                if (ttl > rTtl) {
                    ttl = rTtl;
                }
            }

            // Ensure that the found TTL is between minTtl and maxTtl.
            ttl = Math.max(minTtl(), Math.min(maxTtl, ttl));

            res.retain();

            DnsNameResolver.this.cache(question, new DnsCacheEntry(res), ttl);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn("Unexpected exception: ", cause);
        }
    }

    static final class DnsCacheEntry {
        final DnsResponse response;
        final Throwable cause;
        volatile ScheduledFuture<?> expirationFuture;

        DnsCacheEntry(DnsResponse response) {
            this.response = response;
            cause = null;
        }

        DnsCacheEntry(Throwable cause) {
            this.cause = cause;
            response = null;
        }

        void release() {
            DnsResponse response = this.response;
            if (response != null) {
                ReferenceCountUtil.safeRelease(response);
            }

            ScheduledFuture<?> expirationFuture = this.expirationFuture;
            if (expirationFuture != null) {
                expirationFuture.cancel(false);
            }
        }
    }
}
