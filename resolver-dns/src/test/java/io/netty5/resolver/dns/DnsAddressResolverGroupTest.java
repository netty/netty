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
package io.netty5.resolver.dns;

import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.resolver.AddressResolver;
import io.netty5.resolver.InetSocketAddressResolver;
import io.netty5.util.concurrent.Promise;

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
        EventLoopGroup group = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        MultithreadEventLoopGroup defaultEventLoopGroup = new MultithreadEventLoopGroup(1, LocalIoHandler.newFactory());
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).channelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        try {
            final Promise<?> promise = loop.newPromise();
            AddressResolver<?> resolver = resolverGroup.getResolver(defaultEventLoopGroup.next());
            resolver.resolve(new SocketAddress() {
                private static final long serialVersionUID = 3169703458729818468L;
            }).addListener(future -> {
                try {
                    assertThat(future.cause(),
                               instanceOf(UnsupportedAddressTypeException.class));
                    assertTrue(loop.inEventLoop());
                    promise.setSuccess(null);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            }).asStage().await();
            promise.asFuture().asStage().sync();
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testSharedDNSCacheAcrossEventLoops() throws InterruptedException, ExecutionException {
        MultithreadEventLoopGroup group = new MultithreadEventLoopGroup(3, NioIoHandler.newFactory());
        final EventLoop loop = group.next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).channelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        EventLoop eventLoop1 = group.next();
        EventLoop eventLoop2 = group.next();
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
        }
    }

    private InetAddress resolve(InetSocketAddressResolver resolver, SocketAddress socketAddress,
                                final Promise<InetSocketAddress> promise)
            throws InterruptedException, ExecutionException {
        resolver.resolve(socketAddress)
                .addListener(f -> {
                    try {
                        promise.setSuccess(f.getNow());
                    } catch (Throwable cause) {
                        promise.setFailure(cause);
                    }
                });
        promise.asFuture().asStage().sync();
        InetSocketAddress inetSocketAddress = promise.getNow();
        return inetSocketAddress.getAddress();
    }
}
