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
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class DnsNameResolver extends SimpleNameResolver<InetSocketAddress> implements Closeable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsNameResolver.class);

    private static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);
    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;

    private static final DnsResponseDecoder DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder ENCODER = new DnsQueryEncoder();

    private final Iterable<InetSocketAddress> nameServerAddresses;
    final DatagramChannel ch;

    /**
     * An array whose index is the ID of a DNS query and whose value is the promise of the corresponsing response. We
     * don't use {@link IntObjectHashMap} or map-like data structure here because 64k elements are fairly small, which
     * is only about 512KB.
     */
    private final AtomicReferenceArray<DnsResponsePromise> promises =
            new AtomicReferenceArray<DnsResponsePromise>(65536);

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

    private static void onQueryComplete(
            String hostname, int port, DnsQuestion question,
            Future<DnsResponse> queryFuture, Promise<SocketAddress> resolvePromise) {

        final DnsResponse response = queryFuture.getNow();
        try {
            if (queryFuture.isSuccess()) {
                InetAddress resolved = decodeFirstAddressRecord(hostname, response);
                if (resolved == null) {
                    // No address decoded
                    resolvePromise.setFailure(new UnknownHostException("no address resource found: " + question));
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
        final Object cachedResult = queryCache.get(question);
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
        final int maxTries = maxTries();
        final DnsResponsePromise p;

        try {
            p = new DnsResponsePromise(question, maxTries);
        } catch (Exception e) {
            return eventLoop.newFailedFuture(e);
        }

        p.query();

        return p;
    }

    void cache(DnsQuestion question, DnsResponse res) {
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

    void cache(final DnsQuestion question, Throwable cause) {
        final int negativeTtl = negativeTtl();
        if (negativeTtl == 0) {
            return;
        }

        queryCache.put(question, cause);
        scheduleCacheExpiration(question, negativeTtl);
    }

    private void scheduleCacheExpiration(final DnsQuestion question, long delaySeconds) {
        ch.eventLoop().schedule(new OneTimeTask() {
            @Override
            public void run() {
                Object response = queryCache.remove(question);
                ReferenceCountUtil.safeRelease(response);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private final class DnsResponsePromise extends DefaultPromise<DnsResponse> {

        final int id;
        final DnsQuestion question;
        final Iterator<InetSocketAddress> nameServerAddresses = DnsNameResolver.this.nameServerAddresses.iterator();

        final int maxTries;
        int remainingTries;
        volatile ScheduledFuture<?> timeoutFuture;
        StringBuilder failureMessages;

        DnsResponsePromise(DnsQuestion question, int maxTries) throws UnknownHostException {
            super(DnsNameResolver.this.executor());
            id = allocateId();
            this.question = question;
            this.maxTries = maxTries;
            remainingTries = maxTries;
        }

        private int allocateId() throws UnknownHostException {
            int id = ThreadLocalRandom.current().nextInt(promises.length());
            final int maxTries = promises.length() << 1;
            int tries = 0;
            for (;;) {
                if (promises.compareAndSet(id, null, this)) {
                    return id;
                }

                id = id + 1 & 0xFFFF;

                if (++ tries >= maxTries) {
                    throw new UnknownHostException("query ID space exhausted: " + question);
                }
            }
        }

        void query() {
            if (remainingTries <= 0 || !nameServerAddresses.hasNext()) {
                promises.lazySet(id, null);

                int tries = maxTries - remainingTries;
                UnknownHostException cause;
                if (tries > 1) {
                    cause = new UnknownHostException(
                            "failed to resolve " + question + " after " + tries + " attempt(s):" +
                            failureMessages);
                } else {
                    cause = new UnknownHostException("failed to resolve " + question + ':' + failureMessages);
                }

                cache(question, cause);
                setFailure(cause);
                return;
            }

            remainingTries --;

            final InetSocketAddress nameServerAddr = nameServerAddresses.next();
            final DnsQuery query = new DnsQuery(id, nameServerAddr).addQuestion(question);
            final ChannelFuture writeFuture = ch.writeAndFlush(query);
            if (writeFuture.isDone()) {
                onQueryWriteCompletion(writeFuture, nameServerAddr);
            } else {
                writeFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        onQueryWriteCompletion(writeFuture, nameServerAddr);
                    }
                });
            }
        }

        private void onQueryWriteCompletion(ChannelFuture writeFuture, final InetSocketAddress nameServerAddr) {
            if (!writeFuture.isSuccess()) {
                retry(nameServerAddr, "failed to send a query: " + writeFuture.cause());
                return;
            }

            // Schedule a query timeout task if necessary.
            final int queryTimeoutMillis = timeoutMillis();
            if (queryTimeoutMillis > 0) {
                timeoutFuture = ch.eventLoop().schedule(new OneTimeTask() {
                    @Override
                    public void run() {
                        if (isDone()) {
                            // Received a response before the query times out.
                            return;
                        }

                        retry(nameServerAddr, "query timed out after " + queryTimeoutMillis + " milliseconds");
                    }
                }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
            }
        }

        void retry(InetSocketAddress nameServerAddr, String message) {
            if (failureMessages == null) {
                failureMessages = new StringBuilder(128);
            }

            failureMessages.append(StringUtil.NEWLINE);
            failureMessages.append("\tfrom ");
            failureMessages.append(nameServerAddr);
            failureMessages.append(": ");
            failureMessages.append(message);
            query();
        }
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean success = false;
            try {
                final DnsResponse res = (DnsResponse) msg;

                final int queryId = res.header().id();
                final DnsResponsePromise p = promises.get(queryId);

                if (p == null) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Received a DNS response with an unknown ID: {}", queryId);
                    }
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
                    p.retry(res.sender(),
                            "response code: " + res.header().responseCode() +
                            " with " + res.answers().size() + " answer(s)");
                }
            } finally {
                if (!success) {
                    ReferenceCountUtil.safeRelease(msg);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.warn("Unexpected exception: ", cause);
        }
    }
}
