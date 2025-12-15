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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;

import java.nio.charset.StandardCharsets;

/**
 * WebTransport example demonstrating server and client communication
 */
public class WebTransportExample {
    
    public static void main(String[] args) throws Exception {
        // Start server in a separate thread
        Thread serverThread = new Thread(() -> {
            try {
                startServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        
        // Wait a bit for server to start
        Thread.sleep(2000);
        
        // Start client
        startClient();
        
        // Wait for server to finish
        serverThread.join();
    }
    
    private static void startServer() throws Exception {
        // Load SSL context
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(
                WebTransportExample.class.getResourceAsStream("/server.crt"),
                WebTransportExample.class.getResourceAsStream("/server.key"))
                .build();
        
        // Create WebTransport observer
        WebTransportObserver observer = new WebTransportObserver() {
            @Override
            public void onSessionEstablished(WebTransportSession session) {
                System.out.println("Server: Session established");
            }
            
            @Override
            public void onSessionClosed(WebTransportSession session, Throwable cause) {
                System.out.println("Server: Session closed");
            }
            
            @Override
            public void onBidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Server: Bidirectional stream created: " + stream.id());
                stream.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ByteBuf) {
                            ByteBuf buf = (ByteBuf) msg;
                            String message = buf.toString(StandardCharsets.UTF_8);
                            System.out.println("Server: Received message: " + message);
                            // Echo back
                            ByteBuf response = Unpooled.copiedBuffer("ECHO: " + message, StandardCharsets.UTF_8);
                            ctx.writeAndFlush(response);
                        }
                    }
                });
            }
            
            @Override
            public void onUnidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Server: Unidirectional stream created: " + stream.id());
            }
            
            @Override
            public void onStreamClosed(QuicStreamChannel stream, Throwable cause) {
                System.out.println("Server: Stream closed: " + stream.id());
            }
            
            @Override
            public void onDatagramSent(WebTransportSession session, ByteBuf data) {
                System.out.println("Server: Datagram sent: " + data.readableBytes() + " bytes");
            }
            
            @Override
            public void onDatagramReceived(WebTransportSession session, ByteBuf data) {
                String message = data.toString(StandardCharsets.UTF_8);
                System.out.println("Server: Datagram received: " + message);
            }
            
            @Override
            public void onError(WebTransportSession session, Throwable cause) {
                System.err.println("Server: Error: " + cause.getMessage());
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
    
    private static void startClient() throws Exception {
        // Load SSL context
        QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(WebTransportExample.class.getResourceAsStream("/server.crt"))
                .build();
        
        // Create WebTransport observer
        WebTransportObserver observer = new WebTransportObserver() {
            @Override
            public void onSessionEstablished(WebTransportSession session) {
                System.out.println("Client: Session established");
                
                // Send datagram
                ByteBuf datagram = Unpooled.copiedBuffer("Hello from datagram", StandardCharsets.UTF_8);
                session.sendDatagram(datagram);
                
                // Create bidirectional stream
                session.createBidirectionalStream().addListener(future -> {
                    if (future.isSuccess()) {
                        QuicStreamChannel stream = (QuicStreamChannel) future.get();
                        System.out.println("Client: Bidirectional stream created: " + stream.id());
                        // Send message
                        ByteBuf message = Unpooled.copiedBuffer("Hello from stream", StandardCharsets.UTF_8);
                        stream.writeAndFlush(message);
                        // Read response
                        stream.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof ByteBuf) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    String response = buf.toString(StandardCharsets.UTF_8);
                                    System.out.println("Client: Received response: " + response);
                                    // Close stream after receiving response
                                    stream.close();
                                    // Close session after 1 second
                                    ctx.executor().schedule(() -> session.close(), 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                                }
                            }
                        });
                    }
                });
            }
            
            @Override
            public void onSessionClosed(WebTransportSession session, Throwable cause) {
                System.out.println("Client: Session closed");
            }
            
            @Override
            public void onBidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Client: Bidirectional stream created: " + stream.id());
            }
            
            @Override
            public void onUnidirectionalStreamCreated(QuicStreamChannel stream) {
                System.out.println("Client: Unidirectional stream created: " + stream.id());
            }
            
            @Override
            public void onStreamClosed(QuicStreamChannel stream, Throwable cause) {
                System.out.println("Client: Stream closed: " + stream.id());
            }
            
            @Override
            public void onDatagramSent(WebTransportSession session, ByteBuf data) {
                System.out.println("Client: Datagram sent: " + data.readableBytes() + " bytes");
            }
            
            @Override
            public void onDatagramReceived(WebTransportSession session, ByteBuf data) {
                String message = data.toString(StandardCharsets.UTF_8);
                System.out.println("Client: Datagram received: " + message);
            }
            
            @Override
            public void onError(WebTransportSession session, Throwable cause) {
                System.err.println("Client: Error: " + cause.getMessage());
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