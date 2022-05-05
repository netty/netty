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

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.h2new.Http2ClientCodecBuilder;
import io.netty5.handler.codec.h2new.Http2ClientSslContextBuilder;
import io.netty5.handler.codec.h2new.DefaultHttp2DataFrame;
import io.netty5.handler.codec.h2new.DefaultHttp2HeadersFrame;
import io.netty5.handler.codec.h2new.Http2Channel;
import io.netty5.handler.codec.h2new.DefaultHttp2ClientChannelInitializer;
import io.netty5.handler.codec.h2new.Http2DataFrame;
import io.netty5.handler.codec.h2new.Http2HeadersFrame;
import io.netty5.handler.codec.h2new.Http2RequestStreamInboundHandler;
import io.netty5.handler.codec.h2new.Http2StreamChannel;
import io.netty5.handler.codec.http2.DefaultHttp2Headers;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CountDownLatch;

import static io.netty5.example.h2new.Http2ServerExampleStreams.controlStreamInitiatlizer;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Http2ClientExampleStreams {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2ClientExampleStreams.class);

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Http2ClientCodecBuilder codecBuilder =
                    new Http2ClientCodecBuilder()
                            .sslContext(new Http2ClientSslContextBuilder()
                                    // you probably won't want to use this in production, but it is fine for this example:
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .build())
                            .initialSettings(new Http2Settings());

            DefaultHttp2ClientChannelInitializer channelInitializer =
                    new DefaultHttp2ClientChannelInitializer(controlStreamInitiatlizer());

            Future<Channel> connect = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                            ch.pipeline().addLast(codecBuilder.build(channelInitializer));
                        }
                    })
                    .remoteAddress("127.0.0.1", 8081)
                    .connect();

            Http2Channel h2Channel = channelInitializer.http2ChannelFuture(connect).get();
            final BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
            CountDownLatch done = new CountDownLatch(1);
            Http2StreamChannel stream = h2Channel.createStream(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                    ch.pipeline().addLast(new Http2RequestStreamInboundHandler() {
                        @Override
                        protected void handleHeaders(Http2HeadersFrame headersFrame) {
                            logger.info("Stream id: {}, headers: {}.", headersFrame.streamId(),
                                    headersFrame.headers());
                            if (headersFrame.isEndStream()) {
                                done.countDown();
                            }
                        }

                        @Override
                        protected void handleData(Http2DataFrame dataFrame) {
                            logger.info("Stream id: {}, Date: {}.", dataFrame.data().toString());
                            dataFrame.data().close();
                            if (dataFrame.isEndStream()) {
                                logger.info("Stream id: {}, response done.", dataFrame.streamId());
                                done.countDown();
                            }
                        }
                    });
                }
            }).get();
            stream.write(new DefaultHttp2HeadersFrame(stream.streamId(), new DefaultHttp2Headers()));
            stream.writeAndFlush(new DefaultHttp2DataFrame(stream.streamId(), allocator.allocate(20).writeBytes("Hello world".getBytes(UTF_8)), true));
            done.await();
        } finally {
            group.shutdownGracefully();
        }
    }
}
