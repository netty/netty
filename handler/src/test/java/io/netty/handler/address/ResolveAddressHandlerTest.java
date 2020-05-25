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
package io.netty.handler.address;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ResolveAddressHandlerTest {

    private static final LocalAddress UNRESOLVED = new LocalAddress("unresolved-" + UUID.randomUUID().toString());
    private static final LocalAddress RESOLVED = new LocalAddress("resolved-" + UUID.randomUUID().toString());
    private static final Exception ERROR = new UnknownHostException();

    private static EventLoopGroup group;

    @BeforeClass
    public static void createEventLoop() {
        group = new DefaultEventLoopGroup();
    }

    @AfterClass
    public static void destroyEventLoop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testResolveSuccessful() {
        testResolve(false);
    }

    @Test
    public void testResolveFails() {
        testResolve(true);
    }

    private static void testResolve(boolean fail) {
        AddressResolverGroup<SocketAddress> resolverGroup = new TestResolverGroup(fail);
        Bootstrap cb = new Bootstrap();
        cb.group(group).channel(LocalChannel.class).handler(new ResolveAddressHandler(resolverGroup));

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        ctx.close();
                    }
                });

        // Start server
        Channel sc = sb.bind(RESOLVED).syncUninterruptibly().channel();
        ChannelFuture future = cb.connect(UNRESOLVED).awaitUninterruptibly();
        try {
            if (fail) {
                assertSame(ERROR, future.cause());
            } else {
                assertTrue(future.isSuccess());
            }
            future.channel().close().syncUninterruptibly();
        } finally {
            future.channel().close().syncUninterruptibly();
            sc.close().syncUninterruptibly();
            resolverGroup.close();
        }
    }

    private static final class TestResolverGroup extends AddressResolverGroup<SocketAddress> {
        private final boolean fail;

        TestResolverGroup(boolean fail) {
            this.fail = fail;
        }

        @Override
        protected AddressResolver<SocketAddress> newResolver(EventExecutor executor) {
            return new AbstractAddressResolver<SocketAddress>(executor) {
                @Override
                protected boolean doIsResolved(SocketAddress address) {
                    return address == RESOLVED;
                }

                @Override
                protected void doResolve(SocketAddress unresolvedAddress, Promise<SocketAddress> promise) {
                    assertSame(UNRESOLVED, unresolvedAddress);
                    if (fail) {
                        promise.setFailure(ERROR);
                    } else {
                        promise.setSuccess(RESOLVED);
                    }
                }

                @Override
                protected void doResolveAll(SocketAddress unresolvedAddress, Promise<List<SocketAddress>> promise) {
                    fail();
                }
            };
        }
    };
}
