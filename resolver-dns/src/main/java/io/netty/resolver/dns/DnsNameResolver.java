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

import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class DnsNameResolver extends SimpleNameResolver<InetSocketAddress> implements Closeable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    private static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);

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
    final ConcurrentMap<DnsQuestion, Object> queryCache = PlatformDependent.newConcurrentHashMap();

    private final DnsResponseHandler responseHandler = new DnsResponseHandler();

    private volatile int timeoutMillis = 5000;

    // The default TTL values here respect the TTL returned by the DNS server and do not cache the negative response.
    private volatile int minTtl;
    private volatile int maxTtl = Integer.MAX_VALUE;
    private volatile int negativeTtl;
    private volatile int maxTries = 2;

    private volatile InternetProtocolFamily preferredProtocolFamily = InternetProtocolFamily.IPv4;
    private volatile boolean recursionDesired = true;
    private volatile int maxQueriesPerResolve = 8;

    private volatile int maxPayloadSize;
    private volatile DnsClass maxPayloadSizeClass; // EDNS uses the CLASS field as the payload size field.

    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelType, ANY_LOCAL_ADDR, nameServerAddress);
    }

    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress localAddress, InetSocketAddress nameServerAddress) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), localAddress, nameServerAddress);
    }

    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelFactory, ANY_LOCAL_ADDR, nameServerAddress);
    }

    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress localAddress, InetSocketAddress nameServerAddress) {
        this(eventLoop, channelFactory, localAddress, DnsServerAddresses.singleton(nameServerAddress));
    }

    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, channelType, ANY_LOCAL_ADDR, nameServerAddresses);
    }

    public DnsNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress localAddress, Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), localAddress, nameServerAddresses);
    }

    public DnsNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            Iterable<InetSocketAddress> nameServerAddresses) {
        this(eventLoop, channelFactory, ANY_LOCAL_ADDR, nameServerAddresses);
    }

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

        DatagramChannel ch = channelFactory.newChannel();
        ch.pipeline().addLast(DECODER, ENCODER, responseHandler);

        // Register and bind the channel synchronously.
        // It should not take very long at all because it does not involve any remote I/O.
        executor().register(ch).syncUninterruptibly();
        ch.bind(localAddress).syncUninterruptibly();

        return ch;
    }

    public int minTtl() {
        return minTtl;
    }

    public int maxTtl() {
        return maxTtl;
    }

    public void setTtl(int minTtl, int maxTtl) {
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
    }

    public int negativeTtl() {
        return negativeTtl;
    }

    public void setNegativeTtl(int negativeTtl) {
        if (negativeTtl < 0) {
            throw new IllegalArgumentException("negativeTtl: " + negativeTtl + " (expected: >= 0)");
        }

        this.negativeTtl = negativeTtl;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis: " + timeoutMillis + " (expected: >= 0)");
        }
        this.timeoutMillis = timeoutMillis;
    }

    public int maxTries() {
        return maxTries;
    }

    public void setMaxTries(int maxTries) {
        if (maxTries < 1) {
            throw new IllegalArgumentException("maxTries: " + maxTries + " (expected: > 0)");
        }
        this.maxTries = maxTries;
    }

    public InternetProtocolFamily preferredProtocolFamily() {
        return preferredProtocolFamily;
    }

    public void setPreferredProtocolFamily(InternetProtocolFamily preferredProtocolFamily) {
        if (preferredProtocolFamily == null) {
            throw new NullPointerException("preferredProtocolFamily");
        }
        this.preferredProtocolFamily = preferredProtocolFamily;
    }

    public boolean isRecursionDesired() {
        return recursionDesired;
    }

    public void setRecursionDesired(boolean recursionDesired) {
        this.recursionDesired = recursionDesired;
    }

    public int maxQueriesPerResolve() {
        return maxQueriesPerResolve;
    }

    public void setMaxQueriesPerResolve(int maxQueriesPerResolve) {
        if (maxQueriesPerResolve <= 0) {
            throw new IllegalArgumentException("maxQueriesPerResolve: " + maxQueriesPerResolve + " (expected: > 0)");
        }
        this.maxQueriesPerResolve = maxQueriesPerResolve;
    }

    public int maxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(int maxPayloadSize) {
        if (maxPayloadSize <= 0) {
            throw new IllegalArgumentException("maxPayloadSize: " + maxPayloadSize + " (expected: > 0)");
        }
        this.maxPayloadSize = maxPayloadSize;
        maxPayloadSizeClass = DnsClass.valueOf(maxPayloadSize);
        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(maxPayloadSize));
    }

    DnsClass maxPayloadSizeClass() {
        return maxPayloadSizeClass;
    }

    public void clearCache() {
        queryCache.clear();
    }

    public boolean clearCache(DnsQuestion question) {
        return queryCache.remove(question) != null;
    }

    /**
     * Closes the internal datagram channel used for sending and receiving DNS messages.
     * Attempting to send a DNS query or to resolve a domain name will fail once this method has been called.
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
    protected Future<SocketAddress> doResolve(final InetSocketAddress unresolvedAddress) throws Exception {
        final String hostname = IDN.toASCII(unresolvedAddress.getHostString());
        final int port = unresolvedAddress.getPort();

        final DnsNameResolverContext ctx = new DnsNameResolverContext(this, hostname, port);

        return ctx.resolve();
    }

    public Future<DnsResponse> query(DnsQuestion question) {
        return query(nameServerAddresses, question);
    }

    public Future<DnsResponse> query(Iterable<InetSocketAddress> nameServerAddresses, final DnsQuestion question) {
        if (nameServerAddresses == null) {
            throw new NullPointerException("nameServerAddresses");
        }
        if (question == null) {
            throw new NullPointerException("question");
        }

        final EventLoop eventLoop = ch.eventLoop();
        final Object cachedResult = queryCache.get(question);
        if (cachedResult != null) {
            if (cachedResult instanceof DnsResponse) {
                return eventLoop.newSucceededFuture(((DnsResponse) cachedResult).retain());
            } else {
                return eventLoop.newFailedFuture((Throwable) cachedResult);
            }
        } else {
            return query0(eventLoop, nameServerAddresses, question);
        }
    }

    private Future<DnsResponse> query0(
            EventLoop eventLoop, Iterable<InetSocketAddress> nameServerAddresses, DnsQuestion question) {
        final DnsQueryContext p;

        try {
            p = new DnsQueryContext(this, nameServerAddresses, question);
        } catch (Exception e) {
            return eventLoop.newFailedFuture(e);
        }

        p.query();

        return p;
    }

    void scheduleCacheExpiration(final DnsQuestion question, long delaySeconds) {
        ch.eventLoop().schedule(new OneTimeTask() {
            @Override
            public void run() {
                Object response = queryCache.remove(question);
                ReferenceCountUtil.safeRelease(response);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean success = false;
            try {
                final DnsResponse res = (DnsResponse) msg;
                final int queryId = res.header().id();
                final DnsQueryContext p = promises.get(queryId);

                if (p == null) {
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

                final DnsQuestion q = p.question();
                if (!q.equals(questions.get(0))) {
                    logger.warn("Received a mismatching DNS response: {}", res);
                    return;
                }

                // Cancel the timeout task.
                final ScheduledFuture<?> timeoutFuture = p.timeoutFuture();
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }

                if (res.header().responseCode() == DnsResponseCode.NOERROR) {
                    cache(q, res);
                    promises.lazySet(queryId, null);
                    p.trySuccess(res);
                    success = true;
                } else {
                    p.retry(res.sender(),
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
            queryCache.put(question, res);
            scheduleCacheExpiration(question, ttl);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn("Unexpected exception: ", cause);
        }
    }
}
