/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.stomp.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.internal.ObjectUtil;

public class StompWebSocketChatServerInitializer extends ChannelInitializer<SocketChannel> {

    private final String chatPath;
    private final StompWebSocketProtocolCodec stompWebSocketProtocolCodec;

    public StompWebSocketChatServerInitializer(String chatPath) {
        this.chatPath = ObjectUtil.checkNotNull(chatPath, "chatPath");
        stompWebSocketProtocolCodec = new StompWebSocketProtocolCodec();
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        channel.pipeline()
               .addLast(new HttpServerCodec())
               .addLast(new HttpObjectAggregator(65536))
               .addLast(StompWebSocketClientPageHandler.INSTANCE)
               .addLast(new WebSocketServerProtocolHandler(chatPath, StompVersion.SUB_PROTOCOLS))
               .addLast(stompWebSocketProtocolCodec);
    }
}
