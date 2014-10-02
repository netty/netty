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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.DnsType;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class DomainNameResolver extends SimpleNameResolver<InetSocketAddress> implements Closeable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DomainNameResolver.class);

    private static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);
    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;
    private static final int DNS_PORT = 53;

    private static final List<InetSocketAddress> DEFAULT_NAME_SERVERS;

    private static final DnsResponseDecoder DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder ENCODER = new DnsQueryEncoder();

    static {
        List<InetSocketAddress> defaultNameServers = new ArrayList<InetSocketAddress>(2);
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);

            for (int i = 0; i < list.size(); i ++) {
                defaultNameServers.add(new InetSocketAddress(InetAddress.getByName(list.get(i)), DNS_PORT));
            }

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Default DNS servers: {} (sun.net.dns.ResolverConfiguration)", defaultNameServers);
            }
        } catch (Exception ignore) {
            defaultNameServers = Arrays.asList(
                    new InetSocketAddress("8.8.8.8", DNS_PORT),
                    new InetSocketAddress("8.8.4.4", DNS_PORT));

            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Default DNS servers: {} (Google Public DNS as a fallback)", defaultNameServers);
            }
        }

        DEFAULT_NAME_SERVERS = Collections.unmodifiableList(defaultNameServers);
    }

    public static List<InetSocketAddress> defaultNameServers() {
        return DEFAULT_NAME_SERVERS;
    }

    // TODO: Support multiple name servers with pluggable policy.
    // TODO: Implement a NameResolver wrapper that retries on failure.

    private final InetSocketAddress nameServerAddress;
    final DatagramChannel ch;

    /**
     * An array whose index is the ID of a DNS query and whose value is the promise of the corresponsing response. We
     * don't use {@link IntObjectHashMap} or map-like data structure here because 64k elements are fairly small, which
     * is only about 512KB.
     */
    private final AtomicReferenceArray<DnsResponsePromise> promises =
            new AtomicReferenceArray<DnsResponsePromise>(65536);

    final ConcurrentMap<DnsQuestion, Object> cache = PlatformDependent.newConcurrentHashMap();

    private final DnsResponseHandler responseHandler = new DnsResponseHandler();

    private volatile int queryTimeoutMillis = 5000;

    // The default TTL values here respect the TTL returned by the DNS server and do not cache the negative response.
    private volatile int minTtl;
    private volatile int maxTtl = Integer.MAX_VALUE;
    private volatile int negativeTtl;

    public DomainNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelType, nameServerAddress, ANY_LOCAL_ADDR);
    }

    public DomainNameResolver(
            EventLoop eventLoop, Class<? extends DatagramChannel> channelType,
            InetSocketAddress nameServerAddress, InetSocketAddress localAddress) {
        this(eventLoop, new ReflectiveChannelFactory<DatagramChannel>(channelType), nameServerAddress, localAddress);
    }

    public DomainNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress nameServerAddress) {
        this(eventLoop, channelFactory, nameServerAddress, ANY_LOCAL_ADDR);
    }

    public DomainNameResolver(
            EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress nameServerAddress, InetSocketAddress localAddress) {

        super(eventLoop);

        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (nameServerAddress == null) {
            throw new NullPointerException("nameServerAddress");
        }
        if (nameServerAddress.isUnresolved()) {
            throw new IllegalArgumentException("nameServerAddress (" + nameServerAddress + ") must be resolved.");
        }
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }

        this.nameServerAddress = nameServerAddress;
        ch = newChannel(channelFactory, nameServerAddress, localAddress);
    }

    private DatagramChannel newChannel(
            ChannelFactory<? extends DatagramChannel> channelFactory,
            InetSocketAddress remoteAddress, InetSocketAddress localAddress) {

        DatagramChannel ch = channelFactory.newChannel();
        ch.pipeline().addLast(DECODER, ENCODER, responseHandler);

        // Register and bind the channel synchronously.
        // It should not take very long at all because it does not involve any remote I/O.
        executor().register(ch).syncUninterruptibly();
        ch.connect(remoteAddress, localAddress).syncUninterruptibly();

        return ch;
    }

    public int queryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    public void setQueryTimeoutMillis(int queryTimeoutMillis) {
        if (queryTimeoutMillis < 0) {
            throw new IllegalArgumentException("queryTimeoutMillis: " + queryTimeoutMillis + " (expected: >= 0)");
        }
        this.queryTimeoutMillis = queryTimeoutMillis;
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

        final DnsQuestion question = new DnsQuestion(hostname, DnsType.ANY);
        final Promise<SocketAddress> resolvePromise = executor().newPromise();
        final Future<DnsResponse> queryFuture = query(question);
        if (queryFuture.isDone()) {
            // Query has been finished immediately - probably cached result.
            onQueryComplete(hostname, port, question, queryFuture, resolvePromise);
        } else {
            queryFuture.addListener(new FutureListener<DnsResponse>() {
                @Override
                public void operationComplete(Future<DnsResponse> future) throws Exception {
                    onQueryComplete(hostname, port, question, future, resolvePromise);
                }
            });
        }

        return resolvePromise;
    }

    private void onQueryComplete(
            String hostname, int port, DnsQuestion question,
            Future<DnsResponse> queryFuture, Promise<SocketAddress> resolvePromise) {

        final DnsResponse response = queryFuture.getNow();
        try {
            if (queryFuture.isSuccess()) {
                InetAddress resolved = decodeFirstAddressRecord(hostname, response);
                if (resolved == null) {
                    // No address decoded
                    final UnknownHostException cause =
                            new UnknownHostException("no address resource found: " + question);
                    cache(question, cause);
                    resolvePromise.setFailure(cause);
                } else {
                    resolvePromise.setSuccess(new InetSocketAddress(resolved, port));
                }
            } else {
                resolvePromise.setFailure(queryFuture.cause());
            }
        } finally {
            ReferenceCountUtil.safeRelease(response);
        }
    }

    static InetAddress decodeFirstAddressRecord(String hostname, DnsResponse response)  {

        final List<DnsResource> answers = response.answers();
        final int maxCount = answers.size();

        for (int i = 0; i < maxCount; i ++) {
            final DnsResource a = answers.get(i);
            if (a.dnsClass() != DnsClass.IN) {
                continue;
            }

            final DnsType type = a.type();
            final ByteBuf content = a.content();
            final byte[] addrBytes;

            if (!hostname.equalsIgnoreCase(a.name())) {
                continue;
            }

            if (type == DnsType.A && content.readableBytes() == INADDRSZ4) {
                addrBytes = new byte[INADDRSZ4];
            } else if (type == DnsType.AAAA && content.readableBytes() == INADDRSZ6) {
                addrBytes = new byte[INADDRSZ6];
            } else {
                continue;
            }

            content.getBytes(content.readerIndex(), addrBytes);

            try {
                return InetAddress.getByAddress(hostname, addrBytes);
            } catch (UnknownHostException ignore) {
                // Should never happen
                throw new Error();
            }
        }

        return null;
    }

    public Future<DnsResponse> query(final DnsQuestion question) {
        final EventLoop eventLoop = ch.eventLoop();
        final Object cachedResult = cache.get(question);
        if (cachedResult != null) {
            if (cachedResult instanceof DnsResponse) {
                return eventLoop.newSucceededFuture(((DnsResponse) cachedResult).retain());
            } else {
                return eventLoop.newFailedFuture((Throwable) cachedResult);
            }
        } else {
            return query0(eventLoop, question);
        }
    }

    private Future<DnsResponse> query0(EventLoop eventLoop, DnsQuestion question) {
        final DnsResponsePromise p = new DnsResponsePromise(question);

        int id = ThreadLocalRandom.current().nextInt(promises.length());
        final int maxTries = promises.length() << 1;
        int tries = 0;
        for (;;) {
            if (promises.compareAndSet(id, null, p)) {
                p.id = id;
                break;
            }

            id = id + 1 & 0xFFFF;

            if (++ tries >= maxTries) {
                return eventLoop.newFailedFuture(
                        new UnknownHostException("query ID space exhausted: " + question));
            }
        }

        final DnsQuery query = new DnsQuery(id, nameServerAddress).addQuestion(question);
        final ChannelFuture writeFuture = ch.writeAndFlush(query);
        if (writeFuture.isDone()) {
            onQueryWriteCompletion(writeFuture, p);
        } else {
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    onQueryWriteCompletion(writeFuture, p);
                }
            });
        }

        return p;
    }

    private void onQueryWriteCompletion(ChannelFuture writeFuture, final DnsResponsePromise p) {
        final int id = p.id;

        if (!writeFuture.isSuccess()) {
            promises.lazySet(id, null);
            p.setFailure(
                    new UnknownHostException("failed to send a query: " + p.question).initCause(writeFuture.cause()));
            return;
        }

        // Schedule a query timeout task if necessary.
        final int queryTimeoutMillis = queryTimeoutMillis();
        if (queryTimeoutMillis > 0) {
            p.timeoutFuture = ch.eventLoop().schedule(new OneTimeTask() {
                @Override
                public void run() {
                    if (p.isDone()) {
                        // Received a response before the query times out.
                        return;
                    }

                    promises.lazySet(id, null);
                    p.setFailure(new UnknownHostException("query timeout: " + p.question));
                }
            }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
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
        cache.put(question, res);
        scheduleCacheExpiration(question, ttl);
    }

    private void cache(final DnsQuestion question, Throwable cause) {
        final int negativeTtl = negativeTtl();
        if (negativeTtl == 0) {
            return;
        }

        cache.put(question, cause);
        scheduleCacheExpiration(question, negativeTtl);
    }

    private void scheduleCacheExpiration(final DnsQuestion question, long delaySeconds) {
        ch.eventLoop().schedule(new OneTimeTask() {
            @Override
            public void run() {
                Object response = cache.remove(question);
                ReferenceCountUtil.safeRelease(response);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private final class DnsResponsePromise extends DefaultPromise<DnsResponse> {
        int id;
        final DnsQuestion question;
        volatile ScheduledFuture<?> timeoutFuture;

        DnsResponsePromise(DnsQuestion question) {
            super(DomainNameResolver.this.executor());
            this.question = question;
        }
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean success = false;
            try {
                final DnsResponse res = (DnsResponse) msg;
                final DnsResponsePromise p = promises.getAndSet(res.header().id(), null);

                if (p == null) {
                    return;
                }

                // Cancel the timeout task.
                final ScheduledFuture<?> timeoutFuture = p.timeoutFuture;
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }

                if (res.header().responseCode() == DnsResponseCode.NOERROR && !res.answers().isEmpty()) {
                    cache(p.question, res);
                    p.setSuccess(res);
                    success = true;
                } else {
                    final UnknownHostException cause = new UnknownHostException(
                            "failed to resolve: " + p.question + " (" + res.header().responseCode() + ')');

                    cache(p.question, cause);
                    p.setFailure(cause);
                }
            } finally {
                if (!success) {
                    ReferenceCountUtil.safeRelease(msg);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.debug("Unexpected exception: ", cause);
        }
    }
}
