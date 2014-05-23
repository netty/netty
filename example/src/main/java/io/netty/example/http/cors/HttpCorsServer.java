/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.http.cors;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * This example server aims to demonstrate
 * <a href="http://www.w3.org/TR/cors/">Cross Origin Resource Sharing</a> (CORS) in Netty.
 * It does not have a client like most of the other examples, but instead has
 * a html page that is loaded to try out CORS support in a web brower.
 * <p>
 *
 * CORS is configured in {@link HttpCorsServerInitializer} and by updating the config you can
 * try out various combinations, like using a specific origin instead of a
 * wildcard origin ('*').
 * <p>
 *
 * The file {@code src/main/resources/cors/cors.html} contains a very basic example client
 * which can be used to try out different configurations. For example, you can add
 * custom headers to force a CORS preflight request to make the request fail. Then
 * to enable a successful request, configure the CorsHandler to allow that/those
 * request headers.
 *
 * <h2>Testing CORS</h2>
 * You can either load the file {@code src/main/resources/cors/cors.html} using a web server
 * or load it from the file system using a web browser.
 *
 * <h3>Using a web server</h3>
 * To test CORS support you can serve the file {@code src/main/resources/cors/cors.html}
 * using a web server. You can then add a new host name to your systems hosts file, for
 * example if you are on Linux you may update /etc/hosts to add an addtional name
 * for you local system:
 * <pre>
 * 127.0.0.1   localhost domain1.com
 * </pre>
 * Now, you should be able to access {@code http://domain1.com/cors.html} depending on how you
 * have configured you local web server the exact url may differ.
 *
 * <h3>Using a web browser</h3>
 * Open the file {@code src/main/resources/cors/cors.html} in a web browser. You should see
 * loaded page and in the text area the following message:
 * <pre>
 * 'CORS is not working'
 * </pre>
 *
 * If you inspect the headers being sent using your browser you'll see that the 'Origin'
 * request header is {@code 'null'}. This is expected and happens when you load a file from the
 * local file system. Netty can handle this by configuring the CorsHandler which is done
 * in the {@link HttpCorsServerInitializer}.
 *
 */
public final class HttpCorsServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new HttpCorsServerInitializer(sslCtx));

            b.bind(PORT).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
