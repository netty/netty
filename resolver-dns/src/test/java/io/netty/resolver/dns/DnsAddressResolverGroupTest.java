/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsAddressResolverGroupTest {
    @Test
    public void testUseConfiguredEventLoop() throws InterruptedException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();

        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        try {
            final Promise<?> promise = loop.newPromise();
            AddressResolver<?> resolver = resolverGroup.getResolver(defaultEventLoopGroup.next());
            resolver.resolve(new SocketAddress() {
                private static final long serialVersionUID = 3169703458729818468L;
            }).addListener((FutureListener<Object>) future -> {
                try {
                    assertInstanceOf(UnsupportedAddressTypeException.class, future.cause());
                    assertTrue(loop.inEventLoop());
                    promise.setSuccess(null);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            }).await();
            promise.sync();
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testSharedDNSCacheAcrossEventLoops() throws InterruptedException, ExecutionException {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(2, LocalIoHandler.newFactory());
        EventLoop eventLoop1 = defaultEventLoopGroup.next();
        EventLoop eventLoop2 = defaultEventLoopGroup.next();
        try {
            assertNotSame(eventLoop1, eventLoop2);
            final Promise<InetSocketAddress> promise1 = loop.newPromise();
            InetSocketAddressResolver resolver1 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop1);
            InetAddress address1 =
                    resolve(resolver1, InetSocketAddress.createUnresolved("netty.io", 80), promise1);
            final Promise<InetSocketAddress> promise2 = loop.newPromise();
            InetSocketAddressResolver resolver2 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop2);
            assertNotSame(resolver1, resolver2);
            InetAddress address2 =
                    resolve(resolver2, InetSocketAddress.createUnresolved("netty.io", 80), promise2);
            assertSame(address1, address2);
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testEventLoopTerminationDoesNotClearGroupSharedCache() {
        EventLoopGroup group1 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup group2 = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoop eventLoop1 = group1.next();
        EventLoop eventLoop2 = group2.next();
        final AtomicReference<DnsNameResolver> resolver1Ref = new AtomicReference<>();
        final AtomicReference<DnsNameResolver> resolver2Ref = new AtomicReference<>();

        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder) {
            @Override
            protected NameResolver<InetAddress> newNameResolver(
                    EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
                    DnsServerAddressStreamProvider nameServerProvider) throws Exception {
                DnsNameResolver resolver = (DnsNameResolver)
                        super.newNameResolver(eventLoop, channelFactory, nameServerProvider);
                if (eventLoop == eventLoop1) {
                    resolver1Ref.set(resolver);
                } else if (eventLoop == eventLoop2) {
                    resolver2Ref.set(resolver);
                }
                return resolver;
            }
        };

        try {
            AddressResolver<InetSocketAddress> resolver1 = resolverGroup.getResolver(eventLoop1);
            AddressResolver<InetSocketAddress> resolver2 = resolverGroup.getResolver(eventLoop2);
            assertNotSame(resolver1, resolver2);

            DnsCache resolveCache = resolver1Ref.get().resolveCache();
            assertSame(resolveCache, resolver2Ref.get().resolveCache());
            resolveCache.cache("netty.io", null, NetUtil.LOCALHOST, 3600, eventLoop1);

            group1.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();

            List<? extends DnsCacheEntry> entries = resolveCache.get("netty.io", null);
            assertNotNull(entries);
            assertFalse(entries.isEmpty());

            resolverGroup.close();
            entries = resolveCache.get("netty.io", null);
            assertTrue(entries == null || entries.isEmpty());
        } finally {
            resolverGroup.close();
            group1.shutdownGracefully();
            group2.shutdownGracefully();
        }
    }

    @Test
    public void testCloseDoesNotClearProvidedCaches() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsCache resolveCache = new DefaultDnsCache();
        DnsCnameCache cnameCache = new DefaultDnsCnameCache();
        AuthoritativeDnsServerCache authoritativeDnsServerCache = new DefaultAuthoritativeDnsServerCache();
        try {
            DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                    .eventLoop(loop)
                    .datagramChannelType(NioDatagramChannel.class)
                    .resolveCache(resolveCache)
                    .cnameCache(cnameCache)
                    .authoritativeDnsServerCache(authoritativeDnsServerCache);
            DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
            // Instantiate a resolver so closing the group also closes the resolver.
            resolverGroup.getResolver(loop);

            resolveCache.cache("netty.io", null, NetUtil.LOCALHOST, 3600, loop);
            cnameCache.cache("netty.io", "mapping.netty.io", 3600, loop);
            authoritativeDnsServerCache.cache(
                    "netty.io", new InetSocketAddress(NetUtil.LOCALHOST, 53), 3600, loop);
            resolverGroup.close();

            List<? extends DnsCacheEntry> entries = resolveCache.get("netty.io", null);
            assertNotNull(entries);
            assertFalse(entries.isEmpty());
            assertEquals("mapping.netty.io", cnameCache.get("netty.io"));
            assertNotNull(authoritativeDnsServerCache.get("netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testCloseClearsCachesCreatedByGroup() {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        try {
            DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                    .eventLoop(loop)
                    .datagramChannelType(NioDatagramChannel.class);
            final AtomicReference<DnsNameResolver> resolverRef = new AtomicReference<>();
            DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder) {
                @Override
                protected NameResolver<InetAddress> newNameResolver(
                        EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory,
                        DnsServerAddressStreamProvider nameServerProvider) throws Exception {
                    NameResolver<InetAddress> resolver =
                            super.newNameResolver(eventLoop, channelFactory, nameServerProvider);
                    resolverRef.set((DnsNameResolver) resolver);
                    return resolver;
                }
            };
            resolverGroup.getResolver(loop);

            DnsCache resolveCache = resolverRef.get().resolveCache();
            DnsCnameCache cnameCache = resolverRef.get().cnameCache();
            AuthoritativeDnsServerCache authoritativeDnsServerCache =
                    resolverRef.get().authoritativeDnsServerCache();
            resolveCache.cache("netty.io", null, NetUtil.LOCALHOST, 3600, loop);
            cnameCache.cache("netty.io", "mapping.netty.io", 3600, loop);
            authoritativeDnsServerCache.cache(
                    "netty.io", new InetSocketAddress(NetUtil.LOCALHOST, 53), 3600, loop);
            List<? extends DnsCacheEntry> entries = resolveCache.get("netty.io", null);
            assertNotNull(entries);
            assertFalse(entries.isEmpty());
            assertEquals("mapping.netty.io", cnameCache.get("netty.io"));
            assertNotNull(authoritativeDnsServerCache.get("netty.io"));

            resolverGroup.close();

            entries = resolveCache.get("netty.io", null);
            assertTrue(entries == null || entries.isEmpty());
            assertNull(cnameCache.get("netty.io"));
            assertNull(authoritativeDnsServerCache.get("netty.io"));
        } finally {
            group.shutdownGracefully();
        }
    }

    private InetAddress resolve(InetSocketAddressResolver resolver, SocketAddress socketAddress,
                                final Promise<InetSocketAddress> promise)
            throws InterruptedException, ExecutionException {
        resolver.resolve(socketAddress)
                .addListener((FutureListener<InetSocketAddress>) future -> {
                    try {
                        promise.setSuccess(future.get());
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                }).await();
        promise.sync();
        InetSocketAddress inetSocketAddress = promise.get();
        return inetSocketAddress.getAddress();
    }
}
