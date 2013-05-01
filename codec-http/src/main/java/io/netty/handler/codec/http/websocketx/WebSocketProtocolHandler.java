/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.http.websocketx;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

abstract class WebSocketProtocolHandler  extends ChannelInboundMessageHandlerAdapter<WebSocketFrame> {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ctx.channel().write(new PongWebSocketFrame(frame.content()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            // Pong frames need to get ignored
            return;
        }

        frame.retain();
        ctx.nextInboundMessageBuffer().add(frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
