/*
 * Copyright 2012 The Netty Project
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

package io.netty.test.udt.util;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.nio.NioUdtProvider;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * Bootstrap utilities.
 */
public final class BootHelp {

    /**
     * bootstrap for byte rendezvous peer
     */
    public static Bootstrap bytePeerBoot(final InetSocketAddress self,
            final InetSocketAddress peer, final ChannelHandler handler) {

        final Bootstrap boot = new Bootstrap();

        final ThreadFactory connectFactory = new UtilThreadFactory("peer");

        final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                connectFactory, NioUdtProvider.BYTE_PROVIDER);

        boot.group(connectGroup).channelFactory(NioUdtProvider.BYTE_RENDEZVOUS)
                .localAddress(self).remoteAddress(peer).handler(handler);

        return boot;

    }

    /**
     * bootstrap for message rendezvous peer
     */
    public static Bootstrap messagePeerBoot(final InetSocketAddress self,
            final InetSocketAddress peer, final ChannelHandler handler) {

        final Bootstrap boot = new Bootstrap();

        final ThreadFactory connectFactory = new UtilThreadFactory("peer");

        final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                connectFactory, NioUdtProvider.MESSAGE_PROVIDER);

        boot.group(connectGroup)
                .channelFactory(NioUdtProvider.MESSAGE_RENDEZVOUS)
                .localAddress(self).remoteAddress(peer).handler(handler);

        return boot;

    }

    private BootHelp() { }
}
