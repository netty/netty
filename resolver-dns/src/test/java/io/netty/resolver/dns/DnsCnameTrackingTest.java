/*
 * Copyright 2025 The Netty Project
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

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CNAME chain tracking functionality in DNS resolution.
 */
public class DnsCnameTrackingTest {

    private NioEventLoopGroup group;
    private DnsNameResolver resolver;

    @BeforeEach
    public void setUp() {
        group = new NioEventLoopGroup(1);
        resolver = new DnsNameResolverBuilder(group.next())
                .channelType(NioDatagramChannel.class)
                .build();
    }

    @AfterEach
    public void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testResolveAllWithCnamesBasic() throws Exception {
        // Test CNAME-aware resolution for all addresses
        Future<List<DnsResolveResult>> future = resolver.resolveAllWithCnames("fairplay-pdc.amp.apple.com");
        List<DnsResolveResult> results = future.sync().getNow();

        assertNotNull(results);
        assertFalse(results.isEmpty());

        for (DnsResolveResult result : results) {
            assertNotNull(result.address());
            assertNotNull(result.cnameChain());
            System.out.println("Resolved address: " + result.address() +
                             ", CNAME chain: " + result.cnameChain());

            // This hostname should have CNAME indirection
            assertTrue(result.hasCnameIndirection(), "Expected CNAME indirection");
            assertFalse(result.cnameChain().isEmpty(), "CNAME chain should not be empty");
        }
    }

    @Test
    public void testBackwardCompatibility() throws Exception {
        // Ensure existing APIs still work unchanged
        Future<InetAddress> future = resolver.resolve("fairplay-pdc.amp.apple.com");
        InetAddress address = future.sync().getNow();

        assertNotNull(address);
        System.out.println("Traditional resolve: " + address);
    }

    @Test
    public void testCacheIntegration() throws Exception {
        // Test that CNAME chains are properly cached and retrieved

        // First resolution - should hit DNS server
        Future<List<DnsResolveResult>> future1 = resolver.resolveAllWithCnames("fairplay-pdc.amp.apple.com");
        DnsResolveResult result1 = future1.sync().getNow().get(0);

        // Verify first resolution has CNAME data
        assertTrue(result1.hasCnameIndirection(), "Expected CNAME indirection");
        assertFalse(result1.cnameChain().isEmpty(), "CNAME chain should not be empty");

        // Second resolution - should hit cache
        Future<List<DnsResolveResult>> future2 = resolver.resolveAllWithCnames("fairplay-pdc.amp.apple.com");
        DnsResolveResult result2 = future2.sync().getNow().get(0);

        // Both should have same address and CNAME chain
        assertEquals(result1.address(), result2.address());
        assertEquals(result1.cnameChain(), result2.cnameChain());

        // Verify cached result also has CNAME data
        assertTrue(result2.hasCnameIndirection(), "Cached result should have CNAME indirection");
        assertFalse(result2.cnameChain().isEmpty(), "Cached CNAME chain should not be empty");

        System.out.println("Cache test - Address: " + result1.address() +
                         ", CNAME chain: " + result1.cnameChain());
    }

    @Test
    public void testDnsResolveResultEquality() throws Exception {
        // Test DnsResolveResult equality and behavior
        DnsResolveResult result1 = new DnsResolveResult(
            java.net.InetAddress.getByName("127.0.0.1"),
            java.util.Arrays.asList("alias1.example.com", "alias2.example.com")
        );

        DnsResolveResult result2 = new DnsResolveResult(
            java.net.InetAddress.getByName("127.0.0.1"),
            java.util.Arrays.asList("alias1.example.com", "alias2.example.com")
        );

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertTrue(result1.hasCnameIndirection());

        // Test empty CNAME chain
        DnsResolveResult result3 = new DnsResolveResult(
            java.net.InetAddress.getByName("127.0.0.1"),
            java.util.Collections.<String>emptyList()
        );
        
        assertFalse(result3.hasCnameIndirection());
        assertEquals(0, result3.cnameChain().size());
    }

    @Test
    public void testDirectAddressLookup() throws Exception {
        // Test with a hostname that has direct A record (no CNAME)
        Future<List<DnsResolveResult>> future = resolver.resolveAllWithCnames("dns.google");
        DnsResolveResult result = future.sync().getNow().get(0);

        assertNotNull(result);
        assertNotNull(result.address());
        assertNotNull(result.cnameChain());

        System.out.println("Direct resolution - Address: " + result.address() +
                         ", CNAME chain: " + result.cnameChain());
        System.out.println("Has CNAME indirection: " + result.hasCnameIndirection());

        // This should be a direct A record lookup, no CNAME indirection expected
        assertFalse(result.hasCnameIndirection(), "dns.google should not have CNAME indirection");
        assertTrue(result.cnameChain().isEmpty(), "CNAME chain should be empty for direct A record");
    }
}
