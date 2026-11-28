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
import io.netty.resolver.DefaultNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static io.netty.resolver.dns.TestDnsServer.newARecord;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void testSharedDNSCacheAcrossEventLoops() throws Exception {
        final String hostname = "netty.io";
        final String expectedIp = "1.2.3.4";
        TestDnsServer dnsServer = new TestDnsServer(new RecordStore() {
            @Override
            public Set<ResourceRecord> getRecords(QuestionRecord question) {
                if (question.getRecordType() == RecordType.A && question.getDomainName().equalsIgnoreCase(hostname)) {
                    return Collections.singleton(newARecord(hostname, expectedIp));
                }
                return null;
            }
        });
        dnsServer.start();
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class)
                .optResourceEnabled(false)
                .nameServerProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()));
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        EventLoopGroup defaultEventLoopGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        EventLoop eventLoop1 = defaultEventLoopGroup.next();
        EventLoop eventLoop2 = defaultEventLoopGroup.next();
        try {
            final Promise<InetSocketAddress> promise1 = loop.newPromise();
            InetSocketAddressResolver resolver1 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop1);
            InetAddress address1 =
                    resolve(resolver1, InetSocketAddress.createUnresolved(hostname, 80), promise1);
            final Promise<InetSocketAddress> promise2 = loop.newPromise();
            InetSocketAddressResolver resolver2 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop2);
            InetAddress address2 =
                    resolve(resolver2, InetSocketAddress.createUnresolved(hostname, 80), promise2);
            assertSame(address1, address2);
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
            dnsServer.stop();
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

    @Test
    public void testGetNameResolver() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(
                new DnsNameResolverBuilder().datagramChannelType(NioDatagramChannel.class));
        try {
            EventLoop loop1 = group.next();
            EventLoop loop2 = group.next();

            DnsNameResolver nameResolver1 = resolverGroup.getNameResolver(loop1);
            DnsNameResolver nameResolver2 = resolverGroup.getNameResolver(loop2);

            // One per executor, the same as the one of its resolver, sharing the caches.
            assertSame(nameResolver1, resolverGroup.getNameResolver(loop1));
            assertNotSame(nameResolver1, nameResolver2);
            assertSame(loop1, nameResolver1.executor());
            assertSame(nameResolver1.resolveCache(), nameResolver2.resolveCache());

            resolverGroup.close();

            assertNotSame(nameResolver1, resolverGroup.getNameResolver(loop1));
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
        }
    }

    @Test
    public void testGetNameResolverOfTerminatedExecutor() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(
                new DnsNameResolverBuilder().datagramChannelType(NioDatagramChannel.class));
        try {
            EventLoop loop = group.next();
            resolverGroup.getNameResolver(loop);
            group.shutdownGracefully().sync();

            assertThrows(IllegalStateException.class, () -> resolverGroup.getNameResolver(loop));
        } finally {
            resolverGroup.close();
        }
    }

    @Test
    public void testGetNameResolverWithoutDnsNameResolver() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(
                new DnsNameResolverBuilder().datagramChannelType(NioDatagramChannel.class)) {
            @Override
            protected NameResolver<InetAddress> newNameResolver(EventLoop eventLoop,
                    ChannelFactory<? extends DatagramChannel> channelFactory,
                    DnsServerAddressStreamProvider nameServerProvider) throws Exception {
                return new DefaultNameResolver(eventLoop);
            }
        };
        try {
            assertThrows(IllegalStateException.class, () -> resolverGroup.getNameResolver(group.next()));
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
        }
    }
}
