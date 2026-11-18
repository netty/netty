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
}
