/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http2.client;

import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.http2.draft10.connection.Http2ConnectionHandler;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameCodec;
import io.netty.handler.ssl.SslHandler;
import org.eclipse.jetty.npn.NextProtoNego;

import javax.net.ssl.SSLEngine;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SimpleChannelInboundHandler<Http2DataFrame> httpResponseHandler;

    public Http2ClientInitializer(SimpleChannelInboundHandler<Http2DataFrame> httpResponseHandler) {
        this.httpResponseHandler = httpResponseHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        SSLEngine engine = SecureChatSslContextFactory.getClientContext().createSSLEngine();
        engine.setUseClientMode(true);
        NextProtoNego.put(engine, new Http2ClientProvider());
        NextProtoNego.debug = true;

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("ssl", new SslHandler(engine));
        pipeline.addLast("http2FrameCodec", new Http2FrameCodec());
        pipeline.addLast("http2FrameLogger", new Http2FrameLogger(INFO));
        pipeline.addLast("http2ConnectionHandler", new Http2ConnectionHandler(false));
        pipeline.addLast("httpHandler", httpResponseHandler);
    }
}
