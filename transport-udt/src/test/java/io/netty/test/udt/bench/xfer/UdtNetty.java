/*
 * Copyright 2012 The Netty Project
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

package io.netty.test.udt.bench.xfer;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.test.udt.util.CustomReporter;
import io.netty.test.udt.util.EchoMessageHandler;
import io.netty.test.udt.util.TrafficControl;
import io.netty.test.udt.util.UnitHelp;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * perform two way netty send/recv
 */
public final class UdtNetty {

    private UdtNetty() {
    }

    static final InternalLogger log = InternalLoggerFactory.getInstance(UdtNetty.class);

    /** benchmark duration */
    static final int time = 10 * 60 * 1000;

    /** transfer chunk size */
    static final int size = 64 * 1024;

    static final Counter benchTime = Metrics.newCounter(UdtNetty.class,
            "bench time");

    static final Counter benchSize = Metrics.newCounter(UdtNetty.class,
            "bench size");

    static {
        benchTime.inc(time);
        benchSize.inc(size);
    }

    static final Meter rate = Metrics.newMeter(UdtNetty.class, "rate",
            "bytes", TimeUnit.SECONDS);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    TrafficControl.delay(0);
                } catch (final Exception e) {
                    log.error("", e);
                }
            }
        });
    }

    public static void main(final String[] args) throws Exception {

        log.info("init");
        TrafficControl.delay(0);

        final AtomicBoolean isOn = new AtomicBoolean(true);

        final InetSocketAddress addr1 = UnitHelp.localSocketAddress();
        final InetSocketAddress addr2 = UnitHelp.localSocketAddress();

        final ChannelHandler handler1 = new EchoMessageHandler(rate, size);
        final ChannelHandler handler2 = new EchoMessageHandler(null, size);

        final EventLoopGroup group1 = new MultiThreadIoEventLoopGroup(
                1, Executors.defaultThreadFactory(), NioIoHandler.newFactory(NioUdtProvider.MESSAGE_PROVIDER));
        final EventLoopGroup group2 = new MultiThreadIoEventLoopGroup(
                1, Executors.defaultThreadFactory(), NioIoHandler.newFactory(NioUdtProvider.MESSAGE_PROVIDER));

        final Bootstrap peerBoot1 = new Bootstrap();
        peerBoot1.group(group1)
                 .channelFactory(NioUdtProvider.MESSAGE_RENDEZVOUS)
                 .localAddress(addr1).remoteAddress(addr2).handler(handler1);

        final Bootstrap peerBoot2 = new Bootstrap();
        peerBoot2.group(group2)
                 .channelFactory(NioUdtProvider.MESSAGE_RENDEZVOUS)
                 .localAddress(addr2).remoteAddress(addr1).handler(handler2);

        final ChannelFuture peerFuture1 = peerBoot1.connect();
        final ChannelFuture peerFuture2 = peerBoot2.connect();

        CustomReporter.enable(3, TimeUnit.SECONDS);

        Thread.sleep(time);

        isOn.set(false);

        Thread.sleep(1000);

        peerFuture1.channel().close().sync();
        peerFuture2.channel().close().sync();

        Thread.sleep(1000);

        group1.shutdownGracefully();
        group2.shutdownGracefully();

        Metrics.defaultRegistry().shutdown();

        TrafficControl.delay(0);
        log.info("done");
    }

}
