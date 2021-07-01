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

package io.netty.example.h2new;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.h2new.Http2ClientCodecBuilder;
import io.netty.handler.codec.h2new.Http2ClientSslContextBuilder;
import io.netty.handler.codec.h2new.DefaultHttp2DataFrame;
import io.netty.handler.codec.h2new.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.h2new.Http2DataFrame;
import io.netty.handler.codec.h2new.Http2Frame;
import io.netty.handler.codec.h2new.Http2HeadersFrame;
import io.netty.handler.codec.h2new.Http2SettingsFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static io.netty.util.ReferenceCountUtil.release;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Http2ClientExampleNoStreams {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2ClientExampleNoStreams.class);

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Http2ClientCodecBuilder codecBuilder = new Http2ClientCodecBuilder()
                    .sslContext(new Http2ClientSslContextBuilder()
                            // you probably won't want to use this in production, but it is fine for this example:
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build())
                    .initialSettings(new Http2Settings());

            CountDownLatch awaitMaxConcurrentStreamsSettings = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            // Below is to be done per channel
            ChannelHandler channelInitializer = new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                    ch.pipeline().addLast(new ChannelHandlerAdapter() {
                        private final Map<Integer, StreamListener> streams = new HashMap<>();

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (!(msg instanceof Http2Frame)) {
                                release(msg);
                                return;
                            }
                            Http2Frame http2Frame = (Http2Frame) msg;
                            System.out.println("Received frame: " + http2Frame);
                            if (http2Frame instanceof Http2SettingsFrame) {
                                Http2Settings settings = ((Http2SettingsFrame) http2Frame).settings();
                                if (settings.maxConcurrentStreams() >= 2) {
                                    awaitMaxConcurrentStreamsSettings.countDown();
                                }
                            }
                            final int streamId = http2Frame.streamId();
                            if (streamId == 0) {
                                // control stream, ignore
                                release(http2Frame);
                            }
                            StreamListener processor;
                            switch (http2Frame.frameType()) {
                                case Data:
                                    processor = streams.get(streamId);
                                    assert processor != null;
                                    Http2DataFrame dataFrame = (Http2DataFrame) http2Frame;
                                    processor.data(dataFrame.data());
                                    if (dataFrame.isEndStream()) {
                                        processor.end(done);
                                    }
                                    break;
                                case Headers:
                                    processor = streams.get(streamId);
                                    if (processor == null) {
                                        // headers
                                        processor = new StreamListener(streamId);
                                        streams.put(streamId, processor);
                                        processor.headers(((Http2HeadersFrame) http2Frame).headers());
                                    } else {
                                        // trailers
                                        streams.remove(streamId);
                                        processor.end(done);
                                    }
                                    break;
                                case RstStream:
                                    processor = streams.get(streamId);
                                    processor.reset();
                                    break;
                                default:
                                    release(msg);
                            }
                        }
                    });
                }
            };
            Channel channel = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(codecBuilder.buildRaw(channelInitializer))
                    .remoteAddress("127.0.0.1", 8081)
                    .connect().get();

            awaitMaxConcurrentStreamsSettings.await();

            final BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
            // Beyond examples, one would have some way of creating stream IDs for a channel.
            final int stream1 = 1;
            channel.write(new DefaultHttp2HeadersFrame(stream1,
                    new DefaultHttp2Headers().path("/foo")));
            channel.writeAndFlush(new DefaultHttp2DataFrame(stream1, allocator.allocate(20)
                    .writeBytes("Hello world".getBytes(UTF_8)), true));

            final int stream2 = stream1 + 2;
            channel.write(new DefaultHttp2HeadersFrame(stream2,
                    new DefaultHttp2Headers().path("/bar")));
            channel.writeAndFlush(new DefaultHttp2DataFrame(stream2, allocator.allocate(20)
                    .writeBytes("Hello world".getBytes(UTF_8)), true));
            done.await();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static final class StreamListener {
        private final int streamId;

        StreamListener(int streamId) {
            this.streamId = streamId;
        }

        void headers(Http2Headers headers) {
            logger.info("Stream id: {}, headers: {}.", streamId , headers);
        }

        void data(Buffer buffer) {
            logger.info("Stream id: {}, data: {}.", streamId , buffer.toString());
            buffer.close();
        }

        void end(CountDownLatch done) {
            logger.info("Stream id: {}, response done!", streamId);
            done.countDown();
        }

        void reset() {
            // noop
        }
    }
}
