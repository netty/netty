/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.address;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.net.NetworkInterface;
import java.net.SocketAddress;

/**
 * {@link ChannelOutboundHandler} implementation which allows to dynamically replace the used
 * {@code remoteAddress} and / or {@code localAddress} when making a connection attempt.
 * <p>
 * This can be useful to for example bind to a specific {@link NetworkInterface} based on
 * the {@code remoteAddress}.
 */
public abstract class DynamicAddressConnectHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public final void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                              SocketAddress localAddress, ChannelPromise promise) {
        final SocketAddress remote;
        final SocketAddress local;
        try {
            remote = remoteAddress(remoteAddress, localAddress);
            local = localAddress(remoteAddress, localAddress);
        } catch (Exception e) {
            promise.setFailure(e);
            return;
        }
        ctx.connect(remote, local, promise).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    // We only remove this handler from the pipeline once the connect was successful as otherwise
                    // the user may try to connect again.
                    future.channel().pipeline().remove(DynamicAddressConnectHandler.this);
                }
            }
        });
    }

    /**
     * Returns the local {@link SocketAddress} to use for
     * {@link ChannelHandlerContext#connect(SocketAddress, SocketAddress)} based on the original {@code remoteAddress}
     * and {@code localAddress}.
     * By default, this method returns the given {@code localAddress}.
     */
    protected SocketAddress localAddress(
            @SuppressWarnings("unused") SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        return localAddress;
    }

    /**
     * Returns the remote {@link SocketAddress} to use for
     * {@link ChannelHandlerContext#connect(SocketAddress, SocketAddress)} based on the original {@code remoteAddress}
     * and {@code localAddress}.
     * By default, this method returns the given {@code remoteAddress}.
     */
    protected SocketAddress remoteAddress(
            SocketAddress remoteAddress, @SuppressWarnings("unused") SocketAddress localAddress) throws Exception {
        return remoteAddress;
    }
}
