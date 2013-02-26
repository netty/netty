/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.test.udt.nio;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.BufType;
import io.netty.channel.ChannelFuture;
import io.netty.channel.udt.nio.NioUdtMessageRendezvousChannel;
import io.netty.test.udt.util.BootHelp;
import io.netty.test.udt.util.EchoMessageHandler;
import io.netty.test.udt.util.UnitHelp;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NioUdtMessageRendezvousChannelTest extends AbstractUdtTest {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(NioUdtByteAcceptorChannelTest.class);

    /**
     * verify channel meta data
     */
    @Test
    public void metadata() throws Exception {
        assertEquals(BufType.MESSAGE, new NioUdtMessageRendezvousChannel().metadata().bufferType());

    }

    /**
     * verify basic echo message rendezvous
     */
    @Test(timeout = 10 * 1000)
    public void basicEcho() throws Exception {

        final int messageSize = 64 * 1024;
        final int transferLimit = messageSize * 16;

        final Meter rate1 = Metrics.newMeter(
                NioUdtMessageRendezvousChannelTest.class, "send rate", "bytes",
                TimeUnit.SECONDS);

        final Meter rate2 = Metrics.newMeter(
                NioUdtMessageRendezvousChannelTest.class, "send rate", "bytes",
                TimeUnit.SECONDS);

        final InetSocketAddress addr1 = UnitHelp.localSocketAddress();
        final InetSocketAddress addr2 = UnitHelp.localSocketAddress();

        final EchoMessageHandler handler1 = new EchoMessageHandler(rate1,
                messageSize);
        final EchoMessageHandler handler2 = new EchoMessageHandler(rate2,
                messageSize);

        final Bootstrap boot1 = BootHelp
                .messagePeerBoot(addr1, addr2, handler1);
        final Bootstrap boot2 = BootHelp
                .messagePeerBoot(addr2, addr1, handler2);

        final ChannelFuture connectFuture1 = boot1.connect();
        final ChannelFuture connectFuture2 = boot2.connect();

        while (handler1.meter().count() < transferLimit
                && handler2.meter().count() < transferLimit) {

            log.info("progress : {} {}", handler1.meter().count(), handler2
                    .meter().count());

            Thread.sleep(1000);

        }

        connectFuture1.channel().close().sync();
        connectFuture2.channel().close().sync();

        log.info("handler1 : {}", handler1.meter().count());
        log.info("handler2 : {}", handler2.meter().count());

        assertTrue(handler1.meter().count() >= transferLimit);
        assertTrue(handler2.meter().count() >= transferLimit);

        assertEquals(handler1.meter().count(), handler2.meter().count());

        boot1.shutdown();
        boot2.shutdown();

    }

}
