/*
 * Copyright 2018 The Netty Project
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

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultDnsCnameCacheTest {

    @Test
    public void testExpire() throws Throwable {
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultDnsCnameCache cache = new DefaultDnsCnameCache();
            cache.cache("netty.io", "mapping.netty.io", 1, loop);

            Throwable error = loop.schedule(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        assertNull(cache.get("netty.io"));
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

    @Test
    public void testExpireWithDifferentTTLs() {
        testExpireWithTTL0(1);
        testExpireWithTTL0(1000);
        testExpireWithTTL0(1000000);
    }

    private static void testExpireWithTTL0(int days) {
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultDnsCnameCache cache = new DefaultDnsCnameCache();
            cache.cache("netty.io", "mapping.netty.io", TimeUnit.DAYS.toSeconds(days), loop);
            assertEquals("mapping.netty.io", cache.get("netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testMultipleCnamesForSameHostname() throws Exception {
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultDnsCnameCache cache = new DefaultDnsCnameCache();
            cache.cache("netty.io", "mapping1.netty.io", 10, loop);
            cache.cache("netty.io", "mapping2.netty.io", 10000, loop);

            assertEquals("mapping2.netty.io", cache.get("netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testAddSameCnameForSameHostname() throws Exception {
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultDnsCnameCache cache = new DefaultDnsCnameCache();
            cache.cache("netty.io", "mapping.netty.io", 10, loop);
            cache.cache("netty.io", "mapping.netty.io", 10000, loop);

            assertEquals("mapping.netty.io", cache.get("netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testClear() throws Exception {
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultDnsCnameCache cache = new DefaultDnsCnameCache();
            cache.cache("x.netty.io", "mapping.netty.io", 100000, loop);
            cache.cache("y.netty.io", "mapping.netty.io", 100000, loop);

            assertEquals("mapping.netty.io", cache.get("x.netty.io"));
            assertEquals("mapping.netty.io", cache.get("y.netty.io"));

            assertTrue(cache.clear("x.netty.io"));
            assertNull(cache.get("x.netty.io"));
            assertEquals("mapping.netty.io", cache.get("y.netty.io"));
            cache.clear();
            assertNull(cache.get("y.netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }
}
