/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http3;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;

/**
 * WebTransport client implementation
 */
public class WebTransportClient {
    
    private final String host;
    private final int port;
    private final QuicSslContext sslContext;
    private final WebTransportObserver observer;
    private EventLoopGroup group;
    private WebTransportSession session;
    
    public WebTransportClient(String host, int port, QuicSslContext sslContext, WebTransportObserver observer) {
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
        this.observer = observer;
    }
    
    public ChannelFuture connect() throws Exception {
        group = new NioEventLoopGroup();
        
        // Create QUIC client codec
        QuicClientCodec codec = QuicClientCodecBuilder.forClient(sslContext)
                .maxIdleTimeout(30000)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .build();
        
        // Create HTTP/3 handler
        Http3ClientConnectionHandler http3Handler = new Http3ClientConnectionHandler();
        
        // Create client bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .childHandler(new WebTransportChannelInitializer(observer, http3Handler));
        
        // Connect to server
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
        System.out.println("Connected to WebTransport server at " + host + ":" + port);
        
        // Send WebTransport CONNECT request
        sendConnectRequest(future.channel());
        
        return future;
    }
    
    private void sendConnectRequest(io.netty.channel.Channel channel) {
        // Create CONNECT request
        Http3RequestStreamFrame request = new DefaultHttp3RequestStreamFrame(HttpMethod.CONNECT, 
                "webtransport://" + host + ":" + port, HttpVersion.HTTP_1_1, new DefaultHttp3Headers());
        // Add WebTransport handshake header
        request.headers().add(AsciiString.cached("sec-webtransport-http3-draft02"), "1");
        
        // Send request
        channel.writeAndFlush(request);
    }
    
    public void disconnect() throws Exception {
        if (session != null) {
            session.close();
        }
        group.shutdownGracefully().sync();
    }
    
    public WebTransportSession getSession() {
        return session;
    }
    
    public static void main(String[] args) throws Exception {
        // Load SSL context
        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(WebTransportClient.class.getResourceAsStream("/server.crt"))
                .build();
        
        // Create WebTransport observer
        WebTransportObserver observer = new WebTransportObserver() {
            @Override
            public void onSessionEstablished(WebTransportSession session) {
                System.out.println("WebTransport session established");
            }
            
            @Override
            public void onSessionClosed(WebTransportSession session, Throwable cause) {
                System.out.println("WebTransport session closed");
            }
            
            @Override
            public void onBidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Bidirectional stream created: " + stream.id());
            }
            
            @Override
            public void onUnidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Unidirectional stream created: " + stream.id());
            }
            
            @Override
            public void onStreamClosed(QuicStreamChannel stream, Throwable cause) {
                System.out.println("Stream closed: " + stream.id());
            }
            
            @Override
            public void onDatagramSent(WebTransportSession session, io.netty.buffer.ByteBuf data) {
                System.out.println("Datagram sent: " + data.readableBytes() + " bytes");
            }
            
            @Override
            public void onDatagramReceived(WebTransportSession session, io.netty.buffer.ByteBuf data) {
                System.out.println("Datagram received: " + data.readableBytes() + " bytes");
            }
            
            @Override
            public void onError(WebTransportSession session, Throwable cause) {
                System.err.println("Error: " + cause.getMessage());
            }
        };
        
        // Create WebTransport client
        WebTransportClient client = new WebTransportClient("localhost", 4433, sslContext, observer);
        ChannelFuture future = client.connect();
        
        // Wait until the client socket is closed
        future.channel().closeFuture().sync();
        
        // Disconnect
        client.disconnect();
    }
}