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

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsAddressResolverGroupTest {
    @Test
    public void testUseConfiguredEventLoop() throws InterruptedException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DefaultEventLoopGroup defaultEventLoopGroup = new DefaultEventLoopGroup(1);
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).datagramChannelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        try {
            final Promise<?> promise = loop.newPromise();
            AddressResolver<?> resolver = resolverGroup.getResolver(defaultEventLoopGroup.next());
            resolver.resolve(new SocketAddress() {
                private static final long serialVersionUID = 3169703458729818468L;
            }).addListener(new FutureListener<Object>() {
                @Override
                public void operationComplete(Future<Object> future) {
                    try {
                        assertThat(future.cause(),
                                instanceOf(UnsupportedAddressTypeException.class));
                        assertTrue(loop.inEventLoop());
                        promise.setSuccess(null);
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
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
        DefaultEventLoopGroup defaultEventLoopGroup = new DefaultEventLoopGroup(2);
        EventLoop eventLoop1 = defaultEventLoopGroup.next();
        EventLoop eventLoop2 = defaultEventLoopGroup.next();
        try {
            final Promise<InetSocketAddress> promise1 = loop.newPromise();
            InetSocketAddressResolver resolver1 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop1);
            InetAddress address1 =
                    resolve(resolver1, InetSocketAddress.createUnresolved("netty.io", 80), promise1);
            final Promise<InetSocketAddress> promise2 = loop.newPromise();
            InetSocketAddressResolver resolver2 = (InetSocketAddressResolver) resolverGroup.getResolver(eventLoop2);
            InetAddress address2 =
                    resolve(resolver2, InetSocketAddress.createUnresolved("netty.io", 80), promise2);
            assertSame(address1, address2);
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    private InetAddress resolve(InetSocketAddressResolver resolver, SocketAddress socketAddress,
                                final Promise<InetSocketAddress> promise)
            throws InterruptedException, ExecutionException {
        resolver.resolve(socketAddress)
                .addListener(new FutureListener<InetSocketAddress>() {
                    @Override
                    public void operationComplete(Future<InetSocketAddress> future) {
                        try {
                            promise.setSuccess(future.get());
                        } catch (Throwable cause) {
                            promise.setFailure(cause);
                        }
                    }
                }).await();
        promise.sync();
        InetSocketAddress inetSocketAddress = promise.get();
        return inetSocketAddress.getAddress();
    }
}
