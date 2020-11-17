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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import org.junit.Test;

import java.net.InetSocketAddress;

public class QuicStreamChannelCloseTest {

    @Test
    public void testCloseFromServerWhileInActiveUnidirectional() throws Exception {
        testCloseFromServerWhileInActive(QuicStreamType.UNIDIRECTIONAL);
    }

    @Test
    public void testCloseFromServerWhileInActiveBidirectional() throws Exception {
        testCloseFromServerWhileInActive(QuicStreamType.BIDIRECTIONAL);
    }

    private static void testCloseFromServerWhileInActive(QuicStreamType type) throws Exception {
        Channel server = null;
        QuicChannel client = null;
        try {
            server = QuicTestUtils.newServer(new QuicChannelInitializer(new StreamCreationHandler(type),
                    new ChannelInboundHandlerAdapter()));
            client = (QuicChannel) QuicTestUtils.newClientBootstrap().handler(
                    new QuicChannelInitializer(new StreamHandler())).connect(
                            QuicConnectionAddress.random((InetSocketAddress) server.localAddress())).sync().channel();

            // Wait till the client was closed
            client.closeFuture().sync();
        } finally {
            QuicTestUtils.closeParent(client);
            if (server != null) {
                server.close().sync();
            }
        }
    }

    @Test
    public void testCloseFromClientWhileInActiveUnidirectional() throws Exception {
        testCloseFromClientWhileInActive(QuicStreamType.UNIDIRECTIONAL);
    }

    @Test
    public void testCloseFromClientWhileInActiveBidirectional() throws Exception {
        testCloseFromClientWhileInActive(QuicStreamType.BIDIRECTIONAL);
    }

    private static void testCloseFromClientWhileInActive(QuicStreamType type) throws Exception {
        Channel server = null;
        QuicChannel client = null;
        try {
            server = QuicTestUtils.newServer(new QuicChannelInitializer(new StreamHandler()));
            client = (QuicChannel) QuicTestUtils.newClientBootstrap().handler(new StreamCreationHandler(type))
                    .connect(QuicConnectionAddress.random((InetSocketAddress) server.localAddress())).sync().channel();

            // Close stream and quic channel
            client.closeFuture().sync();
        } finally {
            QuicTestUtils.closeParent(client);
            if (server != null) {
                server.close().sync();
            }
        }
    }

    private static final class StreamCreationHandler extends ChannelInboundHandlerAdapter {
        private final QuicStreamType type;

        StreamCreationHandler(QuicStreamType type) {
            this.type = type;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            QuicChannel channel = (QuicChannel) ctx.channel();
            channel.createStream(type, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx)  {
                    // Do the write and close the channel
                    ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            });
        }
    }

    private static final class StreamHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // Close the QUIC channel as well.
            ctx.channel().parent().close();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }
    }
}
