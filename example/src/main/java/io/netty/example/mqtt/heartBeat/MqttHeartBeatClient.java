/*
 * Copyright 2019 The Netty Project
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
package io.netty.example.mqtt.heartBeat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public final class MqttHeartBeatClient {
    private MqttHeartBeatClient() {
    }

    private static final String HOST = System.getProperty("host", "127.0.0.1");
    private static final int PORT = Integer.parseInt(System.getProperty("port", "1883"));
    private static final String CLIENT_ID = System.getProperty("clientId", "guestClient");
    private static final String USER_NAME = System.getProperty("userName", "guest");
    private static final String PASSWORD = System.getProperty("password", "guest");

    public static void main(String[] args) throws Exception {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast("encoder", MqttEncoder.INSTANCE);
                    ch.pipeline().addLast("decoder", new MqttDecoder());
                    ch.pipeline().addLast("heartBeatHandler", new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS));
                    ch.pipeline().addLast("handler", new MqttHeartBeatClientHandler(CLIENT_ID, USER_NAME, PASSWORD));
                }
            });

            ChannelFuture f = b.connect(HOST, PORT).sync();
            System.out.println("Client connected");
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
