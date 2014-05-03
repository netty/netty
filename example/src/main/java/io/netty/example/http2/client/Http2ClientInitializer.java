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
package io.netty.example.http2.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandler;
import io.netty.handler.codec.http2.Http2PrefaceHandler;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.npn.NextProtoNego;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final AbstractHttp2ConnectionHandler connectionHandler;

    public Http2ClientInitializer(AbstractHttp2ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        SSLEngine engine = SecureChatSslContextFactory.getClientContext().createSSLEngine();
        engine.setUseClientMode(true);
        NextProtoNego.put(engine, new Http2ClientProvider());
        NextProtoNego.debug = true;

        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("ssl", new SslHandler(engine));
        pipeline.addLast("http2PrefaceHandler", new Http2PrefaceHandler(false));
        pipeline.addLast("http2ConnectionHandler", connectionHandler);
    }
}
