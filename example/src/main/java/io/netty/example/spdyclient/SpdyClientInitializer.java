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
package io.netty.example.spdyclient;

import static io.netty.handler.codec.spdy.SpdyVersion.SPDY_3_1;
import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.spdy.SpdyFrameDecoder;
import io.netty.handler.codec.spdy.SpdyFrameEncoder;
import io.netty.handler.codec.spdy.SpdyHttpDecoder;
import io.netty.handler.codec.spdy.SpdyHttpEncoder;
import io.netty.handler.codec.spdy.SpdySessionHandler;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;

public class SpdyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final HttpResponseClientHandler httpResponseHandler;

    public SpdyClientInitializer(HttpResponseClientHandler httpResponseHandler) {
        this.httpResponseHandler = httpResponseHandler;
    }

    private static final int MAX_SPDY_CONTENT_LENGTH = 1024 * 1024; // 1 MB

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        SSLEngine engine = SecureChatSslContextFactory.getClientContext().createSSLEngine();
        engine.setUseClientMode(true);
        NextProtoNego.put(engine, new SpdyClientProvider());
        NextProtoNego.debug = true;

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("ssl", new SslHandler(engine));
        pipeline.addLast("spdyEncoder", new SpdyFrameEncoder(SPDY_3_1));
        pipeline.addLast("spdyDecoder", new SpdyFrameDecoder(SPDY_3_1));
        pipeline.addLast("spdyFrameLogger", new SpdyFrameLogger(INFO));
        pipeline.addLast("spdySessionHandler", new SpdySessionHandler(SPDY_3_1, false));
        pipeline.addLast("spdyHttpEncoder", new SpdyHttpEncoder(SPDY_3_1));
        pipeline.addLast("spdyHttpDecoder", new SpdyHttpDecoder(SPDY_3_1, MAX_SPDY_CONTENT_LENGTH));
        pipeline.addLast("spdyStreamIdHandler", new SpdyClientStreamIdHandler());
        pipeline.addLast("httpHandler", httpResponseHandler);
    }
}
