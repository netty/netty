/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.example.h2new;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.codec.h2new.DefaultHttp2HeadersFrame;
import io.netty5.handler.codec.h2new.Http2ServerCodecBuilder;
import io.netty5.handler.codec.h2new.Http2ServerSslContextBuilder;
import io.netty5.handler.codec.h2new.Http2DataFrame;
import io.netty5.handler.codec.h2new.Http2HeadersFrame;
import io.netty5.handler.codec.h2new.Http2RequestStreamInboundHandler;
import io.netty5.handler.codec.h2new.Http2ServerStreamsInitializer;
import io.netty5.handler.codec.h2new.Http2StreamChannel;
import io.netty5.handler.codec.http2.DefaultHttp2Headers;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.ReferenceCountUtil;

public class Http2ServerExampleStreams {
    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            final Http2ServerSslContextBuilder sslContextBuilder =
                    new Http2ServerSslContextBuilder(ssc.certificate(), ssc.privateKey());
            Http2ServerCodecBuilder codecBuilder = new Http2ServerCodecBuilder()
                    .sslContext(sslContextBuilder.build())
                    .initialSettings(new Http2Settings().maxConcurrentStreams(100));

            final ChannelHandler codec =
                    codecBuilder.build(new Http2ServerStreamsInitializer(controlStreamInitiatlizer()) {
                        @Override
                        protected void handleRequestStream(Http2StreamChannel stream) {
                            stream.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                            stream.pipeline().addLast(new Http2RequestStreamInboundHandler() {
                                @Override
                                protected void handleHeaders(Http2HeadersFrame headersFrame) {
                                    stream.writeAndFlush(new DefaultHttp2HeadersFrame(stream.streamId(),
                                            new DefaultHttp2Headers(), headersFrame.isEndStream()));
                                }

                                @Override
                                protected void handleData(Http2DataFrame dataFrame) {
                                    stream.writeAndFlush(dataFrame);
                                }
                            });
                        }
                    });

            new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.ERROR))
                    .childHandler(codec)
                    .bind(8081).get()
                    .closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    static ChannelInitializer<Channel> controlStreamInitiatlizer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                ch.pipeline().addLast(new ChannelHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        };
    }
}
