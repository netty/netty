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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * WebTransport server implementation
 */
public class WebTransportServer {
    
    private final int port;
    private final QuicSslContext sslContext;
    private final WebTransportObserver observer;
    private EventLoopGroup group;
    private ChannelFuture serverChannelFuture;
    
    public WebTransportServer(int port, QuicSslContext sslContext, WebTransportObserver observer) {
        this.port = port;
        this.sslContext = sslContext;
        this.observer = observer;
    }
    
    public ChannelFuture start() throws Exception {
        group = new NioEventLoopGroup();
        
        // Create QUIC server codec
        QuicServerCodec codec = QuicServerCodecBuilder.forServer(sslContext)
                .maxIdleTimeout(30000)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .build();
        
        // Create HTTP/3 handler
        Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler();
        
        // Create server bootstrap
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .childHandler(new WebTransportChannelInitializer(observer, http3Handler));
        
        // Bind to port
        serverChannelFuture = bootstrap.bind(new InetSocketAddress(port)).sync();
        System.out.println("WebTransport server started on port " + port);
        
        return serverChannelFuture;
    }
    
    public void stop() throws Exception {
        if (serverChannelFuture != null) {
            serverChannelFuture.channel().close().sync();
        }
        if (group != null) {
            group.shutdownGracefully().sync();
        }
    }
    
    public static void main(String[] args) throws Exception {
        // Load SSL context
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(
                WebTransportServer.class.getResourceAsStream("/server.crt"),
                WebTransportServer.class.getResourceAsStream("/server.key"))
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
        
        // Create WebTransport server
        WebTransportServer server = new WebTransportServer(4433, sslContext, observer);
        ChannelFuture future = server.start();
        
        // Wait until the server socket is closed
        future.channel().closeFuture().sync();
        
        // Stop server
        server.stop();
    }
}