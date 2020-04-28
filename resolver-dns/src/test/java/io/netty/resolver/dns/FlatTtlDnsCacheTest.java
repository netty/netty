/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import org.junit.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class FlatTtlDnsCacheTest {

    @Test
    public void testTtlOverride() throws Throwable {
        final InetAddress addr1 = InetAddress.getByAddress(new byte[] { 10, 0, 0, 1 });
        final InetAddress addr2 = InetAddress.getByAddress(new byte[] { 10, 0, 0, 2 });
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final FlatTtlDnsCache cache = new FlatTtlDnsCache(2, 10);
            cache.cache("netty.io", null, addr1, 1, loop);
            cache.cache("netty.io", null, addr2, 10000, loop);

            Throwable error = loop.schedule(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        List<? extends DnsCacheEntry> entries = cache.get("netty.io", null);
                        assertEquals(2, entries.size());
                        assertEntry(entries.get(0), addr1);
                        assertEntry(entries.get(1), addr2);
                        return null;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            }, 1, TimeUnit.SECONDS).get();
            if (error != null) {
                throw error;
            }

            error = loop.schedule(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        assertNull(cache.get("netty.io", null));
                        return null;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            }, 1, TimeUnit.SECONDS).get();
            if (error != null) {
                throw error;
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void assertEntry(DnsCacheEntry entry, InetAddress address) {
        assertEquals(address, entry.address());
        assertNull(entry.cause());
    }

    @Test
    public void testCacheFailedNoCaching() throws Exception {
        InetAddress addr1 = InetAddress.getByAddress(new byte[] { 10, 0, 0, 1 });
        InetAddress addr2 = InetAddress.getByAddress(new byte[] { 10, 0, 0, 2 });
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final FlatTtlDnsCache cache = new FlatTtlDnsCache(2, 0);
            cache.cache("netty.io", null, addr1, 10000, loop);
            cache.cache("netty.io", null, addr2, 10000, loop);

            List<? extends DnsCacheEntry> entries = cache.get("netty.io", null);
            assertEquals(2, entries.size());
            assertEntry(entries.get(0), addr1);
            assertEntry(entries.get(1), addr2);

            cache.cache("netty.io", null, new Exception(), loop);

            entries = cache.get("netty.io", null);
            assertEquals(2, entries.size());
            assertEntry(entries.get(0), addr1);
            assertEntry(entries.get(1), addr2);
        } finally {
            group.shutdownGracefully();
        }
    }

}
