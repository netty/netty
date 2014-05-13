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

import static io.netty.example.http2.Http2ExampleUtil.parseEndpointConfig;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.http2.Http2ExampleUtil.EndpointConfig;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;

import java.net.InetSocketAddress;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged. When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a "Hello World" response.
 * <p>
 * To client accepts command-line arguments for {@code -host=<host/ip>}
 * <i>(default="localhost")</i>, {@code -port=<port number>} <i>(default: http=8080,
 * https=8443)</i>, and {@code -ssl=<true/false>} <i>(default=false)</i>.
 */
public class Http2Client {

    private final EndpointConfig config;
    private Http2ClientConnectionHandler http2ConnectionHandler;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public Http2Client(EndpointConfig config) {
        this.config = config;
    }

    /**
     * Starts the client and waits for the HTTP/2 upgrade to occur.
     */
    public void start() throws Exception {
        if (channel != null) {
            System.out.println("Already running!");
            return;
        }

        workerGroup = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(new InetSocketAddress(config.host(), config.port()));
        Http2ClientInitializer initializer = new Http2ClientInitializer(config.isSsl());
        b.handler(initializer);

        // Start the client.
        channel = b.connect().syncUninterruptibly().channel();
        System.out.println("Connected to [" + config.host() + ':' + config.port() + ']');

        // Wait for the HTTP/2 upgrade to occur.
        http2ConnectionHandler = initializer.connectionHandler();
        http2ConnectionHandler.awaitInitialization();
    }

    /**
     * Sends the given request to the server.
     */
    public void sendRequest(FullHttpRequest request) throws Exception {
        ChannelFuture requestFuture = channel.writeAndFlush(request).sync();
        System.out.println("Back from sending headers...");
        if (!requestFuture.isSuccess()) {
            throw new RuntimeException(requestFuture.cause());
        }
    }

    /**
     * Waits for the full response to be received.
     */
    public void awaitResponse() throws Exception {
        http2ConnectionHandler.awaitResponse();
    }

    /**
     * Closes the channel and waits for shutdown to complete.
     */
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

    public static void main(String[] args) throws Exception {
        EndpointConfig config = parseEndpointConfig(args);
        System.out.println(config);

        final Http2Client client = new Http2Client(config);
        try {
            // Start the client and wait for the HTTP/2 upgrade to complete.
            client.start();

            // Create a simple GET request with just headers.
            FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/whatever");
            request.headers().add(HOST, config.host());

            System.out.println("Sending request...");
            ChannelFuture requestFuture = client.channel.writeAndFlush(request).sync();
            System.out.println("Back from sending headers...");
            if (!requestFuture.isSuccess()) {
                requestFuture.cause().printStackTrace();
                return;
            }

            // Waits for the complete response
            client.awaitResponse();
            System.out.println("Finished HTTP/2 request");
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            client.stop();
        }
    }
}
