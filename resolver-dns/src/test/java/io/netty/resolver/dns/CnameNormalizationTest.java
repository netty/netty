/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CnameNormalizationTest {

    private EventLoopGroup group;
    private DnsNameResolver resolver;

    @BeforeEach
    void setUp() {
        group = new NioEventLoopGroup(1);
        resolver = new DnsNameResolverBuilder(group.next())
                .channelType(NioDatagramChannel.class)
                .nameServerProvider(DnsServerAddressStreamProviders.platformDefault())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    void testCnameNormalizationConsistency() throws Exception {
        // Test a hostname known to have CNAME redirects
        String hostname = "www.github.com";

        // First resolution - populates cache
        Future<List<DnsResolveResult>> future1 =
                resolver.resolveAllWithCnames(hostname);
        List<DnsResolveResult> results1 = future1.get();

        assertFalse(results1.isEmpty(), "Should have results");
        DnsResolveResult result1 = results1.get(0);
        List<String> cnameChain1 = result1.cnameChain();

        // Clear only the DnsCache but keep CNAME cache to force cache-based CNAME resolution
        resolver.resolveCache().clear();

        // Second resolution - should use cached CNAMEs
        Future<List<DnsResolveResult>> future2 =
                resolver.resolveAllWithCnames(hostname);
        List<DnsResolveResult> results2 = future2.get();

        assertFalse(results2.isEmpty(), "Should have results");
        DnsResolveResult result2 = results2.get(0);
        List<String> cnameChain2 = result2.cnameChain();

        // The critical test: CNAME chains should be identical regardless of source
        assertEquals(cnameChain1, cnameChain2,
                "CNAME chains should be identical regardless of DNS vs cache source. " +
                "From DNS: " + cnameChain1 + ", From cache: " + cnameChain2);

        // All CNAMEs should have consistent formatting (no trailing dots in chain)
        for (String cname : cnameChain1) {
            assertFalse(cname.endsWith("."),
                    "CNAME chain entries should not have trailing dots: " + cname);
        }

        for (String cname : cnameChain2) {
            assertFalse(cname.endsWith("."),
                    "CNAME chain entries should not have trailing dots: " + cname);
        }
    }

    @Test
    void testCnameNormalizationWithDirectDnsLookup() throws Exception {
        // Test with a CNAME that we can control more directly
        String hostname = "docs.github.com";  // Known to redirect

        Future<List<DnsResolveResult>> future =
                resolver.resolveAllWithCnames(hostname);
        List<DnsResolveResult> results = future.get();

        assertFalse(results.isEmpty(), "Should have results");
        DnsResolveResult result = results.get(0);
        List<String> cnameChain = result.cnameChain();

        if (!cnameChain.isEmpty()) {
            // Verify no trailing dots in CNAME chain
            for (String cname : cnameChain) {
                assertFalse(cname.endsWith("."),
                        "CNAME chain entries should not have trailing dots: " + cname);
            }
        }
    }
}
