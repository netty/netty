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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

import static java.util.concurrent.TimeUnit.*;

/**
 * An SPDY client that allows you to send HTTP GET to a SPDY server.
 * <p>
 * This class must be run with the JVM parameter: {@code java -Xbootclasspath/p:<path_to_npn_boot_jar> ...}. The
 * "path_to_npn_boot_jar" is the path on the file system for the NPN Boot Jar file which can be downloaded from Maven at
 * coordinates org.mortbay.jetty.npn:npn-boot. Different versions applies to different OpenJDK versions. See
 * <a href="http://www.eclipse.org/jetty/documentation/current/npn-chapter.html">Jetty docs</a> for more information.
 * <p>
 * You may also use maven to start the client from the command line:
 * <pre>
 *     mvn exec:exec -Pspdy-client
 * </pre>
 */
public class SpdyClient {

    private final SslContext sslCtx;
    private final String host;
    private final int port;
    private final HttpResponseClientHandler httpResponseHandler;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public SpdyClient(String host, int port) throws SSLException {
        sslCtx = SslContext.newClientContext(SslProvider.JDK, InsecureTrustManagerFactory.INSTANCE);
        this.host = host;
        this.port = port;
        httpResponseHandler = new HttpResponseClientHandler();
    }

    public void start() {
        if (channel != null) {
            System.out.println("Already running!");
            return;
        }

        workerGroup = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(new InetSocketAddress(host, port));
        b.handler(new SpdyClientInitializer(sslCtx, httpResponseHandler));

        // Start the client.
        channel = b.connect().syncUninterruptibly().channel();
        System.out.println("Connected to [" + host + ':' + port + ']');
    }

    public void stop() {
        try {
            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
    }

    public ChannelFuture send(HttpRequest request) {
        // Sends the HTTP request.
        return channel.writeAndFlush(request);
    }

    public HttpRequest get() {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        request.headers().set(HttpHeaders.Names.HOST, host);
        request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
        return request;
    }

    public BlockingQueue<ChannelFuture> httpResponseQueue() {
        return httpResponseHandler.queue();
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        final SpdyClient client = new SpdyClient("localhost", port);

        try {
            client.start();
            ChannelFuture requestFuture = client.send(client.get()).sync();

            if (!requestFuture.isSuccess()) {
                requestFuture.cause().printStackTrace();
            }

            // Waits for the complete HTTP response
            ChannelFuture responseFuture = client.httpResponseQueue().poll(5, SECONDS);

            if (!responseFuture.isSuccess()) {
                responseFuture.cause().printStackTrace();
            }

            System.out.println("Finished SPDY HTTP GET");
        } finally {
            client.stop();
        }
    }
}
