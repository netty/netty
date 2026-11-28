/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.resolver.dns;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.ProtocolId;
import io.netty.resolver.ResolvedAddressTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.HTTPSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.SVCBBase;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractDnsHttpsTest {

    EventLoopGroup group;
    DnsJavaTestServer server;
    DnsNameResolver resolver;
    private final List<DnsNameResolver> resolvers = new ArrayList<DnsNameResolver>();

    @BeforeEach
    public void setUp() throws Exception {
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        server = new DnsJavaTestServer(group);
        resolver = build(newResolver());
    }

    /**
     * Returns a builder of a resolver of the IPv4 preferred address types, querying {@link #server}.
     */
    DnsNameResolverBuilder newResolver() {
        return new DnsNameResolverBuilder(group.next())
                .datagramChannelType(NioDatagramChannel.class)
                .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(server.localAddress()))
                .searchDomains(Collections.<String>emptyList())
                .maxQueriesPerResolve(32);
    }

    /**
     * Builds a resolver closed after the test.
     */
    DnsNameResolver build(DnsNameResolverBuilder builder) {
        DnsNameResolver resolver = builder.build();
        resolvers.add(resolver);
        return resolver;
    }

    @AfterEach
    public void tearDown() {
        for (DnsNameResolver resolver : resolvers) {
            resolver.close();
        }
        server.close();
        group.shutdownGracefully();
    }

    static HTTPSRecord https(String owner, int priority, String target, SVCBBase.ParameterBase... params)
            throws Exception {
        return https(owner, 300, priority, target, params);
    }

    static HTTPSRecord https(String owner, long ttl, int priority, String target,
                                     SVCBBase.ParameterBase... params) throws Exception {
        return new HTTPSRecord(Name.fromString(owner), DClass.IN, ttl, priority, Name.fromString(target),
                Arrays.asList(params));
    }

    static ARecord a(String owner, String address) throws Exception {
        return a(owner, 300, address);
    }

    static ARecord a(String owner, long ttl, String address) throws Exception {
        return new ARecord(Name.fromString(owner), DClass.IN, ttl, InetAddress.getByName(address));
    }

    static AAAARecord aaaa(String owner, String address) throws Exception {
        return new AAAARecord(Name.fromString(owner), DClass.IN, 300, InetAddress.getByName(address));
    }

    static CNAMERecord cname(String owner, String target) throws Exception {
        return new CNAMERecord(Name.fromString(owner), DClass.IN, 300, Name.fromString(target));
    }

    static SVCBBase.ParameterAlpn alpn(String... protocols) throws Exception {
        return new SVCBBase.ParameterAlpn(Arrays.asList(protocols));
    }

    static SVCBBase.ParameterPort port(int port) {
        return new SVCBBase.ParameterPort(port);
    }

    static SVCBBase.ParameterEch ech(int... values) {
        return new SVCBBase.ParameterEch(bytes(values));
    }

    static SVCBBase.ParameterIpv4Hint ipv4Hint(String... addresses) throws UnknownHostException {
        List<Inet4Address> hints = new ArrayList<Inet4Address>();
        for (String address : addresses) {
            hints.add((Inet4Address) InetAddress.getByName(address));
        }
        return new SVCBBase.ParameterIpv4Hint(hints);
    }

    static SVCBBase.ParameterIpv6Hint ipv6Hint(String... addresses) throws UnknownHostException {
        List<Inet6Address> hints = new ArrayList<Inet6Address>();
        for (String address : addresses) {
            hints.add((Inet6Address) InetAddress.getByName(address));
        }
        return new SVCBBase.ParameterIpv6Hint(hints);
    }

    static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    static DnsHttpsQuery query(String host) {
        return new DnsHttpsQuery("https", host, 443);
    }

    List<DnsHttpsAddress> resolve(DnsHttpsQuery query) throws Exception {
        return resolve(resolver, query);
    }

    static List<DnsHttpsAddress> resolve(DnsNameResolver resolver, DnsHttpsQuery query) throws Exception {
        return resolver.resolveAll(query).get(10, TimeUnit.SECONDS);
    }

    static Throwable failure(DnsNameResolver resolver, DnsHttpsQuery query) throws Exception {
        return resolver.resolveAll(query).await().cause();
    }

    /**
     * Returns an address resolved through an HTTPS record of SvcPriority {@code priority}, without ECH.
     */
    static DnsHttpsAddress endpoint(int priority, String address, int port, String... protocols)
            throws UnknownHostException {
        return endpoint(priority, address, port, null, protocols);
    }

    /**
     * Returns an address resolved through an HTTPS record of SvcPriority {@code priority}, with the ECH configuration
     * {@code ech}.
     */
    static DnsHttpsAddress endpoint(int priority, String address, int port, byte[] ech, String... protocols)
            throws UnknownHostException {
        List<ProtocolId> ids = new ArrayList<ProtocolId>(protocols.length);
        for (String protocol : protocols) {
            ids.add(ProtocolId.valueOf(protocol));
        }
        return new DnsHttpsAddress(InetAddress.getByName(address), port, ids, ech, priority, null);
    }

    /**
     * Returns an address of the host, on port 443.
     */
    static DnsHttpsAddress host(String address) throws UnknownHostException {
        return host(address, 443);
    }

    static DnsHttpsAddress host(String address, int port) throws UnknownHostException {
        return DnsHttpsAddress.of(InetAddress.getByName(address), port);
    }

    static List<InetAddress> ips(List<DnsHttpsAddress> addresses) {
        List<InetAddress> result = new ArrayList<InetAddress>(addresses.size());
        for (DnsHttpsAddress address : addresses) {
            result.add(address.address());
        }
        return result;
    }

    /**
     * Asserts the TTL of {@code address}, whose records were cached during a resolution that started at
     * {@code beforeNanos} and whose result was computed before {@code afterNanos}, both from {@link System#nanoTime()}.
     */
    static void assertTtl(DnsHttpsAddress address, long ttlSeconds, long beforeNanos, long afterNanos) {
        assertThat(address.ttl()).isBetween(
                Duration.ofSeconds(ttlSeconds).minusNanos(afterNanos - beforeNanos), Duration.ofSeconds(ttlSeconds));
    }
}
