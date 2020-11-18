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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class QuicStreamLimitTest {

    @Test
    public void testStreamLimitEnforcedBidirectional() throws Throwable {
        testStreamLimitEnforced(QuicStreamType.BIDIRECTIONAL);
    }

    @Test
    public void testStreamLimitEnforcedUnidirectional() throws Throwable {
        testStreamLimitEnforced(QuicStreamType.UNIDIRECTIONAL);
    }

    private static void testStreamLimitEnforced(QuicStreamType type) throws Throwable {
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder().initialMaxStreamsBidirectional(1)
                        .initialMaxStreamsUnidirectional(1),
                InsecureQuicTokenHandler.INSTANCE,
                new QuicChannelInitializer(new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        ChannelFuture future = null;
        try {
            Bootstrap bootstrap = QuicTestUtils.newClientBootstrap();
            future = bootstrap
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());
            QuicChannel channel = (QuicChannel) future.channel();
            QuicStreamChannel stream = channel.createStream(
                    type, new ChannelInboundHandlerAdapter()).get();

            // Second stream creation should fail.
            Throwable cause = channel.createStream(
                    type, new ChannelInboundHandlerAdapter()).await().cause();
            assertThat(cause, CoreMatchers.instanceOf(IOException.class));
            stream.close().sync();
            channel.close().sync();
        } finally {
            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }
}
