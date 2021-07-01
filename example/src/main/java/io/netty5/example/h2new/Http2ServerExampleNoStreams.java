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
import io.netty5.handler.codec.h2new.Http2Frame;
import io.netty5.handler.codec.h2new.Http2HeadersFrame;
import io.netty5.handler.codec.http2.DefaultHttp2Headers;
import io.netty5.handler.codec.http2.Http2Headers;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.util.ReferenceCountUtil;

import java.util.HashMap;
import java.util.Map;

import static io.netty.util.ReferenceCountUtil.release;

public class Http2ServerExampleNoStreams {
    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Http2ServerCodecBuilder codecBuilder = new Http2ServerCodecBuilder();
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            final Http2ServerSslContextBuilder sslContextBuilder =
                    new Http2ServerSslContextBuilder(ssc.certificate(), ssc.privateKey());
            final ChannelHandler codec = codecBuilder.sslContext(sslContextBuilder.build())
                    .initialSettings(new Http2Settings().maxConcurrentStreams(100))
                    .buildRaw(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.ERROR));
                            ch.pipeline().addLast(new ChannelHandlerAdapter() {
                                private final Map<Integer, StreamProcessor> streams = new HashMap<>();

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (!(msg instanceof Http2Frame)) {
                                        ReferenceCountUtil.release(msg);
                                        return;
                                    }
                                    Http2Frame http2Frame = (Http2Frame) msg;
                                    StreamProcessor processor;
                                    final int streamId = http2Frame.streamId();
                                    if (streamId == 0) {
                                        // control stream, ignore
                                        release(http2Frame);
                                    }

                                    switch (http2Frame.frameType()) {
                                        case Data:
                                            processor = streams.get(streamId);
                                            assert processor != null;
                                            Http2DataFrame dataFrame = (Http2DataFrame) http2Frame;
                                            processor.data(ch, dataFrame);
                                            if (dataFrame.isEndStream()) {
                                                processor.end(ch);
                                            }
                                            break;
                                        case Headers:
                                            processor = streams.get(streamId);
                                            if (processor == null) {
                                                // headers
                                                processor = new StreamProcessor(streamId);
                                                streams.put(streamId, processor);
                                                processor.headers(ch, ((Http2HeadersFrame) http2Frame).headers());
                                            } else {
                                                // trailers
                                                streams.remove(streamId);
                                                processor.end(ch);
                                            }
                                            break;
                                        case RstStream:
                                            processor = streams.get(streamId);
                                            processor.reset(ch);
                                            break;
                                        default:
                                            ReferenceCountUtil.release(msg);
                                    }
                                }
                            });

                        }
                    });

            new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(codec)
                    .bind(8081).get()
                    .closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static final class StreamProcessor {
        private final int streamId;

        StreamProcessor(int streamId) {
            this.streamId = streamId;
        }

        void headers(Channel channel, Http2Headers headers) {
            channel.write(new DefaultHttp2HeadersFrame(streamId, new DefaultHttp2Headers().status("200")));
        }

        void data(Channel channel, Http2DataFrame dataFrame) {
            channel.write(dataFrame);
        }

        void end(Channel channel) {
            channel.flush();
        }

        void reset(Channel channel) {
            // noop
        }
    }
}
