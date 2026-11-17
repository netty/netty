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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use cases of {@link DnsNameResolver#resolveAll(DnsHttpsQuery)}, written from the point of view of a client using the
 * result.
 */
public class DnsHttpsUseCasesTest extends AbstractDnsHttpsTest {

    // An "http" URL (RFC 9460, Section 9.5): the resolver resolves it as "https" (an explicit port 80 becomes 443),
    // with the addresses of the host too. The client then:
    // - upgrades to "https" when an address is resolved through HTTPS records (an AliasMode record or a compatible
    //   ServiceMode record), as if it had received a 307 redirect, and connects to those addresses;
    // - otherwise connects with "http" to the addresses of the host, on the port of the URL. The addresses do not
    //   depend on the port, so a single resolution is enough.

    @Test
    public void testHttpUrlUpgradedByServiceModeRecord() throws Exception {
        server.add(https("example.com.", 1, ".", alpn("h2")));
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));

        List<DnsHttpsAddress> addresses = resolveHttpUrl();

        // Upgrade: connect with "https" on port 443, since the record has no port; otherwise connect with "http" to
        // the addresses of the host on port 80.
        assertThat(addresses).containsExactly(endpoint(1, "192.0.2.1", 443, "h2", "http/1.1"),
                endpoint(1, "2001:db8::1", 443, "h2", "http/1.1"), host("192.0.2.1", 80), host("2001:db8::1", 80));
        assertThat(addresses.get(0).isHttpsResolved()).isTrue();
        assertThat(addresses.get(2).isHttpsResolved()).isFalse();
    }

    @Test
    public void testHttpUrlUpgradedByAliasModeRecord() throws Exception {
        // Aliased to a CDN without ServiceMode record.
        server.add(https("example.com.", 0, "cdn.example.net."));
        server.add(a("cdn.example.net.", "198.51.100.7"));
        server.add(a("example.com.", "192.0.2.1"));

        List<DnsHttpsAddress> addresses = resolveHttpUrl();

        // An AliasMode record also upgrades: the alias endpoint (RFC 9460, Section 3) connects with "https" to the
        // CDN on port 443.
        assertThat(addresses).containsExactly(endpoint(65535, "198.51.100.7", 443, "http/1.1"),
                host("192.0.2.1", 80));
        assertThat(addresses.get(0).isHttpsResolved()).isTrue();
    }

    @Test
    public void testHttpUrlNotUpgradedWithoutHttpsRecords() throws Exception {
        server.add(a("example.com.", "192.0.2.1"));
        server.add(aaaa("example.com.", "2001:db8::1"));

        List<DnsHttpsAddress> addresses = resolveHttpUrl();

        // No upgrade: connect with "http" to the addresses of the host, on port 80, without another resolution.
        assertThat(addresses).containsExactly(host("192.0.2.1", 80), host("2001:db8::1", 80));
        assertThat(server.receivedTypes()).hasSize(3);
    }

    // http://example.com
    private List<DnsHttpsAddress> resolveHttpUrl() throws Exception {
        return resolve(new DnsHttpsQuery("http", "example.com", 80));
    }
}
