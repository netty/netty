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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DoHQueryEncoder;
import io.netty.handler.codec.dns.DoHResponseDecoder;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

import java.net.URL;

public class Initializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final URL url;

    public Initializer(SslContext sslCtx, URL url) {
        this.sslCtx = sslCtx;
        this.url = url;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) {
        socketChannel.pipeline()
                .addLast(sslCtx.newHandler(socketChannel.alloc(), url.getHost(), url.getDefaultPort()))
                .addLast(new ALPNHandler(url));
    }

    private static final class ALPNHandler extends ApplicationProtocolNegotiationHandler {

        private final URL url;

        private ALPNHandler(URL url) {
            super(ApplicationProtocolNames.HTTP_1_1);
            this.url = url;
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (protocol.equalsIgnoreCase(ApplicationProtocolNames.HTTP_2)) {
                Http2Connection connection = new DefaultHttp2Connection(false);

                InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                        .propagateSettings(false)
                        .validateHttpHeaders(true)
                        .maxContentLength(1024 * 64)
                        .build();

                HttpToHttp2ConnectionHandler http2Handler = new HttpToHttp2ConnectionHandlerBuilder()
                        .frameListener(new DelegatingDecompressorFrameListener(connection, listener))
                        .connection(connection)
                        .build();

                ctx.pipeline().addLast(http2Handler,
                        new HttpObjectAggregator(1024 * 64, true),
                        new DoHQueryEncoder(true, url),
                        new DoHResponseDecoder(),
                        new Handler());
            } else if (protocol.equalsIgnoreCase(ApplicationProtocolNames.HTTP_1_1)) {
                ctx.pipeline().addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(1024 * 64, true),
                        new DoHQueryEncoder(url),
                        new DoHResponseDecoder(),
                        new Handler());
            } else {
                throw new IllegalArgumentException("Unsupported Protocol: " + protocol);
            }
        }

        @Override
        protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
        }
    }
}
