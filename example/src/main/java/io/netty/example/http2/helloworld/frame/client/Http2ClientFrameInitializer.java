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
package io.netty.example.http2.helloworld.frame.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Configures client pipeline to support HTTP/2 frames via {@link Http2FrameCodec} and {@link Http2MultiplexHandler}.
 */
public final class Http2ClientFrameInitializer extends ChannelInitializer<Channel> {

    private boolean isSsl;

    public Http2ClientFrameInitializer(boolean isSsl) {
        this.isSsl = isSsl;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        // ensure that our 'trust all' SSL handler is the first in the pipeline if SSL is enabled.
        if (isSsl) {
            ch.pipeline().addFirst("SSL Handler", this.buildClientSslContext().newHandler(ch.alloc()));
        }

        final Http2FrameCodec http2FrameCodec = Http2FrameCodecBuilder.forClient()
            .initialSettings(Http2Settings.defaultSettings()) // this is the default, but shows it can be changed.
            .build();
        ch.pipeline().addLast("HTTP2 Frame Codec", http2FrameCodec);
        ch.pipeline().addLast("HTTP2 Multiplex Handler", new Http2MultiplexHandler(new SimpleChannelInboundHandler() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                // NOOP (this is the handler for 'inbound' streams, which is not relevant in this example)
            }
        }));
    }

    private SslContext buildClientSslContext() throws Exception {
        final SslProvider provider =
                SslProvider.isAlpnSupported(SslProvider.OPENSSL)? SslProvider.OPENSSL : SslProvider.JDK;
        return SslContextBuilder.forClient()
            .sslProvider(provider)
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            // you probably won't want to use this in production, but it is fine for this example:
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocolConfig(new ApplicationProtocolConfig(
                Protocol.ALPN,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1))
            .build();
    }

}
