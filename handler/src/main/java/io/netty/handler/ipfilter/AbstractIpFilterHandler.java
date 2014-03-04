/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.ipfilter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

/**
 * This class provides the functionality to either accept or reject new {@link Channel}s
 * based on their IP address.
 * <p>
 * You should inherit from this class if you would like to implement your own IP-based filter. Basically you have to
 * implement {@link #accept(InetSocketAddress)} to decided whether you want to allow or deny a connection from an IP
 * address. Furthermore overriding {@link #rejected(ChannelHandlerContext, InetSocketAddress)} gives you the flexibility
 * to respond to rejected (denied) connections. If you do not want to send a response, just have it return null.
 * Take a look at {@link IpFilterRuleHandler} for details.
 */
public abstract class AbstractIpFilterHandler extends ChannelHandlerAdapter {
    private final AttributeKey<IpFilterDecision> decisionKey = AttributeKey.valueOf(getClass().getName());

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        final InetSocketAddress ipAddress = (InetSocketAddress) ctx.channel().remoteAddress();

        if (!accept(ipAddress)) {
            // the channel might be active already
            if (ctx.channel().isActive()) {
                handleRejected(ctx);
            } else {
                // if the channel is not active yet, store the decision for later use
                // in #channelActive(ChannelHandlerContext ctx)
                Attribute<IpFilterDecision> decision = ctx.attr(decisionKey);
                decision.set(IpFilterDecision.REJECTED);

                super.channelRegistered(ctx);
            }
        } else {
            super.channelRegistered(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final IpFilterDecision decision = ctx.attr(decisionKey).get();

        if (decision == IpFilterDecision.REJECTED) {
            handleRejected(ctx);
        } else {
            super.channelActive(ctx);
        }
    }

    private void handleRejected(ChannelHandlerContext ctx) {
        final InetSocketAddress ipAddress = (InetSocketAddress) ctx.channel().remoteAddress();

        ChannelFuture rejectedFuture = rejected(ctx, ipAddress);
        if (rejectedFuture != null) {
            rejectedFuture.addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.close();
        }
    }

    /**
     * This method is called immediately after a {@link io.netty.channel.Channel} gets registered.
     *
     * @return Return true if connections from this IP address and port should be accepted. False otherwise.
     */
    protected abstract boolean accept(InetSocketAddress ipAndPort) throws Exception;

    /**
     * This method is called if ipAndPort gets rejected by {@link #accept(InetSocketAddress)}.
     * You should override it if you would like to handle (e.g. respond to) rejected addresses.
     *
     * @return A {@link ChannelFuture} if you perform I/O operations, so that
     *         the {@link Channel} can be closed once it completes. Null otherwise.
     */
    protected abstract ChannelFuture rejected(ChannelHandlerContext ctx, InetSocketAddress ipAndPort);
}
