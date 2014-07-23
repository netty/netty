/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.client.httpobject;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DelegatingHttp2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.DefaultHttp2InboundFlowController;
import io.netty.handler.codec.http2.DefaultHttp2OutboundFlowController;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2EventListener;
import io.netty.handler.codec.http2.Http2HttpDecoderSettingsNotifier;
import io.netty.handler.codec.http2.DelegatingHttp2HttpConnectionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLoggerFactory;
import static io.netty.util.internal.logging.InternalLogLevel.*;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {

  private static final Http2FrameLogger           logger = new Http2FrameLogger(INFO,
                                                             InternalLoggerFactory
                                                                 .getInstance(Http2ClientInitializer.class));

  private final SslContext                        sslCtx;
  private final int                               maxContentLength;
  private final Http2EventListener<Http2Settings> listener;

  public Http2ClientInitializer(SslContext sslCtx, int maxContentLength, Http2EventListener<Http2Settings> listener) {
    this.sslCtx = sslCtx;
    this.maxContentLength = maxContentLength;
    this.listener = listener;
  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline pipeline = ch.pipeline();
    if (sslCtx != null) {
      pipeline.addLast(sslCtx.newHandler(ch.alloc()));
    }
    Http2HttpDecoderSettingsNotifier http2Decoder = new Http2HttpDecoderSettingsNotifier(maxContentLength);
    Http2Connection connection = new DefaultHttp2Connection(false);
    DelegatingHttp2ConnectionHandler connectionHandler = new DelegatingHttp2HttpConnectionHandler(
        connection, frameReader(), frameWriter(), new DefaultHttp2InboundFlowController(
            connection), new DefaultHttp2OutboundFlowController(connection), http2Decoder);
    if (listener != null) {
      http2Decoder.addListener(listener);
    }
    pipeline.addLast("http2", connectionHandler);
    pipeline.addLast("inflater", new HttpContentDecompressor());
    pipeline.addLast("aggregator", new HttpObjectAggregator(maxContentLength));
    pipeline.addLast("myhandler", new Http2ResponseHandler());
  }

  private static Http2FrameReader frameReader() {
    return new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), logger);
  }

  private static Http2FrameWriter frameWriter() {
    return new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), logger);
  }
}
