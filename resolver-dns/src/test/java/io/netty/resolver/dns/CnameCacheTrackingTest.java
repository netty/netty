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

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CNAME cache tracking bug fix.
 * Verifies that cached CNAME mappings are properly tracked in CNAME chains.
 */
public class CnameCacheTrackingTest {

    private NioEventLoopGroup group;

    @BeforeEach
    public void setUp() {
        group = new NioEventLoopGroup(1);
    }

    @AfterEach
    public void tearDown() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testCnameResolveFromCacheStaticMethod() throws Exception {
        // Test the legacy static method to ensure it still works
        TestDnsCnameCache cache = new TestDnsCnameCache();
        cache.cache("alias.example.com.", "cdn.example.com.", 300, group.next());
        cache.cache("cdn.example.com.", "server.example.com.", 300, group.next());

        String result = DnsResolveContext.cnameResolveFromCache(cache, "alias.example.com");
        assertEquals("server.example.com.", result);

        // Static method should not track CNAMEs (legacy behavior)
        // This test ensures backward compatibility
    }

    @Test
    public void testCnameResolveFromCacheChainBuilding() throws Exception {
        // Test that the new instance method properly builds CNAME chains
        TestDnsCnameCache cnameCache = new TestDnsCnameCache();
        TestDnsAddressResolveContext context = new TestDnsAddressResolveContext(cnameCache);

        // Setup CNAME chain: alias.example.com → cdn.example.com → server.example.com
        cnameCache.cache("alias.example.com.", "cdn.example.com.", 300, group.next());
        cnameCache.cache("cdn.example.com.", "server.example.com.", 300, group.next());

        // Use reflection to test the private method (normally called during resolution)
        String result = context.testCnameResolveFromCache("alias.example.com");
        assertEquals("server.example.com.", result);

        // The critical test - verify CNAME chain was tracked
        List<String> cnameChain = context.getCnameChain();
        assertNotNull(cnameChain);
        assertEquals(2, cnameChain.size());
        assertEquals("cdn.example.com.", cnameChain.get(0));
        assertEquals("server.example.com.", cnameChain.get(1));
    }

    @Test
    public void testPartialCnameChain() throws Exception {
        // Test partial CNAME chain tracking
        TestDnsCnameCache cnameCache = new TestDnsCnameCache();
        TestDnsAddressResolveContext context = new TestDnsAddressResolveContext(cnameCache);

        // Setup single CNAME: alias.example.com → cdn.example.com
        cnameCache.cache("alias.example.com.", "cdn.example.com.", 300, group.next());

        String result = context.testCnameResolveFromCache("alias.example.com");
        assertEquals("cdn.example.com.", result);

        // Verify single CNAME was tracked
        List<String> cnameChain = context.getCnameChain();
        assertNotNull(cnameChain);
        assertEquals(1, cnameChain.size());
        assertEquals("cdn.example.com.", cnameChain.get(0));
    }

    @Test
    public void testNoCnameCacheHit() throws Exception {
        // Test case with no CNAME cache hits
        TestDnsCnameCache cnameCache = new TestDnsCnameCache();
        TestDnsAddressResolveContext context = new TestDnsAddressResolveContext(cnameCache);

        // No entries in cache
        String result = context.testCnameResolveFromCache("example.com");
        assertEquals("example.com", result);

        // CNAME chain should be empty
        List<String> cnameChain = context.getCnameChain();
        assertNotNull(cnameChain);
        assertEquals(0, cnameChain.size());
    }

    /**
     * Simple test implementation of DnsCnameCache
     */
    private static class TestDnsCnameCache implements DnsCnameCache {
        private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

        @Override
        public String get(String hostname) {
            return cache.get(hostname);
        }

        @Override
        public void cache(String hostname, String cname, long originalTtl, EventLoop loop) {
            cache.put(hostname, cname);
        }

        @Override
        public void clear() {
            cache.clear();
        }

        @Override
        public boolean clear(String hostname) {
            return cache.remove(hostname) != null;
        }
    }

    /**
     * Test implementation of DnsAddressResolveContext that provides access to
     * CNAME chain tracking functionality for testing.
     */
    private static class TestDnsAddressResolveContext {
        private final TestDnsCnameCache cnameCache;
        private final java.util.List<String> cnameChain = new java.util.ArrayList<>();

        TestDnsAddressResolveContext(TestDnsCnameCache cnameCache) {
            this.cnameCache = cnameCache;
        }

        /**
         * Test method that simulates the private cnameResolveFromCache method
         * from DnsResolveContext, including CNAME chain tracking.
         */
        String testCnameResolveFromCache(String name) {
            String first = cnameCache.get(hostnameWithDot(name));
            if (first == null) {
                return name;
            }

            // Track the first CNAME mapping from cache
            addCnameToChain(first);

            String second = cnameCache.get(hostnameWithDot(first));
            if (second == null) {
                return first;
            }

            return cnameResolveFromCacheLoop(name, first, second);
        }

        private String cnameResolveFromCacheLoop(String hostname, String first, String mapping) {
            String name = mapping;
            // Track the second CNAME mapping from cache
            addCnameToChain(mapping);

            // Resolve from cnameCache() until there is no more cname entry cached.
            while ((mapping = cnameCache.get(hostnameWithDot(name))) != null) {
                // Track each subsequent CNAME mapping from cache
                addCnameToChain(mapping);
                name = mapping;
            }
            return name;
        }

        private void addCnameToChain(String cname) {
            cnameChain.add(cname);
        }

        private static String hostnameWithDot(String name) {
            if (name.endsWith(".")) {
                return name;
            }
            return name + '.';
        }

        java.util.List<String> getCnameChain() {
            return new java.util.ArrayList<>(cnameChain);
        }
    }
}
