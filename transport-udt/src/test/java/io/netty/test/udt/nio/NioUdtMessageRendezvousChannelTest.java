/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.test.udt.nio;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.udt.nio.NioUdtMessageRendezvousChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.test.udt.util.EchoMessageHandler;
import io.netty.test.udt.util.UnitHelp;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioUdtMessageRendezvousChannelTest extends AbstractUdtTest {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(NioUdtByteAcceptorChannelTest.class);

    /**
     * verify channel meta data
     */
    @Test
    public void metadata() throws Exception {
        assertFalse(new NioUdtMessageRendezvousChannel().metadata().hasDisconnect());
    }

    /**
     * verify basic echo message rendezvous
     *
     * FIXME: Re-enable after making it pass on Windows without unncessary tight loop.
     *        https://github.com/netty/netty/issues/2853
     */
    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    @Disabled
    public void basicEcho() throws Exception {

        final int messageSize = 64 * 1024;
        final int transferLimit = messageSize * 16;

        final InetSocketAddress addr1 = UnitHelp.localSocketAddress();
        final InetSocketAddress addr2 = UnitHelp.localSocketAddress();

        final EchoMessageHandler handler1 = new EchoMessageHandler(messageSize);
        final EchoMessageHandler handler2 = new EchoMessageHandler(messageSize);

        final EventLoopGroup group1 = new MultiThreadIoEventLoopGroup(
                1, Executors.defaultThreadFactory(), NioIoHandler.newFactory(NioUdtProvider.MESSAGE_PROVIDER));
        final EventLoopGroup group2 = new MultiThreadIoEventLoopGroup(
                1, Executors.defaultThreadFactory(), NioIoHandler.newFactory(NioUdtProvider.MESSAGE_PROVIDER));

        final Bootstrap boot1 = new Bootstrap();
        boot1.group(group1)
             .channelFactory(NioUdtProvider.MESSAGE_RENDEZVOUS)
             .localAddress(addr1).remoteAddress(addr2).handler(handler1);

        final Bootstrap boot2 = new Bootstrap();
        boot2.group(group2)
             .channelFactory(NioUdtProvider.MESSAGE_RENDEZVOUS)
             .localAddress(addr2).remoteAddress(addr1).handler(handler2);

        final ChannelFuture connectFuture1 = boot1.connect();
        final ChannelFuture connectFuture2 = boot2.connect();

        while (handler1.counter() < transferLimit
                && handler2.counter() < transferLimit) {

            log.info("progress : {} {}", handler1.counter(), handler2.counter());

            Thread.sleep(1000);
        }

        connectFuture1.channel().close().sync();
        connectFuture2.channel().close().sync();

        log.info("handler1 : {}", handler1.counter());
        log.info("handler2 : {}", handler2.counter());

        assertTrue(handler1.counter() >= transferLimit);
        assertTrue(handler2.counter() >= transferLimit);

        assertEquals(handler1.counter(), handler2.counter());

        group1.shutdownGracefully();
        group2.shutdownGracefully();

        group1.terminationFuture().sync();
        group2.terminationFuture().sync();
    }
}
