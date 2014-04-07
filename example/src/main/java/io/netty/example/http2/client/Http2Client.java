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
package io.netty.example.http2.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.draft10.DefaultHttp2Headers;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames
 * are logged.
 */
public class Http2Client {

    private final String host;
    private final int port;
    private final Http2ResponseClientHandler httpResponseHandler;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public Http2Client(String host, int port) {
        this.host = host;
        this.port = port;
        httpResponseHandler = new Http2ResponseClientHandler();
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
        b.handler(new Http2ClientInitializer(httpResponseHandler));

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

    public ChannelFuture send(Http2Frame request) {
        // Sends the HTTP request.
        return channel.writeAndFlush(request);
    }

    public Http2Frame get() {
        Http2Headers headers =
                DefaultHttp2Headers.newBuilder().setAuthority(host)
                        .setMethod(HttpMethod.GET.name()).build();
        return new DefaultHttp2HeadersFrame.Builder().setHeaders(headers).setStreamId(3)
                .setEndOfStream(true).build();
    }

    public BlockingQueue<ChannelFuture> queue() {
        return httpResponseHandler.queue();
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        final Http2Client client = new Http2Client("localhost", port);

        try {
            client.start();
            ChannelFuture requestFuture = client.send(client.get()).sync();

            if (!requestFuture.isSuccess()) {
                requestFuture.cause().printStackTrace();
            }

            // Waits for the complete response
            ChannelFuture responseFuture = client.queue().poll(5, SECONDS);

            if (!responseFuture.isSuccess()) {
                responseFuture.cause().printStackTrace();
            }

            System.out.println("Finished HTTP/2 request");
        } finally {
            client.stop();
        }
    }
}
