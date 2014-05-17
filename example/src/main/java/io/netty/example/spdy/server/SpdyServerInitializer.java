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
package io.netty.example.spdy.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.eclipse.jetty.npn.NextProtoNego;

import javax.net.ssl.SSLEngine;

/**
 * Sets up the Netty pipeline
 */
public class SpdyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;

    public SpdyServerInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();

        SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
        SSLEngine engine = sslHandler.engine();
        p.addLast("ssl", new SslHandler(engine));

        // Setup NextProtoNego with our server provider
        NextProtoNego.put(engine, new SpdyServerProvider());
        NextProtoNego.debug = true;

        // Negotiates with the browser if SPDY or HTTP is going to be used
        p.addLast("handler", new SpdyOrHttpHandler());
    }
}
