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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQueryHeader;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.DnsType;
import io.netty.resolver.NameResolver;
import io.netty.resolver.SimpleNameResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class DomainNameResolver extends SimpleNameResolver<InetSocketAddress> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DomainNameResolver.class);

    private static final InetSocketAddress ANY_LOCAL_ADDR = new InetSocketAddress(0);
    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;
    private static final int DNS_PORT = 53;

    private static final InetSocketAddress[] DEFAULT_NAME_SERVERS;

    private static final DnsResponseDecoder DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder ENCODER = new DnsQueryEncoder();

    static {
        InetSocketAddress[] defaultNameServers;
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);

            defaultNameServers = new InetSocketAddress[list.size()];
            for (int i = 0; i < defaultNameServers.length; i++) {
                defaultNameServers[i] = new InetSocketAddress(InetAddress.getByName(list.get(i)), DNS_PORT);
            }

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Default DNS servers: {} (sun.net.dns.ResolverConfiguration)",
                        Arrays.asList(defaultNameServers));
            }
        } catch (Exception ignore) {
            defaultNameServers = new InetSocketAddress[] {
                    new InetSocketAddress("8.8.8.8", DNS_PORT),
                    new InetSocketAddress("8.8.4.4", DNS_PORT)
            };

            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Default DNS servers: {} (Google Public DNS as a fallback)",
                        Arrays.asList(defaultNameServers));
            }
        }

        DEFAULT_NAME_SERVERS = defaultNameServers;
    }

    // TODO: Support multiple name servers with pluggable policy.
    // TODO: Implement a NameResolver wrapper that retries on failure.

    private final InetSocketAddress nameServerAddress;
    private final DatagramChannel ch;

    private volatile int queryTimeoutMillis = 5000;

    /**
     * An array whose index is the ID of a DNS query and whose value is the promise of the corresponsing response. We
     * don't use {@link IntObjectHashMap} or map-like data structure here because 64k elements are fairly small, which
     * is only about 512KB.
     */
    private final AtomicReferenceArray<DnsResponsePromise> promises =
            new AtomicReferenceArray<DnsResponsePromise>(65536);

    private final DnsResponseHandler responseHandler = new DnsResponseHandler();

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

        final DnsQuery query = new DnsQuery(0, nameServerAddress);
        query.addQuestion(new DnsQuestion(hostname, DnsType.ANY));

        final Promise<SocketAddress> resolvePromise = executor().newPromise();
        query(query).addListener(new FutureListener<DnsResponse>() {
            @Override
            public void operationComplete(Future<DnsResponse> future) throws Exception {
                if (future.isSuccess()) {
                    InetAddress[] resolved = decodeAddressRecords(hostname, future.getNow());
                    if (resolved == null) {
                        // No address decoded
                        resolvePromise.setFailure(new IllegalStateException());
                    } else {
                        // TODO: Pluggable mechanism for choosing an address among many.
                        resolvePromise.setSuccess(new InetSocketAddress(resolved[0], port));
                    }
                } else {
                    resolvePromise.setFailure(future.cause());
                }
            }
        });

        return resolvePromise;
    }

    private static InetAddress[] decodeAddressRecords(String hostname, DnsResponse response)  {

        final List<DnsResource> answers = response.answers();
        final int maxCount = answers.size();
        final InetAddress[] resolved = new InetAddress[maxCount];
        int count = 0;

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

            content.readBytes(addrBytes);
            try {
                resolved[count ++] = InetAddress.getByAddress(hostname, addrBytes);
            } catch (UnknownHostException ignore) {
                // Should never happen
                throw new Error();
            }
        }

        if (count == 0) {
            return null;
        }

        return resolved;
    }

    public Future<DnsResponse> query(final DnsQuery query) {
        final EventLoop eventLoop = ch.eventLoop();
        final DnsResponsePromise p = new DnsResponsePromise(query);
        final DnsQueryHeader header = query.header();

        int id = header.id();
        if (id == 0) {
            // The caller did not set the query ID; generate one.
            id = ThreadLocalRandom.current().nextInt(promises.length());
            final int maxTries = promises.length() << 1;
            int tries = 0;
            for (;;) {
                if (promises.compareAndSet(id, null, p)) {
                    break;
                }

                id = id + 1 & 0xFFFF;

                if (++ tries >= maxTries) {
                    return eventLoop.newFailedFuture(
                            new UnknownHostException("query ID space exhausted: " + query));
                }
            }

            header.setId(id);
        } else {
            // The caller set the query ID already; respect the specified query ID while preventing the conflict.
            if (!promises.compareAndSet(id, null, p)) {
                return eventLoop.newFailedFuture(
                        new IllegalArgumentException("query ID (" + id + ") in use by other query"));
            }
        }

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
        final int id = p.query.header().id();

        if (!writeFuture.isSuccess()) {
            promises.lazySet(id, null);
            p.setFailure(
                    new UnknownHostException("failed to send a query: " + p.query).initCause(writeFuture.cause()));
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
                    p.setFailure(new UnknownHostException("query timeout: " + p.query));
                }
            }, queryTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private final class DnsResponsePromise extends DefaultPromise<DnsResponse> {

        final DnsQuery query;
        volatile ScheduledFuture<?> timeoutFuture;

        DnsResponsePromise(DnsQuery query) {
            super(DomainNameResolver.this.executor());
            this.query = query;
        }
    }

    private final class DnsResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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

            if (res.header().responseCode() == DnsResponseCode.NOERROR) {
                // TODO: Implement positive cache with configurable minimum and maximum TTL.
                p.setSuccess(res);
            } else {
                // TODO: Implement negative cache with configurable TTL.
                ReferenceCountUtil.safeRelease(res);
                p.setFailure(new UnknownHostException(
                        "failed to resolve: " + p.query + " (" + res.header().responseCode() + ')'));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.debug("Unexpected exception: ", cause);
        }
    }

    public static void main(String[] args) throws Exception {
        EventLoop loop = new NioEventLoopGroup(1).next();
        NameResolver resolver = new DomainNameResolver(loop, NioDatagramChannel.class, DEFAULT_NAME_SERVERS[0]);

        System.err.println(resolver.resolve("netty.io", 80).sync().getNow());
    }
}
