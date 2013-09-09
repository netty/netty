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
package io.netty.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.dns.decoder.record.MailExchangerRecord;
import io.netty.dns.decoder.record.ServiceRecord;
import io.netty.handler.codec.dns.DnsEntry;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This is the main class for looking up information from DNS servers. Users should call the methods in this class to
 * query and receive answers from DNS servers. The class attempts to load user's default DNS servers, but also includes
 * Google and OpenDNS DNS servers.
 */
public class DnsAsynchronousResolver {

    /**
     * How long to wait for an answer from a DNS server after sending a query before timing out and moving on to the
     * next DNS server.
     */
    public static final long REQUEST_TIMEOUT = 2000;

    private static final EventExecutorGroup executor = new DefaultEventExecutorGroup(1);
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsAsynchronousResolver.class);
    private static final Object idxLock = new Object();

    private static int idx;

    /**
     * Returns an id in the range 0-65536.
     */
    private static int obtainId() {
        synchronized (idxLock) {
            return idx = idx + 1 & 0xffff;
        }
    }

    private final List<InetAddress> dnsServers = new ArrayList<InetAddress>();
    private final Map<InetAddress, Channel> dnsServerChannels = new HashMap<InetAddress, Channel>();

    private final EventLoop eventLoop;
    private final DnsCachingStrategy cache;
    private final DnsSelectionStrategy selector;

    /**
     * Constructs a new {@link DnsAsynchronousResolver} with the system's default DNS servers, a new
     * {@link NioEventLoopGroup} for every DNS server {@link Channel} created, a {@link DnsDefaultCache} caching
     * strategy and a {@link DnsRoundRobinSelectionStrategy}.
     */
    public DnsAsynchronousResolver() {
        this(null, null, new DnsDefaultCache(), new DnsRoundRobinSelectionStrategy());
    }

    /**
     * Constructs a new {@link DnsAsynchronousResolver}.
     *
     * @param dnsServers
     *            an array of DNS server addresses to use
     * @param eventLoop
     *            an {@link EventLoop} to use for all DNS server {@link Channel} s. If left {@code null}, a new
     *            {@link NioEventLoopGroup} will be used for every {@link Channel}.
     * @param cache
     *            the caching strategy to use
     * @param selector
     *            the selecting strategy to use for single results
     */
    public DnsAsynchronousResolver(InetAddress[] dnsServers, EventLoop eventLoop, DnsCachingStrategy cache,
            DnsSelectionStrategy selector) {
        if (cache == null) {
            throw new NullPointerException("The caching strategy cannot be null.");
        }
        if (selector == null) {
            throw new NullPointerException("The selection strategy cannot be null.");
        }
        this.eventLoop = eventLoop;
        this.cache = cache;
        this.selector = selector;
        if (dnsServers == null || dnsServers.length == 0) {
            useSystemDefault();
        } else {
            for (int i = 0; i < dnsServers.length; i++) {
                if (validAddress(dnsServers[i])) {
                    this.dnsServers.add(dnsServers[i]);
                }
            }
        }
    }

    /**
     * Loads system's default DNS server settings and validates servers before adding them to the list of servers to use
     * for this {@link DnsAsynchronousResolver }.
     */
    private void useSystemDefault() {
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);
            for (String dns : list) {
                InetAddress address = InetAddress.getByName(dns);
                if (validAddress(address)) {
                    dnsServers.add(address);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to obtain system's DNS server addresses.", e);
        }
    }

    /**
     * Checks if a DNS server can actually be connected to and queried for information by running through a request.
     * This is a <strong>blocking</strong> method.
     *
     * @param address
     *            the DNS server being checked
     * @return {@code true} if the DNS server provides a valid response
     */
    private boolean validAddress(InetAddress address) {
        try {
            int id = obtainId();
            Channel channel = channelForAddress(address);
            Callable<List<ByteBuf>> callback = new DnsCallback<List<ByteBuf>>(this, -1, sendQuery(DnsEntry.TYPE_A,
                    "google.com", id, channel));
            return callback.call() != null;
        } catch (Exception e) {
            removeChannel(dnsServerChannels.get(address));
            if (logger.isWarnEnabled()) {
                StringBuilder string = new StringBuilder();
                byte[] raw = address.getAddress();
                for (int i = 0; i < raw.length; i++) {
                    string.append(raw[i]).append(".");
                }
                logger.warn("Failed to add DNS server " + string.substring(0, string.length() - 1), e);
            }
            return false;
        }
    }

    /**
     * Returns the {@link DnsCachingStrategy} that this resolver uses for caching responses.
     */
    public DnsCachingStrategy cache() {
        return cache;
    }

    /**
     * Returns the {@link DnsSelectionStrategy} that this resolver uses for picking a single resource record from a
     * {@link List}.
     */
    public DnsSelectionStrategy selector() {
        return selector;
    }

    /**
     * Writes a {@link DnsQuery} to a specified channel.
     *
     * @param type
     *            the type for the {@link DnsQuery}
     * @param domain
     *            the domain being queried
     * @param id
     *            the id for the {@link DnsQuery}
     * @param channel
     *            the channel the {@link DnsQuery} is written to
     * @return the {@link DnsQuery} being written
     * @throws InterruptedException
     */
    private DnsQuery sendQuery(int type, String domain, int id, Channel channel) throws InterruptedException {
        DnsQuery query = new DnsQuery(id);
        query.addQuestion(new DnsQuestion(domain, type));
        channel.writeAndFlush(query);
        return query;
    }

    /**
     * Adds a DNS server to the default {@link List} of DNS servers used.
     *
     * @param dnsServerAddress
     *            the DNS server being added
     * @return {@code true} if the DNS server was added successfully
     */
    public boolean addDnsServer(InetAddress dnsServerAddress) {
        return dnsServers.add(dnsServerAddress);
    }

    /**
     * Removes a DNS server from the default {@link List} of DNS servers.
     *
     * @param dnsServerAddress
     *            the DNS server being removed
     * @return {@code true} if the DNS server was removed successfully
     */
    public boolean removeDnsServer(InetAddress dnsServerAddress) {
        return dnsServers.remove(dnsServerAddress);
    }

    /**
     * Returns the DNS server address at the specified {@code index} in the {@link List}.
     */
    public InetAddress getDnsServer(int index) {
        if (index > -1 && index < dnsServers.size()) {
            return dnsServers.get(index);
        }
        return null;
    }

    /**
     * Creates a channel for a DNS server if it does not already exist, or else returns the existing channel. Internal
     * use only.
     *
     * @param dnsServerAddress
     *            the address of the DNS server
     * @return the {@link Channel} created
     * @throws UnknownHostException
     * @throws SocketException
     * @throws InterruptedException
     */
    protected Channel channelForAddress(InetAddress dnsServerAddress) throws UnknownHostException, SocketException,
            InterruptedException {
        Channel channel = null;
        if ((channel = dnsServerChannels.get(dnsServerAddress)) != null) {
            return channel;
        } else {
            synchronized (dnsServerChannels) {
                if ((channel = dnsServerChannels.get(dnsServerAddress)) == null) {
                    Bootstrap b = new Bootstrap();
                    if (eventLoop == null) {
                        b.group(new NioEventLoopGroup());
                    } else {
                        b.group(eventLoop);
                    }
                    b.channel(NioDatagramChannel.class).remoteAddress(dnsServerAddress, 53)
                            .option(ChannelOption.SO_BROADCAST, true).option(ChannelOption.SO_SNDBUF, 1048576)
                            .option(ChannelOption.SO_RCVBUF, 1048576).handler(new DnsClientInitializer(this));
                    dnsServerChannels.put(dnsServerAddress, channel = b.connect().sync().channel());
                }
                return channel;
            }
        }
    }

    /**
     * Returns a {@link Future} which can be used to obtain either an IPv4 or IPv6 {@link InetAddress} for the specified
     * domain.
     */
    public Future<InetAddress> lookup(String domain) {
        return resolveSingle(domain, dnsServers.get(0), DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
    }

    /**
     * Returns a {@link Future} which can be used to obtain either an IPv4 or IPv6 {@link InetAddress} for the specified
     * domain depending on the {@code family}.
     *
     * @param family
     *            {@code 4} for IPv4 addresses, {@code 6} for IPv6 addresses, or {@code null} for one of the two (based
     *            on which is first received)
     */
    public Future<InetAddress> lookup(String domain, Integer family) {
        if (family != null && family != 4 && family != 6) {
            throw new IllegalArgumentException("Family must be 4, 6, or null to indicate both 4 and 6.");
        }
        if (family == null) {
            return resolveSingle(domain, dnsServers.get(0), DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
        } else if (family == 4) {
            return resolveSingle(domain, dnsServers.get(0), DnsEntry.TYPE_A);
        }
        return resolveSingle(domain, dnsServers.get(0), DnsEntry.TYPE_AAAA);
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
    public <T> Future<T> resolveSingle(String domain, InetAddress dnsServerAddress, int... types) {
        for (int i = 0; i < types.length; i++) {
            List<T> results = cache.getRecords(domain, types[i]);
            if (results != null) {
                return (eventLoop == null ? executor.next() : eventLoop).newSucceededFuture(selector
                        .selectRecord(results));
            }
        }
        int id = obtainId();
        try {
            Channel channel = channelForAddress(dnsServerAddress);
            DnsQuery[] queries = new DnsQuery[types.length];
            for (int i = 0; i < types.length; i++) {
                queries[i] = sendQuery(types[i], domain, id, channel);
            }
            return (eventLoop == null ? executor : eventLoop).submit(new DnsSingleResultCallback<T>(
                    new DnsCallback<List<T>>(this, dnsServers.indexOf(dnsServerAddress), queries)));
        } catch (Exception e) {
            return (eventLoop == null ? executor.next() : eventLoop).newFailedFuture(e);
        }
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
    public <T extends List<?>> Future<T> resolve(String domain, InetAddress dnsServerAddress, int... types) {
        for (int i = 0; i < types.length; i++) {
            List<T> results = cache.getRecords(domain, types[i]);
            if (results != null) {
                return (eventLoop == null ? executor.next() : eventLoop).newSucceededFuture(selector
                        .selectRecord(results));
            }
        }
        try {
            int id = obtainId();
            Channel channel = channelForAddress(dnsServerAddress);
            DnsQuery[] queries = new DnsQuery[types.length];
            for (int i = 0; i < types.length; i++) {
                queries[i] = sendQuery(types[i], domain, id, channel);
            }
            return (eventLoop == null ? executor : eventLoop).submit(new DnsCallback<T>(this, dnsServers
                    .indexOf(dnsServerAddress), queries));
        } catch (Exception e) {
            return (eventLoop == null ? executor.next() : eventLoop).newFailedFuture(e);
        }
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of {@link Inet4Address}es.
     */
    public Future<List<Inet4Address>> resolve4(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_A);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of {@link Inet6Address}es.
     */
    public Future<List<Inet6Address>> resolve6(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_AAAA);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of mail exchanger records as
     * {@link MailExchangerRecord}s.
     *
     * @throws UnknownHostException
     * @throws SocketException
     * @throws InterruptedException
     */
    public Future<List<MailExchangerRecord>> resolveMx(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_MX);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of service records as {@link ServiceRecord}s.
     */
    public Future<List<ServiceRecord>> resolveSrv(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_SRV);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of text records as {@link String}s in a
     * {@link List}.
     */
    public Future<List<List<String>>> resolveTxt(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_TXT);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of canonical name records as {@link String}s.
     */
    public Future<List<String>> resolveCname(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_CNAME);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of name server records as {@link String}s.
     */
    public Future<List<String>> resolveNs(String domain) {
        return resolve(domain, dnsServers.get(0), DnsEntry.TYPE_NS);
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of domain names as {@link String}s when given
     * their corresponding IP address.
     *
     * @param ipAddress
     *            the IP address to perform a reverse lookup on
     */
    public Future<List<String>> reverse(ByteBuf ipAddress) {
        ByteBuf buf = Unpooled.wrappedBuffer(ipAddress);
        StringBuilder builder = new StringBuilder();
        for (int i = ipAddress.readerIndex(); i < ipAddress.writerIndex(); i++) {
            builder.append(ipAddress.getUnsignedByte(i)).append(".");
        }
        Future<List<String>> future = reverse(builder.substring(0, builder.length() - 1));
        buf.release();
        return future;
    }

    /**
     * Returns a {@link Future} which can be used to obtain a {@link List} of domain names as {@link String}s when given
     * their corresponding IP address.
     *
     * @param ipAddress
     *            the IP address to perform a reverse lookup on
     */
    public Future<List<String>> reverse(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        StringBuilder domain = new StringBuilder();
        for (int i = octets.length - 1; i > -1; i--) {
            domain.append(octets[i]).append(".");
        }
        return resolve(domain.append("in-addr.arpa").toString(), dnsServers.get(0), DnsEntry.TYPE_PTR);
    }

    /**
     * Adds a channel with a corresponding IP address. Internal use only.
     *
     * @param channel
     *            the channel to be added
     */
    protected void addChannel(InetAddress address, Channel channel) {
        synchronized (dnsServerChannels) {
            dnsServerChannels.put(address, channel);
        }
    }

    /**
     * Removes an inactive channel after timing out. Internal use only.
     *
     * @param channel
     *            the channel to be removed
     */
    protected void removeChannel(Channel channel) {
        synchronized (dnsServerChannels) {
            for (Iterator<Map.Entry<InetAddress, Channel>> iter = dnsServerChannels.entrySet().iterator(); iter
                    .hasNext();) {
                Map.Entry<InetAddress, Channel> entry = iter.next();
                if (entry.getValue() == channel) {
                    if (channel.isOpen()) {
                        try {
                            channel.close().sync();
                        } catch (Exception e) {
                            if (logger.isErrorEnabled()) {
                                logger.error("Could not close channel for address " + entry.getKey().getHostAddress(),
                                        e);
                            }
                        } finally {
                            if (eventLoop == null) {
                                channel.eventLoop().shutdownGracefully();
                            }
                        }
                    }
                    iter.remove();
                    break;
                }
            }
        }
    }

}
