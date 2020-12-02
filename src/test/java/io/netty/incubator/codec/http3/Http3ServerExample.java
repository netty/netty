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
package io.netty.incubator.codec.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public final class Http3ServerExample {
    private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
    private Http3ServerExample() { }

    public static void main(String... args) throws Exception {

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                .certificateChain("./src/test/resources/cert.crt")
                .privateKey("./src/test/resources/cert.key")
                .maxIdleTimeout(5000)
                .maxUdpPayloadSize(1350)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .disableActiveMigration(true)
                .enableEarlyData()
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // Called for each connection
                        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                new ChannelInitializer<QuicStreamChannel>() {
                                    // Called for each request-stream,
                                    @Override
                                    protected void initChannel(QuicStreamChannel ch) {
                                        ch.pipeline().addLast(new Http3RequestStreamHandler() {

                                            @Override
                                            public void channelRead(ChannelHandlerContext ctx,
                                                                    Http3RequestStreamFrame frame, boolean isLast) {
                                                if (frame instanceof Http3HeadersFrame) {
                                                    Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                    headersFrame.headers().status("404");
                                                    headersFrame.headers().add("server", "netty");
                                                    headersFrame.headers().addInt("content-length", CONTENT.length);
                                                    ctx.write(headersFrame);
                                                }
                                                if (isLast) {
                                                    ctx.writeAndFlush(new DefaultHttp3DataFrame(
                                                            Unpooled.wrappedBuffer(CONTENT)))
                                                            .addListener(ChannelFutureListener.CLOSE);
                                                }
                                                ReferenceCountUtil.release(frame);
                                            }
                                        });
                                    }
                                }));
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
