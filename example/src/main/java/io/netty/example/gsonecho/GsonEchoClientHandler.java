/*
 * Copyright 2016 The Netty Project
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
package io.netty.example.gsonecho;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler implementation for the Gson echo client.  It initiates the
 * ping-pong traffic between the Gson echo client and server by sending the
 * message to the server.
 */
public class GsonEchoClientHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, String> message;

    /**
     * Creates a client-side handler.
     */
    public GsonEchoClientHandler() {
        Map<String, String> message = new HashMap<String, String>();
        message.put("firstName", "John");
        message.put("lastName", "Snow");
        this.message = Collections.unmodifiableMap(message);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Send the message if this handler is a client-side handler.
        ctx.writeAndFlush(message);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Echo back the received object to the server.
        ctx.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
