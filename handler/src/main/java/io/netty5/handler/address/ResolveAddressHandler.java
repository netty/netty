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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.resolver.AddressResolver;
import io.netty5.resolver.AddressResolverGroup;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.Objects;

/**
 * {@link ChannelHandler} which will resolve the {@link SocketAddress} that is passed to
 * {@link #connect(ChannelHandlerContext, SocketAddress, SocketAddress)} if it is not already resolved
 * and the {@link AddressResolver} supports the type of {@link SocketAddress}.
 */
public class ResolveAddressHandler implements ChannelHandler {

    private final AddressResolverGroup<? extends SocketAddress> resolverGroup;

    public ResolveAddressHandler(AddressResolverGroup<? extends SocketAddress> resolverGroup) {
        this.resolverGroup = Objects.requireNonNull(resolverGroup, "resolverGroup");
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public Future<Void> connect(final ChannelHandlerContext ctx, SocketAddress remoteAddress,
                          final SocketAddress localAddress)  {
        AddressResolver<? extends SocketAddress> resolver = resolverGroup.getResolver(ctx.executor());
        if (resolver.isSupported(remoteAddress) && !resolver.isResolved(remoteAddress)) {
            Promise<Void> promise = ctx.newPromise();
            resolver.resolve(remoteAddress).addListener((FutureListener<SocketAddress>) future -> {
                Throwable cause = future.cause();
                if (cause != null) {
                    promise.setFailure(cause);
                } else {
                    ctx.connect(future.getNow(), localAddress).cascadeTo(promise);
                }
                ctx.pipeline().remove(this);
            });
            return promise.asFuture();
        } else {
            Future<Void> f = ctx.connect(remoteAddress, localAddress);
            ctx.pipeline().remove(this);
            return f;
        }
    }
}
