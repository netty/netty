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
package io.netty.example.stomp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.stomp.FullStompFrame;

/**
 * STOMP client inbound handler implementation, which just passes received messages to listener
 */
public class StompClientHandler extends SimpleChannelInboundHandler<FullStompFrame> {
    private final StompFrameListener listener;

    public StompClientHandler(StompFrameListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        this.listener = listener;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullStompFrame msg) throws Exception {
        listener.onFrame(msg);
    }
}
