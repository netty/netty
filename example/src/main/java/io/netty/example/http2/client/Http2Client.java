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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged. When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a "Hello World" response.
 */
public final class Http2Client {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
    static final String URL = System.getProperty("url", "/whatever");
    static final String URL2 = System.getProperty("url2");
    static final String URL2DATA = System.getProperty("url2data", "test data!");

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContext.newClientContext(
                    null, InsecureTrustManagerFactory.INSTANCE, null,
                    Arrays.asList(SelectedProtocol.HTTP_2.protocolName(), SelectedProtocol.HTTP_1_1.protocolName()),
                    0, 0);
        } else {
            sslCtx = null;
        }

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE);

        try {
            // Configure the client.
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(HOST, PORT);
            b.handler(initializer);

            // Start the client.
            Channel channel = b.connect().syncUninterruptibly().channel();
            System.out.println("Connected to [" + HOST + ':' + PORT + ']');

            // Wait for the HTTP/2 upgrade to occur.
            Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
            http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);

            HttpResponseHandler responseHandler = initializer.responseHandler();
            int streamId = 3;
            URI hostName = URI.create((SSL ? "https" : "http") + "://" + HOST + ':' + PORT);
            System.err.println("Sending request(s)...");
            if (URL != null) {
                // Create a simple GET request.
                FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, URL);
                request.headers().add(HttpHeaders.Names.HOST, hostName);
                request.headers().add(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
                channel.writeAndFlush(request);
                responseHandler.put(streamId, channel.newPromise());
                streamId += 2;
            }
            if (URL2 != null) {
                // Create a simple POST request with a body.
                FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, URL2,
                                Unpooled.copiedBuffer(URL2DATA.getBytes(CharsetUtil.UTF_8)));
                request.headers().add(HttpHeaders.Names.HOST, hostName);
                request.headers().add(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
                channel.writeAndFlush(request);
                responseHandler.put(streamId, channel.newPromise());
                streamId += 2;
            }
            responseHandler.awaitResponses(5, TimeUnit.SECONDS);
            System.out.println("Finished HTTP/2 request(s)");

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
