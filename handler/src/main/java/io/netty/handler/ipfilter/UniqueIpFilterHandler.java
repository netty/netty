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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ConcurrentSet;
import io.netty.channel.Channel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * This class allows one to ensure that at all times for every IP address there is at most one
 * {@link Channel} connected to the server.
 */
@ChannelHandler.Sharable
public class UniqueIpFilterHandler extends AbstractIpFilterHandler {
    private final Set<InetAddress> connected = new ConcurrentSet<InetAddress>();

    @Override
    protected boolean accept(InetSocketAddress ipAndPort) throws Exception {
        InetAddress ipAddress = ipAndPort.getAddress();
        if (connected.contains(ipAddress)) {
            return false;
        } else {
            connected.add(ipAddress);
            return true;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetAddress ipAddress = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        connected.remove(ipAddress);

        super.channelInactive(ctx);
    }

    @Override
    protected ChannelFuture rejected(ChannelHandlerContext ctx, InetSocketAddress ipAndPort) {
        return null;
    }
}
