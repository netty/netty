/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns.resolver;

import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DnsResponseException;
import io.netty.handler.codec.dns.DnsEntry;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * This is the main class for looking up information from DNS servers. Users should call the methods in this class to
 * query and receive answers from DNS servers. The class attempts to load user's default DNS servers.
 */
public final class AsynchronousDnsResolver {

    private static final Pattern REVERSE_PATTERN = Pattern.compile("\\.");
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AsynchronousDnsResolver.class);
    private static final DnsResponseDecoder RESPONSE_DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder QUERY_ENCODER = new DnsQueryEncoder();
    private static final DnsResponseHandler RESPONSE_HANDLER = new DnsResponseHandler();
    private static final AtomicInteger ID = new AtomicInteger(0);
    private static final String IN_ADDR_ARPA = "in-addr.arpa";

    private final AtomicInteger dnsServerIndex = new AtomicInteger();
    private final InetSocketAddress[] dnsServers;
    private final Channel channel;

    /**
     * Constructs a new {@link AsynchronousDnsResolver}.
     *
     * @param dnsServers
     *            an array of DNS server addresses to use
     * @param eventLoupGroup
     *            an {@link EventLoopGroup} to use for all DNS server {@link Channel} s.
     */
    public AsynchronousDnsResolver(ChannelFactory<DatagramChannel> channelFactory,
                                   EventLoopGroup eventLoupGroup, InetSocketAddress... dnsServers) {
        this(createChannel(channelFactory, eventLoupGroup), dnsServers);
    }

    public AsynchronousDnsResolver(DatagramChannel channel, InetSocketAddress... dnsServers) {
        if (dnsServers == null || dnsServers.length == 0) {
            this.dnsServers = useSystemDefault();
        } else {
            this.dnsServers = dnsServers.clone();
        }
        this.channel = channel;
    }

    private static DatagramChannel createChannel(
            ChannelFactory<DatagramChannel> channelFactory, EventLoopGroup eventLoupGroup) {
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (eventLoupGroup == null) {
            throw new NullPointerException("eventLoupGroup");
        }
        DatagramChannel channel = channelFactory.newChannel(eventLoupGroup.next());
        ChannelConfig config = channel.config();
        config.setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
        config.setOption(ChannelOption.SO_BROADCAST, true);
        channel.pipeline().addLast("decoder", RESPONSE_DECODER).addLast("encoder", QUERY_ENCODER)
                .addLast("handler", RESPONSE_HANDLER);
        ChannelPromise regFuture = channel.newPromise();
        channel.unsafe().register(regFuture);
        return (DatagramChannel) regFuture.syncUninterruptibly().channel();
    }

    /**
     * Returns an id in the range 0-65536.
     */
    private static int nextId() {
        for (;;) {
            int nextId = ID.incrementAndGet();
            if (nextId < 0 || nextId > 65536)  {
                ID.set(-1);
            } else {
                return nextId;
            }
        }
    }

    /**
     * Loads system's default DNS server settings and validates servers before adding them to the list of servers to use
     * for this {@link AsynchronousDnsResolver }.
     */
    private static InetSocketAddress[] useSystemDefault() {
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);
            InetSocketAddress[] addresses = new InetSocketAddress[list.size()];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = new InetSocketAddress(InetAddress.getByName(list.get(i)), 53);
            }
            return addresses;
        } catch (Exception e) {
            logger.error("Failed to obtain system's DNS server addresses.", e);
            throw new IllegalStateException("Failed to obtains system's DNS server addresses", e);
        }
    }

    /**
     * Writes a {@link DnsQuery} to a specified {@link Channel}.
     *
     * @param domain
     *              the domain being queried
     * @param dnsServerAddress
     *              the {@link InetSocketAddress} of the dnsserver
     * @param single
     *              {@code true} if a single result is expected.
     * @param types
     *              the types for the {@link DnsQuery}
     *
     * @return future
     *              {@link Future} that is notified once a response othe query was received or it failed
     */
    private <T> Future<T> sendQuery(String domain, InetSocketAddress dnsServerAddress, boolean single, int... types) {
        final Promise<T> promise = channel.eventLoop().newPromise();
        final ResolverDnsQuery<T> query = new ResolverDnsQuery<T>(nextId(), dnsServerAddress, single, promise);
        for (int type: types) {
            query.addQuestion(new DnsQuestion(domain, type));
        }
        channel.writeAndFlush(query);
        return promise;
    }

    /**
     * Returns a {@link Future} which can be used to obtain either an IPv4 or IPv6 {@link InetAddress} for the specified
     * name.
     */
    public Future<InetAddress> lookup(String domain) {
        return lookup(domain, null);
    }

    /**
     * Returns a {@link Future} which can be used to obtain either an IPv4 or IPv6 {@link
     * InetAddress} for the specified domain depending on the {@code family}.
     */
    public Future<InetAddress> lookup(String domain,  InternetProtocolFamily family) {
        if (family == null) {
            return resolveSingle(domain, nextDnsServer(), DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
        }
        switch (family) {
            case IPv4:  {
                return resolveSingle(domain, nextDnsServer(), DnsEntry.TYPE_A);
            }
            case IPv6: {
                return resolveSingle(domain, nextDnsServer(), DnsEntry.TYPE_AAAA);
            }
        }
        // should never happen
        return channel.eventLoop().newFailedFuture(
                new IllegalArgumentException("Unknown InetProtocolFamily:" + family));
    }

    /**
     * Returns a {@link Future} which can be used to obtain a <strong>single</strong> resource record with one of the
     * specified {@code types}.
     *
     * @param domain
     *            the domain name being queried
     * @param dnsServerAddress
     *            the DNS server to use
     * @param types
     *            the desired resource record (only <strong>one</strong> type and one record can be returned in a single
     *            method call. The first valid resource record in the array of types will be returned)
     */
    private <T> Future<T> resolveSingle(String domain, InetSocketAddress dnsServerAddress, int... types) {
        return sendQuery(domain, dnsServerAddress, true, types);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a <strong> {@link List}</strong> of resource records with
     * one of the specified {@code types}.
     *
     * @param domain
     *            the domain name being queried
     * @param dnsServerAddress
     *            the DNS server to use
     * @param types
     *            the desired resource records (only <strong>one</strong> type can be returned, but multiple resource
     *            records, in a single method call. The first valid type of resource records will be returned in a
     *            {@link List})
     */
    private <T extends List<?>> Future<T> resolve(String domain, InetSocketAddress dnsServerAddress, int... types) {
        return sendQuery(domain, dnsServerAddress, false, types);
    }

    // TODO: Maybe allow to override
    private InetSocketAddress nextDnsServer() {
        return dnsServers[dnsServerIndex.incrementAndGet() % dnsServers.length];
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of {@link Inet4Address}es.
     */
    public Future<List<Inet4Address>> resolve4(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_A);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of {@link Inet6Address}es.
     */
    public Future<List<Inet6Address>> resolve6(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_AAAA);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of mail exchanger records as
     * {@link MailExchangerRecord}s.
     */
    public Future<List<MailExchangerRecord>> resolveMx(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_MX);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of service records as {@link ServiceRecord}s.
     */
    public Future<List<ServiceRecord>> resolveSrv(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_SRV);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of text records as {@link String}s in a
     * {@link List}.
     */
    public Future<List<List<String>>> resolveTxt(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_TXT);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of canonical name records as {@link String}s.
     */
    public Future<List<String>> resolveCname(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_CNAME);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of name server records as {@link String}s.
     */
    public Future<List<String>> resolveNs(String domain) {
        return resolve(domain, nextDnsServer(), DnsEntry.TYPE_NS);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of domain names as {@link String}s when given
     * their corresponding IP address.
     *
     * @param ipAddress
     *            the IP address to perform a reverse lookup on
     */
    public Future<List<String>> reverse(String ipAddress) {
        String[] octets = REVERSE_PATTERN.split(ipAddress);
        StringBuilder domain = new StringBuilder(ipAddress.length() + IN_ADDR_ARPA.length() + 4);
        for (int i = octets.length - 1; i > -1; i--) {
            domain.append(octets[i]).append('.');
        }
        return resolve(domain.append(IN_ADDR_ARPA).toString(), nextDnsServer(), DnsEntry.TYPE_PTR);
    }

    private static final class DnsResponseHandler extends SimpleChannelInboundHandler<DnsResponse> {
        private final Map<Integer, ResolverDnsQuery<?>> queries = PlatformDependent.newConcurrentHashMap();

        /**
         * Called when a new {@link DnsResponse} is received. The callback corresponding to this message is found and
         * finished.
         */
        @Override
        public void messageReceived(ChannelHandlerContext ctx, DnsResponse response) throws Exception {
            ResolverDnsQuery<?> query = queries.remove(response.getHeader().getId());
            if (query != null) {
                if (response.getHeader().getResponseCode() == DnsResponseCode.NOERROR) {
                    if (processResource(query, response.getAnswers())) {
                        return;
                    }
                    query.setFailure(new IllegalStateException("Unable to decode resources"));
                } else {
                    query.setFailure(new DnsResponseException(response.getHeader().getResponseCode()));
                }
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static boolean processResource(ResolverDnsQuery query, List<DnsResource> resources) {
            boolean single = query.single;

            List<Object> decoded = null;
            for (int i = 0; i < resources.size(); i++) {
                DnsResource resource = resources.get(i);
                Object result = DnsResourceDecoderFactory.getFactory().decode(resource);
                if (result != null) {
                    if (single) {
                        query.setSuccess(result);
                        return true;
                    }
                    if (decoded == null) {
                        decoded = new ArrayList<Object>(resources.size());
                    }
                    decoded.add(result);
                }
            }
            if (!decoded.isEmpty()) {
                query.setSuccess(decoded);
                return true;
            }
            return false;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            final ResolverDnsQuery<?> query = (ResolverDnsQuery<?>) msg;
            queries.put(query.getHeader().getId(), query);
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        queries.remove(query.getHeader().getId());
                        query.setFailure(future.cause());
                    }
                }
            });

            super.write(ctx, msg, promise);
        }
    }

    private static final class ResolverDnsQuery<T> extends DnsQuery {
        private final Promise<T> promise;
        private final boolean single;

        private ResolverDnsQuery(int id, InetSocketAddress recipient, boolean single, Promise<T> promise) {
            super(id, recipient);
            this.single = single;
            this.promise = promise;
        }

        void setSuccess(T result) {
            promise.setSuccess(result);
        }

        void setFailure(Throwable cause) {
            promise.setFailure(cause);
        }
    }
}
