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
package io.netty5.handler.address;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.resolver.AbstractAddressResolver;
import io.netty5.resolver.AddressResolver;
import io.netty5.resolver.AddressResolverGroup;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ResolveAddressHandlerTest {

    private static final LocalAddress UNRESOLVED = new LocalAddress("unresolved-" + UUID.randomUUID());
    private static final LocalAddress RESOLVED = new LocalAddress("resolved-" + UUID.randomUUID());
    private static final Exception ERROR = new UnknownHostException();

    private static EventLoopGroup group;

    @BeforeAll
    public static void createEventLoop() {
        group = new MultithreadEventLoopGroup(LocalHandler.newFactory());
    }

    @AfterAll
    public static void destroyEventLoop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testResolveSuccessful() throws Exception {
        testResolve(false);
    }

    @Test
    public void testResolveFails() throws Exception {
        testResolve(true);
    }

    private static void testResolve(boolean fail) throws Exception {
        AddressResolverGroup<SocketAddress> resolverGroup = new TestResolverGroup(fail);
        Bootstrap cb = new Bootstrap();
        cb.group(group).channel(LocalChannel.class).handler(new ResolveAddressHandler(resolverGroup));

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelHandler() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        ctx.close();
                    }
                });

        // Start server
        Channel sc = sb.bind(RESOLVED).get();
        Future<Channel> future = cb.connect(UNRESOLVED).awaitUninterruptibly();
        try {
            if (fail) {
                assertSame(ERROR, future.cause());
            } else {
                assertTrue(future.isSuccess());
                future.get().close().syncUninterruptibly();
            }
        } finally {
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
    }
}
