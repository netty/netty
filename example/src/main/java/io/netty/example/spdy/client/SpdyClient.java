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
package io.netty.example.spdy.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.spdy.SpdyOrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.Arrays;

/**
 * An SPDY client that allows you to send HTTP GET to a SPDY server.
 * <p>
 * This class must be run with the JVM parameter: {@code java -Xbootclasspath/p:<path_to_npn_boot_jar> ...}. The
 * "path_to_npn_boot_jar" is the path on the file system for the NPN Boot Jar file which can be downloaded from Maven at
 * coordinates org.mortbay.jetty.npn:npn-boot. Different versions applies to different OpenJDK versions. See
 * <a href="http://www.eclipse.org/jetty/documentation/current/npn-chapter.html">Jetty docs</a> for more information.
 * <p>
 * You may also use the {@code run-example.sh} script to start the client from the command line:
 * <pre>
 *     ./run-example.sh spdy-client
 * </pre>
 */
public final class SpdyClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx = SslContext.newClientContext(
                null, InsecureTrustManagerFactory.INSTANCE, null,
                Arrays.asList(SelectedProtocol.SPDY_3_1.protocolName(), SelectedProtocol.HTTP_1_1.protocolName()),
                0, 0);

        HttpResponseClientHandler httpResponseHandler = new HttpResponseClientHandler();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(HOST, PORT);
            b.handler(new SpdyClientInitializer(sslCtx, httpResponseHandler));

            // Start the client.
            Channel channel = b.connect().syncUninterruptibly().channel();
            System.out.println("Connected to " + HOST + ':' + PORT);

            // Create a GET request.
            HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
            request.headers().set(HttpHeaders.Names.HOST, HOST);
            request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

            // Send the GET request.
            channel.writeAndFlush(request).sync();

            // Waits for the complete HTTP response
            httpResponseHandler.queue().take().sync();
            System.out.println("Finished SPDY HTTP GET");

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
    }
}
