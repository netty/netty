/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.ipfilter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.SocketAddress;

/**
 * This class provides the functionality to either accept or reject new {@link Channel}s
 * based on their IP address.
 * <p>
 * You should inherit from this class if you would like to implement your own IP-based filter. Basically you have to
 * implement {@link #accept(ChannelHandlerContext, SocketAddress)} to decided whether you want to accept or reject
 * a connection from the remote address.
 * <p>
 * Furthermore overriding {@link #channelRejected(ChannelHandlerContext, SocketAddress)} gives you the
 * flexibility to respond to rejected (denied) connections. If you do not want to send a response, just have it return
 * null.  Take a look at {@link RuleBasedIpFilter} for details.
 */
public abstract class AbstractRemoteAddressFilter<T extends SocketAddress> extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        handleNewChannel(ctx);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!handleNewChannel(ctx)) {
            throw new IllegalStateException("cannot determine to accept or reject a channel: " + ctx.channel());
        } else {
            ctx.fireChannelActive();
        }
    }

    private boolean handleNewChannel(ChannelHandlerContext ctx) throws Exception {
        @SuppressWarnings("unchecked")
        T remoteAddress = (T) ctx.channel().remoteAddress();

        // If the remote address is not available yet, defer the decision.
        if (remoteAddress == null) {
            return false;
        }

        // No need to keep this handler in the pipeline anymore because the decision is going to be made now.
        // Also, this will prevent the subsequent events from being handled by this handler.
        ctx.pipeline().remove(this);

        if (accept(ctx, remoteAddress)) {
            channelAccepted(ctx, remoteAddress);
        } else {
            ChannelFuture rejectedFuture = channelRejected(ctx, remoteAddress);
            if (rejectedFuture != null) {
                rejectedFuture.addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.close();
            }
        }

        return true;
    }

    /**
     * This method is called immediately after a {@link io.netty.channel.Channel} gets registered.
     *
     * @return Return true if connections from this IP address and port should be accepted. False otherwise.
     */
    protected abstract boolean accept(ChannelHandlerContext ctx, T remoteAddress) throws Exception;

    /**
     * This method is called if {@code remoteAddress} gets accepted by
     * {@link #accept(ChannelHandlerContext, SocketAddress)}.  You should override it if you would like to handle
     * (e.g. respond to) accepted addresses.
     */
    @SuppressWarnings("UnusedParameters")
    protected void channelAccepted(ChannelHandlerContext ctx, T remoteAddress) { }

    /**
     * This method is called if {@code remoteAddress} gets rejected by
     * {@link #accept(ChannelHandlerContext, SocketAddress)}.  You should override it if you would like to handle
     * (e.g. respond to) rejected addresses.
     *
     * @return A {@link ChannelFuture} if you perform I/O operations, so that
     *         the {@link Channel} can be closed once it completes. Null otherwise.
     */
    @SuppressWarnings("UnusedParameters")
    protected ChannelFuture channelRejected(ChannelHandlerContext ctx, T remoteAddress) {
        return null;
    }
}
