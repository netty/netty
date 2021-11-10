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
package io.netty.incubator.codec.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuicStreamChannelCreationTest extends AbstractQuicTest {

    private static final AttributeKey<String> ATTRIBUTE_KEY = AttributeKey.newInstance("testKey");
    private static final String ATTRIBUTE_VALUE = "Test";

    @Test
    public void testCreateStream() throws Throwable {
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(serverHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();

        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            CountDownLatch latch = new CountDownLatch(1);
            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
               @Override
               public void channelRegistered(ChannelHandlerContext ctx) {
                   assertQuicStreamChannel((QuicStreamChannel) ctx.channel(),
                           QuicStreamType.UNIDIRECTIONAL, Boolean.TRUE, null);
                   latch.countDown();
               }
            }).sync().get();
            assertQuicStreamChannel(stream, QuicStreamType.UNIDIRECTIONAL, Boolean.TRUE, null);
            latch.await();
            stream.close().sync();
            quicChannel.close().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testCreateStreamViaBootstrap() throws Throwable {
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(serverHandler,
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();

        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();
            CountDownLatch latch = new CountDownLatch(1);
            QuicStreamChannel stream = quicChannel.newStreamBootstrap()
                    .type(QuicStreamType.UNIDIRECTIONAL)
                    .attr(ATTRIBUTE_KEY, ATTRIBUTE_VALUE)
                    .option(ChannelOption.AUTO_READ,  Boolean.FALSE)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRegistered(ChannelHandlerContext ctx) {
                            assertQuicStreamChannel((QuicStreamChannel) ctx.channel(),
                                    QuicStreamType.UNIDIRECTIONAL, Boolean.FALSE, ATTRIBUTE_VALUE);
                            latch.countDown();
                        }
                    }).create().sync().get();
            assertQuicStreamChannel(stream, QuicStreamType.UNIDIRECTIONAL, Boolean.FALSE, ATTRIBUTE_VALUE);
            latch.await();
            stream.close().sync();
            quicChannel.close().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    private static void assertQuicStreamChannel(QuicStreamChannel channel, QuicStreamType expectedType,
                                                Boolean expectedAutoRead, String expectedAttribute) {
        assertEquals(expectedType, channel.type());
        assertEquals(expectedAutoRead, channel.config().getOption(ChannelOption.AUTO_READ));
        assertEquals(expectedAttribute, channel.attr(ATTRIBUTE_KEY).get());
    }
}
