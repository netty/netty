/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.dns.doh;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;

import java.net.URL;

class Initializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final URL url;

    Initializer(SslContext sslCtx, URL url) {
        this.sslCtx = sslCtx;
        this.url = url;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) {
        socketChannel.pipeline()
                .addLast(sslCtx.newHandler(socketChannel.alloc(), url.getHost(), url.getDefaultPort()))
                .addLast(new ALPNHandler(url, socketChannel.newPromise()));
    }
}
