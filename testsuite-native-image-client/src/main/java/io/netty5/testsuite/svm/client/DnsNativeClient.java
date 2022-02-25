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
package io.netty5.testsuite.svm.client;

import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.resolver.AddressResolver;
import io.netty5.resolver.dns.DnsAddressResolverGroup;
import io.netty5.resolver.dns.DnsServerAddressStreamProviders;
import io.netty5.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;

/**
 * A client that uses netty-dns and gets compiled to a native image.
 */
public final class DnsNativeClient {
    /**
     * Main entry point (not instantiable)
     */
    private DnsNativeClient() {
    }

    public static void main(String[] args) throws Exception {
        MultithreadEventLoopGroup group = new MultithreadEventLoopGroup(
                1, new DefaultThreadFactory("netty"), NioHandler.newFactory());

        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(NioDatagramChannel.class,
                DnsServerAddressStreamProviders.platformDefault());
        AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(group.next());
        System.out.println(resolver);

        resolver.close();
        group.shutdownGracefully().get();
    }
}
