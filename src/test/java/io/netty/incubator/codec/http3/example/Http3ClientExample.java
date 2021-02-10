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
package io.netty.incubator.codec.http3.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class Http3ClientExample {
    private Http3ClientExample() { }

    public static void main(String... args) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);

        try {
            QuicSslContext context = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols(Http3.supportedApplicationProtocols()).build();
            ChannelHandler codec = Http3.newQuicClientCodecBuilder()
                    .sslContext(context)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build();

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0).sync().channel();

            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new Http3ClientConnectionHandler())
                    .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999))
                    .connect()
                    .get();

            QuicStreamChannel streamChannel = Http3.newRequestStream(quicChannel,
                    new Http3RequestStreamInboundHandler() {
                        @Override
                        protected void channelRead(ChannelHandlerContext ctx,
                                                   Http3HeadersFrame frame, boolean isLast) {
                            releaseFrameAndCloseIfLast(ctx, frame, isLast);
                        }

                        @Override
                        protected void channelRead(ChannelHandlerContext ctx,
                                                   Http3DataFrame frame, boolean isLast) {
                            System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
                            releaseFrameAndCloseIfLast(ctx, frame, isLast);
                        }

                        private void releaseFrameAndCloseIfLast(ChannelHandlerContext ctx,
                                                                Http3RequestStreamFrame frame, boolean isLast) {
                            ReferenceCountUtil.release(frame);
                            if (isLast) {
                                ctx.close();
                            }
                        }
                    }).sync().getNow();

            // Write the Header frame and send the FIN to mark the end of the request.
            // After this its not possible anymore to write any more data.
            Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
            frame.headers().method("GET").path("/");
            streamChannel.writeAndFlush(frame)
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

            // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
            // After this is done we will close the underlying datagram channel.
            streamChannel.closeFuture().sync();

            // After we received the response lets also close the underlying QUIC channel and datagram channel.
            quicChannel.close().sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
