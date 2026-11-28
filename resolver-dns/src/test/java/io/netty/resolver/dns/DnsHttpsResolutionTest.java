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

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.ProtocolId;
import io.netty.handler.codec.dns.SvcParam;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.SVCBBase;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the resolution of HTTPS records through {@link DnsNameResolver#resolveAll(DnsHttpsQuery)}: the records
 * answered by the server and the settings of the resolver in, the {@link DnsHttpsAddress}es and the queries out.
 */
public class DnsHttpsResolutionTest extends AbstractDnsHttpsTest {

    /**
     * The number of AliasMode records followed for one resolution.
     */
    private static final int MAX_ALIASES = 8;

    @Test
    public void testServiceModeRecordsSortedByPriority() throws Exception {
        server.add(https("example.com.", 2, "b.example.net.", port(443)));
        server.add(https("example.com.", 1, "a.example.net.", alpn("h3")));
        server.add(https("example.com.", 1, "c.example.net."));
        server.add(a("a.example.net.", "198.51.100.1"));
        server.add(a("b.example.net.", "198.51.100.2"));
        server.add(a("c.example.net.", "198.51.100.3"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.1", 443, "h3", "http/1.1"),
                endpoint(1, "198.51.100.3", 443, "http/1.1"), endpoint(2, "198.51.100.2", 443, "http/1.1"));
    }

    @Test
    public void testKnownProtocolsAreTheProtocolIdConstants() throws Exception {
        server.add(https("example.com.", 1, "a.example.net.", alpn("h2", "h3", "h3-29")));
        server.add(a("a.example.net.", "198.51.100.1"));

        List<ProtocolId> protocols = resolve(query("example.com")).get(0).protocols();

        assertThat(protocols).hasSize(4);
        assertThat(protocols.get(0)).isSameAs(ProtocolId.H2);
        assertThat(protocols.get(1)).isSameAs(ProtocolId.H3);
        assertThat(protocols.get(2)).isEqualTo(ProtocolId.valueOf("h3-29"));
        assertThat(protocols.get(3)).isSameAs(ProtocolId.HTTP_1_1);
    }

    @Test
    public void testEndpointParameters() throws Exception {
        server.add(https("example.com.", 1, "a.example.net.", alpn("h2", "h3")));
        server.add(https("example.com.", 2, "b.example.net.", alpn("h3"), new SVCBBase.ParameterNoDefaultAlpn(),
                port(8443), ipv4Hint("192.0.2.1"), ech(1, 2, 3), ipv6Hint("2001:db8::1")));
        server.add(a("a.example.net.", "198.51.100.1"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The second endpoint has no address: its hints are used.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.1", 443, "h2", "h3", "http/1.1"),
                endpoint(2, "192.0.2.1", 8443, bytes(1, 2, 3), "h3"),
                endpoint(2, "2001:db8::1", 8443, bytes(1, 2, 3), "h3"));
    }

    @Test
    public void testPortZeroIsReported() throws Exception {
        // RFC 9460, Section 7.2 allows a port between 0 and 65535: an endpoint on port 0 is not filtered.
        server.add(https("example.com.", 1, ".", port(0)));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 0, "http/1.1"), host("192.0.2.1"));
    }

    @Test
    public void testUnsupportedMandatoryKey() throws Exception {
        // RFC 9460, Section 8: a record with a mandatory key the client does not support is ignored; ech is not
        // supported by default.
        server.add(https("example.com.", 1, "ech.example.net.",
                new SVCBBase.ParameterMandatory(Collections.singletonList(SvcParam.ECH)), ech(1, 2, 3)));
        server.add(https("example.com.", 2, "plain.example.net."));
        server.add(a("ech.example.net.", "198.51.100.1"));
        server.add(a("plain.example.net.", "198.51.100.2"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(2, "198.51.100.2", 443, "http/1.1"));
    }

    @Test
    public void testNonSelfConsistentRecordDropped() throws Exception {
        // SvcPriority 1, TargetName ".", mandatory=port without a port SvcParam
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 1, 0, 0, 0, 0, 2, 0, 3));
        server.add(https("example.com.", 2, "valid.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(a("valid.example.net.", "198.51.100.7"));
        // Same record with the port SvcParam (8443), sent the same way, to show raw records do reach the resolver
        server.addRaw("consistent.example.com.", Type.HTTPS,
                bytes(0, 1, 0, 0, 0, 0, 2, 0, 3, 0, 3, 0, 2, 0x20, 0xfb));
        server.add(a("consistent.example.com.", "192.0.2.2"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        List<DnsHttpsAddress> consistent = resolve(query("consistent.example.com"));

        assertThat(addresses).containsExactly(endpoint(2, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(consistent).containsExactly(endpoint(1, "192.0.2.2", 8443, "http/1.1"), host("192.0.2.2"));
    }

    // AliasMode

    @Test
    public void testAliasEndpointIsAppendedAfterAliasMode() throws Exception {
        // RFC 9460, Section 3, step 6: after an AliasMode record, an endpoint made of the final QNAME, the authority
        // port and no SvcParams is appended, here without any ServiceMode record at the alias target.
        server.add(https("example.com.", 0, "cdn.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("cdn.example.net.", "198.51.100.7"));
        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        server.clear();
        List<DnsHttpsAddress> fromCache = resolve(query("example.com"));

        for (List<DnsHttpsAddress> r : Arrays.asList(addresses, fromCache)) {
            // Without SvcParams, the ALPN set is the HTTPS default one (RFC 9460, Section 9): http/1.1 is the only
            // protocol the DNS data guarantees, the client can still negotiate HTTP/2 in the TLS handshake.
            assertThat(r).containsExactly(endpoint(65535, "198.51.100.7", 443, "http/1.1"),
                    host("192.0.2.1"), host("2001:db8::1"));
        }
    }

    @Test
    public void testAliasEndpointComesAfterServiceModeEndpoints() throws Exception {
        server.add(https("example.com.", 0, "cdn.example.net."));
        server.add(https("cdn.example.net.", 2, "b.example.net."));
        server.add(https("cdn.example.net.", 1, "a.example.net.", alpn("h2", "h3"), port(8443), ech(1, 2, 3)));
        server.add(a("a.example.net.", "198.51.100.1"));
        server.add(a("b.example.net.", "198.51.100.2"));
        server.add(a("cdn.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The ServiceMode endpoints of the alias target by priority with their parameters, the alias endpoint with
        // the addresses of the alias target, then the host.
        assertThat(addresses).containsExactly(
                endpoint(1, "198.51.100.1", 8443, bytes(1, 2, 3), "h2", "h3", "http/1.1"),
                endpoint(2, "198.51.100.2", 443, "http/1.1"), endpoint(65535, "198.51.100.7", 443, "http/1.1"),
                host("192.0.2.1"));
    }

    @Test
    public void testAliasEndpointIsTheFinalAliasTarget() throws Exception {
        server.add(https("example.com.", 0, "a.example.net."));
        server.add(https("a.example.net.", 0, "b.example.net."));
        server.add(a("a.example.net.", "198.51.100.1"));
        server.add(a("b.example.net.", "198.51.100.2"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(65535, "198.51.100.2", 443, "http/1.1"));
    }

    @Test
    public void testNoAliasEndpointWhenAliasLimitReached() throws Exception {
        // RFC 9460, Section 3.1: reaching the limit falls back to the authority endpoint, without alias endpoint.
        for (int i = 0; i < MAX_ALIASES + 1; i++) {
            server.add(https("n" + i + ".example.com.", 0, "n" + (i + 1) + ".example.com."));
            server.add(a("n" + (i + 1) + ".example.com.", "198.51.100." + (i + 1)));
        }
        server.add(a("n0.example.com.", "192.0.2.1"));
        server.add(aaaa("n0.example.com.", "2001:db8::1"));

        List<DnsHttpsAddress> addresses = resolve(query("n0.example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testAliasModeWinsOverServiceMode() throws Exception {
        server.add(https("example.com.", 1, "ignored.example.net."));
        server.add(https("example.com.", 0, "svc.example.net."));
        server.add(https("svc.example.net.", 1, "."));
        server.add(a("ignored.example.net.", "198.51.100.9"));
        server.add(a("svc.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The ServiceMode endpoint of the alias target, then the alias endpoint.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"));
    }

    @Test
    public void testFirstOfSeveralAliasModeRecordsIsFollowed() throws Exception {
        server.add(https("example.com.", 0, "first.example.net."));
        server.add(https("example.com.", 0, "second.example.net."));
        server.add(https("first.example.net.", 1, "."));
        server.add(https("second.example.net.", 1, "."));
        server.add(a("first.example.net.", "198.51.100.1"));
        server.add(a("second.example.net.", "198.51.100.2"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.1", 443, "http/1.1"),
                endpoint(65535, "198.51.100.1", 443, "http/1.1"));
    }

    @Test
    public void testAliasModeParamsAreIgnored() throws Exception {
        // SvcPriority 0, TargetName "svc.example.net.", mandatory=key65280 (unsupported, and missing)
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 0,
                3, 's', 'v', 'c', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'n', 'e', 't', 0,
                0, 0, 0, 2, 0xff, 0x00));
        server.add(https("svc.example.net.", 1, "."));
        server.add(a("svc.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The ServiceMode endpoint of the alias target, then the alias endpoint.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"));
    }

    @Test
    public void testAliasModeToRootMeansServiceUnavailable() throws Exception {
        server.add(https("example.com.", 0, "."));

        Throwable cause = failure(resolver, query("example.com"));

        // "." is not an alias target: nothing else is queried, and without addresses nothing is left.
        assertThat(cause).isInstanceOf(UnknownHostException.class);
        assertThat(server.receivedNames()).containsOnly("example.com.");
    }

    @Test
    public void testAliasModeLoop() throws Exception {
        server.add(https("a.example.com.", 0, "b.example.com."));
        server.add(https("b.example.com.", 0, "a.example.com."));

        Throwable cause = failure(resolver, query("a.example.com"));

        assertThat(cause).isExactlyInstanceOf(UnknownHostException.class);
        // The loop is detected without going round it up to the limit.
        assertThat(server.receivedNames()).containsOnlyOnce("b.example.com.");
    }

    @Test
    public void testAliasModeLimit() throws Exception {
        for (int i = 0; i < MAX_ALIASES + 1; i++) {
            server.add(https("n" + i + ".example.com.", 0, "n" + (i + 1) + ".example.com."));
        }
        String last = "n" + (MAX_ALIASES + 1) + ".example.com.";
        server.add(https(last, 1, "."));
        server.add(a(last, "198.51.100.9"));

        Throwable cause = failure(resolver, query("n0.example.com"));
        List<DnsHttpsAddress> addresses = resolve(query("n1.example.com"));

        assertThat(cause).isExactlyInstanceOf(UnknownHostException.class);
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.9", 443, "http/1.1"),
                endpoint(65535, "198.51.100.9", 443, "http/1.1"));
    }

    @Test
    public void testAliasModeTargetWithCnameCacheLoop() throws Exception {
        server.add(https("example.com.", 0, "svc.example.net."));
        DnsCnameCache cnameCache = new DefaultDnsCnameCache();
        cnameCache.cache("svc.example.net.", "a.example.net.", Long.MAX_VALUE, group.next());
        cnameCache.cache("a.example.net.", "svc.example.net.", Long.MAX_VALUE, group.next());
        DnsNameResolver loopResolver = build(newResolver().cnameCache(cnameCache));

        Throwable cause = failure(loopResolver, query("example.com"));

        assertThat(cause).isExactlyInstanceOf(UnknownHostException.class);
        assertThat(cause.getMessage()).contains("CNAME loop");
    }

    // Port prefixed names

    @Test
    public void testHttpsRecordsOfAnotherPortUseThePortPrefixedName() throws Exception {
        // RFC 9460, Section 9.1: the HTTPS records of https://example.com:8443 are at _8443._https.example.com, while
        // the addresses are those of example.com; the records of example.com are for port 443.
        server.add(https("example.com.", 1, "port443.example.net."));
        server.add(https("_8443._https.example.com.", 1, "svc.example.net.", alpn("h3")));
        server.add(a("port443.example.net.", "198.51.100.4"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(new DnsHttpsQuery("https", "example.com", 8443));
        int queries = server.receivedTypes().size();
        List<DnsHttpsAddress> fromCache = resolve(new DnsHttpsQuery("https", "example.com", 8443));
        int cachedQueries = server.receivedTypes().size();
        List<DnsHttpsAddress> port443 = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 8443, "h3", "http/1.1"),
                host("192.0.2.1", 8443));
        assertThat(fromCache).isEqualTo(addresses);
        assertThat(cachedQueries).isEqualTo(queries);
        // The records of example.com were not cached for port 8443: they are queried for port 443.
        assertThat(port443).containsExactly(endpoint(1, "198.51.100.4", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(server.receivedNames(Type.HTTPS)).containsExactly("_8443._https.example.com.", "example.com.");
    }

    @Test
    public void testDotTargetOfThePortPrefixedNameIsTheOriginHost() throws Exception {
        // RFC 9460, Section 2.5.2 makes "." the owner name, _8443._https.example.com, which has no addresses: like the
        // example of Section 10.4.1, it stands for the origin host, whose addresses are already resolved.
        server.add(https("_8443._https.example.com.", 1, ".", alpn("h3")));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(new DnsHttpsQuery("https", "example.com", 8443));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 8443, "h3", "http/1.1"),
                host("192.0.2.1", 8443));
        // Only the HTTPS query is sent for the port prefixed name.
        assertThat(server.receivedNames()).containsOnlyOnce("_8443._https.example.com.");
    }

    @Test
    public void testDotTargetOfThePortPrefixedNameUsesTheOriginAddressesOverHints() throws Exception {
        // RFC 9460, Section 7.3: the addresses of the origin host are available, so the hints are ignored.
        server.add(https("_8443._https.example.com.", 1, ".", alpn("h3"), ipv4Hint("192.0.2.9")));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolve(new DnsHttpsQuery("https", "example.com", 8443));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 8443, "h3", "http/1.1"),
                host("192.0.2.1", 8443));
    }

    // Malformed records

    @Test
    public void testCompressedTargetNameIsMalformed() throws Exception {
        // RFC 9460, Section 2.2 requires an uncompressed TargetName without listing compression as malformed: it is
        // treated as malformed, so the whole RRset is rejected.
        // SvcPriority 1, compressed TargetName
        server.addRaw("compressed.example.com.", Type.HTTPS, bytes(0, 1, 0xc0, 0x0c));
        server.add(https("with-addresses.example.com.", 1, "valid.example.net."));
        server.addRaw("with-addresses.example.com.", Type.HTTPS, bytes(0, 2, 0xc0, 0x0c));
        server.add(a("valid.example.net.", "198.51.100.7"));
        server.add(a("with-addresses.example.com.", "192.0.2.1"));
        server.add(aaaa("with-addresses.example.com.", "2001:db8::1"));

        Throwable withoutAddresses = failure(resolver, query("compressed.example.com"));
        List<DnsHttpsAddress> withAddresses = resolve(query("with-addresses.example.com"));

        assertThat(withoutAddresses).isInstanceOf(UnknownHostException.class)
                .hasCauseInstanceOf(CorruptedFrameException.class);
        assertThat(withAddresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testMalformedWithAddressesFallsBackToAddresses() throws Exception {
        server.add(https("example.com.", 1, "valid.example.net."));
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 2, 0, 0, 3, 0, 1, 1));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(https("ipv4.example.com.", 1, "valid.example.net."));
        server.addRaw("ipv4.example.com.", Type.HTTPS, bytes(0, 2, 0, 0, 3, 0, 1, 1));
        server.add(a("ipv4.example.com.", "192.0.2.1"));
        server.add(a("valid.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        List<DnsHttpsAddress> oneFamily = resolve(query("ipv4.example.com"));

        // The whole RRset is rejected, and the resolution falls back to non-SVCB (RFC 9460, Section 2.2), with any
        // address available.
        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
        assertThat(oneFamily).containsExactly(host("192.0.2.1"));
    }

    @Test
    public void testMalformedRecordWithAliasToRootFallsBackToAddresses() throws Exception {
        server.add(https("example.com.", 0, "."));
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 2, 0, 0, 3, 0, 1, 1));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(https("no-addresses.example.com.", 0, "."));
        server.addRaw("no-addresses.example.com.", Type.HTTPS, bytes(0, 2, 0, 0, 3, 0, 1, 1));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        Throwable withoutAddresses = failure(resolver, query("no-addresses.example.com"));

        // Rejecting the RRset also rejects its AliasMode record to ".".
        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
        assertThat(withoutAddresses).isInstanceOf(UnknownHostException.class)
                .hasCauseInstanceOf(CorruptedFrameException.class);
    }

    @Test
    public void testMalformedSetAtAliasTargetFallsBackToOriginAddresses() throws Exception {
        // RFC 9460 rejects the malformed RRset without saying what happens to the alias chain leading to it: the
        // resolution rejects all its HTTPS records and falls back to the addresses of the origin.
        server.add(https("example.com.", 0, "svc.example.net."));
        server.add(https("svc.example.net.", 1, "valid.example.net."));
        server.addRaw("svc.example.net.", Type.HTTPS, bytes(0, 2, 0, 0, 3, 0, 1, 1));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(a("valid.example.net.", "198.51.100.8"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testMalformedWithoutAddressesFailsAsMalformed() throws Exception {
        // RFC 9460, Section 2.2 falls back to non-SVCB connection, which needs addresses: without them, the resolution
        // fails with a distinct exception so that the caller can fall back itself.
        // SvcPriority 1, TargetName ".", port with a 1-byte value
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 1, 0, 0, 3, 0, 1, 1));

        Throwable noRecords = failure(resolver, query("example.com"));
        server.fail(Type.A, Rcode.SERVFAIL);
        server.fail(Type.AAAA, Rcode.SERVFAIL);
        Throwable queriesFailed = failure(resolver, query("example.com"));

        assertThat(noRecords).isInstanceOf(UnknownHostException.class)
                .hasCauseInstanceOf(CorruptedFrameException.class);
        assertThat(queriesFailed).isInstanceOf(UnknownHostException.class)
                .hasCauseInstanceOf(CorruptedFrameException.class);
    }

    @Test
    public void testMalformedOutcomeIsNotCached() throws Exception {
        // RFC 9460 says nothing about caching: neither the fallback nor the failure caused by a malformed RRset is
        // cached for HTTPS, even with negative caching, so that a fixed zone is used by the next resolution.
        server.addRaw("example.com.", Type.HTTPS, bytes(0, 1, 0, 0, 3, 0, 1, 1));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.addRaw("no-addresses.example.com.", Type.HTTPS, bytes(0, 1, 0, 0, 3, 0, 1, 1));
        DnsNameResolver negativeCaching = build(newResolver().negativeTtl(30));
        List<DnsHttpsAddress> fallback = resolve(negativeCaching, query("example.com"));
        Throwable failure = failure(negativeCaching, query("no-addresses.example.com"));
        server.clear();
        server.add(https("example.com.", 1, "."));
        server.add(https("no-addresses.example.com.", 1, ".", ipv4Hint("192.0.2.9")));

        List<DnsHttpsAddress> fixed = resolve(negativeCaching, query("example.com"));
        List<DnsHttpsAddress> fixedWithoutAddresses = resolve(negativeCaching, query("no-addresses.example.com"));

        assertThat(fallback).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
        assertThat(failure).isInstanceOf(UnknownHostException.class)
                .hasCauseInstanceOf(CorruptedFrameException.class);
        // The addresses are still cached, the HTTPS records are queried again.
        assertThat(fixed).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        assertThat(fixedWithoutAddresses).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
    }

    @Test
    public void testMalformedAddressRecordIsIgnoredLikeResolveAll() throws Exception {
        // RFC 9460 does not cover A/AAAA records: like DnsNameResolver.resolveAll, an address record with an RDATA of
        // the wrong length, including the length of the other family, is ignored, without affecting the rest of the
        // resolution.
        for (String name : Arrays.asList("example.com.", "other.example.com.")) {
            server.add(https(name, 1, "."));
            server.add(a(name, "192.0.2.1"));
            server.addRaw(name, Type.A, bytes(192, 0, 2, 2, 9));
            server.addRaw(name, Type.A, InetAddress.getByName("2001:db8::2").getAddress());
            server.addRaw(name, Type.AAAA, InetAddress.getByName("192.0.2.3").getAddress());
        }

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        List<InetAddress> resolveAll = resolver.resolveAll("other.example.com.").get(10, TimeUnit.SECONDS);

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(resolveAll).containsExactly(InetAddress.getByName("192.0.2.1"));
    }

    @Test
    public void testHttpsRecordsUpToTheLimitAreUsed() throws Exception {
        server.add(https("example.com.", 1, "a.example.net."));
        server.add(https("example.com.", 2, "b.example.net."));
        server.add(a("a.example.net.", "198.51.100.1"));
        server.add(a("b.example.net.", "198.51.100.2"));
        DnsNameResolver resolver = build(newResolver().maxHttpsRecords(2));

        assertThat(resolve(resolver, query("example.com"))).containsExactly(
                endpoint(1, "198.51.100.1", 443, "http/1.1"), endpoint(2, "198.51.100.2", 443, "http/1.1"));
    }

    @Test
    public void testHttpsRecordsAboveTheLimitFallBackToTheAddresses() throws Exception {
        // An RRset with more HTTPS records than the limit is rejected like a malformed one: the resolution falls back
        // to the addresses of the host, and nothing is cached so that a fixed zone is used by the next resolution.
        server.add(https("example.com.", 1, "a.example.net."));
        server.add(https("example.com.", 2, "b.example.net."));
        server.add(https("example.com.", 3, "c.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver resolver = build(newResolver().maxHttpsRecords(2));

        List<DnsHttpsAddress> addresses = resolve(resolver, query("example.com"));
        int queries = server.receivedTypes().size();
        resolve(resolver, query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"));
        assertThat(server.receivedTypesSince(queries)).containsExactly(Type.HTTPS);
    }

    @Test
    public void testHttpsRecordsAboveTheLimitWithoutAddressesFail() throws Exception {
        server.add(https("example.com.", 1, "a.example.net."));
        server.add(https("example.com.", 2, "b.example.net."));
        server.add(https("example.com.", 3, "c.example.net."));
        DnsNameResolver resolver = build(newResolver().maxHttpsRecords(2));

        Throwable cause = failure(resolver, query("example.com"));

        assertThat(cause).isInstanceOf(UnknownHostException.class);
        assertThat(cause.getCause()).hasMessage("more than 2 HTTPS records for example.com.");
    }

    // Targets and hints

    @Test
    public void testTargetAddressesReuseOriginAnswers() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        // Without resolve cache, the target cannot be answered by the cache instead.
        DnsNameResolver noResolveCache = build(newResolver().resolveCache(NoopDnsCache.INSTANCE));

        List<DnsHttpsAddress> addresses = resolve(noResolveCache, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        // Only the three parallel queries: the target is the origin, whose answers are reused.
        assertThat(server.receivedTypes()).containsExactlyInAnyOrder(Type.HTTPS, Type.A, Type.AAAA);
    }

    @Test
    public void testTargetAddressesResolvedOncePerTarget() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net.", port(443)));
        server.add(https("example.com.", 2, "svc.example.net.", port(8443)));
        server.add(a("svc.example.net.", "192.0.2.1"));
        server.add(aaaa("svc.example.net.", "2001:db8::1"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), endpoint(2, "192.0.2.1", 8443, "http/1.1"),
                endpoint(2, "2001:db8::1", 8443, "http/1.1"));
        // One A query for example.com. (parallel) and one for svc.example.net., not one per endpoint.
        assertThat(server.receivedTypes()).containsExactlyInAnyOrder(
                Type.HTTPS, Type.A, Type.AAAA, Type.A, Type.AAAA);
    }

    @Test
    public void testTargetsAreResolvedWithoutHttpsQuery() throws Exception {
        // A target only needs its addresses: its own HTTPS records are not queried.
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(https("svc.example.net.", 1, "other.example.net."));
        server.add(a("svc.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"));
        assertThat(server.receivedNames(Type.HTTPS)).containsExactly("example.com.");
    }

    @Test
    public void testTargetAddressesFollowCname() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(cname("svc.example.net.", "cdn.example.net."));
        server.add(a("cdn.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"));
    }

    @Test
    public void testUnresolvableTargetKeepsEndpoint() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net.", ipv4Hint("192.0.2.9")));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
    }

    @Test
    public void testAddressServfailWithHttps() throws Exception {
        server.fail(Type.A, Rcode.SERVFAIL);
        server.fail(Type.AAAA, Rcode.SERVFAIL);
        server.add(https("example.com.", 1, "svc.example.net.", ipv4Hint("192.0.2.9")));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The endpoint is kept: its target addresses could not be resolved either, but it has hints.
        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
    }

    @Test
    public void testHintsIgnoredWhenTheTargetHasAddresses() throws Exception {
        // RFC 9460, Section 7.3: if A and AAAA records for TargetName are available, the client SHOULD ignore hints.
        server.add(https("example.com.", 1, "svc.example.net.", ipv4Hint("192.0.2.9")));
        server.add(a("svc.example.net.", "198.51.100.7"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"));
    }

    @Test
    public void testHintsOfTheAddressTypesOfTheResolver() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net.", ipv4Hint("192.0.2.9"), ipv6Hint("2001:db8::9")));
        DnsNameResolver ipv4Only = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY));
        DnsNameResolver ipv6Preferred = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED));

        assertThat(resolve(ipv4Only, query("example.com"))).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
        assertThat(resolve(ipv6Preferred, query("example.com"))).containsExactly(
                endpoint(1, "2001:db8::9", 443, "http/1.1"), endpoint(1, "192.0.2.9", 443, "http/1.1"));
    }

    @Test
    public void testAddressTypesOfTheResolver() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        DnsNameResolver ipv4Only = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY));
        DnsNameResolver ipv6Preferred = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED));

        List<DnsHttpsAddress> v4 = resolve(ipv4Only, query("example.com"));
        assertThat(server.receivedTypes()).doesNotContain(Type.AAAA);
        List<DnsHttpsAddress> v6First = resolve(ipv6Preferred, query("example.com"));

        assertThat(v4).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(v6First).containsExactly(endpoint(1, "2001:db8::1", 443, "http/1.1"),
                endpoint(1, "192.0.2.1", 443, "http/1.1"), host("2001:db8::1"), host("192.0.2.1"));
    }

    @Test
    public void testDuplicateRecordsAreRemoved() throws Exception {
        // An answer repeating its records gives each endpoint and each address once, in their order.
        server.add(https("example.com.", 1, ".", alpn("h2")));
        server.add(https("example.com.", 1, ".", alpn("h2")));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(a("example.com.", "192.0.2.2"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "h2", "http/1.1"),
                endpoint(1, "192.0.2.2", 443, "h2", "http/1.1"), host("192.0.2.1"), host("192.0.2.2"));
    }

    @Test
    public void testQueriesOfAResolutionShareTheQueryBudget() throws Exception {
        // The HTTPS records, the addresses and the targets of a resolution send at most maxQueriesPerResolve queries
        // in total, like resolveAll(String), so that many targets do not multiply the queries.
        for (int i = 1; i <= 10; i++) {
            server.add(https("example.com.", i, "t" + i + ".example.net."));
            server.add(a("t" + i + ".example.net.", "198.51.100." + i));
        }
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver resolver = build(newResolver().maxQueriesPerResolve(8));

        List<DnsHttpsAddress> addresses = resolve(resolver, query("example.com"));

        // HTTPS, A and AAAA for example.com, A and AAAA for t1 and t2, and only A for t3 when the budget runs out: the
        // targets are resolved in priority order, the others are not reached, like the existing resolver the result is
        // what was obtained.
        assertThat(server.receivedTypes()).hasSize(8);
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.1", 443, "http/1.1"),
                endpoint(2, "198.51.100.2", 443, "http/1.1"), endpoint(3, "198.51.100.3", 443, "http/1.1"),
                host("192.0.2.1"));
    }

    // Caching

    @Test
    public void testAddressesCachedInResolveCache() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));
        int queries = server.receivedTypes().size();
        List<InetAddress> resolveAll = resolver.resolveAll("example.com").get(10, TimeUnit.SECONDS);
        List<DnsHttpsAddress> fromCache = resolve(query("example.com"));

        // The addresses are those resolveAll uses, the HTTPS records are cached too.
        assertThat(resolveAll).containsExactly(
                InetAddress.getByName("192.0.2.1"), InetAddress.getByName("2001:db8::1"));
        assertThat(fromCache).isEqualTo(addresses);
        assertThat(server.receivedTypes()).hasSize(queries);
    }

    @Test
    public void testAddressesComeFromTheSharedResolveCache() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(aaaa("svc.example.net.", "2001:db8::7"));
        DnsCache resolveCache = new DefaultDnsCache();
        DnsNameResolver first = build(newResolver().resolveCache(resolveCache));
        DnsNameResolver second = build(newResolver().resolveCache(resolveCache));
        resolve(first, query("example.com"));
        int queries = server.receivedTypes().size();

        List<DnsHttpsAddress> addresses = resolve(second, query("example.com"));

        // The origin and target addresses go to the resolve cache of the resolver: another resolver with the same
        // resolve cache, but not the same HTTPS cache, only queries the HTTPS records.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(1, "2001:db8::7", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        assertThat(server.receivedTypesSince(queries)).containsExactly(Type.HTTPS);
    }

    @Test
    public void testCachedAddressesOfOneFamilyAreNotQueriedAgain() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        DnsCache resolveCache = new DefaultDnsCache();
        DnsNameResolver ipv4Only = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                .resolveCache(resolveCache));
        DnsNameResolver ipv4Preferred = build(newResolver().resolveCache(resolveCache));
        ipv4Only.resolveAll("example.com").get(10, TimeUnit.SECONDS);

        List<DnsHttpsAddress> addresses = resolve(ipv4Preferred, query("example.com"));
        List<InetAddress> resolveAll = ipv4Preferred.resolveAll("example.com").get(10, TimeUnit.SECONDS);

        // The cached addresses are used like DnsNameResolver does, only the HTTPS records are queried.
        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(resolveAll).containsExactly(InetAddress.getByName("192.0.2.1"));
        assertThat(server.receivedTypes()).containsExactly(Type.A, Type.HTTPS);
    }

    @Test
    public void testCachedAddressesFilteredByTheAddressTypes() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        DnsCache resolveCache = new DefaultDnsCache();
        DnsNameResolver ipv4Preferred = build(newResolver().resolveCache(resolveCache));
        DnsNameResolver ipv6Only = build(newResolver().resolvedAddressTypes(ResolvedAddressTypes.IPV6_ONLY)
                .resolveCache(resolveCache));
        resolve(ipv4Preferred, query("example.com"));
        int queries = server.receivedTypes().size();

        List<DnsHttpsAddress> addresses = resolve(ipv6Only, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "2001:db8::1", 443, "http/1.1"), host("2001:db8::1"));
        assertThat(server.receivedTypesSince(queries)).containsExactly(Type.HTTPS);
    }

    @Test
    public void testCachedHttpsRecordsAreNotQueriedAgain() throws Exception {
        // The addresses expired from the cache before the HTTPS records: only the addresses are queried.
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));
        resolve(query("example.com"));
        resolver.resolveCache().clear("example.com.");
        int queries = server.receivedTypes().size();

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(server.receivedTypesSince(queries)).containsExactlyInAnyOrder(Type.A, Type.AAAA);
    }

    @Test
    public void testTargetAddressesOnCacheHit() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(aaaa("svc.example.net.", "2001:db8::7"));
        resolve(query("example.com"));
        server.clear();

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // Both the HTTPS result and the target addresses come from the caches.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(1, "2001:db8::7", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testAliasChainFromCache() throws Exception {
        server.add(https("example.com.", 0, "svc.example.net."));
        server.add(https("svc.example.net.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        resolve(query("example.com"));
        server.clear();

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        // The ServiceMode endpoint of the alias target, then the alias endpoint.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testHttpsFailureNotCachedWithAddresses() throws Exception {
        server.fail(Type.HTTPS, Rcode.SERVFAIL);
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        List<DnsHttpsAddress> first = resolve(query("example.com"));
        server.clear();
        server.add(https("example.com.", 1, "."));

        List<DnsHttpsAddress> second = resolve(query("example.com"));

        // The addresses of the first resolution are cached, but the missing HTTPS records are queried again.
        assertThat(first).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
        assertThat(second).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testAbsenceOfHttpsRecordsIsCachedWithSoaTtl() throws Exception {
        // RFC 2308, Section 5: the absence of records is cached for the minimum of the SOA TTL and SOA MINIMUM.
        server.add(a("example.com.", 300, "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.addSoa("example.com.", 600, 60);
        server.add(a("example.net.", "192.0.2.1"));
        server.add(aaaa("example.net.", "2001:db8::1"));
        server.addSoa("example.net.", 30, 60);

        long before = System.nanoTime();
        List<DnsHttpsAddress> com = resolve(query("example.com"));
        List<DnsHttpsAddress> net = resolve(query("example.net"));
        int queries = server.receivedTypes().size();
        List<DnsHttpsAddress> fromCache = resolve(query("example.com"));
        long after = System.nanoTime();

        // The addresses of the host are used as long as the absence of HTTPS records is cached, which bounds their
        // TTL below the one of their own records.
        assertThat(com).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
        assertTtl(com.get(0), 60, before, after);
        assertTtl(net.get(0), 30, before, after);
        assertThat(fromCache).isEqualTo(com);
        assertTtl(fromCache.get(0), 60, before, after);
        assertThat(server.receivedTypes()).hasSize(queries);
    }

    @Test
    public void testAbsenceOfHttpsRecordsWithoutSoaIsNotCached() throws Exception {
        // RFC 2308, Section 5: negative responses without SOA records SHOULD NOT be cached. Only the HTTPS records are
        // queried again, the addresses are still cached.
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));

        resolve(query("example.com"));
        int queries = server.receivedTypes().size();
        resolve(query("example.com"));

        assertThat(server.receivedTypesSince(queries)).containsExactly(Type.HTTPS);
    }

    @Test
    public void testNegativeCaching() throws Exception {
        DnsNameResolver negativeCaching = build(newResolver().negativeTtl(30));
        Throwable first = failure(negativeCaching, query("example.com"));
        int queries = server.receivedTypes().size();
        server.add(https("example.com.", 1, ".", ipv4Hint("192.0.2.9")));

        Throwable second = failure(negativeCaching, query("example.com"));

        assertThat(first).isExactlyInstanceOf(UnknownHostException.class);
        assertThat(second).isSameAs(first);
        assertThat(server.receivedTypes()).hasSize(queries);
    }

    @Test
    public void testNoNegativeCachingByDefault() throws Exception {
        Throwable first = failure(resolver, query("example.com"));
        server.add(https("example.com.", 1, ".", ipv4Hint("192.0.2.9")));

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(first).isExactlyInstanceOf(UnknownHostException.class);
        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
    }

    @Test
    public void testNegativeTtlAppliesToTheHttpsCacheWithACustomResolveCache() throws Exception {
        // No record at all: the resolution fails, and the failure of the HTTPS records is cached for the negative TTL,
        // while the custom resolve cache does not cache the failure of the addresses.
        DnsNameResolver negativeTtl = build(newResolver().resolveCache(new DefaultDnsCache()).negativeTtl(600));
        Throwable first = failure(negativeTtl, query("example.com"));
        int queries = server.receivedTypes().size();

        Throwable second = failure(negativeTtl, query("example.com"));

        assertThat(first).isInstanceOf(UnknownHostException.class);
        assertThat(second).isInstanceOf(UnknownHostException.class);
        assertThat(server.receivedTypesSince(queries)).containsExactlyInAnyOrder(Type.A, Type.AAAA);
    }

    @Test
    public void testServiceUnavailableNotCached() throws Exception {
        server.add(https("example.com.", 0, "."));
        server.add(a("example.com.", "192.0.2.1"));
        DnsNameResolver negativeCaching = build(newResolver().negativeTtl(30));
        List<DnsHttpsAddress> first = resolve(negativeCaching, query("example.com"));
        server.clear();
        server.add(https("example.com.", 1, "."));

        List<DnsHttpsAddress> second = resolve(negativeCaching, query("example.com"));

        assertThat(first).containsExactly(host("192.0.2.1"));
        assertThat(second).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
    }

    @Test
    public void testMaxTtlZeroDisablesTheHttpsCache() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        // The TTLs also apply to the CNAME and authoritative caches, which do not accept a maximum TTL of 0.
        DnsNameResolver noCaching = build(newResolver().cnameCache(NoopDnsCnameCache.INSTANCE)
                .authoritativeDnsServerCache(NoopAuthoritativeDnsServerCache.INSTANCE).ttl(0, 0));
        resolve(noCaching, query("example.com"));
        int queries = server.receivedTypes().size();

        List<DnsHttpsAddress> addresses = resolve(noCaching, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(server.receivedTypesSince(queries)).contains(Type.HTTPS);
    }

    @Test
    public void testResultsWithAdditionalsAreNotCached() throws Exception {
        // The answers to queries with additionals, e.g. an ECS option, may depend on them: like the resolve cache,
        // the HTTPS cache neither answers nor keeps them.
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        List<DnsRecord> additionals = Collections.<DnsRecord>singletonList(
                new DefaultDnsOptEcsRecord(1024, 24, new byte[] { (byte) 192, 0, 2, 0 }));
        resolver.resolveAll(query("example.com"), additionals).get(10, TimeUnit.SECONDS);
        int queries = server.receivedTypes().size();
        List<DnsHttpsAddress> withAdditionals =
                resolver.resolveAll(query("example.com"), additionals).get(10, TimeUnit.SECONDS);
        int queriesWithAdditionals = server.receivedTypes().size();
        resolve(query("example.com"));
        int queriesWithout = server.receivedTypes().size();

        List<DnsHttpsAddress> cached = resolver.resolveAll(query("example.com"), additionals)
                .get(10, TimeUnit.SECONDS);

        assertThat(withAdditionals).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
        assertThat(server.receivedTypes().subList(queries, queriesWithAdditionals)).contains(Type.HTTPS);
        // Without additionals, the result was not cached by the resolutions with additionals.
        assertThat(server.receivedTypes().subList(queriesWithAdditionals, queriesWithout)).contains(Type.HTTPS);
        // With additionals, the result cached without them is not used.
        assertThat(cached).isEqualTo(withAdditionals);
        assertThat(server.receivedTypesSince(queriesWithout)).contains(Type.HTTPS);
    }

    // Parallel queries

    @Test
    public void testHttpsAndAddressQueriesSentInParallel() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        // The server answers nothing until it has received the HTTPS, A and AAAA queries.
        server.holdUntil(3);

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        assertThat(server.receivedTypes()).containsExactlyInAnyOrder(Type.HTTPS, Type.A, Type.AAAA);
    }

    @Test
    public void testParallelHttpsNoDataWithAddresses() throws Exception {
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.holdUntil(3);

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testParallelAliasModeWithAddresses() throws Exception {
        server.add(https("example.com.", 0, "svc.example.net."));
        server.add(https("svc.example.net.", 1, ".", port(8443)));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        // The HTTPS, A and AAAA queries for example.com. are answered together; the follow-up query for
        // svc.example.net. comes after.
        server.holdUntil(3);

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 8443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        // The parallel queries, the follow-up HTTPS query for svc.example.net., then its A/AAAA lookup.
        assertThat(server.receivedTypes()).containsExactlyInAnyOrder(
                Type.HTTPS, Type.A, Type.AAAA, Type.HTTPS, Type.A, Type.AAAA);
    }

    @Test
    public void testParallelQueriesFollowTheOriginCname() throws Exception {
        server.add(cname("example.com.", "cdn.example.net."));
        server.add(https("cdn.example.net.", 1, "."));
        server.add(a("cdn.example.net.", "192.0.2.1"));
        server.add(aaaa("cdn.example.net.", "2001:db8::1"));
        server.holdUntil(3);

        List<DnsHttpsAddress> addresses = resolve(query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
    }

    // Failures

    @Test
    public void testHttpsTimeoutWithAddresses() throws Exception {
        server.drop(Type.HTTPS);
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        DnsNameResolver fastResolver = build(newResolver().queryTimeoutMillis(500));

        List<DnsHttpsAddress> addresses = resolve(fastResolver, query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testHttpsRetriedOnNextServerWhileAddressesAnswered() throws Exception {
        // The first server answers the addresses but not HTTPS; the second one answers everything.
        server.drop(Type.HTTPS);
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        DnsJavaTestServer second = new DnsJavaTestServer(group);
        try {
            second.add(https("example.com.", 1, ".", port(8443)));
            DnsNameResolver twoServers = build(newResolver()
                    .nameServerProvider(new SequentialDnsServerAddressStreamProvider(
                            server.localAddress(), second.localAddress()))
                    .queryTimeoutMillis(500));

            List<DnsHttpsAddress> addresses = resolve(twoServers, query("example.com"));

            assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 8443, "http/1.1"),
                    endpoint(1, "2001:db8::1", 8443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
            assertThat(second.receivedTypes()).contains(Type.HTTPS);
        } finally {
            second.close();
        }
    }

    @Test
    public void testAllQueriesFail() throws Exception {
        server.fail(Type.HTTPS, Rcode.SERVFAIL);
        server.fail(Type.A, Rcode.SERVFAIL);
        server.fail(Type.AAAA, Rcode.SERVFAIL);

        Throwable cause = failure(resolver, query("example.com"));

        assertThat(cause).isInstanceOf(UnknownHostException.class);
    }

    // Early completion: the resolution completes once it has the HTTPS part and an address of the family preferred
    // by the address types; the other family is not waited for.

    @Test
    public void testCompleteEarlyWithoutWaitingForTheOtherAddressFamily() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.hold("example.com.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"));
        server.release();
    }

    @Test
    public void testCompleteEarlyWaitsForTheHttpsAnswer() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.hold("example.com.", Type.HTTPS);
        server.hold("example.com.", Type.AAAA);
        CountDownLatch addressDone = new CountDownLatch(1);
        DnsNameResolver observedResolver = newObservedResolver(addressDone, DnsRecordType.A);

        Future<List<DnsHttpsAddress>> future = observedResolver.resolveAll(query("example.com"));

        assertThat(addressDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(isDone(future)).isFalse();
        server.release();
        assertThat(future.get(10, TimeUnit.SECONDS)).containsExactly(
                endpoint(1, "192.0.2.1", 443, "http/1.1"), host("192.0.2.1"));
    }

    @Test
    public void testCompleteEarlyWaitsForThePreferredAddressFamily() throws Exception {
        server.add(https("example.com.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.hold("example.com.", Type.A);
        CountDownLatch done = new CountDownLatch(2);
        DnsNameResolver observedResolver = newObservedResolver(done, DnsRecordType.HTTPS, DnsRecordType.AAAA);

        Future<List<DnsHttpsAddress>> future = observedResolver.resolveAll(query("example.com"));

        // The HTTPS answer and the other family are in, but not the preferred family.
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(isDone(future)).isFalse();
        server.release();
        assertThat(future.get(10, TimeUnit.SECONDS)).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
    }

    @Test
    public void testCompleteEarlyWithoutHttpsRecords() throws Exception {
        // An answer without HTTPS records completes the HTTPS part.
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.hold("example.com.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"));
        server.release();
    }

    @Test
    public void testCompleteEarlyWaitsForTheAliasTarget() throws Exception {
        server.add(https("example.com.", 0, "svc.example.net."));
        server.add(https("svc.example.net.", 1, "."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.hold("example.com.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        // Completing on the AliasMode record would give the alias endpoint only, without the ServiceMode endpoint.
        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                endpoint(65535, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"));
        server.release();
    }

    @Test
    public void testCompleteEarlyWhenTheHttpsQueryFails() throws Exception {
        // Without the HTTPS part, the resolution is a resolution of the addresses: it completes once it has the
        // preferred family, without waiting for the other one.
        server.fail(Type.HTTPS, Rcode.SERVFAIL);
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.hold("example.com.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(host("192.0.2.1"));
        server.release();
    }

    @Test
    public void testCompleteEarlyUsesTheOriginAddressesOverHints() throws Exception {
        // RFC 9460, Section 7.3: the A records of the target are available, so its hints are ignored. The AAAA
        // answer is not waited for, like for the addresses of the host, and its hints are not used in its place.
        server.add(https("example.com.", 1, ".", alpn("h3"), port(4044), ipv4Hint("192.0.2.9"),
                ipv6Hint("2001:db8::9")));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.hold("example.com.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 4044, "h3", "http/1.1"), host("192.0.2.1"));
        server.release();
    }

    @Test
    public void testCompleteEarlyUsesTheHintsOfThePreferredFamily() throws Exception {
        // RFC 9460, Section 7.3: hints minimize connection latency. The target is still resolved so that its
        // addresses are cached for future connections.
        server.add(https("example.com.", 1, "svc.example.net.", ipv4Hint("192.0.2.9")));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.hold("example.com.", Type.AAAA);
        server.hold("svc.example.net.", Type.A);
        final CountDownLatch targetCached = new CountDownLatch(1);
        DnsCache resolveCache = new DefaultDnsCache() {
            @Override
            public DnsCacheEntry cache(String hostname, DnsRecord[] additionals, InetAddress address,
                                       long originalTtl, EventLoop loop) {
                if (hostname.startsWith("svc.example.net")) {
                    targetCached.countDown();
                }
                return super.cache(hostname, additionals, address, originalTtl, loop);
            }
        };
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver().resolveCache(resolveCache));

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"), host("192.0.2.1"));
        server.release();
        assertThat(targetCached.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testCompleteEarlyWaitsForATargetWithoutHintOfThePreferredFamily() throws Exception {
        server.add(https("example.com.", 1, "svc.example.net.", ipv6Hint("2001:db8::9")));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.hold("example.com.", Type.AAAA);
        server.hold("svc.example.net.", Type.A);
        CountDownLatch done = new CountDownLatch(2);
        DnsNameResolver observedResolver = newObservedResolver(done, DnsRecordType.HTTPS, DnsRecordType.A);

        Future<List<DnsHttpsAddress>> future = observedResolver.resolveAll(query("example.com"));

        // The HTTPS answer and the preferred family of the origin are in, but the target is still resolved.
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(isDone(future)).isFalse();
        server.release();
        assertThat(future.get(10, TimeUnit.SECONDS)).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"),
                host("192.0.2.1"));
    }

    @Test
    public void testCompleteEarlyResolvesTargetsLikeResolveAll() throws Exception {
        // The target is resolved like resolveAll(String) with completeOncePreferredResolved: only its preferred
        // family is waited for.
        server.add(https("example.com.", 1, "svc.example.net."));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(a("svc.example.net.", "198.51.100.7"));
        server.add(aaaa("svc.example.net.", "2001:db8::7"));
        server.hold("svc.example.net.", Type.AAAA);
        DnsNameResolver completeEarly = build(newCompleteEarlyResolver());

        List<DnsHttpsAddress> addresses = resolve(completeEarly, query("example.com"));

        assertThat(addresses).containsExactly(endpoint(1, "198.51.100.7", 443, "http/1.1"), host("192.0.2.1"));
        server.release();
    }

    // Search domains

    @Test
    public void testSearchDomainUsesCache() throws Exception {
        server.add(https("svc.example.com.", 1, "."));
        server.add(a("svc.example.com.", "192.0.2.1"));
        server.add(aaaa("svc.example.com.", "2001:db8::1"));
        DnsNameResolver searchResolver = newSearchDomainResolver();
        List<DnsHttpsAddress> resolved = resolve(searchResolver, query("svc"));
        server.clear();

        List<DnsHttpsAddress> cached = resolve(searchResolver, query("svc"));

        assertThat(resolved).containsExactly(endpoint(1, "192.0.2.1", 443, "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "http/1.1"), host("192.0.2.1"), host("2001:db8::1"));
        assertThat(cached).isEqualTo(resolved);
    }

    @Test
    public void testSearchDomainServiceUnavailable() throws Exception {
        // The search domain attempt "svc.example.com." finds an AliasMode record to ".", and the resolution should
        // report it rather than trying "svc." next, whose addresses would be used otherwise.
        server.add(https("svc.example.com.", 0, "."));
        server.add(a("svc.", "192.0.2.1"));

        Throwable cause = failure(newSearchDomainResolver(), query("svc"));

        assertThat(cause).isInstanceOf(UnknownHostException.class);
        assertThat(server.receivedNames()).doesNotContain("svc.");
    }

    @Test
    public void testSearchDomainMalformedRecord() throws Exception {
        server.addRaw("svc.example.com.", Type.HTTPS, bytes(0, 1, 0, 0, 3, 0, 1, 1));

        Throwable cause = failure(newSearchDomainResolver(), query("svc"));

        assertThat(cause).isInstanceOf(UnknownHostException.class).hasCauseInstanceOf(CorruptedFrameException.class);
    }

    @Test
    public void testSearchDomainCacheUsedLikeResolveAll() throws Exception {
        server.add(a("svc.sub.example.com.", "192.0.2.1"));
        // The absence of HTTPS records is cached too.
        server.addSoa("example.com.", 300, 300);
        DnsNameResolver searchResolver = newSearchDomainResolver();
        resolve(searchResolver, query("svc.sub"));
        int queries = server.receivedTypes().size();

        // "svc.sub" has ndots dots, so it would be queried as is first: the cached search domain permutation
        // answers instead, as it does for resolveAll.
        List<DnsHttpsAddress> addresses = resolve(searchResolver, query("svc.sub"));
        List<InetAddress> resolveAll = searchResolver.resolveAll("svc.sub").get(10, TimeUnit.SECONDS);

        assertThat(addresses).containsExactly(host("192.0.2.1"));
        assertThat(resolveAll).containsExactly(InetAddress.getByName("192.0.2.1"));
        assertThat(server.receivedTypes()).hasSize(queries);
    }

    @Test
    public void testSearchDomainResultsComeFromOnePermutation() throws Exception {
        // Each search domain permutation queries all the types in parallel, and the first one with records wins, like
        // resolveAll: the endpoints and the addresses are never mixed from different names.
        server.add(https("svc.example.com.", 1, ".", ipv4Hint("192.0.2.9")));
        server.add(a("svc.example.net.", "192.0.2.1"));
        server.add(aaaa("svc.example.net.", "2001:db8::1"));
        server.add(a("web.example.com.", "192.0.2.7"));
        server.add(https("web.example.net.", 1, "."));
        DnsNameResolver searchResolver = build(newResolver()
                .searchDomains(Arrays.asList("example.com", "example.net"))
                .ndots(1));

        List<DnsHttpsAddress> svc = resolve(searchResolver, query("svc"));
        List<DnsHttpsAddress> web = resolve(searchResolver, query("web"));

        assertThat(svc).containsExactly(endpoint(1, "192.0.2.9", 443, "http/1.1"));
        assertThat(web).containsExactly(host("192.0.2.7"));
        assertThat(server.receivedNames()).contains("svc.example.com.", "web.example.com.")
                .doesNotContain("svc.example.net.", "web.example.net.");
    }

    private DnsNameResolver newSearchDomainResolver() {
        return build(newResolver().searchDomains(Collections.singletonList("example.com")).ndots(1));
    }

    /**
     * Returns a resolver builder completing once the preferred address family is resolved, with a query timeout long
     * enough for the held queries not to time out before the resolution is awaited.
     */
    private DnsNameResolverBuilder newCompleteEarlyResolver() {
        return newResolver().completeOncePreferredResolved(true).queryTimeoutMillis(60000);
    }

    /**
     * Returns a resolver completing early, counting down {@code done} when a query of one of the {@code types}
     * completes.
     */
    private DnsNameResolver newObservedResolver(final CountDownLatch done, DnsRecordType... types) {
        final List<DnsRecordType> observed = Arrays.asList(types);
        final CountDownLatch ignored = new CountDownLatch(0);
        return build(newCompleteEarlyResolver().dnsQueryLifecycleObserverFactory(question ->
                new CompletionObserver(observed.contains(question.type()) ? done : ignored)));
    }

    /**
     * Returns whether {@code future} is done once the tasks already submitted to the event loop of the resolvers have
     * run, so that a completion triggered by an answer already processed is visible.
     */
    private boolean isDone(Future<?> future) throws Exception {
        group.next().submit(() -> { }).sync();
        return future.isDone();
    }

    /**
     * Counts down a latch when the query completes.
     */
    private static final class CompletionObserver implements DnsQueryLifecycleObserver {
        private final CountDownLatch done;

        CompletionObserver(CountDownLatch done) {
            this.done = done;
        }

        @Override
        public void queryWritten(InetSocketAddress dnsServerAddress, ChannelFuture future) {
        }

        @Override
        public void queryCancelled(int queriesRemaining) {
            done.countDown();
        }

        @Override
        public DnsQueryLifecycleObserver queryRedirected(List<InetSocketAddress> nameServers) {
            return this;
        }

        @Override
        public DnsQueryLifecycleObserver queryCNAMEd(DnsQuestion cnameQuestion) {
            return this;
        }

        @Override
        public DnsQueryLifecycleObserver queryNoAnswer(DnsResponseCode code) {
            return this;
        }

        @Override
        public void queryFailed(Throwable cause) {
            done.countDown();
        }

        @Override
        public void querySucceed() {
            done.countDown();
        }
    }
}
