/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic.example;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelBootstrap;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.quic.SslEarlyDataReadyEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

public final class QuicClientZeroRTTExample {

    private QuicClientZeroRTTExample() { }

    public static void main(String[] args) throws Exception {
        QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                applicationProtocols("http/0.9").earlyData(true).build();

        newChannelAndSendData(context, null);
        newChannelAndSendData(context, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslEarlyDataReadyEvent) {
                    createStream((QuicChannel) ctx.channel()).addListener(f -> {
                        if (f.isSuccess()) {
                            QuicStreamChannel streamChannel = (QuicStreamChannel) f.getNow();
                            streamChannel.writeAndFlush(
                                    Unpooled.copiedBuffer("0rtt stream data\r\n", CharsetUtil.US_ASCII));
                        }
                    });
                }
                super.userEventTriggered(ctx, evt);
            }
        });
    }

    static void newChannelAndSendData(QuicSslContext context, ChannelHandler earlyDataSendHandler) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslEngineProvider(q -> context.newEngine(q.alloc(), "localhost", 9999))
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    // As we don't want to support remote initiated streams just setup the limit for local initiated
                    // streams in this example.
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build();

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0).sync().channel();

            QuicChannelBootstrap quicChannelBootstrap = QuicChannel.newBootstrap(channel)
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // As we did not allow any remote initiated streams we will never see this method called.
                            // That said just let us keep it here to demonstrate that this handle would be called
                            // for each remote initiated stream.
                            ctx.close();
                        }
                    })
                    .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));

            if (earlyDataSendHandler != null) {
                quicChannelBootstrap.handler(earlyDataSendHandler);
            }

            QuicChannel quicChannel = quicChannelBootstrap
                    .connect()
                    .get();

            QuicStreamChannel streamChannel = createStream(quicChannel).sync().getNow();
            // Write the data and send the FIN. After this its not possible anymore to write any more data.
            streamChannel.writeAndFlush(Unpooled.copiedBuffer("Bye\r\n", CharsetUtil.US_ASCII))
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            streamChannel.closeFuture().sync();
            quicChannel.closeFuture().sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    static Future<QuicStreamChannel> createStream(QuicChannel quicChannel) {
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        System.err.println(byteBuf.toString(CharsetUtil.US_ASCII));
                        byteBuf.release();
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            // Close the connection once the remote peer did send the FIN for this stream.
                            ((QuicChannel) ctx.channel().parent()).close(true, 0,
                                    ctx.alloc().directBuffer(16)
                                            .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
                        }
                    }
                });
    }
}
