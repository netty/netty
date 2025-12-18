/*
 * Copyright 2024 The Netty Project
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
import io.netty.util.CharsetUtil;

/**
 * WebTransport 使用示例，展示如何建立会话、创建流和发送数据报。
 */
public class WebTransportExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting WebTransportExample...");
        
        // 启动服务器
        System.out.println("Starting WebTransport server...");
        WebTransportServer server = new WebTransportServer(8443, new CustomWebTransportObserver());
        ChannelFuture serverFuture = server.start();
        System.out.println("WebTransport server started successfully.");
        
        // 启动客户端
        System.out.println("Starting WebTransport client...");
        WebTransportClient client = new WebTransportClient("localhost", 8443, new CustomWebTransportObserver());
        ChannelFuture clientFuture = client.connect();
        System.out.println("WebTransport client started successfully.");
        
        // 等待 WebTransport 会话建立
        System.out.println("Waiting for WebTransport session to be established...");
        WebTransportSession session = null;
        int attempts = 0;
        while (session == null && attempts < 50) {
            session = client.getSession();
            if (session == null) {
                Thread.sleep(100);
                attempts++;
            }
        }
        System.out.println("WebTransport session obtained: " + (session != null ? "success" : "failed"));
        
        if (session != null) {
            // 创建双向流并发送数据
            session.createBidirectionalStream().addListener(future -> {
                if (future.isSuccess()) {
                    WebTransportStream stream = (WebTransportStream) future.getNow();
                    ByteBuf buf = Unpooled.copiedBuffer("Hello, WebTransport!", CharsetUtil.UTF_8);
                    stream.writeAndFlush(buf).addListener(f -> {
                        if (f.isSuccess()) {
                            System.out.println("Data sent over stream: Hello, WebTransport!");
                        }
                    });
                }
            });
            
            // 发送数据报
            ByteBuf datagram = Unpooled.copiedBuffer("Hello, WebTransport Datagram!", CharsetUtil.UTF_8);
            session.sendDatagram(datagram).addListener(f -> {
                if (f.isSuccess()) {
                    System.out.println("Datagram sent: Hello, WebTransport Datagram!");
                }
            });
        }
        
        // 等待服务器和客户端关闭
        serverFuture.channel().closeFuture().sync();
        clientFuture.channel().closeFuture().sync();
        
        // 关闭服务器和客户端
        server.shutdown();
        client.close();
    }

    /**
     * 自定义 WebTransportObserver，用于观测会话、流和数据报的事件。
     */
    private static class CustomWebTransportObserver extends NoopWebTransportObserver {

        @Override
        public void onSessionCreated(WebTransportSession session) {
            System.out.println("Session created: " + session.sessionId());
        }

        @Override
        public void onSessionClosed(WebTransportSession session, io.netty.util.concurrent.Future<Void> future) {
            System.out.println("Session closed: " + session.sessionId());
        }

        @Override
        public void onStreamCreated(WebTransportStream stream) {
            System.out.println("Stream created: " + stream.streamId());
        }

        @Override
        public void onStreamClosed(WebTransportStream stream, io.netty.util.concurrent.Future<Void> future) {
            System.out.println("Stream closed: " + stream.streamId());
        }

        @Override
        public void onDatagramReceived(WebTransportSession session, WebTransportDatagram datagram) {
            System.out.println("Datagram received: " + datagram.content().toString(CharsetUtil.UTF_8));
        }

        @Override
        public void onDatagramSent(WebTransportSession session, WebTransportDatagram datagram) {
            System.out.println("Datagram sent: " + datagram.content().toString(CharsetUtil.UTF_8));
        }

        @Override
        public void onError(io.netty.channel.Channel channel, Throwable cause) {
            System.err.println("Error: " + cause.getMessage());
            cause.printStackTrace();
        }
    }
}
