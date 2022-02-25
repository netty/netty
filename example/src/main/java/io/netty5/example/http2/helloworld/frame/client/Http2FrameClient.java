/*
 * Copyright 2020 The Netty Project
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
package io.netty5.example.http2.helloworld.frame.client;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http2.DefaultHttp2Headers;
import io.netty5.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty5.handler.codec.http2.Http2HeadersFrame;
import io.netty5.handler.codec.http2.Http2SecurityUtil;
import io.netty5.handler.codec.http2.Http2StreamChannel;
import io.netty5.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty5.handler.ssl.ApplicationProtocolConfig;
import io.netty5.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty5.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty5.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty5.handler.ssl.ApplicationProtocolNames;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.SupportedCipherSuiteFilter;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server using the newer HTTP2
 * approach (via {@link io.netty5.handler.codec.http2.Http2FrameCodec}).
 * When run from the command-line, sends a single HEADERS frame (with prior knowledge) to
 * the server configured at host:port/path.
 * You should include {@link io.netty5.handler.codec.http2.Http2ClientUpgradeCodec} if the
 * HTTP/2 server you are hitting doesn't support h2c/prior knowledge.
 */
public final class Http2FrameClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
    static final String PATH = System.getProperty("path", "/");

    private Http2FrameClient() {
    }

    public static void main(String[] args) throws Exception {
        final EventLoopGroup clientWorkerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());

        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            final SslProvider provider =
                    SslProvider.isAlpnSupported(SslProvider.OPENSSL)? SslProvider.OPENSSL : SslProvider.JDK;
            sslCtx = SslContextBuilder.forClient()
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
        } else {
            sslCtx = null;
        }

        try {
            final Bootstrap b = new Bootstrap();
            b.group(clientWorkerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(HOST, PORT);
            b.handler(new Http2ClientFrameInitializer(sslCtx));

            // Start the client.
            final Channel channel = b.connect().get();
            System.out.println("Connected to [" + HOST + ':' + PORT + ']');

            final Http2ClientStreamFrameResponseHandler streamFrameResponseHandler =
                    new Http2ClientStreamFrameResponseHandler();

            final Http2StreamChannelBootstrap streamChannelBootstrap = new Http2StreamChannelBootstrap(channel);
            final Http2StreamChannel streamChannel = streamChannelBootstrap.open().syncUninterruptibly().getNow();
            streamChannel.pipeline().addLast(streamFrameResponseHandler);

            // Send request (a HTTP/2 HEADERS frame - with ':method = GET' in this case)
            final DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.method("GET");
            headers.path(PATH);
            headers.scheme(SSL? "https" : "http");
            final Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers, true);
            streamChannel.writeAndFlush(headersFrame);
            System.out.println("Sent HTTP/2 GET request to " + PATH);

            // Wait for the responses (or for the latch to expire), then clean up the connections
            if (!streamFrameResponseHandler.responseSuccessfullyCompleted()) {
                System.err.println("Did not get HTTP/2 response in expected time.");
            }

            System.out.println("Finished HTTP/2 request, will close the connection.");

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            clientWorkerGroup.shutdownGracefully();
        }
    }

}
