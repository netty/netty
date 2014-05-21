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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

import static java.util.concurrent.TimeUnit.*;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged.
 */
public class Http2Client {

    private final SslContext sslCtx;
    private final String host;
    private final int port;
    private final Http2ClientConnectionHandler http2ConnectionHandler;
    private Channel channel;
    private EventLoopGroup workerGroup;

    public Http2Client(String host, int port) throws SSLException {
        sslCtx = SslContext.newClientContext(
                null, InsecureTrustManagerFactory.INSTANCE, null,
                SslContext.newApplicationProtocolSelector(
                        SelectedProtocol.HTTP_2.protocolName(),
                        SelectedProtocol.HTTP_1_1.protocolName()),
                0, 0);

        this.host = host;
        this.port = port;
        http2ConnectionHandler = new Http2ClientConnectionHandler();
    }

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
        b.remoteAddress(new InetSocketAddress(host, port));
        b.handler(new Http2ClientInitializer(sslCtx, http2ConnectionHandler));

        // Start the client.
        channel = b.connect().syncUninterruptibly().channel();
        http2ConnectionHandler.awaitInitialization();
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

    public ChannelFuture sendHeaders(int streamId, Http2Headers headers) throws Http2Exception {
        return http2ConnectionHandler.writeHeaders(streamId, headers, 0, true, true);
    }

    public ChannelFuture send(int streamId, ByteBuf data, int padding, boolean endStream,
            boolean endSegment, boolean compressed) throws Http2Exception {
        return http2ConnectionHandler.writeData(streamId, data, padding, endStream, endSegment,
                compressed);
    }

    public Http2Headers headers() {
        return DefaultHttp2Headers.newBuilder().authority(host).method(HttpMethod.GET.name())
                .build();
    }

    public BlockingQueue<ChannelFuture> queue() {
        return http2ConnectionHandler.queue();
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
            System.out.println("Sending headers...");
            ChannelFuture requestFuture = client.sendHeaders(3, client.headers()).sync();
            System.out.println("Back from sending headers...");
            if (!requestFuture.isSuccess()) {
                requestFuture.cause().printStackTrace();
            }

            // Waits for the complete response
            ChannelFuture responseFuture = client.queue().poll(5, SECONDS);

            if (!responseFuture.isSuccess()) {
                responseFuture.cause().printStackTrace();
            }

            System.out.println("Finished HTTP/2 request");
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            client.stop();
        }
    }
}
