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
package io.netty.example.spdyclient;

import static java.util.concurrent.TimeUnit.SECONDS;
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

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

/**
 * An SPDY client that allows you to send HTTP GET to a SPDY server.
 * <p>
 * This class must be run with the JVM parameter: {@code java -Xbootclasspath/p:<path_to_npn_boot_jar> ...}. The
 * "path_to_npn_boot_jar" is the path on the file system for the NPN Boot Jar file which can be downloaded from Maven at
 * coordinates org.mortbay.jetty.npn:npn-boot. Different versions applies to different OpenJDK versions. See
 * {@link http://www.eclipse.org/jetty/documentation/current/npn-chapter.html Jetty docs} for more information.
 * <p>
 */
public class SpdyClient {

    private final String host;
    private final int port;
    private final HttpResponseClientHandler httpResponseHandler;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public SpdyClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.httpResponseHandler = new HttpResponseClientHandler();
    }

    public void start() {
        if (this.channel != null) {
            System.out.println("Already running!");
            return;
        }

        this.workerGroup = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(new InetSocketAddress(this.host, this.port));
        b.handler(new SpdyClientInitializer(this.httpResponseHandler));

        // Start the client.
        this.channel = b.connect().syncUninterruptibly().channel();
        System.out.println("Connected to [" + this.host + ":" + this.port + "]");
    }

    public void stop() {
        try {
            // Wait until the connection is closed.
            this.channel.close().syncUninterruptibly();
        } finally {
            if (this.workerGroup != null) {
                this.workerGroup.shutdownGracefully();
            }
        }
    }

    public ChannelFuture send(HttpRequest request) {
        // Sends the HTTP request.
        return this.channel.writeAndFlush(request);
    }

    public HttpRequest get() {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
        request.headers().set(HttpHeaders.Names.HOST, this.host);
        request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
        return request;
    }

    public BlockingQueue<ChannelFuture> httpResponseQueue() {
        return this.httpResponseHandler.queue();
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
